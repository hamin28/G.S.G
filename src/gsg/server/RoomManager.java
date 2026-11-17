// Room의 생성/삭제를 관리하는 클래스이다. Room을 여러개 가질 수 있음
package gsg.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

public class RoomManager {
    private final Set<String> nicknames = new HashSet<>();
	//게임 방 생성
	private final Vector<GameRoom> rooms = new Vector<>();

    // 새 닉네임이면 등록하고 true, 이미 사용 중이면 false를 반환한다.
    public synchronized boolean registerNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        return nicknames.add(nickname.trim());
    }

	// 방 생성
	public synchronized GameRoom createRoom(String title, GameUser creator) {
		if (title == null || title.isBlank()
				|| creator == null
				|| creator.nickname == null
				|| creator.currentRoom != null) {
			return null;
		}

		String roomId = "room-" + UUID.randomUUID();
		GameRoom room = new GameRoom(roomId, title.trim());

		room.users.add(creator);
		creator.currentRoom = room;
		creator.ready = false;
		rooms.add(room);

		return room;
	}

	// 현재 방 목록의 요약 정보를 만든다.
	public synchronized JsonObject getRoomListPayload() {
		JsonArray roomSummaries = new JsonArray();
		for (GameRoom room : rooms) {
			roomSummaries.add(room.toSummary());
		}

		JsonObject payload = new JsonObject();
		payload.add("rooms", roomSummaries);
		return payload;
	}

	// roomId로 방을 찾아, 입장 조건을 만족하면 플레이어를 추가한다.
	public synchronized GameRoom joinRoom(String roomId, GameUser user) {
		if (roomId == null || roomId.isBlank()
				|| user == null
				|| user.nickname == null
				|| user.currentRoom != null) {
			return null;
		}

		for (GameRoom room : rooms) {
			if (room.roomId.equals(roomId)) {
				if (room.users.size() >= room.maxPlayers) {
					return null;
				}

				for (GameUser member : room.users) {
					member.ready = false;
				}
				room.users.add(user);
				user.currentRoom = room;
				user.ready = false;
				return room;
			}
		}

		return null;
	}

	// 방 참가자의 준비 상태를 변경한다.
	public synchronized GameRoom setReady(GameUser user, boolean ready) {
		if (user == null || user.currentRoom == null || !user.currentRoom.users.contains(user)) {
			return null;
		}

		user.ready = ready;
		return user.currentRoom;
	}

	// 플레이어를 방에서 내보낸다. 방이 비면 방도 삭제한다.
	public synchronized GameRoom leaveRoom(GameUser user) {
		if (user == null || user.currentRoom == null) {
			return null;
		}

		GameRoom room = user.currentRoom;
		if (!room.users.remove(user)) {
			user.currentRoom = null;
			user.ready = false;
			return null;
		}

		user.currentRoom = null;
		user.ready = false;
		for (GameUser member : room.users) {
			member.ready = false;
		}

		if (room.users.isEmpty()) {
			rooms.remove(room);
		}

		return room;
	}

	// 방의 최신 상태를 현재 참가자 모두에게 알린다.
	public void broadcastRoomState(GameRoom room) {
		List<GameUser> recipients;
		JsonObject payload;

		synchronized (this) {
			if (room == null || !rooms.contains(room)) {
				return;
			}
			recipients = new ArrayList<>(room.users);
			payload = room.toState();
		}

		for (GameUser user : recipients) {
			user.sendEvent("ROOM_STATE", payload);
		}
	}

	// 접속 종료 시 플레이어를 방과 닉네임 목록에서 제거한다.
	public void disconnect(GameUser user) {
		GameRoom changedRoom = null;
		boolean shouldBroadcast = false;

		synchronized (this) {
			if (user.currentRoom != null) {
				changedRoom = user.currentRoom;
				changedRoom.users.remove(user);
				user.currentRoom = null;
				user.ready = false;
				for (GameUser member : changedRoom.users) {
					member.ready = false;
				}

				if (changedRoom.users.isEmpty()) {
					rooms.remove(changedRoom);
				} else {
					shouldBroadcast = true;
				}
			}
			user.ready = false;

			if (user.nickname != null) {
				nicknames.remove(user.nickname);
				user.nickname = null;
			}
		}

		if (shouldBroadcast) {
			broadcastRoomState(changedRoom);
		}
	}
}
