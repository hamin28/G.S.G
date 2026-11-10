package gsg.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 서버와의 TCP 연결 (docs/README.md 프로토콜 1~2, 8~9장).
 * - JSON 객체 하나를 한 줄로 주고받는다 (UTF-8, LF 구분).
 * - 수신은 별도 스레드에서 하고, 화면 갱신은 invokeLater로 EDT에 넘긴다.
 * - 요청마다 연결 안에서 고유한 requestId("req-1", "req-2" ...)를 붙인다.
 */
public class ServerConnection {

    /** 서버 메시지를 받는 쪽 (항상 EDT에서 호출된다) */
    public interface Listener {
        void onMessage(JsonObject msg);
        void onDisconnected(String reason);
    }

    private static final int MAX_LINE = 64 * 1024;

    private final Listener listener;
    private final AtomicInteger requestSeq = new AtomicInteger(0);
    private Socket socket;
    private BufferedWriter out;
    private volatile boolean closing;

    public ServerConnection(Listener listener) {
        this.listener = listener;
    }

    /** 블로킹 연결이므로 EDT가 아닌 스레드에서 호출한다. */
    public void connect(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        Thread reader = new Thread(() -> readLoop(in), "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(BufferedReader in) {
        String reason = "서버와 연결이 끊어졌습니다.";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.length() > MAX_LINE) {
                    reason = "서버 메시지가 너무 깁니다.";
                    break;
                }
                JsonObject msg;
                try {
                    msg = JsonParser.parseString(line).getAsJsonObject();
                } catch (RuntimeException e) {
                    System.err.println("[수신] JSON 해석 실패: " + line);
                    continue;
                }
                System.out.println("[수신] " + line);
                SwingUtilities.invokeLater(() -> listener.onMessage(msg));
            }
        } catch (IOException e) {
            if (!closing) reason = "연결 오류: " + e.getMessage();
        }
        if (!closing) {
            String r = reason;
            SwingUtilities.invokeLater(() -> listener.onDisconnected(r));
        }
        close();
    }

    /**
     * 요청 전송. payload가 null이면 빈 객체 {}를 보낸다.
     * @return 이 요청에 붙인 requestId (ACK/ERROR 응답과 짝을 맞출 때 사용)
     */
    public String send(String type, JsonObject payload) {
        String requestId = "req-" + requestSeq.incrementAndGet();
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        msg.addProperty("requestId", requestId);
        msg.add("payload", payload != null ? payload : new JsonObject());
        String line = msg.toString();   // Gson은 줄바꿈 없이 한 줄로 직렬화한다
        synchronized (this) {           // 두 메시지가 섞이지 않도록 직렬화
            if (out == null) return requestId;
            try {
                out.write(line);
                out.write('\n');
                out.flush();
                System.out.println("[송신] " + line);
            } catch (IOException e) {
                System.err.println("[송신] 실패: " + e.getMessage());
            }
        }
        return requestId;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void close() {
        closing = true;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    /** payload를 간단히 만들기 위한 도우미: payload("nickname", "철수") */
    public static JsonObject payload(Object... keyValues) {
        JsonObject p = new JsonObject();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String k = (String) keyValues[i];
            Object v = keyValues[i + 1];
            if (v instanceof Number) p.addProperty(k, (Number) v);
            else if (v instanceof Boolean) p.addProperty(k, (Boolean) v);
            else p.addProperty(k, String.valueOf(v));
        }
        return p;
    }
}
