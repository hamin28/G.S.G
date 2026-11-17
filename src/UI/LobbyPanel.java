package gsg.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class LobbyPanel extends JPanel {

    private final JPanel listPanel = new JPanel();                 // 방 줄들이 쌓이는 곳
    private final JLabel nickname = new JLabel("닉네임 : 플레이어");
    private final Runnable onEnterRoom;

    public LobbyPanel(Runnable onEnterRoom) {
        this.onEnterRoom = onEnterRoom;
        setLayout(new BorderLayout());
        setBackground(new Color(0x17, 0x3A, 0x27));

        // ---------- 헤더: 제목(왼쪽) + 방 만들기(오른쪽) ----------
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("갈래? 말래?");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        header.add(title, BorderLayout.WEST);

        JButton btnCreate = new JButton("방 만들기");
        btnCreate.addActionListener(e -> createRoom());
        header.add(btnCreate, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------- 가운데: 스크롤되는 방 목록 ----------
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 8));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        // 가짜 방 (시험용, 서버 연결 후 삭제)
        addRoom("초보 환영", 1, 2, false);
        addRoom("고수만 오세요", 2, 2, true);
        addRoom("심심해서 만든 방", 0, 2, false);

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
        profile.add(new JLabel("보유금 : 1,000,000원"));
        JButton btnNick = new JButton("닉네임 변경");
        btnNick.addActionListener(e -> changeNickname());
        profile.add(btnNick);
    }

    // 방 한 줄 추가
    private void addRoom(String name, int cur, int max, boolean playing) {
        listPanel.add(new RoomRow(name, cur, max, playing, onEnterRoom));
        listPanel.add(Box.createVerticalStrut(8));
        listPanel.revalidate();
        listPanel.repaint();
    }

    // 4단계: 방 만들기 대화상자
    private void createRoom() {
        String name = JOptionPane.showInputDialog(this, "방 제목 (1~30자)", "방 만들기", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;                 // 취소 버튼
        name = name.trim();
        if (name.isEmpty() || name.length() > 30) {
            JOptionPane.showMessageDialog(this, "방 제목은 1~30자로 입력하세요.");
            return;
        }
        addRoom(name, 1, 2, false);
    }

    // 4단계: 닉네임 변경 대화상자
    private void changeNickname() {
        String name = JOptionPane.showInputDialog(this, "닉네임 (1~12자)", "닉네임 변경", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty() || name.length() > 12) {
            JOptionPane.showMessageDialog(this, "닉네임은 1~12자로 입력하세요.");
            return;
        }
        nickname.setText("닉네임 : " + name);
    }
}