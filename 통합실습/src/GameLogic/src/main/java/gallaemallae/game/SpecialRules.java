package gallaemallae.game;

import java.util.*;

/** 5단계: 점수상의 피 수와 실제로 빼앗는 카드 장수를 구별한다. */
public final class SpecialRules {
    private SpecialRules() {}

    /** 흔들기는 같은 월 손패 3장·바닥 0장이고 아직 선언하지 않은 월이어야 한다. */
    public static boolean canShake(List<Card> hand, List<Card> floor, int month, Set<Integer> announced) {
        return month(hand, month).size() == 3 && month(floor, month).isEmpty() && !announced.contains(month);
    }

    /** 폭탄은 같은 월 손패 3장과 바닥 1장을 동시에 획득하는 행동이다. */
    public static boolean canBomb(List<Card> hand, List<Card> floor, int month) {
        return month(hand, month).size() == 3 && month(floor, month).size() == 1;
    }

    public static int ppeokReward(boolean ownPpeok) { return ownPpeok ? 2 : 1; }
    public static int bombOpportunities(int bombCount) {
        if (bombCount < 0) throw new IllegalArgumentException("폭탄 횟수 음수");
        return Math.multiplyExact(bombCount, 2);
    }

    /** 국진의 분류는 카드 자체를 바꾸지 않고 해당 판의 전환 상태로 판단한다. */
    public static int piValue(Card card, boolean gukjinPi) {
        return card.isGukjin() && gukjinPi ? 2 : card.defaultPiValue();
    }

    /** 상대 획득패에서 일반피→쌍피→2서비스→3서비스 순으로 물리 카드 N장을 이동한다. */
    public static List<Card> stealPi(List<Card> from, List<Card> to, int count, boolean gukjinPi) {
        if (count < 0 || from == to) throw new IllegalArgumentException("잘못된 피 이동 요청");
        List<Card> candidates = new ArrayList<>();
        for (Card card : from) if (piValue(card, gukjinPi) > 0) candidates.add(card);
        candidates.sort(Comparator.comparingInt(SpecialRules::priority).thenComparingInt(Card::id));
        List<Card> moved = new ArrayList<>();
        for (Card card : candidates) {
            if (moved.size() == count) break;
            from.remove(card);
            to.add(card);
            moved.add(card);
        }
        return Collections.unmodifiableList(moved);
    }

    private static int priority(Card card) {
        if (card.isService()) return card.servicePiValue() == 2 ? 2 : 3;
        return card.defaultPiValue() == 1 ? 0 : 1;
    }

    public static List<Card> month(List<Card> cards, int month) {
        List<Card> result = new ArrayList<>();
        for (Card card : cards) if (!card.isService() && card.month() == month) result.add(card);
        return result;
    }
}
