package gsg.client.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/** 두 화면이 함께 쓰는 색과 글꼴 */
public final class Theme {

    private Theme() {}

    // 화투패의 빨강·검정·흰색에 맞춘 색
    public static final Color RED = new Color(0xC8, 0x1E, 0x1E);
    public static final Color RED_DARK = new Color(0x96, 0x14, 0x14);
    public static final Color INK = new Color(0x22, 0x1E, 0x1C);
    public static final Color PAPER = new Color(0xF6, 0xF1, 0xE7);
    public static final Color PAPER_DARK = new Color(0xE6, 0xDD, 0xCB);
    public static final Color FELT = new Color(0x1F, 0x5C, 0x3A);
    public static final Color FELT_DARK = new Color(0x17, 0x47, 0x2D);
    public static final Color FELT_LINE = new Color(0x2E, 0x77, 0x4E);
    public static final Color GOLD = new Color(0xFF, 0xD6, 0x00);
    public static final Color MUTED = new Color(0x7A, 0x72, 0x68);

    // Dialog 논리 글꼴: Windows에서는 맑은 고딕 등 한글 글꼴로 매핑된다
    public static Font font(int style, int size) {
        return new Font(Font.DIALOG, style, size);
    }

    public static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    /** 채워진 빨간 버튼 (주요 동작) */
    public static <T extends AbstractButton> T primary(T b) {
        return style(b, RED, Color.WHITE);
    }

    /** 밝은 버튼 (보조 동작) */
    public static <T extends AbstractButton> T secondary(T b) {
        return style(b, Color.WHITE, INK);
    }

    public static <T extends AbstractButton> T style(T b, Color bg, Color fg) {
        b.setFont(font(Font.BOLD, 13));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.equals(Color.WHITE) ? PAPER_DARK.darker() : bg.darker()),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static void pad(JComponent c, int top, int left, int bottom, int right) {
        c.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    }
}
