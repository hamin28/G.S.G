package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 일반 손패 한 장과 더미 공개 한 장의 결과를 합쳐 판정한다.
 * 뻑·따닥·쪽·쓸 사건을 기록하며, 해당 사건의 피 이동과 뻑 누적은 후속 구현 대상이다.
 */
public final class TurnMatcher {
    public enum Event { PPEOK, TTADAK, JJOK, SWEEP }
    // 손패 매칭 선택 대기, 더미패 매칭 선택 대기, 전체 매칭 완료를 뜻한다.
    public enum Stage { HAND_CHOICE, STOCK_CHOICE, COMPLETE }

    private TurnMatcher() {}

    /** 선택 ID가 null이면 필요한 선택을 요청한다. 원본 바닥 목록은 보존한다. */
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
        // 임시 목록에서 먼저 판정하여 선택 미완료 시 실제 패가 이동하지 않게 한다.
        List<Card> after = new ArrayList<>(floor);
        List<Card> captured = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        if (!apply(hand, handChoice, handMatch, after, captured)) {
            return pending(Stage.HAND_CHOICE, handMatch);
        }

        if (hand.month() == revealed.month() && handMatch.action() == CardMatcher.Action.CAPTURE_PAIR) {
            requireNoChoice(stockChoice);
            // 손패로 잡은 한 쌍에 같은 월 더미패가 나오면 세 장을 바닥에 남긴다.
            after.addAll(captured);
            after.add(revealed);
            captured.clear();
            events.add(Event.PPEOK);
        } else {
            CardMatcher.Result stockMatch = CardMatcher.match(revealed, after);
            if (!apply(revealed, stockChoice, stockMatch, after, captured)) {
                return pending(Stage.STOCK_CHOICE, stockMatch);
            }
            // 바닥 같은 월 2장에서 네 장을 모으면 따닥, 0장에서 짝이 되면 쪽이다.
            if (hand.month() == revealed.month()) {
                if (handMatch.action() == CardMatcher.Action.CHOOSE_ONE) {
                    events.add(Event.TTADAK);
                } else if (handMatch.action() == CardMatcher.Action.PLACE) {
                    events.add(Event.JJOK);
                }
            }
        }
        // 모든 획득 이후 바닥이 비었으면 쓸을 추가한다. 쪽·따닥과 함께 기록될 수 있다.
        if (after.isEmpty()) {
            events.add(Event.SWEEP);
        }
        return new Result(Stage.COMPLETE, Collections.emptyList(), after, captured, events);
    }

    /** 선택 대기 상태와 후보를 전달한다. 완료 전에는 이동 결과를 확정하지 않는다. */
    private static Result pending(Stage stage, CardMatcher.Result match) {
        return new Result(stage, match.candidates(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    /** 임시 목록에 매칭을 반영한다. 선택이 필요하지만 ID가 없으면 false를 반환한다. */
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

    /** 선택할 필요가 없는 단계에 선택 ID가 들어오면 잘못된 요청으로 거절한다. */
    private static void requireNoChoice(Integer choice) {
        if (choice != null) {
            throw new IllegalArgumentException("No match choice is expected");
        }
    }

    /** 매칭 단계, 선택 후보, 완료 후 카드 배치와 사건 목록을 보관한다. */
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
