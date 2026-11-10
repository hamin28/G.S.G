package gsg.testserver;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI 시험용 간이 서버 — 실제 서버(서버 담당 구현)를 대신해 화면을 테스트하기 위한 코드다.
 * docs/README.md 프로토콜의 메시지 형식만 따르고, 게임 규칙은 아주 단순하게 흉내 낸다.
 *  - 보너스 패 제외 48장, 손패 10장씩·바닥 8장 분배
 *  - 같은 월 1장: 함께 획득 / 2장: CHOICE_REQUIRED(CAPTURE) / 3장: 모두 획득 / 없음: 바닥에 놓기
 *  - 낸 뒤 덱에서 1장 뒤집어 같은 방식 처리 (2장 일치 시 첫 번째를 자동 선택)
 *  - 간이 점수(광 3장↑, 열끗·띠 5장↑, 피 10장↑)가 3점 이상이고 지난 고 때보다 오르면 CHOICE_REQUIRED(GO_STOP)
 *  - 손패가 모두 떨어지면 점수가 높은 쪽 승리
 * 실행: Run As → Java Application (기본 포트 5000, 인자로 포트 변경 가능)
 */
public class MockServer {

    static final Object LOCK = new Object();
    static final Map<Integer, Integer> MONTH = new HashMap<>();
    static final Map<Integer, String> TYPE = new HashMap<>();
    static final Map<String, Client> clients = new LinkedHashMap<>();
    static final Map<String, Room> rooms = new LinkedHashMap<>();
    static int playerSeq, roomSeq, gameSeq, choiceSeq;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        try (FileReader r = new FileReader("data/cards.json", StandardCharsets.UTF_8)) {
            List<Map<String, Object>> list = new Gson().fromJson(r, new TypeToken<List<Map<String, Object>>>() {}.getType());
            for (Map<String, Object> c : list) {
                int id = ((Number) c.get("cardId")).intValue();
                Object m = c.get("month");
                if (m != null) MONTH.put(id, ((Number) m).intValue());
                TYPE.put(id, (String) c.get("type"));
            }
        }
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("[테스트 서버] 포트 " + port + " 대기 중");
            while (true) {
                Socket s = ss.accept();
                new Thread(new Client(s)).start();
            }
        }
    }

    // ================================================================ 연결

    static class Client implements Runnable {
        final Socket socket;
        BufferedWriter out;
        String playerId, nickname;
        Room room;
        boolean ready;

        Client(Socket s) { socket = s; }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    JsonObject msg;
                    try {
                        msg = JsonParser.parseString(line).getAsJsonObject();
                    } catch (RuntimeException e) {
                        send(error(null, "INVALID_MESSAGE", "JSON 형식이 아닙니다."));
                        continue;
                    }
                    synchronized (LOCK) {
                        handle(this, msg);
                    }
                }
            } catch (IOException ignored) {
            }
            synchronized (LOCK) {
                disconnect(this);
            }
        }

        void send(JsonObject msg) {
            synchronized (this) {
                try {
                    out.write(msg.toString());
                    out.write('\n');
                    out.flush();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static class Room {
        String roomId, title;
        final List<Client> players = new ArrayList<>();
        Game game;
    }

    static class Game {
        String gameId;
        int revision;
        String phase = "WAIT_PLAY";
        String current;
        final Map<String, List<Integer>> hands = new LinkedHashMap<>();
        final Map<String, List<Integer>> captured = new LinkedHashMap<>();
        final Map<String, Integer> goCount = new HashMap<>();
        final Map<String, Integer> lastGoScore = new HashMap<>();
        final List<Integer> table = new ArrayList<>();
        final List<Integer> deck = new ArrayList<>();
        JsonObject pending;          // 진행 중인 선택
        String pendingFor;
        int pendingPlayed;           // CAPTURE 선택 중 낸 카드
    }

    // ================================================================ 메시지 처리

    static void handle(Client c, JsonObject msg) {
        String type = msg.has("type") ? msg.get("type").getAsString() : "";
        String rid = msg.has("requestId") ? msg.get("requestId").getAsString() : null;
        JsonObject p = msg.has("payload") && msg.get("payload").isJsonObject() ? msg.getAsJsonObject("payload") : new JsonObject();

        if (!type.equals("JOIN") && c.playerId == null) { c.send(error(rid, "NOT_JOINED", "닉네임을 먼저 등록하세요.")); return; }
        switch (type) {
            case "JOIN": {
                if (c.playerId != null) { c.send(error(rid, "ALREADY_JOINED", "이미 등록했습니다.")); return; }
                String nick = p.get("nickname").getAsString().trim();
                for (Client o : clients.values())
                    if (o.nickname.equals(nick)) { c.send(error(rid, "NICKNAME_TAKEN", "이미 사용 중인 닉네임입니다.")); return; }
                c.playerId = "player-" + (++playerSeq);
                c.nickname = nick;
                clients.put(c.playerId, c);
                JsonObject ack = ack(rid, "JOIN");
                ack.getAsJsonObject("payload").addProperty("playerId", c.playerId);
                c.send(ack);
                log(nick + " 접속 (" + c.playerId + ")");
                break;
            }
            case "LIST_ROOMS":
                c.send(ack(rid, type));
                c.send(roomList());
                break;
            case "CREATE_ROOM": {
                if (c.room != null) { c.send(error(rid, "ALREADY_IN_ROOM", "이미 방에 있습니다.")); return; }
                Room r = new Room();
                r.roomId = "room-" + (++roomSeq);
                r.title = p.get("title").getAsString().trim();
                rooms.put(r.roomId, r);
                enter(c, r, rid, type);
                break;
            }
            case "JOIN_ROOM": {
                if (c.room != null) { c.send(error(rid, "ALREADY_IN_ROOM", "이미 방에 있습니다.")); return; }
                Room r = rooms.get(p.get("roomId").getAsString());
                if (r == null) { c.send(error(rid, "ROOM_NOT_FOUND", "방이 없습니다.")); return; }
                if (r.game != null) { c.send(error(rid, "GAME_IN_PROGRESS", "게임 중인 방입니다.")); return; }
                if (r.players.size() >= 2) { c.send(error(rid, "ROOM_FULL", "방이 가득 찼습니다.")); return; }
                enter(c, r, rid, type);
                break;
            }
            case "READY": {
                if (c.room == null) { c.send(error(rid, "NOT_IN_ROOM", "방에 참가하세요.")); return; }
                if (c.room.game != null) { c.send(error(rid, "GAME_IN_PROGRESS", "게임 중입니다.")); return; }
                c.ready = p.get("ready").getAsBoolean();
                c.send(ack(rid, type));
                broadcast(c.room, roomState(c.room));
                Room r = c.room;
                if (r.players.size() == 2 && r.players.stream().allMatch(x -> x.ready)) startGame(r);
                break;
            }
            case "LEAVE_ROOM": {
                if (c.room == null) { c.send(error(rid, "NOT_IN_ROOM", "방에 참가하세요.")); return; }
                c.send(ack(rid, type));
                leaveRoom(c);
                break;
            }
            case "CHAT": {
                if (c.room == null) { c.send(error(rid, "NOT_IN_ROOM", "방에 참가하세요.")); return; }
                String text = p.get("message").getAsString().trim();
                if (text.isEmpty() || text.codePointCount(0, text.length()) > 200) {
                    c.send(error(rid, "INVALID_MESSAGE", "채팅은 1~200자입니다.")); return;
                }
                c.send(ack(rid, type));
                JsonObject m = event("CHAT_MESSAGE");
                m.getAsJsonObject("payload").addProperty("playerId", c.playerId);
                m.getAsJsonObject("payload").addProperty("nickname", c.nickname);
                m.getAsJsonObject("payload").addProperty("message", text);
                broadcast(c.room, m);
                break;
            }
            case "PLAY_CARD":      playCard(c, rid, p); break;
            case "SELECT_CAPTURE": selectCapture(c, rid, p); break;
            case "DECIDE":         decide(c, rid, p); break;
            default:
                c.send(error(rid, "UNKNOWN_TYPE", "알 수 없는 요청: " + type));
        }
    }

    static void enter(Client c, Room r, String rid, String type) {
        r.players.add(c);
        c.room = r;
        c.ready = false;
        JsonObject ack = ack(rid, type);
        ack.getAsJsonObject("payload").addProperty("roomId", r.roomId);
        c.send(ack);
        broadcast(r, roomState(r));
        log(c.nickname + " → " + r.title);
    }

    static void leaveRoom(Client c) {
        Room r = c.room;
        if (r == null) return;
        r.players.remove(c);
        c.room = null;
        c.ready = false;
        if (r.game != null) endGame(r, "PLAYER_LEFT", null);
        if (r.players.isEmpty()) rooms.remove(r.roomId);
        else broadcast(r, roomState(r));
    }

    static void disconnect(Client c) {
        if (c.playerId == null) return;
        leaveRoom(c);
        clients.remove(c.playerId);
        log(c.nickname + " 연결 종료");
    }

    // ================================================================ 게임

    static void startGame(Room r) {
        Game g = new Game();
        g.gameId = "game-" + (++gameSeq);
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i <= 48; i++) all.add(i);
        Collections.shuffle(all);
        for (Client pl : r.players) {
            g.hands.put(pl.playerId, new ArrayList<>(all.subList(0, 10)));
            all.subList(0, 10).clear();
            g.captured.put(pl.playerId, new ArrayList<>());
            g.goCount.put(pl.playerId, 0);
            g.lastGoScore.put(pl.playerId, 0);
        }
        g.table.addAll(all.subList(0, 8));
        all.subList(0, 8).clear();
        g.deck.addAll(all);
        g.current = r.players.get(0).playerId;
        r.game = g;
        broadcast(r, roomState(r));
        for (Client pl : r.players) pl.send(state(r, pl, "GAME_START"));
        log(r.title + " 게임 시작");
    }

    static void playCard(Client c, String rid, JsonObject p) {
        Game g = check(c, rid);
        if (g == null) return;
        if (!"WAIT_PLAY".equals(g.phase)) { c.send(error(rid, "INVALID_PHASE", "지금은 패를 낼 수 없습니다.")); return; }
        int card = p.get("cardId").getAsInt();
        if (!g.hands.get(c.playerId).remove((Integer) card)) { c.send(error(rid, "CARD_NOT_OWNED", "손패에 없는 패입니다.")); return; }
        c.send(ack(rid, "PLAY_CARD"));

        List<Integer> match = sameMonth(g.table, card);
        if (match.size() == 2) {           // 먹을 패 선택 필요
            g.phase = "WAIT_CAPTURE";
            g.pendingPlayed = card;
            g.pending = choice(g, "CAPTURE");
            JsonArray ids = new JsonArray();
            match.forEach(ids::add);
            g.pending.add("cardIds", ids);
            g.pendingFor = c.playerId;
            sendState(c.room);
            c.send(wrap("CHOICE_REQUIRED", g.pending));
            return;
        }
        take(g, c.playerId, card, match);
        afterPlay(c.room, c);
    }

    static void selectCapture(Client c, String rid, JsonObject p) {
        Game g = check(c, rid);
        if (g == null) return;
        if (g.pending == null || !"CAPTURE".equals(g.pending.get("kind").getAsString())
                || !g.pending.get("choiceId").getAsString().equals(p.get("choiceId").getAsString())) {
            c.send(error(rid, "INVALID_SELECTION", "만료된 선택입니다.")); return;
        }
        int pick = p.get("cardId").getAsInt();
        boolean ok = false;
        for (JsonElement e : g.pending.getAsJsonArray("cardIds")) if (e.getAsInt() == pick) ok = true;
        if (!ok) { c.send(error(rid, "INVALID_SELECTION", "후보에 없는 패입니다.")); return; }
        c.send(ack(rid, "SELECT_CAPTURE"));
        g.pending = null;
        g.pendingFor = null;
        g.phase = "WAIT_PLAY";
        take(g, c.playerId, g.pendingPlayed, Collections.singletonList(pick));
        afterPlay(c.room, c);
    }

    static void decide(Client c, String rid, JsonObject p) {
        Game g = check(c, rid);
        if (g == null) return;
        if (g.pending == null || !"GO_STOP".equals(g.pending.get("kind").getAsString())
                || !g.pending.get("choiceId").getAsString().equals(p.get("choiceId").getAsString())) {
            c.send(error(rid, "INVALID_SELECTION", "만료된 선택입니다.")); return;
        }
        c.send(ack(rid, "DECIDE"));
        g.pending = null;
        g.pendingFor = null;
        if ("STOP".equals(p.get("decision").getAsString())) {
            endGame(c.room, "STOP", c.playerId);
            return;
        }
        g.goCount.merge(c.playerId, 1, Integer::sum);
        g.lastGoScore.put(c.playerId, score(g, c.playerId));
        g.phase = "WAIT_PLAY";
        nextTurn(c.room);
    }

    /** 덱에서 한 장 뒤집고, 고·스톱 판정 후 다음 차례로 */
    static void afterPlay(Room r, Client c) {
        Game g = r.game;
        if (!g.deck.isEmpty()) {
            int flip = g.deck.remove(0);
            List<Integer> m = sameMonth(g.table, flip);
            take(g, c.playerId, flip, m.size() == 2 ? m.subList(0, 1) : m);
        }
        int s = score(g, c.playerId);
        if (s >= 3 && s > g.lastGoScore.get(c.playerId) && !g.hands.get(c.playerId).isEmpty()) {
            g.phase = "WAIT_GO_STOP";
            g.pending = choice(g, "GO_STOP");
            JsonArray opts = new JsonArray();
            opts.add("GO");
            opts.add("STOP");
            g.pending.add("options", opts);
            g.pendingFor = c.playerId;
            sendState(r);
            c.send(wrap("CHOICE_REQUIRED", g.pending));
            return;
        }
        nextTurn(r);
    }

    static void nextTurn(Room r) {
        Game g = r.game;
        boolean allEmpty = g.hands.values().stream().allMatch(List::isEmpty);
        if (allEmpty) {
            String a = r.players.get(0).playerId, b = r.players.get(1).playerId;
            int sa = score(g, a), sb = score(g, b);
            if (sa == sb) endGame(r, "DRAW", null);
            else endGame(r, "STOP", sa > sb ? a : b);
            return;
        }
        for (Client pl : r.players) if (!pl.playerId.equals(g.current)) { g.current = pl.playerId; break; }
        g.phase = "WAIT_PLAY";
        sendState(r);
    }

    static void endGame(Room r, String reason, String winner) {
        Game g = r.game;
        if (g == null) return;
        g.phase = "FINISHED";
        g.current = null;
        g.pending = null;
        sendState(r);
        JsonObject over = event("GAME_OVER");
        JsonObject p = over.getAsJsonObject("payload");
        p.addProperty("gameId", g.gameId);
        p.addProperty("reason", reason);
        if (winner == null) p.add("winnerId", com.google.gson.JsonNull.INSTANCE);
        else p.addProperty("winnerId", winner);
        JsonArray results = new JsonArray();
        for (String id : g.hands.keySet()) {
            JsonObject res = new JsonObject();
            res.addProperty("playerId", id);
            res.addProperty("score", score(g, id));
            res.addProperty("goCount", g.goCount.get(id));
            results.add(res);
        }
        p.add("results", results);
        broadcast(r, over);
        r.game = null;
        for (Client pl : r.players) pl.ready = false;
        broadcast(r, roomState(r));
        log(r.title + " 게임 종료: " + reason + " / 승자 " + winner);
    }

    static Game check(Client c, String rid) {
        if (c.room == null) { c.send(error(rid, "NOT_IN_ROOM", "방에 참가하세요.")); return null; }
        Game g = c.room.game;
        if (g == null) { c.send(error(rid, "GAME_NOT_STARTED", "진행 중인 게임이 없습니다.")); return null; }
        if (!c.playerId.equals(g.current)) { c.send(error(rid, "NOT_YOUR_TURN", "자신의 차례가 아닙니다.")); return null; }
        return g;
    }

    static List<Integer> sameMonth(List<Integer> table, int card) {
        List<Integer> m = new ArrayList<>();
        Integer month = MONTH.get(card);
        for (int t : table) if (month != null && month.equals(MONTH.get(t))) m.add(t);
        return m;
    }

    /** card를 내서 matched를 가져가거나(있으면), 바닥에 놓는다 */
    static void take(Game g, String pid, int card, List<Integer> matched) {
        if (matched.isEmpty()) {
            g.table.add(card);
        } else {
            List<Integer> got = new ArrayList<>(matched);
            g.table.removeAll(got);
            got.add(card);
            g.captured.get(pid).addAll(got);
        }
    }

    static int score(Game g, String pid) {
        int gw = 0, an = 0, rb = 0, pi = 0;
        for (int id : g.captured.get(pid)) {
            switch (TYPE.get(id)) {
                case "GWANG": gw++; break;
                case "ANIMAL": an++; break;
                case "RIBBON": rb++; break;
                default: pi++;
            }
        }
        int s = 0;
        if (gw >= 3) s += gw;
        if (an >= 5) s += an - 4;
        if (rb >= 5) s += rb - 4;
        if (pi >= 10) s += pi - 9;
        return s;
    }

    static JsonObject choice(Game g, String kind) {
        JsonObject ch = new JsonObject();
        ch.addProperty("gameId", g.gameId);
        ch.addProperty("choiceId", "choice-" + (++choiceSeq));
        ch.addProperty("kind", kind);
        return ch;
    }

    static void sendState(Room r) {
        for (Client pl : r.players) pl.send(state(r, pl, "GAME_STATE"));
    }

    /** 수신자별 상태: 자기 손패만 ID로, 상대는 장수만 */
    static JsonObject state(Room r, Client to, String type) {
        Game g = r.game;
        g.revision++;
        JsonObject p = new JsonObject();
        p.addProperty("gameId", g.gameId);
        p.addProperty("revision", g.revision);
        p.addProperty("phase", g.phase);
        if (g.current == null) p.add("currentPlayerId", com.google.gson.JsonNull.INSTANCE);
        else p.addProperty("currentPlayerId", g.current);
        p.add("myHand", ints(g.hands.get(to.playerId)));
        p.add("tableCards", ints(g.table));
        p.addProperty("deckCount", g.deck.size());
        JsonArray players = new JsonArray();
        for (String id : g.hands.keySet()) {
            JsonObject pl = new JsonObject();
            pl.addProperty("playerId", id);
            pl.addProperty("handCount", g.hands.get(id).size());
            pl.add("capturedCards", ints(g.captured.get(id)));
            pl.addProperty("score", score(g, id));
            pl.addProperty("goCount", g.goCount.get(id));
            players.add(pl);
        }
        p.add("players", players);
        if (g.pending != null && to.playerId.equals(g.pendingFor)) p.add("pendingChoice", g.pending.deepCopy());
        else p.add("pendingChoice", com.google.gson.JsonNull.INSTANCE);
        return wrap(type, p);
    }

    // ================================================================ 메시지 도우미

    static JsonObject roomList() {
        JsonArray arr = new JsonArray();
        for (Room r : rooms.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("roomId", r.roomId);
            o.addProperty("title", r.title);
            o.addProperty("playerCount", r.players.size());
            o.addProperty("maxPlayers", 2);
            o.addProperty("status", r.game == null ? "WAITING" : "PLAYING");
            arr.add(o);
        }
        JsonObject m = event("ROOM_LIST");
        m.getAsJsonObject("payload").add("rooms", arr);
        return m;
    }

    static JsonObject roomState(Room r) {
        JsonObject p = new JsonObject();
        p.addProperty("roomId", r.roomId);
        p.addProperty("title", r.title);
        p.addProperty("maxPlayers", 2);
        p.addProperty("status", r.game == null ? "WAITING" : "PLAYING");
        JsonArray players = new JsonArray();
        for (Client c : r.players) {
            JsonObject o = new JsonObject();
            o.addProperty("playerId", c.playerId);
            o.addProperty("nickname", c.nickname);
            o.addProperty("ready", c.ready);
            players.add(o);
        }
        p.add("players", players);
        return wrap("ROOM_STATE", p);
    }

    static void broadcast(Room r, JsonObject msg) {
        for (Client c : r.players) c.send(msg);
    }

    static JsonObject ack(String rid, String requestType) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "ACK");
        if (rid != null) m.addProperty("requestId", rid);
        JsonObject p = new JsonObject();
        p.addProperty("requestType", requestType);
        m.add("payload", p);
        return m;
    }

    static JsonObject error(String rid, String code, String message) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "ERROR");
        if (rid != null) m.addProperty("requestId", rid);
        JsonObject p = new JsonObject();
        p.addProperty("code", code);
        p.addProperty("message", message);
        m.add("payload", p);
        return m;
    }

    static JsonObject event(String type) {
        return wrap(type, new JsonObject());
    }

    static JsonObject wrap(String type, JsonObject payload) {
        JsonObject m = new JsonObject();
        m.addProperty("type", type);
        m.add("payload", payload);
        return m;
    }

    static JsonArray ints(List<Integer> list) {
        JsonArray a = new JsonArray();
        list.forEach(a::add);
        return a;
    }

    static void log(String s) {
        System.out.println("[테스트 서버] " + s);
    }
}
