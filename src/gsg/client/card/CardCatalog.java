package gsg.client.card;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카드 데이터(data/cards.json)와 이미지(hwatu_image/*.png)를 관리한다.
 *
 * 원본 이미지가 약 2150×3500px로 크기 때문에, 처음 읽을 때 화면용 크기(CARD_W×CARD_H)로
 * 한 번만 줄여서 보관하고, 필요한 크기는 그 작은 이미지에서 다시 만든다.
 * 프로그램 시작 시 preloadAsync()로 로비에 있는 동안 미리 읽어 둔다.
 *
 * 파일 위치: 클래스패스("/data/cards.json")를 먼저 찾고, 없으면 작업 폴더 기준 파일 경로로 찾는다.
 * (Eclipse에서 저장소 루트를 프로젝트로 열면 작업 폴더가 저장소 루트가 된다.)
 */
public class CardCatalog {

    public static final int CARD_W = 120;   // 기본 보관 크기 (원본 비율 약 0.613)
    public static final int CARD_H = 196;
    private static final String DATA_PATH = "data/cards.json";
    private static final String BACK_PATH = "hwatu_image/back.png";

    private static CardCatalog instance;

    private final Map<Integer, CardInfo> cards = new LinkedHashMap<>();
    private final Map<Integer, BufferedImage> baseImages = new HashMap<>();
    private final Map<String, ImageIcon> iconCache = new HashMap<>();
    private BufferedImage backImage;

    public static synchronized CardCatalog get() {
        if (instance == null) {
            try {
                instance = new CardCatalog();
            } catch (IOException e) {
                throw new IllegalStateException("카드 데이터를 읽을 수 없습니다: " + e.getMessage(), e);
            }
        }
        return instance;
    }

    private CardCatalog() throws IOException {
        try (Reader r = new InputStreamReader(open(DATA_PATH), StandardCharsets.UTF_8)) {
            List<CardInfo> list = new Gson().fromJson(r, new TypeToken<List<CardInfo>>() {}.getType());
            for (CardInfo c : list) cards.put(c.getCardId(), c);
        }
    }

    /** 백그라운드에서 모든 이미지를 미리 읽는다. */
    public static void preloadAsync() {
        Thread t = new Thread(() -> {
            long start = System.currentTimeMillis();
            CardCatalog cat = get();
            for (int id : cat.cards.keySet()) cat.baseImage(id);
            cat.backBase();
            System.out.println("[카드] 이미지 " + cat.cards.size() + "장 미리 읽기 완료 (" + (System.currentTimeMillis() - start) + "ms)");
        }, "card-preload");
        t.setDaemon(true);
        t.start();
    }

    public CardInfo info(int cardId) {
        return cards.get(cardId);
    }

    /** 카드 앞면 아이콘 (너비 width, 높이는 비율 유지) */
    public synchronized ImageIcon icon(int cardId, int width) {
        return iconCache.computeIfAbsent(cardId + "@" + width, k -> new ImageIcon(scale(baseImage(cardId), width)));
    }

    /** 카드 뒷면 아이콘 (상대 손패 표시용) */
    public synchronized ImageIcon backIcon(int width) {
        return iconCache.computeIfAbsent("back@" + width, k -> new ImageIcon(scale(backBase(), width)));
    }

    public static int heightFor(int width) {
        return width * CARD_H / CARD_W;
    }

    // ---------------------------------------------------------------

    private synchronized BufferedImage baseImage(int cardId) {
        BufferedImage img = baseImages.get(cardId);
        if (img == null) {
            CardInfo c = cards.get(cardId);
            img = (c == null) ? placeholder() : loadScaled(c.getImagePath());
            baseImages.put(cardId, img);
        }
        return img;
    }

    private synchronized BufferedImage backBase() {
        if (backImage == null) backImage = loadScaled(BACK_PATH);
        return backImage;
    }

    private BufferedImage loadScaled(String path) {
        try (InputStream in = open(path)) {
            BufferedImage src = ImageIO.read(in);
            return scale(src, CARD_W);
        } catch (IOException e) {
            System.err.println("[카드] 이미지 없음: " + path);
            return placeholder();
        }
    }

    private static BufferedImage scale(BufferedImage src, int width) {
        int height = heightFor(width);
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage placeholder() {
        BufferedImage img = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.LIGHT_GRAY);
        g.fillRect(0, 0, CARD_W, CARD_H);
        g.dispose();
        return img;
    }

    private static InputStream open(String path) throws IOException {
        InputStream in = CardCatalog.class.getResourceAsStream("/" + path);
        if (in != null) return in;
        File f = new File(path);
        if (f.exists()) return new FileInputStream(f);
        throw new IOException(path + " (클래스패스와 작업 폴더 " + new File(".").getAbsolutePath() + " 에서 찾지 못함)");
    }
}
