package server;

import com.google.gson.*;
import gallaemallae.game.*;
import gallaemallae.game.GameState.Seat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Vector 방 목록과 네트워크 전송을 담당한다. 규칙·정산은 RoomRules에 위임한다. */
public class RoomManager implements AutoCloseable {
    private final Vector<GameRoom> rooms = new Vector<>();
    private final Set<String> nicknames = new HashSet<>();
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final LongSupplier clock;
    private final Random random;
    private final long initialBalance;
    private ScheduledExecutorService timer;
    private record Event(GameUser user, String type, JsonObject payload) { }

    public RoomManager() {
        this(() -> System.nanoTime() / 1_000_000L, new Random(),
                Long.getLong("gsg.initialBalance", 1_000_000L));
    }
    RoomManager(LongSupplier clock, Random random, long initialBalance) {
        if (initialBalance <= 0 || initialBalance > Long.MAX_VALUE / 2)
            throw new IllegalArgumentException("초기 잔액 범위를 확인하세요.");
        this.clock = clock; this.random = random; this.initialBalance = initialBalance;
    }
    long initialBalance() { return initialBalance; }

    // 수신 순서대로 상태 변경 → ACK/ERROR → 이벤트를 처리한다.
    public synchronized void handleRequest(GameUser user, String type, String id, JsonObject payload) {
        List<Event> events = new ArrayList<>();
        try {
            if (user.currentRoom != null) expireRoom(user.currentRoom, clock.getAsLong(), events);
            if (!"JOIN".equals(type) && user.nickname == null)
                throw new RequestException("NOT_JOINED", "먼저 닉네임을 등록하세요.");
            JsonObject ack = new JsonObject(); ack.addProperty("requestType", type);
            switch (type) {
                case "JOIN" -> {
                    if (user.nickname != null) throw new RequestException("ALREADY_JOINED", "이미 등록된 연결입니다.");
                    String name = text(payload, "nickname", 12);
                    if (!nicknames.add(name)) throw new RequestException("NICKNAME_TAKEN", "사용 중인 닉네임입니다.");
                    user.nickname = name; ack.addProperty("playerId", user.playerId);
                    ack.addProperty("nickname", name); ack.addProperty("balance", user.balance);
                }
                case "LIST_ROOMS" -> events.add(new Event(user, "ROOM_LIST", roomList()));
                case "CREATE_ROOM" -> {
                    requireOutside(user);
                    GameRoom room = new GameRoom("room-" + UUID.randomUUID(), text(payload, "title", 30), random);
                    add(room, user, Seat.FIRST); rooms.add(room);
                    ack.addProperty("roomId", room.roomId); updates(room, events, false);
                }
                case "JOIN_ROOM" -> {
                    requireOutside(user);
                    String roomId = string(payload, "roomId");
                    GameRoom room = rooms.stream().filter(r -> r.roomId.equals(roomId)).findFirst()
                            .orElseThrow(() -> new RequestException("ROOM_NOT_FOUND", "없는 방입니다."));
                    RoomRules.Status status = room.rules.status();
                    if (status != RoomRules.Status.WAITING && status != RoomRules.Status.READY)
                        throw new RequestException("GAME_IN_PROGRESS", "선 선택 또는 게임 진행 중입니다.");
                    if (room.users.size() >= 2) throw new RequestException("ROOM_FULL", "방 정원은 2명입니다.");
                    add(room, user, room.seats.containsKey(Seat.FIRST) ? Seat.SECOND : Seat.FIRST);
                    ack.addProperty("roomId", room.roomId); updates(room, events, false);
                }
                case "READY" -> {
                    GameRoom room = requireRoom(user); Seat seat = room.seatFor(user);
                    JsonElement ready = payload.get("ready");
                    if (ready == null || !ready.isJsonPrimitive() || !ready.getAsJsonPrimitive().isBoolean())
                        throw new RequestException("INVALID_MESSAGE", "ready는 true 또는 false여야 합니다.");
                    RoomRules.Status before = room.rules.status();
                    if (before == RoomRules.Status.PLAYING || before == RoomRules.Status.CLOSED)
                        throw new RequestException("GAME_IN_PROGRESS", "게임 중에는 준비를 변경할 수 없습니다.");
                    if (ready.getAsBoolean()) {
                        boolean startsRound = room.rules.dealer() != null
                                && room.rules.present(MatchEngine.other(seat))
                                && room.rules.isReady(MatchEngine.other(seat));
                        room.rules.ready(seat, clock.getAsLong());
                        if (startsRound) room.currentRound++;
                    }
                    else room.rules.cancelReady(seat);
                    if (room.rules.status() == RoomRules.Status.DEALER_SELECTION && before != RoomRules.Status.DEALER_SELECTION)
                        room.selectionSequence++;
                    ack.addProperty("roomId", room.roomId); ack.addProperty("ready", room.rules.isReady(seat));
                    updates(room, events, false);
                }
                case "SELECT_DEALER" -> {
                    GameRoom room = requireRoom(user); Seat seat = room.seatFor(user);
                    if (room.rules.status() != RoomRules.Status.DEALER_SELECTION)
                        throw new RequestException("INVALID_PHASE", "선 선택 중이 아닙니다.");
                    if (!room.selectionId().equals(string(payload, "selectionId")))
                        throw new RequestException("STALE_STATE", "이전 선 선택 요청입니다.");
                    int draw = (int) integer(payload, "drawNumber", Integer.MAX_VALUE);
                    int card = (int) integer(payload, "cardId", Integer.MAX_VALUE);
                    if (draw != room.rules.selectionDraw()) throw new RequestException("STALE_STATE", "추첨 번호가 변경되었습니다.");
                    // 동일 패 충돌은 로직이 RESELECT로 처리하므로 후보 존재 여부는 로직에 맡긴다.
                    DealerSelection.ChoiceResult result = room.rules.chooseDealer(seat, draw, card, clock.getAsLong());
                    if (result == DealerSelection.ChoiceResult.DECIDED) room.currentRound++;
                    ack.addProperty("choiceResult", result.name()); updates(room, events, false);
                }
                case "LEAVE_ROOM" -> {
                    GameRoom room = requireRoom(user);
                    room.rules.leave(room.seatFor(user));
                    ack.addProperty("roomId", room.roomId); updates(room, events, false);
                }
                default -> {
                    MatchEngine.Action action = action(type, payload);
                    GameRoom room = requireRoom(user); Seat seat = room.seatFor(user);
                    if (room.rules.status() != RoomRules.Status.PLAYING)
                        throw new RequestException("GAME_NOT_STARTED", "진행 중인 게임이 없습니다.");
                    MatchEngine.View view = room.rules.viewFor(seat);
                    long round = integer(payload, payload.has("roundNumber") ? "roundNumber" : "roundId", Long.MAX_VALUE);
                    long version = integer(payload, "version", Long.MAX_VALUE);
                    if (!room.gameId(view.roundId).equals(string(payload, "gameId")) || round != view.roundId || version != view.version)
                        throw new RequestException("STALE_STATE", "최신 게임 식별자와 버전을 사용하세요.");
                    if (seat != view.turn) throw new RequestException("NOT_YOUR_TURN", "자신의 차례가 아닙니다.");
                    boolean needsCard = switch (action) {
                        case PLAY, SERVICE, SHAKE, BOMB, SELECT -> true;
                        default -> false;
                    };
                    int card = needsCard ? (int) integer(payload, "cardId", Integer.MAX_VALUE) : 0;
                    if (action == MatchEngine.Action.SELECT && view.choices.stream().noneMatch(c -> c.id() == card))
                        throw new RequestException("INVALID_SELECTION", "제시된 후보에서 선택하세요.");
                    room.rules.act(seat, round, version, action, card, clock.getAsLong());
                    updates(room, events, true);
                }
            }
            user.sendMessage("ACK", id, ack);
        } catch (RequestException e) {
            user.sendError(id, e.code, e.getMessage());
            resync(user, events);
        } catch (IllegalArgumentException | IllegalStateException e) {
            user.sendError(id, "INVALID_ACTION", e.getMessage());
            resync(user, events);
        } finally {
            flush(events);
        }
    }
    private void add(GameRoom room, GameUser user, Seat seat) {
        if (user.balance <= 0) throw new RequestException("INSUFFICIENT_BALANCE", "입장할 잔액이 없습니다.");
        room.rules.join(seat, user.balance); room.seats.put(seat, user);
        room.users.add(user); user.currentRoom = room;
    }
    private GameRoom requireRoom(GameUser user) {
        GameRoom room = user.currentRoom;
        if (room == null || !rooms.contains(room) || !room.users.contains(user))
            throw new RequestException("NOT_IN_ROOM", "참가한 방이 없습니다.");
        return room;
    }
    private void requireOutside(GameUser user) {
        if (user.currentRoom != null) throw new RequestException("ALREADY_IN_ROOM", "이미 방에 참가 중입니다.");
    }
    private JsonObject roomList() {
        JsonArray list = new JsonArray();
        for (GameRoom room : rooms) list.add(room.toSummary());
        JsonObject payload = new JsonObject(); payload.add("rooms", list); return payload;
    }
    private MatchEngine.Action action(String type, JsonObject payload) {
        String name = switch (type) {
            case "PLAY_CARD" -> "PLAY";
            case "SELECT_CAPTURE", "CHOOSE_MATCH" -> "SELECT";
            case "DECIDE" -> string(payload, "decision");
            case "GAME_ACTION" -> string(payload, "action");
            default -> type;
        };
        if ("DECIDE".equals(type) && !name.equals("GO") && !name.equals("STOP"))
            throw new RequestException("INVALID_MESSAGE", "decision은 GO 또는 STOP이어야 합니다.");
        try { return MatchEngine.Action.valueOf(name); }
        catch (IllegalArgumentException e) { throw new RequestException("UNKNOWN_TYPE", "지원하지 않는 요청입니다."); }
    }
    // View만 직렬화한다. 상대 손패와 더미 원본은 전송하지 않는다.
    private JsonObject gamePayload(GameRoom room, Seat seat, MatchEngine.View view) {
        JsonObject value = gson.toJsonTree(view).getAsJsonObject();
        value.addProperty("gameId", room.gameId(view.roundId)); value.addProperty("roomId", room.roomId);
        value.addProperty("roundNumber", view.roundId); value.addProperty("mySeat", seat.name());
        value.addProperty("firstPlayerId", room.playerId(Seat.FIRST)); value.addProperty("secondPlayerId", room.playerId(Seat.SECOND));
        value.addProperty("currentPlayerId", view.phase == MatchEngine.Phase.FINISHED ? null : room.playerId(view.turn));
        // 단조 시계의 원점은 클라이언트와 다르므로 남은 시간도 제공한다.
        value.addProperty("remainingMillis", view.phase == MatchEngine.Phase.FINISHED ? 0 : Math.max(0, view.deadlineMillis - clock.getAsLong()));
        return value;
    }
    private JsonObject selectionPayload(GameRoom room, Seat seat) {
        JsonObject value = new JsonObject(); value.addProperty("roomId", room.roomId);
        value.addProperty("selectionId", room.selectionId()); value.addProperty("mySeat", seat.name());
        value.addProperty("drawNumber", room.rules.selectionDraw());
        value.add("choices", gson.toJsonTree(room.rules.selectionCandidates(seat)));
        value.addProperty("remainingMillis", Math.max(0, room.rules.selectionDeadline(seat) - clock.getAsLong()));
        return value;
    }
    private void queueViews(GameRoom room, List<Event> events, String type) {
        for (var entry : room.seats.entrySet()) {
            if (!room.rules.present(entry.getKey())) continue;
            MatchEngine.View view = room.rules.viewFor(entry.getKey());
            if (view != null) events.add(new Event(entry.getValue(), type, gamePayload(room, entry.getKey(), view)));
        }
    }
    private JsonObject resultPayload(GameRoom room, MatchEngine.Result result) {
        JsonObject value = new JsonObject(); value.addProperty("roomId", room.roomId);
        // RoomRules가 기권 후 game을 제거해도 판 식별자를 보존한다.
        value.addProperty("gameId", room.gameId(room.currentRound));
        value.addProperty("roundNumber", room.currentRound);
        value.addProperty("reason", result.end.name()); value.addProperty("winnerId", room.playerId(result.winner));
        value.addProperty("nextDealerId", room.playerId(result.nextDealer));
        value.addProperty("nextDrawStreak", result.nextDrawStreak);
        value.add("settlement", gson.toJsonTree(result.settlement));
        JsonArray balances = new JsonArray();
        for (var entry : room.seats.entrySet()) {
            JsonObject player = new JsonObject(); player.addProperty("playerId", entry.getValue().playerId);
            player.addProperty("balance", result.money(entry.getKey()));
            player.addProperty("bankrupt", result.bankrupt(entry.getKey())); balances.add(player);
        }
        value.add("results", balances); return value;
    }
    private void updates(GameRoom room, List<Event> events, boolean stateChanged) {
        List<GameUser> previous = new ArrayList<>(room.users);
        List<Event> starts = new ArrayList<>();
        MatchEngine.Result result = room.rules.lastResult();
        boolean ended = result != null && result != room.notifiedResult;
        // 종료 직후에도 일반 정산에서는 view가 남아 있다. 즉시 총통도 여기서 판 번호를 확보한다.
        MatchEngine.View firstView = null;
        for (Seat seat : room.seats.keySet()) if (room.rules.present(seat)) {
            firstView = room.rules.viewFor(seat); if (firstView != null) break;
        }
        boolean started = firstView != null && firstView.roundId != room.lastStartedRound;
        if (started) room.lastStartedRound = firstView.roundId;
        if (ended) {
            // CLOSED 및 기권으로 View가 없어져도 lastStartedRound는 시작 시 저장돼 있다.
            queueViews(room, events, "GAME_STATE");
            JsonObject over = resultPayload(room, result);
            for (GameUser user : previous) events.add(new Event(user, "GAME_OVER", over));
            room.notifiedResult = result;
        } else if (room.rules.status() == RoomRules.Status.PLAYING) {
            if (started) queueViews(room, starts, "GAME_START");
            else if (stateChanged) queueViews(room, events, "GAME_STATE");
        }
        for (var entry : new ArrayList<>(room.seats.entrySet())) {
            Seat seat = entry.getKey(); GameUser user = entry.getValue();
            user.balance = room.rules.money(seat);
            if (!room.rules.present(seat)) {
                room.users.remove(user); room.seats.remove(seat); user.currentRoom = null;
                JsonObject left = new JsonObject(); left.addProperty("roomId", room.roomId);
                left.addProperty("balance", user.balance);
                left.addProperty("reason", room.rules.status() == RoomRules.Status.CLOSED ? "BANKRUPTCY" : "LEFT");
                events.add(new Event(user, "ROOM_LEFT", left));
            }
        }
        JsonObject state = room.toState();
        for (GameUser user : previous) events.add(new Event(user, "ROOM_STATE", state));
        events.addAll(starts);
        if (room.users.isEmpty()) rooms.remove(room);
        if (room.rules.status() == RoomRules.Status.DEALER_SELECTION) {
            for (var entry : room.seats.entrySet()) events.add(new Event(entry.getValue(), "DEALER_SELECTION", selectionPayload(room, entry.getKey())));
        }
    }
    private void resync(GameUser user, List<Event> events) {
        GameRoom room = user.currentRoom;
        if (room == null) return;
        Seat seat = room.seatFor(user);
        events.add(new Event(user, "ROOM_STATE", room.toState()));
        if (room.rules.status() == RoomRules.Status.DEALER_SELECTION)
            events.add(new Event(user, "DEALER_SELECTION", selectionPayload(room, seat)));
        else {
            MatchEngine.View view = room.rules.viewFor(seat);
            if (view != null) events.add(new Event(user, "GAME_STATE", gamePayload(room, seat, view)));
        }
    }
    private void flush(List<Event> events) { for (Event event : events) event.user.sendEvent(event.type, event.payload); }
    private void expireRoom(GameRoom room, long now, List<Event> events) {
        if (rooms.contains(room) && room.rules.expire(now)) updates(room, events, true);
    }
    synchronized void expireRooms() {
        List<Event> events = new ArrayList<>();
        for (GameRoom room : new ArrayList<>(rooms)) expireRoom(room, clock.getAsLong(), events);
        flush(events);
    }
    public synchronized void startTimer() {
        if (timer != null) return;
        timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "game-deadlines"); t.setDaemon(true); return t; });
        timer.scheduleAtFixedRate(() -> {
            try { expireRooms(); }
            catch (RuntimeException e) { System.err.println("제한 시간 검사 실패: " + e); }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }
    public synchronized void disconnect(GameUser user) {
        List<Event> events = new ArrayList<>();
        if (user.currentRoom != null) {
            GameRoom room = user.currentRoom;
            room.rules.leave(room.seatFor(user)); updates(room, events, false);
        }
        if (user.nickname != null) { nicknames.remove(user.nickname); user.nickname = null; }
        flush(events);
    }
    @Override public synchronized void close() { if (timer != null) timer.shutdownNow(); }
    static String string(JsonObject value, String key) {
        JsonElement field = value.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString())
            throw new RequestException("INVALID_MESSAGE", key + " 문자열이 필요합니다.");
        return field.getAsString();
    }
    static long integer(JsonObject value, String key, long max) {
        JsonElement field = value.get(key);
        if (field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isNumber() && field.getAsString().matches("[0-9]+")) {
            try { long number = Long.parseLong(field.getAsString()); if (number <= max) return number; }
            catch (NumberFormatException ignored) { }
        }
        throw new RequestException("INVALID_MESSAGE", key + "는 범위 내 음이 아닌 정수여야 합니다.");
    }
    private static String text(JsonObject value, String key, int max) {
        String text = string(value, key).strip(); int length = text.codePointCount(0, text.length());
        if (length < 1 || length > max || text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029))
            throw new RequestException("INVALID_MESSAGE", key + " 길이 또는 문자를 확인하세요.");
        return text;
    }
    public static final class RequestException extends RuntimeException {
        public final String code;
        public RequestException(String code, String message) { super(message); this.code = code; }
    }
}
