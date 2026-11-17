// 클라이언트의 고유한 정보(닉네임, 아이템, 플레이어 정보 등)를 가지는 클래스이며, 중요한 것은 "소켓" 을 가지고 있어야 한다.
package gsg.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.util.UUID;

// 클라이언트 한 명의 연결을 담당한다. start()가 호출되면 run()이 별도 스레드에서 실행된다.
public class GameUser extends Thread{

	// 서버 안에서 각 접속자를 구분하는 고유 ID
	final String playerId = "player-" + UUID.randomUUID();

	// 이후 로비에서 사용할 플레이어 상태
	String nickname;
	boolean ready;
	GameRoom currentRoom;

	// 이 플레이어와 통신할 소켓과 게임방 관리 객체
	private final Socket socket;
	private final RoomManager roomManager;
	private volatile BufferedWriter writer;

	// ServerMain이 accept()로 받은 소켓을 이 플레이어 객체에 전달한다.
	public GameUser(Socket socket, RoomManager roomManager) {
		this.socket = socket;
		this.roomManager = roomManager;
	}

	@Override
	public void run() {
		// 이 스레드가 실행되면 클라이언트 접속을 서버 콘솔에 기록한다.
		System.out.println("클라이언트 접속: " + playerId + " (" + socket.getRemoteSocketAddress() + ")");

		// 연결이 끝나면 소켓과 입력 스트림을 자동으로 닫아 자원을 정리한다.
		try (Socket client = socket;
		     // UTF-8 데이터를 문자로 바꾸고, BufferedReader가 메시지를 한 줄씩 읽게 한다.
		     // 클라이언트는 메시지 뒤에 줄바꿈을 보내야 readLine()이 반환된다.
		     BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
		     BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
			this.writer = out;
			String msg;

			// 클라이언트가 연결을 유지하는 동안 메시지를 계속 읽는다.
			while ((msg = in.readLine()) != null) {
				// 받은 메시지를 해석해 JOIN 등 요청을 처리한다.
				handleMessage(msg, out);
			}
		} catch (IOException e) {
			// 연결 종료나 읽기 오류의 원인을 서버 콘솔에 기록한다.
			System.out.println("클라이언트 통신 오류 (" + playerId + "): " + e.getMessage());
		} finally {
			// 연결 종료 후에는 이 클라이언트로 이벤트를 보내지 않도록 출력기를 비운다.
			this.writer = null;
			roomManager.disconnect(this);
			// 정상 종료와 오류 종료 모두 여기서 접속 종료를 기록한다.
			System.out.println("클라이언트 연결 종료: " + playerId);
		}


	}

	// 메시지를 파싱하고 JOIN 요청이면 중복 확인 후 닉네임을 등록한다.
	private void handleMessage(String msg, BufferedWriter out) throws IOException {
		JsonObject request = JsonParser.parseString(msg).getAsJsonObject();
		String type = request.get("type").getAsString();
		JsonObject responsePayload = new JsonObject();
		String responseType;
		GameRoom roomToBroadcast = null;
		JsonObject roomListPayload = null;

		switch (type) {
			case "JOIN" -> {
				JsonObject payload = request.getAsJsonObject("payload");
				String requestedNickname = payload.get("nickname").getAsString().trim();

				// RoomManager 등록이 성공한 경우에만 이 플레이어의 닉네임으로 저장한다.
				if (roomManager.registerNickname(requestedNickname)) {
					this.nickname = requestedNickname;
					responseType = "ACK";
					responsePayload.addProperty("playerId", playerId);
					responsePayload.addProperty("nickname", this.nickname);
				}
				// 등록 실패
				else {
					System.out.println("닉네임 등록 실패: 비어 있거나 이미 사용 중인 닉네임입니다.");
					responseType = "ERROR";
					responsePayload.addProperty("code", "NICKNAME_TAKEN_OR_INVALID");
					responsePayload.addProperty("message", "닉네임이 비어 있거나 이미 사용 중입니다.");
				}
			}
			case "LIST_ROOMS" -> {
				responseType = "ACK";
				responsePayload.addProperty("message", "방 목록을 전송합니다.");
				roomListPayload = roomManager.getRoomListPayload();
			}

			// 방 생성
			case "CREATE_ROOM" -> {
				JsonObject payload = request.getAsJsonObject("payload");
				String title = payload.get("title").getAsString().trim();

				if (this.nickname == null) {
					responseType = "ERROR";
					responsePayload.addProperty("code", "JOIN_REQUIRED");
					responsePayload.addProperty("message", "먼저 닉네임을 등록해야 합니다.");
				} else if (title.isBlank()) {
					responseType = "ERROR";
					responsePayload.addProperty("code", "INVALID_ROOM_TITLE");
					responsePayload.addProperty("message", "방 제목을 입력해야 합니다.");
				} else {
					GameRoom room = roomManager.createRoom(title, this);
					if (room == null) {
						responseType = "ERROR";
						responsePayload.addProperty("code", "ROOM_CREATE_FAILED");
						responsePayload.addProperty("message", "이미 다른 방에 참가 중입니다.");
					}
					// 방 생성 성공 시
					else {
						responseType = "ACK";
						responsePayload.addProperty("roomId", room.roomId);
						responsePayload.addProperty("title", room.title);
						responsePayload.addProperty("playerCount", room.users.size());
						responsePayload.addProperty("maxPlayers", room.maxPlayers);
						responsePayload.addProperty("status", room.status);
						roomToBroadcast = room;
					}
				}
			}
			case "JOIN_ROOM" -> {
				JsonObject payload = request.getAsJsonObject("payload");
				String roomId = payload.get("roomId").getAsString().trim();

				if (this.nickname == null) {
					responseType = "ERROR";
					responsePayload.addProperty("code", "JOIN_REQUIRED");
					responsePayload.addProperty("message", "먼저 닉네임을 등록해야 합니다.");
				} else {
					GameRoom room = roomManager.joinRoom(roomId, this);
					if (room == null) {
						responseType = "ERROR";
						responsePayload.addProperty("code", "ROOM_JOIN_FAILED");
						responsePayload.addProperty("message", "방을 찾을 수 없거나, 방이 가득 찼거나, 이미 다른 방에 있습니다.");
					}
					/* 방 입장 성공 시 */
					else {
						responseType = "ACK";
						responsePayload.addProperty("roomId", room.roomId);
						responsePayload.addProperty("title", room.title);
						responsePayload.addProperty("playerCount", room.users.size());
						responsePayload.addProperty("maxPlayers", room.maxPlayers);
						responsePayload.addProperty("status", room.status);
						roomToBroadcast = room;
					}
				}
			}
			case "READY" -> {
				JsonObject payload = request.getAsJsonObject("payload");

				if (payload == null
						|| !payload.has("ready")
						|| !payload.get("ready").isJsonPrimitive()
						|| !payload.getAsJsonPrimitive("ready").isBoolean()) {
					responseType = "ERROR";
					responsePayload.addProperty("code", "INVALID_READY_VALUE");
					responsePayload.addProperty("message", "ready 값은 true 또는 false여야 합니다.");
				}
				/* 준비 완료 */
				else {
					boolean ready = payload.get("ready").getAsBoolean();
					GameRoom room = roomManager.setReady(this, ready);

					if (room == null) {
						responseType = "ERROR";
						responsePayload.addProperty("code", "NOT_IN_ROOM");
						responsePayload.addProperty("message", "준비 상태를 바꾸려면 먼저 방에 입장해야 합니다.");
					} else {
						responseType = "ACK";
						responsePayload.addProperty("roomId", room.roomId);
						responsePayload.addProperty("ready", this.ready);
						roomToBroadcast = room;
					}
				}
			}
			case "LEAVE_ROOM" -> {
				GameRoom room = roomManager.leaveRoom(this);
				if (room == null) {
					responseType = "ERROR";
					responsePayload.addProperty("code", "NOT_IN_ROOM");
					responsePayload.addProperty("message", "현재 참가 중인 방이 없습니다.");
				} else {
					responseType = "ACK";
					responsePayload.addProperty("roomId", room.roomId);
					responsePayload.addProperty("message", "방에서 나왔습니다.");
					roomToBroadcast = room;
				}
			}

			// Error
			case null, default -> {
				responseType = "ERROR";
				responsePayload.addProperty("code", "UNKNOWN_MESSAGE_TYPE");
				responsePayload.addProperty("message", "지원하지 않는 요청 종류입니다.");
			}
		}

		if ("ACK".equals(responseType)) {
			responsePayload.addProperty("requestType", type);
		}
		sendResponse(out, responseType, request.get("requestId"), responsePayload);
		if (roomToBroadcast != null) {
			roomManager.broadcastRoomState(roomToBroadcast);
		}
		if (roomListPayload != null) {
			sendEvent("ROOM_LIST", roomListPayload);
		}
	}

	// JSON 응답을 한 줄로 전송한다. 줄바꿈과 flush가 있어야 클라이언트가 바로 읽을 수 있다.
	private synchronized void sendResponse(BufferedWriter out, String type, JsonElement requestId,
						  JsonObject payload) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("type", type);
		if (requestId != null && !requestId.isJsonNull()) {
			response.add("requestId", requestId.deepCopy());
		}
		response.add("payload", payload);

		out.write(response.toString());
		out.newLine();
		out.flush();
	}

	// 다른 서버 코드가 이 플레이어의 방을 관리할 때 사용한다.
	public synchronized void sendEvent(String type, JsonObject payload) {
		BufferedWriter currentWriter = writer;
		if (currentWriter == null) {
			return;
		}

		JsonObject event = new JsonObject();
		event.addProperty("type", type);
		event.add("payload", payload.deepCopy());

		try {
			currentWriter.write(event.toString());
			currentWriter.newLine();
			currentWriter.flush();
		} catch (IOException e) {
			System.out.println("이벤트 전송 실패 (" + playerId + "): " + e.getMessage());
		}
	}

	public RoomManager getRoomManager() {
		return roomManager;
	}

	// 다른 서버 코드가 이 클라이언트에게 응답을 보낼 때 사용할 소켓을 반환한다.
	public Socket getSocket() {
		return socket;
	}
}
