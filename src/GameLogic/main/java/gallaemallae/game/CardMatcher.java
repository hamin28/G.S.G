package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CardMatcher {
    private CardMatcher() {}

    public enum Action { PLACE, CAPTURE_PAIR, CHOOSE_ONE, CAPTURE_ALL }

    public static Result match(Card played, List<Card> floor) {
        if (played.isService()) {
            throw new IllegalArgumentException("Service cards are handled before month matching");
        }
        List<Card> candidates = new ArrayList<>();
        for (Card card : floor) {
            if (card.id() == played.id()) {
                throw new IllegalArgumentException("The played card is already on the floor");
            }
            if (card.month() == played.month()) {
                candidates.add(card);
            }
        }
        switch (candidates.size()) {
            case 0: return new Result(Action.PLACE, candidates);
            case 1: return new Result(Action.CAPTURE_PAIR, candidates);
            case 2: return new Result(Action.CHOOSE_ONE, candidates);
            case 3: return new Result(Action.CAPTURE_ALL, candidates);
            default: throw new IllegalArgumentException("Invalid monthly card count");
        }
    }

    public static final class Result {
        private final Action action;
        private final List<Card> candidates;

        private Result(Action action, List<Card> candidates) {
            this.action = action;
            this.candidates = Collections.unmodifiableList(candidates);
        }

        public Action action() { return action; }
        public List<Card> candidates() { return candidates; }
    }
}
