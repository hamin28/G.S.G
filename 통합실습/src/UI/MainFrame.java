package gsg.client.ui;

import java.awt.CardLayout;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.google.gson.JsonObject;

public class MainFrame extends JFrame {
	private final CardLayout cards = new CardLayout();
	private final JPanel root = new JPanel(cards);

	private final GameClient client = new GameClient(); // [8-1] 서버와의 통로
	private final Session session = new Session(); // [8-3] 서버가 알려 준 내 정보
	private LobbyPanel lobby;
	private GamePanel game;
	private final String presetNickname; // 개발 중 닉네임 입력창을 건너뛰고 싶을 때 (null이면 물어봄)

	public MainFrame(String host, int port, String presetNickname) {
		super("갈래? 말래?");
		this.presetNickname = presetNickname;
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// [8-0] 카드 이미지를 백그라운드에서 미리 읽어 둠 (게임 화면이 처음 뜰 때 끊기지 않게)
		CardView.preloadInBackground();

		// [8-2] 서버에서 이벤트(requestId 없는 메시지)가 오면 onServerEvent가 처리
		client.setOnEvent(this::onServerEvent);
		client.setOnClosed(this::onDisconnected);

		// [8-1] 서버에 접속 (실패하면 안내 후 종료)
		try {
			client.connect(host, port);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,
					"서버에 연결할 수 없습니다. (" + host + ":" + port + ")\n서버가 실행 중인지 확인하세요.\n" + e.getMessage(), "연결 실패",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		// 로비 화면: 방에 들어가기로 정해지면(roomId) 게임 화면으로 전환
		lobby = new LobbyPanel(client, session, roomId -> {
			game.enterRoom(roomId);
			cards.show(root, "game");
		});

		// 게임 화면: 나가기를 마치면 로비로 돌아가 방 목록을 새로 받음
		game = new GamePanel(client, session, () -> {
			lobby.updateProfile();
			cards.show(root, "lobby");
			lobby.refresh();
		});

		root.add(lobby, "lobby");
		root.add(game, "game");
		setContentPane(root);

		setSize(1240, 820);
		setLocationRelativeTo(null);

		join(); // [8-3] 닉네임으로 서버에 등록
	}

	// [8-3] 닉네임을 정하고 JOIN 요청. ACK에서 playerId, balance를 받아 session에 저장
	private void join() {
		String nick = (presetNickname != null) ? presetNickname : askNickname();
		if (nick == null) {
			System.exit(0); // 닉네임 입력을 취소하면 종료
		}
		session.nickname = nick;
		JsonObject payload = new JsonObject();
		payload.addProperty("nickname", nick);
		client.request("JOIN", payload, resp -> {
			if (GameClient.isError(resp)) {
				JOptionPane.showMessageDialog(this, GameClient.errorText(resp), "접속 실패", JOptionPane.WARNING_MESSAGE);
				if (presetNickname != null)
					System.exit(1);
				join(); // 닉네임이 거절되면 다시 입력
				return;
			}
			JsonObject b = Jx.body(resp);
			session.playerId = Jx.str(b, "playerId");
			session.balance = Jx.lng(b, "balance", 0);
			lobby.updateProfile();
			lobby.refresh(); // 방 목록 요청
		});
	}

	// 닉네임 입력창 (1~12자, 서버 규칙과 같게 검사)
	private String askNickname() {
		while (true) {
			String name = JOptionPane.showInputDialog(this, "닉네임 (1~12자)", "접속", JOptionPane.PLAIN_MESSAGE);
			if (name == null)
				return null;
			name = name.trim();
			if (!name.isEmpty() && name.codePointCount(0, name.length()) <= 12)
				return name;
			JOptionPane.showMessageDialog(this, "닉네임은 1~12자로 입력하세요.");
		}
	}

	// [8-2] 서버 이벤트를 종류(type)별로 알맞은 화면에 전달. 화면 스레드에서 불림.
	// TODO(확인): 이벤트 이름이 문서와 같은지 콘솔의 [수신] 로그로 확인
	private void onServerEvent(JsonObject msg) {
		String type = Jx.str(msg, "type");
		if (type == null)
			return;
		JsonObject b = Jx.body(msg);
		switch (type) {
		case "ROOM_LIST":
			lobby.setRooms(b);
			break;
		case "ROOM_STATE":
			game.onRoomState(b);
			break;
		case "DEALER_SELECTION":
			game.onDealerSelection(b);
			break;
		case "GAME_START":
		case "GAME_STATE":
			game.onGameState(b);
			break;
		case "GAME_OVER":
			game.onGameOver(b);
			break;
		case "ROOM_LEFT":
			game.onRoomLeft(b);
			break;
		case "CHAT_MESSAGE":
			game.onChat(b);
			break;
		case "ERROR":
			// 어떤 요청의 응답도 아닌 오류 (requestId 없음)
			JOptionPane.showMessageDialog(this, GameClient.errorText(msg), "서버 오류", JOptionPane.WARNING_MESSAGE);
			break;
		case "ACK":
			break; // 응답은 request()에 등록한 동작이 처리함
		default:
			System.out.println("[알 수 없는 이벤트] " + type);
		}
	}

	// 연결이 끊어졌을 때
	private void onDisconnected() {
		JOptionPane.showMessageDialog(this, "서버와의 연결이 끊어졌습니다.", "연결 종료", JOptionPane.ERROR_MESSAGE);
		System.exit(0); // TODO(선택): 다시 접속하는 기능
	}
}
