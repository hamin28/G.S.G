package server;

import com.google.gson.*;
import gallaemallae.game.*;
import gallaemallae.game.GameState.Seat;
import java.util.*;

/** 방 참가자는 Vector로 관리하고, 게임 규칙은 방별 RoomRules 하나에 맡긴다. */
public class GameRoom {
    final String roomId;
    final String title;
    final int maxPlayers = 2;
    final Vector<GameUser> users = new Vector<>();
    final EnumMap<Seat, GameUser> seats = new EnumMap<>(Seat.class);
    final RoomRules rules;
    long lastStartedRound;
    long currentRound;
    long selectionSequence;
    MatchEngine.Result notifiedResult;

    public GameRoom(String roomId, String title) { this(roomId, title, new Random()); }
    GameRoom(String roomId, String title, Random random) {
        this.roomId = roomId; this.title = title; rules = new RoomRules(random);
    }
    Seat seatFor(GameUser user) {
        for (var entry : seats.entrySet()) if (entry.getValue() == user) return entry.getKey();
        throw new RoomManager.RequestException("NOT_IN_ROOM", "방 참가자가 아닙니다.");
    }
    String playerId(Seat seat) {
        GameUser user = seat == null ? null : seats.get(seat);
        return user == null ? null : user.playerId;
    }
    String gameId(long round) { return roomId + "-round-" + round; }
    String selectionId() { return roomId + "-selection-" + selectionSequence; }
    public JsonObject toSummary() {
        JsonObject value = new JsonObject();
        value.addProperty("roomId", roomId); value.addProperty("title", title);
        value.addProperty("playerCount", users.size()); value.addProperty("maxPlayers", maxPlayers);
        value.addProperty("status", rules.status().name());
        return value;
    }
    public JsonObject toState() {
        JsonObject value = toSummary(); JsonArray players = new JsonArray();
        for (GameUser user : users) {
            Seat seat = seatFor(user); JsonObject player = new JsonObject();
            player.addProperty("playerId", user.playerId); player.addProperty("nickname", user.nickname);
            player.addProperty("seat", seat.name()); player.addProperty("ready", rules.isReady(seat));
            player.addProperty("balance", rules.money(seat)); players.add(player);
        }
        value.add("players", players);
        return value;
    }
}
