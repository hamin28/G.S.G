package gsg.client.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// [8-1] 서버와의 통로. 접속하고, 읽기 전용 스레드로 한 줄씩 받고, 요청을 보낸다.
//       서버 규칙: UTF-8 JSON 한 개 + LF("\n") 한 줄 (docs/NETWORK_SERVER.md)
public class GameClient {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private int seq = 0;                                   // requestId 번호: req-1, req-2, ...
    private final Map<String, Consumer<JsonObject>> pending = new ConcurrentHashMap<>();

    private Consumer<JsonObject> onEvent;                  // requestId 없는 메시지(이벤트)를 받을 때
    private Runnable onClosed;                             // 연결이 끊어졌을 때

    // [8-2] 서버 이벤트(ROOM_LIST, GAME_STATE ...)를 처리할 동작을 등록
    public void setOnEvent(Consumer<JsonObject> c) {
        onEvent = c;
    }

    public void setOnClosed(Runnable r) {
        onClosed = r;
    }

    // [8-1] 접속하고 읽기 스레드를 시작
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        // readLine()은 서버가 보낼 때까지 멈춰 있으므로 화면 스레드가 아닌 별도 스레드에서 읽는다
        Thread reader = new Thread(this::readLoop, "server-reader");
        reader.setDaemon(true);   // 창을 닫으면 같이 종료
        reader.start();
    }

    // [8-1] 읽기 스레드가 하는 일: 한 줄 받을 때마다 handleLine
    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            // 연결이 끊어짐 (아래에서 처리)
        }
        if (onClosed != null) {
            SwingUtilities.invokeLater(onClosed);
        }
    }

    // [8-2] 받은 한 줄(JSON)을 해석해서
    //       - 내 요청에 대한 응답(requestId가 있고 등록된 것)이면 그 요청의 콜백으로
    //       - 아니면 이벤트로 전달한다.
    // [8-3] 읽기 스레드는 화면 스레드가 아니므로, 화면을 만지는 코드는 반드시 invokeLater로 넘겨서 실행한다.
    private void handleLine(String line) {
        final JsonObject msg;
        try {
            msg = JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            System.out.println("[수신/해석 실패] " + line);
            return;
        }
        System.out.println("[수신] " + line);   // 개발 중 확인용: 실제 필드 이름을 여기서 볼 수 있음

        String requestId = Jx.str(msg, "requestId");
        final Consumer<JsonObject> callback = (requestId == null) ? null : pending.remove(requestId);

        SwingUtilities.invokeLater(() -> {
            if (callback != null) {
                callback.accept(msg);
            } else if (onEvent != null) {
                onEvent.accept(msg);
            }
        });
    }

    // [8-2] 요청 보내기. 응답(ACK 또는 ERROR)이 오면 onResponse가 화면 스레드에서 실행된다.
    //       payload가 필요 없는 요청(LIST_ROOMS 등)은 new JsonObject() 를 넘긴다.
    public synchronized String request(String type, JsonObject payload, Consumer<JsonObject> onResponse) {
        seq++;
        String requestId = "req-" + seq;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        msg.addProperty("requestId", requestId);
        msg.add("payload", payload == null ? new JsonObject() : payload);
        if (onResponse != null) {
            pending.put(requestId, onResponse);
        }
        send(msg.toString());
        return requestId;
    }

    // [8-1] 한 줄 전송. println 대신 "\n"을 직접 붙임 (Windows의 \r\n 방지)
    private synchronized void send(String json) {
        System.out.println("[송신] " + json);
        try {
            out.write(json);
            out.write("\n");
            out.flush();
        } catch (IOException e) {
            System.out.println("[송신 실패] " + e.getMessage());
        }
    }

    // 응답이 ERROR인지 검사
    public static boolean isError(JsonObject resp) {
        return "ERROR".equals(Jx.str(resp, "type"));
    }

    // ERROR 응답에서 사람이 읽을 문장을 만든다: payload.code / payload.message
    public static String errorText(JsonObject resp) {
        JsonObject b = Jx.body(resp);
        String code = Jx.str(b, "code");
        String message = Jx.str(b, "message");
        if (code == null && message == null) return "알 수 없는 오류";
        if (message == null) return code;
        if (code == null) return message;
        return message + " (" + code + ")";
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // 무시
        }
    }
}
