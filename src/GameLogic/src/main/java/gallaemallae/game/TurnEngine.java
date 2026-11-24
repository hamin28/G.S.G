package gallaemallae.game;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import gallaemallae.game.GameState.Seat;

/**
 * 서버의 패 제출·매칭 선택 요청을 GameState에 연결하는 한 판의 진입점이다.
 * 서버가 소켓으로 확인한 실제 플레이어 좌석을 전달해야 한다.
 */
public final class TurnEngine {
    public enum Action { PLAY_CARD, CHOOSE_MATCH }

    private final long roundNumber;
    private final GameState game;
    private long version;
    private TurnState latestTurn;

    /** 방이 결정한 판 번호와 게임 상태를 연결한다. 한 판마다 새 엔진을 만든다. */
    public TurnEngine(long roundNumber, GameState game) {
        this.roundNumber = roundNumber;
        this.game = Objects.requireNonNull(game);
    }

    /**
     * 현재 판 번호와 상태 버전이 맞는 요청을 처리한다.
     * 선택 대기를 여는 요청도 성공 시 버전을 올린다. 거절된 요청은 버전을 바꾸지 않는다.
     * synchronized로 같은 엔진의 동시 요청을 한 번에 하나씩 처리한다.
     */
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

    /** 수신자의 손패와 공통 공개 상태를 구성한다. 매칭 후보는 현재 차례인 사람만 받는다. */
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

    /** 클라이언트 한 명에게 보낼 조회 결과. 상대 손패는 장수만 포함한다. */
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
