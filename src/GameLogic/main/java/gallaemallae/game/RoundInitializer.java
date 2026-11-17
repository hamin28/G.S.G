package gallaemallae.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import gallaemallae.game.GameState.Seat;

public final class RoundInitializer {
    public enum Outcome { PLAY, DEALER_TONGTONG, OPPONENT_TONGTONG, VOID }

    private final Seat dealer;
    private final Outcome outcome;
    private final GameState game;

    private RoundInitializer(Seat dealer, DeckManager.InitialDeal deal) {
        this.dealer = Objects.requireNonNull(dealer);
        outcome = classify(deal.dealerHand(), deal.opponentHand(), deal.floor());
        game = outcome == Outcome.PLAY ? new GameState(dealer, deal) : null;
    }

    public static RoundInitializer start(Seat dealer, Random random) {
        return new RoundInitializer(dealer, DeckManager.shuffleAndDeal(random));
    }

    public Outcome outcome() { return outcome; }
    public Optional<GameState> game() { return Optional.ofNullable(game); }

    public Optional<Seat> tongtongWinner() {
        if (outcome == Outcome.DEALER_TONGTONG) {
            return Optional.of(dealer);
        }
        if (outcome == Outcome.OPPONENT_TONGTONG) {
            return Optional.of(dealer == Seat.FIRST ? Seat.SECOND : Seat.FIRST);
        }
        return Optional.empty();
    }

    /** Retain the original dealer when a double tongtong invalidates the deal. */
    public RoundInitializer redeal(Random random) {
        if (outcome != Outcome.VOID) {
            throw new IllegalStateException("Only a void deal can be redealt here");
        }
        return start(dealer, random);
    }

    static Outcome classify(List<Card> dealerHand, List<Card> opponentHand, List<Card> floor) {
        boolean dealerHasTongtong = hasFourOfOneMonth(dealerHand) || hasFourOfOneMonth(floor);
        boolean opponentHasTongtong = hasFourOfOneMonth(opponentHand);
        if (dealerHasTongtong && opponentHasTongtong) {
            return Outcome.VOID;
        }
        if (dealerHasTongtong) {
            return Outcome.DEALER_TONGTONG;
        }
        return opponentHasTongtong ? Outcome.OPPONENT_TONGTONG : Outcome.PLAY;
    }

    private static boolean hasFourOfOneMonth(List<Card> cards) {
        int[] count = new int[13];
        for (Card card : cards) {
            if (!card.isService() && ++count[card.month()] == 4) {
                return true;
            }
        }
        return false;
    }
}
