package gallaemallae.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import gallaemallae.game.GameState.Seat;

/** 분배 직후 총통을 검사하고, 일반 진행이 가능한 경우에만 GameState를 만든다. */
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

    /** 초기 바닥 서비스패 보충까지 마친 분배 결과로 판 시작 가능 여부를 정한다. */
    public static RoundInitializer start(Seat dealer, Random random) {
        return new RoundInitializer(dealer, DeckManager.shuffleAndDeal(random));
    }

    public Outcome outcome() { return outcome; }
    public Optional<GameState> game() { return Optional.ofNullable(game); }

    /** 총통 승자가 있으면 좌석을 반환한다. 일반 진행과 무효 판에는 승자가 없다. */
    public Optional<Seat> tongtongWinner() {
        if (outcome == Outcome.DEALER_TONGTONG) {
            return Optional.of(dealer);
        }
        if (outcome == Outcome.OPPONENT_TONGTONG) {
            return Optional.of(dealer == Seat.FIRST ? Seat.SECOND : Seat.FIRST);
        }
        return Optional.empty();
    }

    /** 동시 총통 무효 판을 기존 선으로 재분배한다. 나가리 배율 보관은 상위 코드의 역할이다. */
    public RoundInitializer redeal(Random random) {
        if (outcome != Outcome.VOID) {
            throw new IllegalStateException("Only a void deal can be redealt here");
        }
        return start(dealer, random);
    }

    /** 바닥 총통은 선의 승리 조건이며, 양쪽에 승리 조건이 있으면 무효 판이다. */
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

    /** 월별 장수를 세어 같은 월 4장이 있는지 검사한다. 서비스패는 월 집계에서 제외한다. */
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
