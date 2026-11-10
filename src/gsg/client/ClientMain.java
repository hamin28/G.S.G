package gsg.client;

import gsg.client.card.CardCatalog;
import gsg.client.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 클라이언트 시작점. Eclipse에서 Run As → Java Application.
 * 실행 위치(작업 폴더)는 data/, hwatu_image/ 폴더가 있는 저장소 루트여야 한다.
 */
public class ClientMain {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        CardCatalog.preloadAsync();   // 로비에 있는 동안 카드 이미지를 미리 읽어 둔다
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
