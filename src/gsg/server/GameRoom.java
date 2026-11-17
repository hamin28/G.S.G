// 게임 내의 로직(게임 진행 관련)을 처리하기 위한 클래스이다.  GameUser를 여러개 가질 수 있음
package gsg.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.Vector;

public class GameRoom {
	final String roomId;
	final String title;
	final int maxPlayers = 2;

	String status = "WAITING";
	final Vector<GameUser> users = new Vector<>();

	public GameRoom(String roomId, String title) {
		this.roomId = roomId;
		this.title = title;
	}

	// 방 목록에 표시할 정보
	public JsonObject toSummary() {
		JsonObject room = new JsonObject();

		room.addProperty("roomId", roomId);
		room.addProperty("title", title);
		room.addProperty("playerCount", users.size());
		room.addProperty("maxPlayers", maxPlayers);
		room.addProperty("status", status);

		return room;
	}

	// 방 상태 이벤트에 포함할 현재 플레이어 목록을 만든다.
	public JsonObject toState() {
		JsonObject state = toSummary();
		JsonArray players = new JsonArray();

		for (GameUser user : users) {
			JsonObject player = new JsonObject();
			player.addProperty("playerId", user.playerId);
			player.addProperty("nickname", user.nickname);
			player.addProperty("ready", user.ready);
			players.add(player);
		}

		state.add("players", players);
		return state;
	}

}
