package gsg.client.ui;

// [8-7] 서버가 알려 주는 카드 ID(정수)를 이미지 파일 경로로 바꿔 주는 표.
//       규칙은 저장소 docs/card-resources.md 와 data/cards.json 의 대응표와 같다.
//       1~4=1월(january_1~4), 5~8=2월 ... 45~48=12월, 49=bonus_1, 50=bonus_2
//       TODO(확인): 서버(하민님 로직)가 쓰는 카드 ID가 이 표와 같은지 대조 (문서에도 "별도 대조 필요"라고 적혀 있음)
public final class CardCatalog {
    private CardCatalog() {}

    public static final String DIR = "/resource/hwatu_image/";
    public static final String BACK = DIR + "back.png";

    // [8-7] 폭탄패: 서버 카드 ID(1~50)에 없는 화면 전용 카드. 서비스패(bonus_1/2)처럼 손패 줄에 그려서 보여 준다.
    //       클릭하면 DECK_ONLY(폭탄 뒤집기)를 보낸다. 이미지: hwatu_image/bomb.png
    public static final int BOMB_ID = -2;
    public static final String BOMB = DIR + "bomb.png";

    private static final String[] MONTHS = {
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    };

    public static String pathOf(int cardId) {
        if (cardId >= 1 && cardId <= 48) {
            return DIR + MONTHS[(cardId - 1) / 4] + "_" + ((cardId - 1) % 4 + 1) + ".png";
        }
        if (cardId == 49) return DIR + "bonus_1.png";
        if (cardId == 50) return DIR + "bonus_2.png";
        if (cardId == BOMB_ID) return BOMB;
        return BACK;   // 모르는 ID는 뒷면으로 표시
    }
}
