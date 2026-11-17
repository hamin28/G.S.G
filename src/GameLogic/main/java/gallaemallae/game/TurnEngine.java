package gallaemallae.game;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import gallaemallae.game.GameState.Seat;

/** One engine per playable round. The room supplies the authenticated player's seat. */
public final class TurnEngine {
    public enum Action { PLAY_CARD, CHOOSE_MATCH }

    private final long roundNumber;
    private final GameState game;
    private long version;
    private TurnState latestTurn;

    public TurnEngine(long roundNumber, GameState game) {
        this.roundNumber = roundNumber;
        this.game = Objects.requireNonNull(game);
    }

    /** Accepted requests each advance the version, including requests that open a choice. */
    public synchronized PlayerView submit(Seat seat, long expectedRound, long expectedVersion,
                                          Action action, int cardId) {
        Objects.requireNonNull(seat);
        Objects.requireNonNull(action);
        if (expectedRound != roundNumber || expectedVersion != version) {
            throw new IllegalArgumentException("The round or state version is no longer current");
        }
        TurnState result;
        switch (action) {
            case PLAY_CARD:
                result = game.play(seat, cardId);
                break;
            case CHOOSE_MATCH:
                result = game.chooseMatch(seat, cardId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action");
        }
        latestTurn = result;
        version++;
        return viewFor(seat);
    }

    public synchronized PlayerView viewFor(Seat viewer) {
        Objects.requireNonNull(viewer);
        Seat opponent = viewer == Seat.FIRST ? Seat.SECOND : Seat.FIRST;
        boolean pending = latestTurn != null && latestTurn.stage() != TurnMatcher.Stage.COMPLETE;
        List<Card> choices = pending && viewer == game.currentTurn()
                ? latestTurn.choices() : Collections.emptyList();
        return new PlayerView(roundNumber, version, game.currentTurn(),
                pending ? Action.CHOOSE_MATCH : Action.PLAY_CARD,
                game.hand(viewer), game.hand(opponent).size(), game.cardsInStock(),
                game.floor(), game.captures(Seat.FIRST), game.captures(Seat.SECOND),
                latestTurn == null ? null : latestTurn.played(),
                latestTurn == null ? null : latestTurn.revealed().orElse(null),
                choices, latestTurn == null ? Collections.emptyList() : latestTurn.events());
    }

    /** Contains the viewer's private hand and the information safe to share with that viewer. */
    public static final class PlayerView {
        private final long roundNumber;
        private final long version;
        private final Seat currentTurn;
        private final Action expectedAction;
        private final List<Card> ownHand;
        private final int opponentHandSize;
        private final int stockSize;
        private final List<Card> floor;
        private final List<Card> firstCaptures;
        private final List<Card> secondCaptures;
        private final Card played;
        private final Card revealed;
        private final List<Card> choices;
        private final List<TurnMatcher.Event> events;

        private PlayerView(long roundNumber, long version, Seat currentTurn, Action expectedAction,
                           List<Card> ownHand, int opponentHandSize, int stockSize, List<Card> floor,
                           List<Card> firstCaptures, List<Card> secondCaptures, Card played,
                           Card revealed, List<Card> choices, List<TurnMatcher.Event> events) {
            this.roundNumber = roundNumber;
            this.version = version;
            this.currentTurn = currentTurn;
            this.expectedAction = expectedAction;
            this.ownHand = ownHand;
            this.opponentHandSize = opponentHandSize;
            this.stockSize = stockSize;
            this.floor = floor;
            this.firstCaptures = firstCaptures;
            this.secondCaptures = secondCaptures;
            this.played = played;
            this.revealed = revealed;
            this.choices = choices;
            this.events = events;
        }

        public long roundNumber() { return roundNumber; }
        public long version() { return version; }
        public Seat currentTurn() { return currentTurn; }
        public Action expectedAction() { return expectedAction; }
        public List<Card> ownHand() { return ownHand; }
        public int opponentHandSize() { return opponentHandSize; }
        public int stockSize() { return stockSize; }
        public List<Card> floor() { return floor; }
        public List<Card> firstCaptures() { return firstCaptures; }
        public List<Card> secondCaptures() { return secondCaptures; }
        public Optional<Card> played() { return Optional.ofNullable(played); }
        public Optional<Card> revealed() { return Optional.ofNullable(revealed); }
        public List<Card> choices() { return choices; }
        public List<TurnMatcher.Event> events() { return events; }
    }
}
