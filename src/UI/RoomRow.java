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

	// [8-4] 방 상태가 "대기/게임 중" 2종류에서 서버의 5종류로 늘어남
	// WAITING(상대 대기), READY(준비 대기), DEALER_SELECTION(선 선택), PLAYING, CLOSED
	private static String statusText(String status) {
		if ("WAITING".equals(status))
			return "대기";
		if ("READY".equals(status))
			return "준비 중";
		if ("DEALER_SELECTION".equals(status))
			return "선 정하는 중";
		if ("PLAYING".equals(status))
			return "게임 중";
		if ("CLOSED".equals(status))
			return "닫힘";
		return status == null ? "?" : status;
	}

	private static Color statusColor(String status) {
		if ("WAITING".equals(status))
			return new Color(0x2E, 0x9E, 0x5B); // 초록: 들어갈 수 있음
		if ("READY".equals(status) || "DEALER_SELECTION".equals(status))
			return new Color(0xD6, 0x8A, 0x1E);
		return new Color(0xC0, 0x39, 0x2B); // 빨강: 게임 중, 닫힘
	}

	public RoomRow(String name, int cur, int max, String status, Runnable onEnter) {
		setLayout(new BorderLayout(12, 0));
		setBackground(new Color(0x1F, 0x4D, 0x35));
		setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 56)); // BoxLayout에서 세로로 늘어나지 않게

		// 방 이름 (왼쪽, 남는 공간 차지)
		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(new Font("맑은 고딕", Font.BOLD, 16));
		add(nameLabel, BorderLayout.CENTER);

		// 인원 수
		JLabel count = new JLabel(cur + " / " + max);
		count.setForeground(Color.WHITE);

		// 상태 뱃지 (배경색이 보이려면 setOpaque(true) 필요)
		JLabel statusLabel = new JLabel(statusText(status), SwingConstants.CENTER);
		statusLabel.setOpaque(true);
		statusLabel.setForeground(Color.WHITE);
		statusLabel.setBackground(statusColor(status));
		statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));

		// 입장 버튼: 상대를 기다리는 방(WAITING)이고 자리가 남았을 때만 활성화
		JButton btnEnter = new JButton("입장");
		btnEnter.setEnabled("WAITING".equals(status) && cur < max);
		btnEnter.addActionListener(e -> onEnter.run());

		// 오른쪽 묶음
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
		right.setOpaque(false);
		right.add(count);
		right.add(statusLabel);
		right.add(btnEnter);
		add(right, BorderLayout.EAST);
	}
}
