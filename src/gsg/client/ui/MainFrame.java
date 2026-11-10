package gsg.client.ui;

import com.google.gson.JsonObject;
import gsg.client.net.ServerConnection;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

import static gsg.client.net.ServerConnection.payload;

/**
 * 클라이언트 메인 창. 로비 화면과 게임 방 화면을 CardLayout으로 전환하고,
 * 서버 메시지를 type별로 알맞은 화면에 전달한다 (프로토콜 10장 통신 흐름).
 */
public class MainFrame extends JFrame implements ServerConnection.Listener, LobbyPanel.Actions, GameRoomPanel.Actions {

    private final CardLayout screens = new CardLayout();
    private final JPanel root = new JPanel(screens);
    private final LobbyPanel lobby = new LobbyPanel(this);
    private final GameRoomPanel room = new GameRoomPanel(this);

    private ServerConnection conn;
    private String myPlayerId;
    private String myNickname;
    private String pendingTitle;                                   // CREATE_ROOM으로 만든 방 제목
    private final Map<String, String> requestTypes = new HashMap<>(); // requestId → 요청 종류 (ERROR 처리용)

    public MainFrame() {
        super("갈래? 말래?");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        root.add(lobby, "lobby");
        root.add(room, "room");
        setContentPane(root);
        setMinimumSize(new Dimension(1100, 760));
        setSize(1240, 820);
        setLocationRelativeTo(null);
    }

    private void showScreen(String name) {
        screens.show(root, name);
    }

    private void send(String type, JsonObject payload) {
        if (conn == null) return;
        String id = conn.send(type, payload);
        requestTypes.put(id, type);
    }

    // ============================================================ 로비 동작

    @Override
    public void connect(String host, int port, String nickname) {
        myNickname = nickname;
        conn = new ServerConnection(this);
        new Thread(() -> {            // 소켓 연결은 EDT 밖에서
            try {
                conn.connect(host, port);
                SwingUtilities.invokeLater(() -> send("JOIN", payload("nickname", nickname)));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> lobby.connectFailed("접속 실패: " + host + ":" + port + " (" + e.getMessage() + ")"));
            }
        }, "connect").start();
    }

    @Override public void refreshRooms()          { send("LIST_ROOMS", null); }
    @Override public void createRoom(String title) { pendingTitle = title; send("CREATE_ROOM", payload("title", title)); }
    @Override public void joinRoom(String roomId)  { pendingTitle = null; send("JOIN_ROOM", payload("roomId", roomId)); }

    // ============================================================ 방 동작

    @Override public void ready(boolean ready) { send("READY", payload("ready", ready)); }
    @Override public void leave()              { send("LEAVE_ROOM", null); }
    @Override public void chat(String message) { send("CHAT", payload("message", message)); }
    @Override public void playCard(int cardId) { send("PLAY_CARD", payload("cardId", cardId)); }

    @Override
    public void selectCapture(String choiceId, int cardId) {
        send("SELECT_CAPTURE", payload("choiceId", choiceId, "cardId", cardId));
    }

    @Override
    public void decide(String choiceId, String decision) {
        send("DECIDE", payload("choiceId", choiceId, "decision", decision));
    }

    // ============================================================ 서버 메시지 (EDT)

    @Override
    public void onMessage(JsonObject msg) {
        String type = msg.get("type").getAsString();
        JsonObject p = msg.has("payload") && msg.get("payload").isJsonObject() ? msg.getAsJsonObject("payload") : new JsonObject();
        String requestId = msg.has("requestId") ? msg.get("requestId").getAsString() : null;

        switch (type) {
            case "ACK":            onAck(p); break;
            case "ERROR":          onError(requestId, p); break;
            case "ROOM_LIST":      lobby.setRooms(p.getAsJsonArray("rooms")); break;
            case "ROOM_STATE":     room.setRoomState(p); break;
            case "CHAT_MESSAGE":   room.addChat(p); break;
            case "GAME_START":     room.setGameState(p, true); break;
            case "GAME_STATE":     room.setGameState(p, false); break;
            case "CHOICE_REQUIRED":room.onChoiceRequired(p); break;
            case "GAME_OVER":      room.onGameOver(p); break;
            default:               System.out.println("[알림] 처리하지 않는 메시지: " + type);
        }
        if (requestId != null) requestTypes.remove(requestId);
    }

    private void onAck(JsonObject p) {
        String req = p.get("requestType").getAsString();
        switch (req) {
            case "JOIN":
                myPlayerId = p.get("playerId").getAsString();
                lobby.setJoined(true, myNickname);
                lobby.showStatus("접속했습니다. (" + myPlayerId + ")", false);
                setTitle("갈래? 말래? — " + myNickname);
                refreshRooms();
                break;
            case "CREATE_ROOM":
            case "JOIN_ROOM":
                room.enterRoom(myPlayerId, pendingTitle);
                showScreen("room");
                break;
            case "LEAVE_ROOM":
                showScreen("lobby");
                lobby.showStatus("방에서 나왔습니다.", false);
                refreshRooms();
                break;
            case "READY":
                room.onReadyAck();
                break;
            default:
                break;
        }
    }

    private void onError(String requestId, JsonObject p) {
        String req = requestId == null ? null : requestTypes.get(requestId);
        String code = p.has("code") ? p.get("code").getAsString() : "UNKNOWN";
        String message = p.has("message") ? p.get("message").getAsString() : code;
        System.err.println("[오류] " + req + " → " + code + ": " + message);

        if ("JOIN".equals(req)) {
            conn.close();
            conn = null;
            lobby.connectFailed(message);
        } else if ("LIST_ROOMS".equals(req) || "CREATE_ROOM".equals(req) || "JOIN_ROOM".equals(req)) {
            lobby.showStatus(message, true);
            refreshRooms();
        } else {
            room.onError(req, message);
        }
    }

    @Override
    public void onDisconnected(String reason) {
        conn = null;
        myPlayerId = null;
        showScreen("lobby");
        lobby.connectFailed(reason);
        JOptionPane.showMessageDialog(this, reason, "연결 종료", JOptionPane.WARNING_MESSAGE);
    }
}
