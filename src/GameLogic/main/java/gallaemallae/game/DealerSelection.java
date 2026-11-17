package gallaemallae.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import gallaemallae.game.GameState.Seat;

/** The room submits choices in server receipt order. */
public final class DealerSelection {
    public enum ChoiceResult { WAITING, RESELECT, REDRAW, DECIDED }

    private final Random random;
    private final Map<Seat, Card> selected = new EnumMap<>(Seat.class);
    private final Set<Seat> reselecting = EnumSet.noneOf(Seat.class);
    private List<Card> offer;
    private int drawNumber;
    private Seat dealer;

    public DealerSelection(Random random) {
        this.random = Objects.requireNonNull(random);
        redraw();
    }

    public int drawNumber() { return drawNumber; }
    public List<Card> offer() { return offer; }
    public Optional<Seat> dealer() { return Optional.ofNullable(dealer); }

    /** Both see the original offer. Only a player asked to reselect sees four candidates. */
    public List<Card> candidates(Seat seat) {
        Objects.requireNonNull(seat);
        if (selected.containsKey(seat)) {
            return Collections.emptyList();
        }
        if (!reselecting.contains(seat)) {
            return offer;
        }
        List<Card> remaining = new ArrayList<>(offer);
        remaining.removeAll(selected.values());
        return Collections.unmodifiableList(remaining);
    }

    public ChoiceResult choose(Seat seat, int expectedDrawNumber, int cardId) {
        Objects.requireNonNull(seat);
        if (dealer != null || expectedDrawNumber != drawNumber || selected.containsKey(seat)) {
            throw new IllegalStateException("This selection is no longer open");
        }
        Card card = offer.stream().filter(candidate -> candidate.id() == cardId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Card is not in the offer"));
        if (selected.containsValue(card)) {
            if (!reselecting.add(seat)) {
                throw new IllegalArgumentException("Choose one of the four remaining cards");
            }
            return ChoiceResult.RESELECT;
        }
        selected.put(seat, card);
        if (selected.size() < 2) {
            return ChoiceResult.WAITING;
        }
        int comparison = compare(selected.get(Seat.FIRST), selected.get(Seat.SECOND));
        if (comparison == 0) {
            redraw();
            return ChoiceResult.REDRAW;
        }
        dealer = comparison > 0 ? Seat.FIRST : Seat.SECOND;
        return ChoiceResult.DECIDED;
    }

    private void redraw() {
        List<Card> cards = DeckManager.standardCards();
        Collections.shuffle(cards, random);
        offer = Collections.unmodifiableList(new ArrayList<>(cards.subList(0, 5)));
        selected.clear();
        reselecting.clear();
        drawNumber++;
    }

    static int compare(Card first, Card second) {
        int month = Integer.compare(first.month(), second.month());
        return month != 0 ? month : Integer.compare(rank(first.type()), rank(second.type()));
    }

    private static int rank(CardType type) {
        switch (type) {
            case GWANG: return 4;
            case YEOLKKEUT: return 3;
            case TTI: return 2;
            case PI: return 1;
            default: throw new IllegalArgumentException("Service cards cannot decide the dealer");
        }
    }
}
