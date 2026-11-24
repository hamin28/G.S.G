package gallaemallae.game;

import java.util.*;

/** 5단계 특수 규칙 부품 검사. 한 판에서의 연결 순서는 8단계 FullGameCheck가 검사한다. */
public final class Step5Check {
    public static void main(String[] args) {
        List<Card> hand = new ArrayList<>(Arrays.asList(Card.monthly(1,1),Card.monthly(1,2),Card.monthly(1,3)));
        List<Card> floor = new ArrayList<>();
        Set<Integer> announced = new HashSet<>();
        check(SpecialRules.canShake(hand,floor,1,announced), "흔들기 허용");
        announced.add(1);
        check(!SpecialRules.canShake(hand,floor,1,announced), "중복 흔들기 금지");
        floor.add(Card.monthly(1,4));
        check(SpecialRules.canBomb(hand,floor,1), "폭탄 허용");
        check(!SpecialRules.canShake(hand,floor,1,new HashSet<>()), "바닥 1장이면 흔들기 아님");
        List<Card> from = new ArrayList<>(Arrays.asList(Card.service(3),Card.monthly(9,1),Card.service(2),Card.monthly(1,3)));
        List<Card> to = new ArrayList<>();
        SpecialRules.stealPi(from,to,2,true);
        check(to.size() == 2 && to.get(0).id() == 3 && to.get(1).isGukjin(), "일반피 다음 전환 국진");
        SpecialRules.stealPi(from,to,10,true);
        check(from.isEmpty() && to.size() == 4, "부족한 피만큼만 이동");
        check(SpecialRules.ppeokReward(true) == 2 && SpecialRules.ppeokReward(false) == 1, "뻑 소유자별 보상");
        check(SpecialRules.bombOpportunities(3) == 6, "폭탄 기회 누적");
        System.out.println("PASS: step5 special rule components (8 checks)");
    }
    private static void check(boolean valid,String message) { if (!valid) throw new AssertionError(message); }
}
