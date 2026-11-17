package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Previews a normal hand play and its normal stock reveal before committing card moves. */
public final class TurnMatcher {
    public enum Event { PPEOK, TTADAK, JJOK, SWEEP }
    public enum Stage { HAND_CHOICE, STOCK_CHOICE, COMPLETE }

    private TurnMatcher() {}

    public static Result match(List<Card> floor, Card hand, Integer handChoice,
                               Card revealed, Integer stockChoice) {
        Objects.requireNonNull(hand);
        Objects.requireNonNull(revealed);
        if (hand.isService() || revealed.isService() || hand.id() == revealed.id()) {
            throw new IllegalArgumentException("Expected two distinct monthly cards");
        }
        for (Card card : floor) {
            if (card.id() == revealed.id()) {
                throw new IllegalArgumentException("The stock card is already on the floor");
            }
        }

        CardMatcher.Result handMatch = CardMatcher.match(hand, floor);
        List<Card> after = new ArrayList<>(floor);
        List<Card> captured = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        if (!apply(hand, handChoice, handMatch, after, captured)) {
            return pending(Stage.HAND_CHOICE, handMatch);
        }

        if (hand.month() == revealed.month() && handMatch.action() == CardMatcher.Action.CAPTURE_PAIR) {
            requireNoChoice(stockChoice);
            after.addAll(captured);
            after.add(revealed);
            captured.clear();
            events.add(Event.PPEOK);
        } else {
            CardMatcher.Result stockMatch = CardMatcher.match(revealed, after);
            if (!apply(revealed, stockChoice, stockMatch, after, captured)) {
                return pending(Stage.STOCK_CHOICE, stockMatch);
            }
            if (hand.month() == revealed.month()) {
                if (handMatch.action() == CardMatcher.Action.CHOOSE_ONE) {
                    events.add(Event.TTADAK);
                } else if (handMatch.action() == CardMatcher.Action.PLACE) {
                    events.add(Event.JJOK);
                }
            }
        }
        if (after.isEmpty()) {
            events.add(Event.SWEEP);
        }
        return new Result(Stage.COMPLETE, Collections.emptyList(), after, captured, events);
    }

    private static Result pending(Stage stage, CardMatcher.Result match) {
        return new Result(stage, match.candidates(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    private static boolean apply(Card played, Integer choice, CardMatcher.Result match,
                                 List<Card> floor, List<Card> captures) {
        List<Card> matches = match.candidates();
        if (match.action() == CardMatcher.Action.CHOOSE_ONE) {
            if (choice == null) {
                return false;
            }
            Card selected = matches.stream().filter(card -> card.id() == choice)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid match choice"));
            matches = Collections.singletonList(selected);
        } else {
            requireNoChoice(choice);
        }
        if (matches.isEmpty()) {
            floor.add(played);
        } else {
            floor.removeAll(matches);
            captures.addAll(matches);
            captures.add(played);
        }
        return true;
    }

    private static void requireNoChoice(Integer choice) {
        if (choice != null) {
            throw new IllegalArgumentException("No match choice is expected");
        }
    }

    public static final class Result {
        private final Stage stage;
        private final List<Card> choices;
        private final List<Card> floor;
        private final List<Card> captures;
        private final List<Event> events;

        private Result(Stage stage, List<Card> choices, List<Card> floor,
                       List<Card> captures, List<Event> events) {
            this.stage = stage;
            this.choices = Collections.unmodifiableList(choices);
            this.floor = Collections.unmodifiableList(floor);
            this.captures = Collections.unmodifiableList(captures);
            this.events = Collections.unmodifiableList(events);
        }

        public Stage stage() { return stage; }
        public List<Card> choices() { return choices; }
        public List<Card> floor() { return floor; }
        public List<Card> captures() { return captures; }
        public List<Event> events() { return events; }
    }
}
