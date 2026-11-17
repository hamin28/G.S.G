package gallaemallae.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-owned state for one round. Calls for the same room must be serialized. */
public final class GameState {
    public enum Seat { FIRST, SECOND }

    private final Seat dealer;
    private final Map<Seat, List<Card>> hands = new EnumMap<>(Seat.class);
    private final Map<Seat, List<Card>> captures = new EnumMap<>(Seat.class);
    private final List<Card> floor;
    private final Deque<Card> stock;
    private Seat currentTurn;
    private TurnState pendingTurn;

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

    private TurnState resolve(Card played, Integer handChoice, Integer stockChoice) {
        Card top = stock.getFirst();
        TurnMatcher.Result result = TurnMatcher.match(floor, played, handChoice, top, stockChoice);
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

    private void requireCurrentTurn(Seat seat) {
        if (seat != currentTurn) {
            throw new IllegalArgumentException("It is not this player's turn");
        }
    }

    private static Seat other(Seat seat) {
        return seat == Seat.FIRST ? Seat.SECOND : Seat.FIRST;
    }

    private static List<Card> snapshot(List<Card> cards) {
        return Collections.unmodifiableList(new ArrayList<>(cards));
    }
}
