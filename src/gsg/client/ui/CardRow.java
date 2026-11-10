package gsg.client.ui;

import gsg.client.card.CardCatalog;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * 카드 여러 장을 한 줄로 그리는 컴포넌트.
 * - step: 카드 사이 간격(px). 카드 너비보다 작으면 겹쳐서 그린다 (획득패 묶음 등).
 * - clickable이면 마우스를 올린 카드가 살짝 위로 올라오고, 클릭 시 onClick(cardId)를 호출한다.
 * - highlight에 들어 있는 카드는 노란 테두리로 강조한다 (먹을 패 선택 후보).
 * - cardId 대신 BACK(-1)을 넣으면 카드 뒷면을 그린다.
 */
public class CardRow extends JComponent {

    public static final int BACK = -1;
    private static final int LIFT = 10;

    private final int cardWidth;
    private final int step;
    private List<Integer> ids = new ArrayList<>();
    private Set<Integer> highlight = new HashSet<>();
    private boolean clickable;
    private int hoverIndex = -1;
    private IntConsumer onClick;

    public CardRow(int cardWidth, int step) {
        this.cardWidth = cardWidth;
        this.step = step;
        setOpaque(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
            @Override public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0 && isCardClickable(ids.get(i)) && onClick != null) onClick.accept(ids.get(i));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setCards(List<Integer> cardIds) {
        this.ids = cardIds == null ? new ArrayList<>() : new ArrayList<>(cardIds);
        hoverIndex = -1;
        revalidate();
        repaint();
    }

    /** 뒷면 카드 n장 */
    public void setBacks(int count) {
        setCards(Collections.nCopies(Math.max(0, count), BACK));
    }

    public void setHighlight(Set<Integer> cardIds) {
        this.highlight = cardIds == null ? new HashSet<>() : new HashSet<>(cardIds);
        repaint();
    }

    /** 클릭 가능 여부. highlight가 있으면 강조된 카드만 클릭된다. */
    public void setClickable(boolean clickable) {
        this.clickable = clickable;
        if (!clickable) setHover(-1);
        repaint();
    }

    public void setOnClick(IntConsumer onClick) {
        this.onClick = onClick;
    }

    public int getCardCount() {
        return ids.size();
    }

    private boolean isCardClickable(int id) {
        if (!clickable || id == BACK) return false;
        return highlight.isEmpty() || highlight.contains(id);
    }

    private int cardHeight() {
        return CardCatalog.heightFor(cardWidth);
    }

    @Override
    public Dimension getPreferredSize() {
        int n = ids.size();
        int w = n == 0 ? cardWidth : cardWidth + step * (n - 1);
        return new Dimension(w, cardHeight() + LIFT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(cardWidth, cardHeight() + LIFT);
    }

    private void updateHover(MouseEvent e) {
        int i = indexAt(e.getX(), e.getY());
        setHover(i >= 0 && isCardClickable(ids.get(i)) ? i : -1);
    }

    private void setHover(int i) {
        if (i != hoverIndex) {
            hoverIndex = i;
            setCursor(i >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }
    }

    /** 자리가 모자라면 간격을 줄여 겹쳐 그린다 (카드가 많아져도 화면 밖으로 나가지 않게) */
    private int effectiveStep() {
        int n = ids.size();
        if (n <= 1 || getWidth() <= 0) return step;
        int fit = (getWidth() - cardWidth) / (n - 1);
        return Math.max(8, Math.min(step, fit));
    }

    /** 겹친 카드는 오른쪽(위에 그려진) 카드부터 검사 */
    private int indexAt(int x, int y) {
        int step = effectiveStep();
        for (int i = ids.size() - 1; i >= 0; i--) {
            int cx = i * step;
            if (x >= cx && x < cx + cardWidth && y >= 0 && y < cardHeight() + LIFT) return i;
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        CardCatalog cat = CardCatalog.get();
        int h = cardHeight();
        int step = effectiveStep();
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            int x = i * step;
            int y = (i == hoverIndex) ? 0 : LIFT;
            ImageIcon icon = (id == BACK) ? cat.backIcon(cardWidth) : cat.icon(id, cardWidth);

            // 그림자
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRoundRect(x + 2, y + 3, cardWidth, h, 8, 8);
            icon.paintIcon(this, g, x, y);

            boolean dim = clickable && !highlight.isEmpty() && !highlight.contains(id);
            if (dim) {
                g.setColor(new Color(0, 0, 0, 110));
                g.fillRoundRect(x, y, cardWidth, h, 8, 8);
            }
            if (highlight.contains(id)) {
                g.setColor(new Color(255, 214, 0));
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(x + 1, y + 1, cardWidth - 3, h - 3, 10, 10);
            }
        }
        g.dispose();
    }
}
