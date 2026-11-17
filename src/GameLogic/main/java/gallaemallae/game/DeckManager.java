package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class DeckManager {
    private DeckManager() {}

    public static InitialDeal shuffleAndDeal(Random random) {
        List<Card> deck = standardCards();
        deck.add(Card.service(2));
        deck.add(Card.service(3));
        Collections.shuffle(deck, random);

        List<Card> floor = new ArrayList<>();
        List<Card> opponent = new ArrayList<>();
        List<Card> dealer = new ArrayList<>();
        for (int start = 0; start < 28; start += 14) {
            floor.addAll(deck.subList(start, start + 4));
            opponent.addAll(deck.subList(start + 4, start + 9));
            dealer.addAll(deck.subList(start + 9, start + 14));
        }

        List<Card> dealerCaptures = new ArrayList<>();
        int next = 28;
        for (int index = 0; index < floor.size(); index++) {
            while (floor.get(index).isService()) {
                dealerCaptures.add(floor.set(index, deck.get(next++)));
            }
        }
        return new InitialDeal(dealer, opponent, floor, dealerCaptures,
                deck.subList(next, deck.size()));
    }

    static List<Card> standardCards() {
        List<Card> cards = new ArrayList<>(48);
        for (int month = 1; month <= 12; month++) {
            for (int slot = 1; slot <= 4; slot++) {
                cards.add(Card.monthly(month, slot));
            }
        }
        return cards;
    }

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
