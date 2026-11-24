package gallaemallae.game;

import static gallaemallae.game.CardType.GWANG;
import static gallaemallae.game.CardType.PI;
import static gallaemallae.game.CardType.SERVICE;
import static gallaemallae.game.CardType.TTI;
import static gallaemallae.game.CardType.YEOLKKEUT;

/** 화투 한 장의 고정 정보. 소유자와 국진 전환 상태는 판 상태에서 관리한다. */
public final class Card {
    // 행은 1~12월, 열은 해당 월의 1~4번 패이다. 이 순서가 카드 ID와 연결된다.
    private static final CardType[][] MONTHLY_TYPES = {
        { GWANG, TTI, PI, PI },          // 1
        { YEOLKKEUT, TTI, PI, PI },     // 2
        { GWANG, TTI, PI, PI },          // 3
        { YEOLKKEUT, TTI, PI, PI },     // 4
        { YEOLKKEUT, TTI, PI, PI },     // 5
        { YEOLKKEUT, TTI, PI, PI },     // 6
        { YEOLKKEUT, TTI, PI, PI },     // 7
        { GWANG, YEOLKKEUT, PI, PI },   // 8
        { YEOLKKEUT, TTI, PI, PI },     // 9
        { YEOLKKEUT, TTI, PI, PI },     // 10
        { GWANG, PI, PI, PI },           // 11월 4번 패는 쌍피
        { GWANG, YEOLKKEUT, TTI, PI }   // 12월 4번 패는 쌍피
    };

    private final int id;
    private final int month;
    private final CardType type;
    private final int defaultPiValue;

    private Card(int id, int month, CardType type, int defaultPiValue) {
        this.id = id;
        this.month = month;
        this.type = type;
        this.defaultPiValue = defaultPiValue;
    }

    /** 월과 슬롯으로 기본 패를 생성한다. ID는 1~48이며 같은 월의 패도 구별된다. */
    public static Card monthly(int month, int slot) {
        if (month < 1 || month > 12 || slot < 1 || slot > 4) {
            throw new IllegalArgumentException("Expected month 1–12 and slot 1–4");
        }
        CardType type = MONTHLY_TYPES[month - 1][slot - 1];
        int piValue = 0;
        if (type == PI) {
            piValue = (month == 11 || month == 12) && slot == 4 ? 2 : 1;
        }
        return new Card((month - 1) * 4 + slot, month, type, piValue);
    }

    /** 서비스패는 월 0으로 표현한다. 2점패 ID는 49, 3점패 ID는 50이다. */
    public static Card service(int piValue) {
        if (piValue != 2 && piValue != 3) {
            throw new IllegalArgumentException("Expected service value 2 or 3");
        }
        return new Card(47 + piValue, 0, SERVICE, piValue);
    }

    public int id() { return id; }
    public int month() { return month; }
    public CardType type() { return type; }
    // 점수 계산 전 피 장수로 환산할 값이다. 국진은 기본 열끗이므로 여기서는 0이다.
    public int defaultPiValue() { return defaultPiValue; }
    public int servicePiValue() { return isService() ? defaultPiValue : 0; }
    public boolean isService() { return type == SERVICE; }
    public boolean isRainGwang() { return month == 12 && type == GWANG; }
    public boolean isGukjin() { return month == 9 && type == YEOLKKEUT; }
}
