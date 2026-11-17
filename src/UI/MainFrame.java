package gsg.client.ui;

import java.awt.CardLayout;
import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    public MainFrame() {
        super("갈래? 말래?");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 로비 화면
        LobbyPanel lobby = new LobbyPanel(() -> cards.show(root, "game"));

        // 게임 화면 (나중에 GamePanel로 교체)
        JPanel game = new JPanel();
        game.setBackground(new Color(0x2A, 0x6B, 0x43));
        JButton toLobby = new JButton("로비로");
        game.add(toLobby);

        // 화면 등록
        root.add(lobby, "lobby");
        root.add(game, "game");
        setContentPane(root);

        // 버튼 동작
        toLobby.addActionListener(e -> cards.show(root, "lobby"));

        setSize(1240, 820);
        setLocationRelativeTo(null);
    }
}