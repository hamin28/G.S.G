package gsg.client.ui;

import javax.swing.SwingUtilities;

public class ClientMain {
    // 실행 인자(선택): 서버주소 포트 닉네임   예) localhost 9999 우진
    // 인자가 없으면 localhost:9999로 접속하고 닉네임은 창에서 입력
    public static void main(String[] args) {
        final String host = args.length > 0 ? args[0] : "localhost";
        final int port = args.length > 1 ? Integer.parseInt(args[1]) : 9999;
        final String nick = args.length > 2 ? args[2] : null;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new MainFrame(host, port, nick).setVisible(true); }
        });
    }
}
