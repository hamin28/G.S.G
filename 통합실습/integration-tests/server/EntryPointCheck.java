package server;

import gsg.client.ui.OriginalUiCheck;
import java.net.*;

/** 실제 배포 진입점 ServerMain.main()과 원본 MainFrame 두 개를 함께 실행하는 검사이다. */
public final class EntryPointCheck {
    public static void main(String[] args) throws Exception {
        int port;
        try(ServerSocket reserved=new ServerSocket(0)) { port=reserved.getLocalPort(); }
        Thread server=new Thread(() -> ServerMain.main(new String[] {Integer.toString(port)}),"original-ServerMain");
        server.setDaemon(true); server.start();
        long until=System.nanoTime()+5_000_000_000L;
        while(true) {
            try(Socket probe=new Socket(InetAddress.getLoopbackAddress(),port)) { break; }
            catch(java.io.IOException error) {
                if(System.nanoTime()>until) throw error;
                Thread.sleep(20);
            }
        }
        OriginalUiCheck.main(new String[] {Integer.toString(port)});
        System.out.println("PASS: 원본 ServerMain.main + MainFrame 두 개, 실제 서버 시계·예약 타이머로 실행");
    }
}
