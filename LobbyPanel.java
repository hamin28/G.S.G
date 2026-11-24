package gsg.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class LobbyPanel extends JPanel {

    private final JPanel listPanel = new JPanel();                 // 방 줄들이 쌓이는 곳
    private final JLabel nickname = new JLabel("닉네임 : 플레이어");
    private final JLabel money = new JLabel("보유금 : -");
    private final GameClient client;
    private final Session session;
    private final Consumer<String> onEnterRoom;                   // 방에 들어가기로 정해지면 (roomId) 실행

    // [8-4] 가짜 방 대신 서버와 대화하도록 client, session을 받음. 입장 동작에는 방 ID를 넘겨 줌
    public LobbyPanel(GameClient client, Session session, Consumer<String> onEnterRoom) {
        this.client = client;
        this.session = session;
        this.onEnterRoom = onEnterRoom;
        setLayout(new BorderLayout());
        setBackground(new Color(0x17, 0x3A, 0x27));

        // ---------- 헤더: 제목(왼쪽) + 새로고침 / 방 만들기(오른쪽) ----------
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("갈래? 말래?");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        header.add(title, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton btnRefresh = new JButton("새로고침");
        btnRefresh.addActionListener(e -> refresh());
        buttons.add(btnRefresh);
        JButton btnCreate = new JButton("방 만들기");
        btnCreate.addActionListener(e -> createRoom());
        buttons.add(btnCreate);
        header.add(buttons, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------- 가운데: 스크롤되는 방 목록 ----------
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 8));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        // (3단계의 가짜 방 3개는 삭제: 방 목록은 서버의 ROOM_LIST로 채움)

        // ---------- 오른쪽: 프로필 ----------
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.setPreferredSize(new Dimension(240, 0));
        add(east, BorderLayout.EAST);

        JPanel profile = new JPanel(new GridLayout(3, 1, 0, 6));
        profile.setBackground(Color.GRAY);
        profile.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        east.add(profile, BorderLayout.NORTH);

        profile.add(nickname);
        profile.add(money);
        JButton btnNick = new JButton("닉네임 변경");
        btnNick.addActionListener(e -> changeNickname());
        profile.add(btnNick);

        updateProfile();
    }

    // [8-3] 내 닉네임과 보유금을 session 값으로 다시 표시
    public void updateProfile() {
        nickname.setText("닉네임 : " + session.nickname);
        money.setText("보유금 : " + Session.won(session.balance));
    }

    // [8-4] 서버에 방 목록을 요청 (LIST_ROOMS). 결과는 ROOM_LIST 이벤트로 와서 setRooms가 처리
    public void refresh() {
        client.request("LIST_ROOMS", new JsonObject(), resp -> {
            if (GameClient.isError(resp)) showError(resp);
        });
    }

    // [8-4] ROOM_LIST 이벤트: 목록을 지우고 서버가 준 방들로 다시 채움
    // TODO(확인): 방 목록 필드 이름 (아래는 rooms / roomId / title / status / playerCount / maxPlayers 로 가정)
    public void setRooms(JsonObject body) {
        listPanel.removeAll();
        JsonElement roomsEl = body.get("rooms");
        int shown = 0;
        if (roomsEl != null && roomsEl.isJsonArray()) {
            for (JsonElement e : roomsEl.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject r = e.getAsJsonObject();
                String roomId = Jx.str(r, "roomId");
                if (roomId == null) roomId = Jx.str(r, "id");
                String name = Jx.str(r, "title");
                if (name == null) name = Jx.str(r, "name");
                String status = Jx.str(r, "status");
                int cur = Jx.num(r, "playerCount", -1);
                if (cur < 0) {
                    JsonElement pe = r.get("players");
                    cur = (pe == null) ? 0 : Jx.count(pe);
                }
                int max = Jx.num(r, "maxPlayers", 2);
                if ("CLOSED".equals(status)) continue;      // 닫힌 방은 보여 주지 않음
                addRoom(roomId, name == null ? "(제목 없음)" : name, cur, max, status);
                shown++;
            }
        }
        if (shown == 0) {
            JLabel empty = new JLabel("열린 방이 없습니다. 방을 만들어 보세요.");
            empty.setForeground(Color.WHITE);
            listPanel.add(empty);
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    // 방 한 줄 추가
    private void addRoom(String roomId, String name, int cur, int max, String status) {
        listPanel.add(new RoomRow(name, cur, max, status, () -> joinRoom(roomId)));
        listPanel.add(Box.createVerticalStrut(8));
    }

    // [8-4] 입장 버튼: JOIN_ROOM 요청 -> ACK이면 게임 화면으로
    private void joinRoom(String roomId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("roomId", roomId);
        client.request("JOIN_ROOM", payload, resp -> {
            if (GameClient.isError(resp)) {
                showError(resp);
                refresh();               // 그 사이 상태가 바뀌었을 수 있으니 목록 갱신
                return;
            }
            String id = Jx.str(Jx.body(resp), "roomId");
            onEnterRoom.accept(id != null ? id : roomId);
        });
    }

    // 4단계: 방 만들기 대화상자 -> [8-4] CREATE_ROOM 요청, ACK의 roomId로 게임 화면으로
    private void createRoom() {
        String name = JOptionPane.showInputDialog(this, "방 제목 (1~30자)", "방 만들기", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;                 // 취소 버튼
        name = name.trim();
        if (name.isEmpty() || name.length() > 30) {
            JOptionPane.showMessageDialog(this, "방 제목은 1~30자로 입력하세요.");
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("title", name);
        client.request("CREATE_ROOM", payload, resp -> {
            if (GameClient.isError(resp)) {
                showError(resp);
                return;
            }
            String id = Jx.str(Jx.body(resp), "roomId");
            onEnterRoom.accept(id);
        });
    }

    // 4단계: 닉네임 변경 대화상자 -> [8-3] JOIN을 다시 보내 봄
    // TODO(확인): 서버가 한 접속에서 JOIN을 다시 받아 주는지. 거절하면 ERROR 문구가 그대로 표시됨
    private void changeNickname() {
        String name = JOptionPane.showInputDialog(this, "닉네임 (1~12자)", "닉네임 변경", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty() || name.length() > 12) {
            JOptionPane.showMessageDialog(this, "닉네임은 1~12자로 입력하세요.");
            return;
        }
        final String newName = name;
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", newName);
        client.request("JOIN", payload, resp -> {
            if (GameClient.isError(resp)) {
                showError(resp);
                return;
            }
            session.nickname = newName;
            updateProfile();
        });
    }

    // 서버가 거절(ERROR)했을 때 이유를 보여 줌: payload.code / payload.message
    private void showError(JsonObject resp) {
        JOptionPane.showMessageDialog(this, GameClient.errorText(resp), "요청 실패", JOptionPane.WARNING_MESSAGE);
    }
}
