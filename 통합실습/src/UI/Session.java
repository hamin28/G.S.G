package gsg.client.ui;

import java.text.NumberFormat;
import java.util.Locale;

// [8-3] 서버가 알려 준 "나"의 정보를 화면들이 함께 쓰도록 모아 두는 상자
//       (JOIN ACK의 playerId, balance를 저장하고, GAME_OVER의 정산 잔액으로 갱신)
public class Session {
	public String playerId; // 서버가 부여한 내 ID (JOIN ACK)
	public String nickname = "플레이어";
	public long balance = 0; // 보유금 (서버 기준)
	public String roomId; // 지금 들어가 있는 방 (없으면 null)

	// 1000000 -> "1,000,000원"
	public static String won(long v) {
		return NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원";
	}
}
