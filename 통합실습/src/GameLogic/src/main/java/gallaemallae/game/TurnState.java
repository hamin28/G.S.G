package gallaemallae.game;

import java.util.List;
import java.util.Optional;

/** 한 번의 패 제출·매칭 선택에 대한 결과. 단계, 후보, 공개패, 특수 사건을 전달한다. */
public final class TurnState {
    private final Card played;
    private final Integer handChoice;
    private final Card revealed;
    private final TurnMatcher.Result result;

    TurnState(Card played, Integer handChoice, Card revealed, TurnMatcher.Result result) {
        this.played = played;
        this.handChoice = handChoice;
        this.revealed = revealed;
        this.result = result;
    }

    public Card played() { return played; }
    public TurnMatcher.Stage stage() { return result.stage(); }
    public List<Card> choices() { return result.choices(); }
    /** 손패 선택이 끝나지 않아 공개할 더미패가 없으면 빈 Optional이다. */
    public Optional<Card> revealed() { return Optional.ofNullable(revealed); }
    public List<TurnMatcher.Event> events() { return result.events(); }

    Integer handChoice() { return handChoice; }
}
