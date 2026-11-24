// 서버 실행·접속 수락
package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
	public static void main(String[] args) {
		// 전체 방 관리 객체 생성
		RoomManager roomManager = new RoomManager();
		int port = args.length > 0 ? Integer.parseInt(args[0]) : 9999;

		try (roomManager; ServerSocket server = new ServerSocket(port)) {
			roomManager.startTimer();
			System.out.println(port + "번 서버 시작");

			while (true) {
				// 사용자가 서버 접속 요청 전까지 대기
				Socket socket = server.accept();

				// 유저 객체 생성
				GameUser user = new GameUser(socket, roomManager);
				user.start();
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
}
