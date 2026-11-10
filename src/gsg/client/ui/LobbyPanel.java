package gsg.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 로비 화면 (프로토콜 3장).
 * 서버 접속 + 닉네임 등록(JOIN) → 방 목록(LIST_ROOMS / ROOM_LIST) → 방 만들기(CREATE_ROOM) 또는 입장(JOIN_ROOM).
 * 실제 전송은 MainFrame이 맡고, 이 화면은 입력 검증과 표시만 한다.
 */
public class LobbyPanel extends JPanel {

    /** 로비에서 일어나는 사용자 동작 */
    public interface Actions {
        void connect(String host, int port, String nickname);
        void refreshRooms();
        void createRoom(String title);
        void joinRoom(String roomId);
    }

    private final Actions actions;

    private final JTextField hostField = new JTextField("localhost", 12);
    private final JTextField portField = new JTextField("5000", 5);
    private final JTextField nickField = new JTextField(10);
    private final JButton connectButton = Theme.primary(new JButton("접속"));
    private final JLabel welcomeLabel = Theme.label(" ", Font.BOLD, 14, Theme.INK);

    private final DefaultTableModel roomModel = new DefaultTableModel(new Object[]{"방 제목", "인원", "상태"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable roomTable = new JTable(roomModel);
    private final List<String> roomIds = new ArrayList<>();
    private final List<Boolean> roomJoinable = new ArrayList<>();

    private final JButton refreshButton = Theme.secondary(new JButton("새로고침"));
    private final JButton createButton = Theme.secondary(new JButton("방 만들기"));
    private final JButton joinButton = Theme.primary(new JButton("입장"));
    private final JLabel emptyLabel = Theme.label("열린 방이 없습니다. 방을 만들어 보세요.", Font.PLAIN, 13, Theme.MUTED);
    private final JLabel statusLabel = Theme.label(" ", Font.PLAIN, 12, Theme.MUTED);

    public LobbyPanel(Actions actions) {
        this.actions = actions;
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(Theme.PAPER_DARK);
        Theme.pad(status, 6, 24, 6, 24);
        status.add(statusLabel, BorderLayout.WEST);
        add(status, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> onConnect());
        nickField.addActionListener(e -> onConnect());
        refreshButton.addActionListener(e -> actions.refreshRooms());
        createButton.addActionListener(e -> onCreate());
        joinButton.addActionListener(e -> onJoin());
        roomTable.getSelectionModel().addListSelectionListener(e -> updateButtons());
        roomTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onJoin();
            }
        });
        setJoined(false, null);
    }

    // ------------------------------------------------------------ 화면 구성

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.INK);
        Theme.pad(header, 18, 24, 18, 24);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = Theme.label("갈래? 말래?", Font.BOLD, 28, Color.WHITE);
        JLabel sub = Theme.label("2인 맞고 · 로비", Font.PLAIN, 13, new Color(0xC9, 0xC1, 0xB4));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(sub);
        header.add(titleBox, BorderLayout.WEST);

        JPanel badge = new JPanel();
        badge.setBackground(Theme.RED);
        badge.setPreferredSize(new java.awt.Dimension(10, 10));
        header.add(badge, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        Theme.pad(center, 20, 24, 16, 24);
        center.add(buildConnectCard(), BorderLayout.NORTH);
        center.add(buildRoomCard(), BorderLayout.CENTER);
        return center;
    }

    private JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.PAPER_DARK),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return p;
    }

    private JPanel buildConnectCard() {
        JPanel p = card();
        p.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; c.gridwidth = 7;
        p.add(Theme.label("서버 접속", Font.BOLD, 15, Theme.INK), c);
        c.gridwidth = 1; c.gridy = 1;

        Object[][] fields = {{"서버 주소", hostField}, {"포트", portField}, {"닉네임", nickField}};
        int x = 0;
        for (Object[] f : fields) {
            c.gridx = x++;
            p.add(Theme.label((String) f[0], Font.PLAIN, 13, Theme.MUTED), c);
            c.gridx = x++;
            JTextField tf = (JTextField) f[1];
            tf.setFont(Theme.font(Font.PLAIN, 14));
            p.add(tf, c);
        }
        c.gridx = x;
        p.add(connectButton, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 7;
        JLabel hint = Theme.label("닉네임은 1~12자, 줄바꿈 없이 입력합니다.", Font.PLAIN, 12, Theme.MUTED);
        p.add(hint, c);
        c.gridy = 3;
        p.add(welcomeLabel, c);

        c.gridx = 7; c.gridy = 0; c.weightx = 1; c.gridheight = 4;
        p.add(Box.createHorizontalGlue(), c);
        return p;
    }

    private JPanel buildRoomCard() {
        JPanel p = card();
        p.setLayout(new BorderLayout(0, 10));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.label("방 목록", Font.BOLD, 15, Theme.INK), BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(refreshButton);
        buttons.add(createButton);
        buttons.add(joinButton);
        top.add(buttons, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        roomTable.setFont(Theme.font(Font.PLAIN, 14));
        roomTable.setRowHeight(34);
        roomTable.setShowVerticalLines(false);
        roomTable.setGridColor(Theme.PAPER_DARK);
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomTable.setSelectionBackground(new Color(0xFB, 0xE3, 0xE3));
        roomTable.setSelectionForeground(Theme.INK);
        roomTable.getTableHeader().setFont(Theme.font(Font.BOLD, 13));
        roomTable.getTableHeader().setReorderingAllowed(false);
        roomTable.getColumnModel().getColumn(0).setPreferredWidth(420);
        roomTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        roomTable.getColumnModel().getColumn(2).setPreferredWidth(100);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        roomTable.getColumnModel().getColumn(1).setCellRenderer(center);
        roomTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                l.setFont(Theme.font(Font.BOLD, 13));
                l.setForeground("게임 중".equals(v) ? Theme.MUTED : new Color(0x1E, 0x7A, 0x3C));
                return l;
            }
        });
        roomTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                Theme.pad(l, 0, 10, 0, 10);
                boolean joinable = row < roomJoinable.size() && roomJoinable.get(row);
                l.setForeground(joinable || sel ? Theme.INK : Theme.MUTED);
                return l;
            }
        });

        JScrollPane scroll = new JScrollPane(roomTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.PAPER_DARK));
        scroll.getViewport().setBackground(Color.WHITE);

        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        tableArea.add(scroll, BorderLayout.CENTER);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        Theme.pad(emptyLabel, 8, 0, 0, 0);
        tableArea.add(emptyLabel, BorderLayout.SOUTH);
        p.add(tableArea, BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------ 사용자 동작

    private void onConnect() {
        String host = hostField.getText().trim();
        String nick = nickField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            showStatus("포트는 숫자로 입력하세요.", true);
            return;
        }
        String err = validateText(nick, 12, "닉네임");
        if (host.isEmpty()) err = "서버 주소를 입력하세요.";
        if (err != null) {
            showStatus(err, true);
            return;
        }
        connectButton.setEnabled(false);
        showStatus("서버에 접속하는 중...", false);
        actions.connect(host, port, nick);
    }

    private void onCreate() {
        String title = (String) JOptionPane.showInputDialog(this, "방 제목 (1~30자)", "방 만들기",
                JOptionPane.PLAIN_MESSAGE, null, null, nickField.getText().trim() + "님의 방");
        if (title == null) return;
        title = title.trim();
        String err = validateText(title, 30, "방 제목");
        if (err != null) {
            showStatus(err, true);
            return;
        }
        actions.createRoom(title);
    }

    private void onJoin() {
        int row = roomTable.getSelectedRow();
        if (row < 0 || !joinButton.isEnabled()) return;
        actions.joinRoom(roomIds.get(row));
    }

    /** 프로토콜 3장 길이 규칙: 앞뒤 공백 제거 후 1~max 코드 포인트, 제어 문자 불가 */
    static String validateText(String s, int max, String name) {
        int len = s.codePointCount(0, s.length());
        if (len < 1) return name + "을(를) 입력하세요.";
        if (len > max) return name + "은(는) " + max + "자 이하로 입력하세요.";
        if (s.codePoints().anyMatch(Character::isISOControl)) return name + "에 줄바꿈이나 제어 문자를 쓸 수 없습니다.";
        return null;
    }

    // ------------------------------------------------------------ MainFrame이 호출

    /** 접속/JOIN 결과 반영 */
    public void setJoined(boolean joined, String nickname) {
        hostField.setEnabled(!joined);
        portField.setEnabled(!joined);
        nickField.setEnabled(!joined);
        connectButton.setEnabled(!joined);
        connectButton.setText(joined ? "접속됨" : "접속");
        welcomeLabel.setText(joined ? nickname + " 님, 환영합니다. 방을 고르거나 새로 만드세요." : " ");
        refreshButton.setEnabled(joined);
        createButton.setEnabled(joined);
        if (!joined) setRooms(new JsonArray());
        updateButtons();
    }

    /** 접속 실패 등으로 다시 입력할 수 있게 */
    public void connectFailed(String message) {
        setJoined(false, null);
        showStatus(message, true);
    }

    /** ROOM_LIST 이벤트 */
    public void setRooms(JsonArray rooms) {
        String selected = roomTable.getSelectedRow() >= 0 ? roomIds.get(roomTable.getSelectedRow()) : null;
        roomModel.setRowCount(0);
        roomIds.clear();
        roomJoinable.clear();
        for (JsonElement el : rooms) {
            JsonObject r = el.getAsJsonObject();
            int count = r.get("playerCount").getAsInt();
            int max = r.get("maxPlayers").getAsInt();
            boolean playing = "PLAYING".equals(r.get("status").getAsString());
            roomIds.add(r.get("roomId").getAsString());
            roomJoinable.add(!playing && count < max);
            roomModel.addRow(new Object[]{r.get("title").getAsString(), count + " / " + max, playing ? "게임 중" : "대기 중"});
        }
        int idx = selected == null ? -1 : roomIds.indexOf(selected);
        if (idx >= 0) roomTable.setRowSelectionInterval(idx, idx);
        emptyLabel.setVisible(roomIds.isEmpty());
        updateButtons();
    }

    public void showStatus(String message, boolean error) {
        statusLabel.setForeground(error ? Theme.RED : Theme.MUTED);
        statusLabel.setText(message);
    }

    private void updateButtons() {
        int row = roomTable.getSelectedRow();
        boolean joined = refreshButton.isEnabled();
        joinButton.setEnabled(joined && row >= 0 && row < roomJoinable.size() && roomJoinable.get(row));
    }
}
