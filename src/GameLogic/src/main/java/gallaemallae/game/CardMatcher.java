package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 패 한 장과 바닥의 같은 월 패를 비교해 기본 매칭 방법과 후보를 반환한다. */
public final class CardMatcher {
    private CardMatcher() {}

    // 순서대로 바닥에 놓기, 한 쌍 획득, 후보 중 하나 선택, 같은 월 전부 획득이다.
    public enum Action { PLACE, CAPTURE_PAIR, CHOOSE_ONE, CAPTURE_ALL }

    /** 실제 카드 이동은 호출자가 처리한다. 이 메서드는 입력 목록을 수정하지 않는다. */
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
        // 바닥의 같은 월 패가 0·1·2·3장인지에 따라 분기한다.
        switch (candidates.size()) {
            case 0: return new Result(Action.PLACE, candidates);
            case 1: return new Result(Action.CAPTURE_PAIR, candidates);
            case 2: return new Result(Action.CHOOSE_ONE, candidates);
            case 3: return new Result(Action.CAPTURE_ALL, candidates);
            default: throw new IllegalArgumentException("Invalid monthly card count");
        }
    }

    /** 기본 매칭 방법과 같은 월의 바닥패 후보를 묶어서 반환한다. */
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
