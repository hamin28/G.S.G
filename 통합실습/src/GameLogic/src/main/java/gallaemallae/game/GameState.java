package gallaemallae.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 서버가 보관하는 한 판의 카드 위치와 차례를 관리한다.
 * 같은 방의 요청은 순서대로 호출해야 한다. 현재 일반 패 턴을 처리하며,
 * 턴 중 서비스패와 더미 소진 처리는 후속 단계에서 연결한다.
 */
public final class GameState {
    public enum Seat { FIRST, SECOND }

    private final Seat dealer;
    private final Map<Seat, List<Card>> hands = new EnumMap<>(Seat.class);
    private final Map<Seat, List<Card>> captures = new EnumMap<>(Seat.class);
    private final List<Card> floor;
    private final Deque<Card> stock;
    private Seat currentTurn;
    private TurnState pendingTurn;

    /** 분배 결과를 복사해 현재 판을 만든다. 선이 첫 번째 차례를 가진다. */
    public GameState(Seat dealer, DeckManager.InitialDeal deal) {
        this.dealer = Objects.requireNonNull(dealer);
        Objects.requireNonNull(deal);
        currentTurn = dealer;
        hands.put(dealer, new ArrayList<>(deal.dealerHand()));
        hands.put(other(dealer), new ArrayList<>(deal.opponentHand()));
        captures.put(dealer, new ArrayList<>(deal.dealerCaptures()));
        captures.put(other(dealer), new ArrayList<>());
        floor = new ArrayList<>(deal.floor());
        stock = new ArrayDeque<>(deal.stock());
    }

    public Seat dealer() { return dealer; }
    public Seat openingTurn() { return dealer; }
    public Seat currentTurn() { return currentTurn; }

    public List<Card> hand(Seat seat) {
        return snapshot(hands.get(Objects.requireNonNull(seat)));
    }

    public List<Card> floor() { return snapshot(floor); }

    public List<Card> captures(Seat seat) {
        return snapshot(captures.get(Objects.requireNonNull(seat)));
    }

    public int cardsInStock() { return stock.size(); }

    /** 자기 손패 ID를 받아 일반 턴을 시작한다. 선택 대기 중 새 패 제출은 거절한다. */
    public TurnState play(Seat seat, int cardId) {
        requireCurrentTurn(seat);
        if (pendingTurn != null) {
            throw new IllegalStateException("A match choice is pending");
        }
        Card played = hands.get(seat).stream().filter(card -> card.id() == cardId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Card is not in this hand"));
        Card revealed = stock.peekFirst();
        if (played.isService() || revealed == null || revealed.isService()) {
            throw new IllegalStateException("This action requires service-card or end-of-stock rules");
        }
        return resolve(played, null, null);
    }

    /** 손패 또는 더미 매칭에서 선택한 바닥패 ID로 중단된 판정을 이어 간다. */
    public TurnState chooseMatch(Seat seat, int floorCardId) {
        requireCurrentTurn(seat);
        if (pendingTurn == null) {
            throw new IllegalStateException("No match choice is pending");
        }
        if (pendingTurn.stage() == TurnMatcher.Stage.HAND_CHOICE) {
            return resolve(pendingTurn.played(), floorCardId, null);
        }
        return resolve(pendingTurn.played(), pendingTurn.handChoice(), floorCardId);
    }

    /** 판정 결과가 COMPLETE일 때만 카드 목록과 차례를 한 번에 갱신한다. */
    private TurnState resolve(Card played, Integer handChoice, Integer stockChoice) {
        Card top = stock.getFirst();
        TurnMatcher.Result result = TurnMatcher.match(floor, played, handChoice, top, stockChoice);
        // 손패의 획득 대상부터 고르는 동안에는 더미패를 화면에 공개하지 않는다.
        Card visibleReveal = result.stage() == TurnMatcher.Stage.HAND_CHOICE ? null : top;
        TurnState state = new TurnState(played, handChoice, visibleReveal, result);
        if (result.stage() == TurnMatcher.Stage.COMPLETE) {
            hands.get(currentTurn).remove(played);
            stock.removeFirst();
            floor.clear();
            floor.addAll(result.floor());
            captures.get(currentTurn).addAll(result.captures());
            pendingTurn = null;
            currentTurn = other(currentTurn);
        } else {
            pendingTurn = state;
        }
        return state;
    }

    /** 서버가 전달한 좌석이 현재 차례인지 확인한다. */
    private void requireCurrentTurn(Seat seat) {
        if (seat != currentTurn) {
            throw new IllegalArgumentException("It is not this player's turn");
        }
    }

    /** 두 좌석 중 상대 좌석을 구해 다음 차례로 사용한다. */
    private static Seat other(Seat seat) {
        return seat == Seat.FIRST ? Seat.SECOND : Seat.FIRST;
    }

    /** 외부에서 목록을 수정하거나, 이후 턴 때문에 이전 조회 결과가 바뀌는 일을 막는다. */
    private static List<Card> snapshot(List<Card> cards) {
        return Collections.unmodifiableList(new ArrayList<>(cards));
    }
}
