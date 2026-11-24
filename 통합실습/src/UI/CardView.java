package gsg.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JComponent;

public class CardView extends JComponent {
    private BufferedImage image;   // 카드 앞면 이미지
    private boolean hover;         // 마우스가 카드 위에 있는지
    private Runnable onClick;      // 클릭됐을 때 실행할 동작 (없으면 null)

    // [8-7] 같은 이미지를 카드마다 다시 읽지 않도록 경로별로 한 번만 읽어 보관한다.
    //       원본이 2151x3505라서 그대로 읽으면 장당 약 30MB가 되므로,
    //       읽을 때 1/4로 줄여 읽고(subsampling) 160x260(표시 크기의 2배)으로 부드럽게 줄여 둔다.
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final int STORE_W = 160;
    private static final int STORE_H = 260;

    public CardView(String imagePath) {
        // [6-1] imagePath로 이미지를 읽어 image에 저장 (실패하면 image는 null로 유지)
        image = load(imagePath);
        // [6-1] 카드 크기 지정 (원본 비율 약 0.615에 맞춰 80 x 130)
        setPreferredSize(new Dimension(80, 130));
        addEventListener();
    }

    // [7-1] 클릭 동작을 외부(FeltPanel/GamePanel)에서 정해 주입받음 (null이면 클릭 불가 카드)
    public void setOnClick(Runnable r) {
        onClick = r;
        if (r == null) {      // 클릭할 수 없게 되면 떠 있던 효과도 내림
            hover = false;
            repaint();
        }
    }

    // [8-7] 경로로 이미지를 얻음. 처음이면 읽어서 보관, 이미 있으면 보관된 것을 사용
    public static BufferedImage load(String path) {
        BufferedImage cached = CACHE.get(path);
        if (cached != null) return cached;
        BufferedImage img = readScaled(path);
        if (img != null) CACHE.put(path, img);
        return img;
    }

    // [8-0] 게임 화면이 처음 뜰 때 끊기지 않도록, 모든 카드를 백그라운드 스레드에서 미리 읽어 둠
    public static void preloadInBackground() {
        Thread t = new Thread(() -> {
            long start = System.currentTimeMillis();
            load(CardCatalog.BACK);
            for (int id = 1; id <= 50; id++) {
                load(CardCatalog.pathOf(id));
            }
            System.out.println("[카드] 이미지 미리 읽기 완료 (" + (System.currentTimeMillis() - start) + "ms)");
        }, "card-preload");
        t.setDaemon(true);
        t.start();
    }

    private static BufferedImage readScaled(String path) {
        try (InputStream is = CardView.class.getResourceAsStream(path)) {
            if (is == null) {
                System.out.println("[카드] 이미지를 찾을 수 없음: " + path);
                return null;
            }
            ImageInputStream iis = ImageIO.createImageInputStream(is);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            ImageReader reader = readers.next();
            reader.setInput(iis);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(4, 4, 0, 0);          // 가로·세로 4칸에 1칸만 읽음
            BufferedImage big = reader.read(0, param);
            reader.dispose();

            // 반씩 줄이며 내려가야 선이 뭉개지지 않음
            BufferedImage cur = big;
            while (cur.getWidth() / 2 >= STORE_W) {
                cur = scaleTo(cur, cur.getWidth() / 2, cur.getHeight() / 2);
            }
            return scaleTo(cur, STORE_W, STORE_H);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static BufferedImage scaleTo(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private void addEventListener()
    {
        this.addMouseListener(new MyMouseListener());
    }

    private class MyMouseListener extends MouseAdapter
    {
        // [6-2] 마우스가 카드 위에 들어오면 hover를 켜고 다시 그림
        //       [8-7] 클릭할 수 있는 카드(onClick이 있는 카드)만 위로 뜸
        @Override
        public void mouseEntered(MouseEvent e) {
            hover = (onClick != null);
            repaint();
        }

        // [6-2] 마우스가 카드를 벗어나면 hover를 끄고 다시 그림
        @Override
        public void mouseExited(MouseEvent e) {
            hover = false;
            repaint();
        }

        // [7-1] 클릭되면 등록된 동작이 있을 때만 실행
        @Override
        public void mouseClicked(MouseEvent e) {
            if (onClick != null)
            {
                onClick.run();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        // [6-1] 이미지를 못 읽었을 때: 회색 사각형 + "?" 로 확인용 표시
        if (image == null)
        {
            g2.setColor(Color.GRAY);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Color.WHITE);
            g2.drawString("?", getWidth() / 2, getHeight() / 2);
        }
        // [6-2] 이미지가 있으면 그림. hover이면 y=0(위로 8px 올라옴), 아니면 y=8
        else {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int y;
            if (hover)
            {
                y = 0; // 올라감 상태
            }
            else
            {
                y = 8; // 기본 상태
            }
            g2.drawImage(image, 0, y, getWidth(), getHeight() - 8, null);
        }
    }
}
