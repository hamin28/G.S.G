package gsg.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

/**
 * 방 안 채팅 (프로토콜 4장).
 * - 보낼 때는 내용만 CHAT으로 보내고, 서버가 돌려주는 CHAT_MESSAGE를 받았을 때만 화면에 추가한다 (중복 표시 방지).
 * - 메시지는 HTML로 해석하지 않고 일반 텍스트로 넣는다.
 * - 빠른 채팅 버튼은 정해진 문구를 같은 CHAT 요청으로 보낸다.
 */
public class ChatPanel extends JPanel {

    public static final String[] QUICK = {"고!", "스톱!", "좋아요!", "빨리 좀 해요~", "아깝다!", "한 판 더?"};

    private final JTextPane log = new JTextPane();
    private final JTextField input = new JTextField();
    private final JButton sendButton = Theme.primary(new JButton("전송"));
    private final Consumer<String> sender;

    public ChatPanel(Consumer<String> sender) {
        this.sender = sender;
        setLayout(new BorderLayout(0, 8));
        setBackground(Theme.PAPER);
        setPreferredSize(new Dimension(290, 100));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.PAPER_DARK),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        add(Theme.label("채팅", Font.BOLD, 15, Theme.INK), BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(Theme.font(Font.PLAIN, 13));
        log.setBackground(Color.WHITE);
        log.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.PAPER_DARK));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);

        JPanel quick = new JPanel(new GridLayout(0, 2, 6, 6));
        quick.setOpaque(false);
        for (String q : QUICK) {
            JButton b = Theme.secondary(new JButton(q));
            b.setFont(Theme.font(Font.BOLD, 12));
            b.addActionListener(e -> sender.accept(q));
            quick.add(b);
        }
        bottom.add(Theme.label("빠른 채팅", Font.BOLD, 12, Theme.MUTED), BorderLayout.NORTH);
        bottom.add(quick, BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        input.setFont(Theme.font(Font.PLAIN, 13));
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);
        bottom.add(inputRow, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendInput());
        input.addActionListener(e -> sendInput());
    }

    private void sendInput() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        if (text.codePointCount(0, text.length()) > 200) {
            addSystem("채팅은 200자까지 보낼 수 있습니다.");
            return;
        }
        sender.accept(text);
        input.setText("");
    }

    /** CHAT_MESSAGE 수신 */
    public void addMessage(String nickname, String message, boolean mine) {
        SimpleAttributeSet name = new SimpleAttributeSet();
        StyleConstants.setBold(name, true);
        StyleConstants.setForeground(name, mine ? Theme.RED : Theme.INK);
        SimpleAttributeSet body = new SimpleAttributeSet();
        StyleConstants.setForeground(body, Theme.INK);
        append(nickname + (mine ? " (나)" : "") + "  ", name);
        append(message + "\n", body);
    }

    /** 입장·퇴장·게임 시작 같은 안내 문구 */
    public void addSystem(String message) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, Theme.MUTED);
        StyleConstants.setItalic(s, true);
        append("· " + message + "\n", s);
    }

    public void clear() {
        log.setText("");
    }

    private void append(String text, SimpleAttributeSet attr) {
        StyledDocument doc = log.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, attr);   // 일반 텍스트로만 삽입
        } catch (BadLocationException ignored) {
        }
        log.setCaretPosition(doc.getLength());
    }
}
