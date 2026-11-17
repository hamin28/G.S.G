package gallaemallae.game;

import java.util.List;
import java.util.Optional;

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
    public Optional<Card> revealed() { return Optional.ofNullable(revealed); }
    public List<TurnMatcher.Event> events() { return result.events(); }

    Integer handChoice() { return handChoice; }
}
