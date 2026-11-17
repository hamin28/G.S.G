package gallaemallae.game;

import static gallaemallae.game.CardType.GWANG;
import static gallaemallae.game.CardType.PI;
import static gallaemallae.game.CardType.SERVICE;
import static gallaemallae.game.CardType.TTI;
import static gallaemallae.game.CardType.YEOLKKEUT;

public final class Card {
    // Each row is a month. The order defines the four internal card IDs for that month.
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
        { GWANG, PI, PI, PI },           // 11 (slot 4 is double pi)
        { GWANG, YEOLKKEUT, TTI, PI }   // 12 (slot 4 is double pi)
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

    public static Card service(int piValue) {
        if (piValue != 2 && piValue != 3) {
            throw new IllegalArgumentException("Expected service value 2 or 3");
        }
        return new Card(47 + piValue, 0, SERVICE, piValue);
    }

    public int id() { return id; }
    public int month() { return month; }
    public CardType type() { return type; }
    public int defaultPiValue() { return defaultPiValue; }
    public int servicePiValue() { return isService() ? defaultPiValue : 0; }
    public boolean isService() { return type == SERVICE; }
    public boolean isRainGwang() { return month == 12 && type == GWANG; }
    public boolean isGukjin() { return month == 9 && type == YEOLKKEUT; }
}
