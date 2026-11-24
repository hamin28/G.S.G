package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 한 판의 50장 생성, 섞기, 분배와 초기 바닥 서비스패 보충을 담당한다. */
public final class DeckManager {
    private DeckManager() {}

    /** Random을 외부에서 받아 테스트에서는 동일한 시드로 같은 분배를 재현한다. */
    /** 테스트에서 Random 시드를 고정하면 같은 분배를 재현할 수 있다. */
    public static InitialDeal shuffleAndDeal(Random random) {
        List<Card> deck = standardCards();
        deck.add(Card.service(2));
        deck.add(Card.service(3));
        Collections.shuffle(deck, random);

        List<Card> floor = new ArrayList<>();
        List<Card> opponent = new ArrayList<>();
        List<Card> dealer = new ArrayList<>();
        // 바닥 4장 → 상대 5장 → 선 5장을 두 번 반복한다.
        for (int start = 0; start < 28; start += 14) {
            floor.addAll(deck.subList(start, start + 4));
            opponent.addAll(deck.subList(start + 4, start + 9));
            dealer.addAll(deck.subList(start + 9, start + 14));
        }

        List<Card> dealerCaptures = new ArrayList<>();
        // 28장 분배 후 남은 더미의 첫 위치이다. 보충할 때마다 더미가 한 장 줄어든다.
        int next = 28;
        for (int index = 0; index < floor.size(); index++) {
            // 교체한 패도 서비스일 수 있으므로 정상 월 패가 올 때까지 반복한다.
            while (floor.get(index).isService()) {
                dealerCaptures.add(floor.set(index, deck.get(next++)));
            }
        }
        return new InitialDeal(dealer, opponent, floor, dealerCaptures,
                deck.subList(next, deck.size()));
    }

    /** 선 결정에도 재사용하는 기본 화투 48장. 호출할 때마다 새 목록을 만든다. */
    static List<Card> standardCards() {
        List<Card> cards = new ArrayList<>(48);
        for (int month = 1; month <= 12; month++) {
            for (int slot = 1; slot <= 4; slot++) {
                cards.add(Card.monthly(month, slot));
            }
        }
        return cards;
    }

    /** 분배 결과를 읽기 전용으로 전달한다. 이후 카드 이동은 GameState가 담당한다. */
    /** 분배 직후의 손패·바닥패·획득패·더미패를 읽기 전용 목록으로 전달한다. */
    public static final class InitialDeal {
        private final List<Card> dealerHand;
        private final List<Card> opponentHand;
        private final List<Card> floor;
        private final List<Card> dealerCaptures;
        private final List<Card> stock;

        private InitialDeal(List<Card> dealerHand, List<Card> opponentHand,
                            List<Card> floor, List<Card> dealerCaptures, List<Card> stock) {
            this.dealerHand = immutableCopy(dealerHand);
            this.opponentHand = immutableCopy(opponentHand);
            this.floor = immutableCopy(floor);
            this.dealerCaptures = immutableCopy(dealerCaptures);
            this.stock = immutableCopy(stock);
        }

        /** 원본 목록을 복사해 외부에서 분배 결과를 수정할 수 없게 한다. */
        private static List<Card> immutableCopy(List<Card> cards) {
            return Collections.unmodifiableList(new ArrayList<>(cards));
        }

        public List<Card> dealerHand() { return dealerHand; }
        public List<Card> opponentHand() { return opponentHand; }
        public List<Card> floor() { return floor; }
        public List<Card> dealerCaptures() { return dealerCaptures; }
        public List<Card> stock() { return stock; }
    }
}
