package gsg.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class RoomRow extends JPanel {

    public RoomRow(String name, int cur, int max, boolean playing, Runnable onEnter) {
        setLayout(new BorderLayout(12, 0));
        setBackground(new Color(0x1F, 0x4D, 0x35));
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));   // 세로로 늘어나지 않게

        // 방 이름 (왼쪽, 남는 공간 차지)
        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        add(nameLabel, BorderLayout.CENTER);

        // 인원 수
        JLabel count = new JLabel(cur + " / " + max);
        count.setForeground(Color.WHITE);

        // 상태 뱃지 (배경색이 보이려면 setOpaque(true) 필요)
        JLabel status = new JLabel(playing ? "게임 중" : "대기", SwingConstants.CENTER);
        status.setOpaque(true);
        status.setForeground(Color.WHITE);
        status.setBackground(playing ? new Color(0xC0, 0x39, 0x2B) : new Color(0x2E, 0x9E, 0x5B));
        status.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));

        // 입장 버튼: 게임 중이거나 가득 찬 방은 비활성화
        JButton btnEnter = new JButton("입장");
        btnEnter.setEnabled(!playing && cur < max);
        btnEnter.addActionListener(e -> onEnter.run());

        // 오른쪽 묶음
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(count);
        right.add(status);
        right.add(btnEnter);
        add(right, BorderLayout.EAST);
    }
}