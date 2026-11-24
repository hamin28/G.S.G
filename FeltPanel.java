package gsg.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

// [8-7] FeltPanel은 이제 "보여 주기만" 하는 화면이다.
//       무엇을 보여 줄지(서버 상태 해석)는 GamePanel이 정하고, 이 클래스의 set 메서드로 넘겨 준다.
public class FeltPanel extends JPanel {

    private final JPanel OtherUser = new JPanel();       // 상대 영역
    private final JLabel OtherUserName = new JLabel("상대: ---  점수 0  고 0");
    private final JPanel otherHand = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));   // 상대 손패(뒷면)
    private final JPanel Koikoi = new JPanel(new BorderLayout());   // 바닥패 영역
    private final JLabel statusLabel = new JLabel("바닥패");
    private final JButton btnReady = new JButton("준비");
    private final JPanel floor = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));         // 바닥패 카드들
    private final JPanel User = new JPanel();            // 내 영역
    private final JLabel myInfo = new JLabel("나: ---  점수 0  고 0");
    private final JPanel hand = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));            // 내 손패

    private Consumer<Integer> onCardClick;               // 손패 카드를 클릭했을 때 (카드 ID)
    private Runnable onReady;                            // 준비 버튼을 눌렀을 때

    public FeltPanel() {
        // [5-1] 담요 화면 전체를 BorderLayout으로 나눔 (NORTH 상대 / CENTER 바닥패 / SOUTH 내 손패)
        setLayout(new BorderLayout());

        // [5-2] NORTH: 상대 정보 영역 (투명 패널, 흰색 라벨)
        //       [8-7] 상대 손패는 장수만큼 뒷면 카드를 보여 줌 -> 높이를 175로 키움
        OtherUser.setOpaque(false);
        OtherUser.setLayout(new BorderLayout());
        add(OtherUser, BorderLayout.NORTH);
        OtherUser.setPreferredSize(new Dimension(0, 175));

        OtherUserName.setForeground(Color.WHITE);
        OtherUserName.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        OtherUserName.setHorizontalAlignment(JLabel.CENTER);
        OtherUserName.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 0, 4, 0));
        OtherUser.add(OtherUserName, BorderLayout.NORTH);

        otherHand.setOpaque(false);
        OtherUser.add(otherHand, BorderLayout.CENTER);

        // [5-3] CENTER: 바닥패 영역 (투명 패널, 손패에서 낸 카드가 이곳에 놓임)
        //       [8-5] 위쪽 줄: 안내 문구 + 준비 버튼 / 아래: 바닥패 카드들
        Koikoi.setOpaque(false);
        add(Koikoi, BorderLayout.CENTER);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        statusRow.setOpaque(false);
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        statusRow.add(statusLabel);
        btnReady.addActionListener(e -> {
            if (onReady != null) onReady.run();
        });
        statusRow.add(btnReady);
        Koikoi.add(statusRow, BorderLayout.NORTH);

        floor.setOpaque(false);
        Koikoi.add(floor, BorderLayout.CENTER);

        // [5-4] SOUTH: 내 손패 영역 (투명 패널)
        //   [6-3] FlowLayout으로 카드 크기(80x130)를 유지하며 왼쪽부터 배치
        //         (GridLayout은 카드가 빠지면 남은 카드 폭이 늘어나 FlowLayout으로 변경)
        //   [8-7] 손패 위에 내 정보(점수/고) 한 줄을 추가 -> 높이 190
        User.setOpaque(false);
        User.setLayout(new BorderLayout());
        User.setPreferredSize(new Dimension(0, 190));

        myInfo.setForeground(Color.WHITE);
        myInfo.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        myInfo.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 0));
        User.add(myInfo, BorderLayout.NORTH);

        hand.setOpaque(false);
        User.add(hand, BorderLayout.CENTER);
        add(User, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------- GamePanel이 부르는 메서드들

    public void setOnCardClick(Consumer<Integer> c) {
        onCardClick = c;
    }

    public void setOnReady(Runnable r) {
        onReady = r;
    }

    public void setOpponentText(String text) {
        OtherUserName.setText(text);
    }

    public void setMyInfoText(String text) {
        myInfo.setText(text);
    }

    public void setStatusText(String text) {
        statusLabel.setText(text);
    }

    // 준비 버튼: 보일지, 누를 수 있을지, 글자
    public void setReadyButton(boolean visible, boolean enabled, String text) {
        btnReady.setVisible(visible);
        btnReady.setEnabled(enabled);
        btnReady.setText(text);
    }

    // 상대 손패: n장만큼 뒷면 카드를 그림
    public void setOpponentHandCount(int n) {
        otherHand.removeAll();
        for (int i = 0; i < n; i++) {
            otherHand.add(new CardView(CardCatalog.BACK));
        }
        otherHand.revalidate();
        otherHand.repaint();
    }

    // 내 손패: 카드 ID 목록으로 다시 그림. clickable이 true일 때만 클릭하면 내기(onCardClick) 실행
    public void setHand(List<Integer> ids, boolean clickable) {
        hand.removeAll();
        for (int id : ids) {
            CardView card = new CardView(CardCatalog.pathOf(id));
            if (clickable) {
                final int cardId = id;
                card.setOnClick(() -> {
                    if (onCardClick != null) onCardClick.accept(cardId);
                });
            }
            hand.add(card);
        }
        hand.revalidate();
        hand.repaint();
    }

    // 요청을 보낸 뒤 서버 응답을 기다리는 동안 손패를 클릭할 수 없게 함 (중복 전송 방지)
    public void lockHand() {
        for (Component c : hand.getComponents()) {
            if (c instanceof CardView) ((CardView) c).setOnClick(null);
        }
    }

    // 바닥패: 카드 ID 목록으로 다시 그림
    public void setFloor(List<Integer> ids) {
        floor.removeAll();
        for (int id : ids) {
            floor.add(new CardView(CardCatalog.pathOf(id)));
        }
        floor.revalidate();
        floor.repaint();
    }

    // 방에 새로 들어갔을 때 화면을 비움
    public void clearTable() {
        setOpponentHandCount(0);
        setHand(java.util.Collections.<Integer>emptyList(), false);
        setFloor(java.util.Collections.<Integer>emptyList());
        OtherUserName.setText("상대: ---  점수 0  고 0");
        myInfo.setText("나: ---  점수 0  고 0");
    }

    // [5-5] 담요 배경: 위(0x2F7A4B)에서 아래(0x1B4D30)로 이어지는 초록 그라데이션
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setPaint(new GradientPaint(0, 0, new Color(0x2F, 0x7A, 0x4B), 0, getHeight(), new Color(0x1B, 0x4D, 0x30)));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }
}
