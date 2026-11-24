package gallaemallae.game;

import java.util.*;

/** 6단계: 획득패만으로 기본 점수를 계산한다. 고 보너스와 돈의 배율은 여기서 더하지 않는다. */
public final class ScoreCalculator {
    private ScoreCalculator() {}

    public static Score calculate(List<Card> cards, boolean gukjinPi) {
        Set<Integer> ids = new HashSet<>();
        Set<Integer> ribbons = new HashSet<>(), animals = new HashSet<>();
        int gwang = 0, pi = 0;
        boolean rain = false;
        for (Card c : cards) {
            if (!ids.add(c.id())) throw new IllegalArgumentException("획득패 중복 ID");
            pi += SpecialRules.piValue(c, gukjinPi);
            if (c.type() == CardType.GWANG) { gwang++; rain |= c.isRainGwang(); }
            if (c.type() == CardType.TTI) ribbons.add(c.month());
            if (c.type() == CardType.YEOLKKEUT && !(c.isGukjin() && gukjinPi)) animals.add(c.month());
        }
        int g = gwang == 5 ? 15 : gwang == 4 ? 4 : gwang == 3 ? (rain ? 2 : 3) : 0;
        int hong = contains(ribbons, 1, 2, 3) ? 3 : 0;
        int blue = contains(ribbons, 6, 9, 10) ? 3 : 0;
        int cho = contains(ribbons, 4, 5, 7) ? 3 : 0;
        int godori = contains(animals, 2, 4, 8) ? 5 : 0;
        return new Score(gwang, animals.size(), ribbons.size(), pi, g,
                Math.max(0, ribbons.size() - 4), hong, blue, cho,
                Math.max(0, animals.size() - 4), godori, Math.max(0, pi - 9));
    }

    private static boolean contains(Set<Integer> months, int a, int b, int c) {
        return months.contains(a) && months.contains(b) && months.contains(c);
    }

    /** 화면과 정산에서 같은 계산 근거를 사용할 수 있는 불변 결과이다. */
    public static final class Score {
        public final int gwangCount, animalCount, ribbonCount, effectivePi;
        public final int gwang, ribbons, hongdan, cheongdan, chodan, animals, godori, pi, total;
        private Score(int gc, int ac, int rc, int ep, int g, int r, int h, int b, int c, int a, int go, int p) {
            gwangCount = gc; animalCount = ac; ribbonCount = rc; effectivePi = ep;
            gwang = g; ribbons = r; hongdan = h; cheongdan = b; chodan = c;
            animals = a; godori = go; pi = p;
            total = g + r + h + b + c + a + go + p;
        }
        @Override public String toString() {
            return "기본 " + total + "점 (광 " + gwang + ", 띠 " + (ribbons + hongdan + cheongdan + chodan)
                    + ", 열끗 " + (animals + godori) + ", 피 " + pi + "; 유효 피 " + effectivePi + ")";
        }
    }
}
