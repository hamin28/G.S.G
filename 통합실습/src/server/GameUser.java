package server;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.net.Socket;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** 클라이언트 한 명의 소켓과 수신 스레드. 게임 규칙은 RoomManager를 통해 호출한다. */
public class GameUser extends Thread {
    final String playerId = "player-" + UUID.randomUUID();
    String nickname;
    GameRoom currentRoom;
    // 계정 저장소 연결 전, 접속 세션에서만 유지하는 테스트 잔액이다.
    long balance;
    private final Socket socket;
    private final RoomManager roomManager;
    private final Set<String> requestIds = new HashSet<>();
    private BufferedWriter writer;

    public GameUser(Socket socket, RoomManager roomManager) {
        this.socket = socket; this.roomManager = roomManager;
        balance = roomManager.initialBalance();
    }
    @Override public void run() {
        System.out.println("클라이언트 접속: " + playerId);
        try (Socket client = socket) {
            synchronized (this) {
                writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
            }
            InputStream input = new BufferedInputStream(client.getInputStream());
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int next;
            while ((next = input.read()) != -1) {
                if (next == '\n') {
                    byte[] bytes = line.toByteArray(); int length = bytes.length;
                    if (length > 0 && bytes[length - 1] == '\r') length--;
                    if (length > 65536) break;
                    String message = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, 0, length)).toString();
                    handleMessage(message);
                    line.reset();
                } else {
                    if (line.size() >= 65537) break;
                    line.write(next);
                }
            }
        } catch (IOException e) {
            System.out.println("연결 종료 (" + playerId + "): " + e.getMessage());
        } finally {
            synchronized (this) { writer = null; }
            roomManager.disconnect(this);
        }
    }
    // 테스트에서도 동일한 요청 해석 경로를 사용한다.
    void handleMessage(String message) {
        String id = null;
        try {
            JsonReader reader = new JsonReader(new StringReader(message)); reader.setStrictness(Strictness.STRICT);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new RoomManager.RequestException("INVALID_MESSAGE", "JSON 객체 하나를 보내세요.");
            JsonObject request = parsed.getAsJsonObject();
            id = RoomManager.string(request, "requestId");
            if (id.isBlank()) { id = null; throw new RoomManager.RequestException("INVALID_MESSAGE", "requestId가 필요합니다."); }
            if (!requestIds.add(id)) throw new RoomManager.RequestException("DUPLICATE_REQUEST", "이미 처리한 요청입니다.");
            String type = RoomManager.string(request, "type");
            JsonElement body = request.get("payload");
            if (body == null || !body.isJsonObject()) throw new RoomManager.RequestException("INVALID_MESSAGE", "payload 객체가 필요합니다.");
            roomManager.handleRequest(this, type, id, body.getAsJsonObject());
        } catch (RoomManager.RequestException e) {
            sendError(id, e.code, e.getMessage());
        } catch (JsonParseException | IOException e) {
            sendError(id, "INVALID_MESSAGE", "JSON 형식을 확인하세요.");
        }
    }
    public synchronized void sendMessage(String type, String requestId, JsonObject payload) {
        if (writer == null) return;
        JsonObject message = new JsonObject(); message.addProperty("type", type);
        if (requestId != null) message.addProperty("requestId", requestId);
        message.add("payload", payload);
        try {
            writer.write(message.toString()); writer.write('\n'); writer.flush();
        } catch (IOException e) {
            // 종료 정리는 수신 스레드의 finally가 수행한다.
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
    public void sendEvent(String type, JsonObject payload) { sendMessage(type, null, payload); }
    void sendError(String id, String code, String message) {
        JsonObject payload = new JsonObject(); payload.addProperty("code", code); payload.addProperty("message", message);
        sendMessage("ERROR", id, payload);
    }
    public RoomManager getRoomManager() { return roomManager; }
    public Socket getSocket() { return socket; }
}
