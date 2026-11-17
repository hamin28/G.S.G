package gallaemallae.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class GameRulesCheck {
    public static void main(String[] args) {
        checkCardClassification();
        Set<Integer> captureCounts = new HashSet<>();
        for (int seed = 0; seed < 500; seed++) {
            DeckManager.InitialDeal deal = DeckManager.shuffleAndDeal(new Random(seed));
            check(deal.dealerHand().size() == 10 && deal.opponentHand().size() == 10, "Hands");
            check(deal.floor().size() == 8, "Floor size");
            check(deal.floor().stream().noneMatch(Card::isService), "Normal floor cards");
            check(deal.dealerCaptures().stream().allMatch(Card::isService), "Initial captures");
            int captures = deal.dealerCaptures().size();
            captureCounts.add(captures);
            check(deal.stock().size() == 22 - captures, "Stock size");
            Set<Integer> ids = new HashSet<>();
            for (List<Card> zone : Arrays.asList(deal.dealerHand(), deal.opponentHand(),
                    deal.floor(), deal.dealerCaptures(), deal.stock())) {
                for (Card card : zone) {
                    check(ids.add(card.id()), "Repeated card ID");
                }
            }
            check(ids.size() == 50, "Card conservation");
        }
        check(captureCounts.equals(new HashSet<>(Arrays.asList(0, 1, 2))), "Service branches");
        System.out.println("PASS: 500 deals, 50 distinct IDs, and 0/1/2 initial service captures");

        DeckManager.InitialDeal initialDeal = DeckManager.shuffleAndDeal(new Random(0));
        GameState initialState = new GameState(GameState.Seat.SECOND, initialDeal);
        check(initialState.openingTurn() == GameState.Seat.SECOND
                && initialState.hand(GameState.Seat.SECOND).equals(initialDeal.dealerHand())
                && initialState.captures(GameState.Seat.FIRST).isEmpty()
                && initialState.cardsInStock() == initialDeal.stock().size(), "Round initialization");

        Card played = Card.monthly(4, 4);
        List<Card> floor = Arrays.asList(Card.monthly(5, 1), Card.monthly(4, 1),
                Card.monthly(4, 2), Card.monthly(4, 3));
        CardMatcher.Action[] expected = {CardMatcher.Action.PLACE, CardMatcher.Action.CAPTURE_PAIR,
                CardMatcher.Action.CHOOSE_ONE, CardMatcher.Action.CAPTURE_ALL};
        for (int sameMonth = 0; sameMonth < expected.length; sameMonth++) {
            CardMatcher.Result result = CardMatcher.match(played, floor.subList(0, sameMonth + 1));
            check(result.action() == expected[sameMonth], "Action for " + sameMonth);
            check(result.candidates().size() == sameMonth, "Candidates for " + sameMonth);
            check(result.candidates().stream().allMatch(card -> card.month() == played.month()),
                    "Candidate month");
        }
        System.out.println("PASS: 4 matching cases (0/1/2/3 cards)");
        checkTurnMatching();
        checkSettlement();
        checkGameStateTurns();
        checkDealerSelection();
        checkOpeningTongtong();
        checkTurnEngine();
    }

    private static void checkTurnEngine() {
        GameState.Seat first = GameState.Seat.FIRST;
        GameState.Seat second = GameState.Seat.SECOND;
        long round = 7;
        for (int seed = 0; seed < 500; seed++) {
            DeckManager.InitialDeal deal = DeckManager.shuffleAndDeal(new Random(seed));
            if (deal.stock().get(0).isService() || deal.stock().get(1).isService()) {
                continue;
            }
            Card openingCard = deal.dealerHand().stream().filter(card -> !card.isService())
                    .filter(card -> CardMatcher.match(card, deal.floor()).action() == CardMatcher.Action.CHOOSE_ONE)
                    .findFirst().orElse(null);
            if (openingCard == null) {
                continue;
            }
            RoundInitializer opening = RoundInitializer.start(first, new Random(seed));
            if (!opening.game().isPresent()) {
                continue;
            }
            TurnEngine engine = new TurnEngine(round, opening.game().get());
            TurnEngine.PlayerView initialFirst = engine.viewFor(first);
            TurnEngine.PlayerView initialSecond = engine.viewFor(second);
            check(initialFirst.ownHand().stream().map(Card::id).noneMatch(id ->
                            initialSecond.ownHand().stream().anyMatch(card -> card.id() == id)),
                    "Each view has its own hand");
            check(initialFirst.opponentHandSize() == 10 && initialSecond.opponentHandSize() == 10
                    && initialFirst.floor().equals(initialSecond.floor()) && initialFirst.version() == 0,
                    "Initial public views agree");

            expectRejected(() -> engine.submit(second, round, 0, TurnEngine.Action.PLAY_CARD,
                    initialSecond.ownHand().get(0).id()));
            expectRejected(() -> engine.submit(first, round - 1, 0, TurnEngine.Action.PLAY_CARD,
                    openingCard.id()));
            check(engine.viewFor(first).version() == 0, "Rejected requests leave the version unchanged");
            TurnEngine.PlayerView pending = engine.submit(first, round, 0, TurnEngine.Action.PLAY_CARD,
                    openingCard.id());
            check(pending.version() == 1 && pending.expectedAction() == TurnEngine.Action.CHOOSE_MATCH
                    && pending.choices().size() == 2 && !pending.revealed().isPresent(),
                    "Hand match choice opens with the new version and keeps the reveal hidden");
            check(engine.viewFor(second).choices().isEmpty() && engine.viewFor(second).version() == 1,
                    "Only the active client gets candidate cards");
            expectRejected(() -> engine.submit(first, round, 0, TurnEngine.Action.PLAY_CARD, openingCard.id()));
            expectRejected(() -> engine.submit(first, round, 1, TurnEngine.Action.CHOOSE_MATCH, -1));
            check(engine.viewFor(first).version() == 1 && engine.viewFor(first).choices().equals(pending.choices()),
                    "Duplicate and invalid requests preserve the pending choice");
            finishChoices(engine, first, pending);

            TurnEngine.PlayerView next = engine.viewFor(second);
            check(next.currentTurn() == second && next.revealed().isPresent()
                    && next.revealed().get().id() == deal.stock().get(0).id()
                    && next.expectedAction() == TurnEngine.Action.PLAY_CARD
                    && next.opponentHandSize() == 9, "First turn exposes its reveal and changes seats");
            check(next.floor().equals(engine.viewFor(first).floor()), "Both clients see the updated floor");
            Card secondCard = next.ownHand().stream().filter(card -> !card.isService()).findFirst().get();
            TurnEngine.PlayerView secondTurn = engine.submit(second, round, next.version(),
                    TurnEngine.Action.PLAY_CARD, secondCard.id());
            finishChoices(engine, second, secondTurn);
            TurnEngine.PlayerView finalFirst = engine.viewFor(first);
            TurnEngine.PlayerView finalSecond = engine.viewFor(second);
            check(finalFirst.currentTurn() == first && finalFirst.version() == finalSecond.version()
                    && finalFirst.ownHand().size() == 9 && finalSecond.ownHand().size() == 9
                    && finalFirst.stockSize() == initialFirst.stockSize() - 2
                    && finalFirst.floor().equals(finalSecond.floor())
                    && finalFirst.firstCaptures().equals(finalSecond.firstCaptures())
                    && finalFirst.secondCaptures().equals(finalSecond.secondCaptures()),
                    "Two clients agree after each player completes a turn");
            System.out.println("PASS: two-player turns, private hands and request versions (seed " + seed + ")");
            return;
        }
        throw new AssertionError("Could not find a normal two-turn test deal");
    }

    private static void finishChoices(TurnEngine engine, GameState.Seat seat, TurnEngine.PlayerView view) {
        while (view.expectedAction() == TurnEngine.Action.CHOOSE_MATCH) {
            view = engine.submit(seat, view.roundNumber(), view.version(),
                    TurnEngine.Action.CHOOSE_MATCH, view.choices().get(0).id());
        }
    }

    private static void checkDealerSelection() {
        GameState.Seat first = GameState.Seat.FIRST;
        GameState.Seat second = GameState.Seat.SECOND;
        DealerSelection selection = new DealerSelection(new Random(0));
        List<Card> offer = selection.offer();
        int draw = selection.drawNumber();
        check(offer.size() == 5 && offer.stream().map(Card::id).distinct().count() == 5
                && offer.stream().noneMatch(Card::isService), "Five distinct standard cards");
        Card high = Collections.max(offer, DealerSelection::compare);
        Card low = Collections.min(offer, DealerSelection::compare);
        check(selection.choose(second, draw, high.id()) == DealerSelection.ChoiceResult.WAITING,
                "Either seat may choose first");
        check(selection.candidates(first).size() == 5 && !selection.dealer().isPresent(),
                "First choice is hidden before collision");
        check(selection.choose(first, draw, high.id()) == DealerSelection.ChoiceResult.RESELECT
                && selection.candidates(first).size() == 4 && !selection.candidates(first).contains(high),
                "Later player reselects from four");
        expectRejected(() -> selection.choose(first, draw, high.id()));
        check(selection.choose(first, draw, low.id()) == DealerSelection.ChoiceResult.DECIDED
                && selection.dealer().get() == second, "Higher card wins after reselection");
        check(DealerSelection.compare(Card.monthly(1, 1), Card.monthly(2, 4)) < 0
                && DealerSelection.compare(Card.monthly(12, 1), Card.monthly(12, 2)) > 0
                && DealerSelection.compare(Card.monthly(12, 2), Card.monthly(12, 3)) > 0
                && DealerSelection.compare(Card.monthly(12, 3), Card.monthly(12, 4)) > 0,
                "Month first, then gwang, yeolkkeut, tti, pi");
        boolean drewAgain = false;
        for (int seed = 0; seed < 500 && !drewAgain; seed++) {
            DealerSelection tie = new DealerSelection(new Random(seed));
            List<Card> cards = tie.offer();
            for (int i = 0; i < cards.size() && !drewAgain; i++) {
                for (int j = i + 1; j < cards.size() && !drewAgain; j++) {
                    if (DealerSelection.compare(cards.get(i), cards.get(j)) == 0) {
                        int oldDraw = tie.drawNumber();
                        tie.choose(first, oldDraw, cards.get(i).id());
                        check(tie.choose(second, oldDraw, cards.get(j).id()) == DealerSelection.ChoiceResult.REDRAW
                                && tie.drawNumber() == oldDraw + 1 && !tie.dealer().isPresent()
                                && tie.candidates(first).size() == 5 && tie.candidates(second).size() == 5,
                                "Tie starts a new draw");
                        try {
                            tie.choose(first, oldDraw, tie.offer().get(0).id());
                            throw new AssertionError("Previous draw was still accepted");
                        } catch (IllegalStateException expected) {
                            drewAgain = true;
                        }
                    }
                }
            }
        }
        check(drewAgain, "A redraw was tested");
        System.out.println("PASS: dealer offer, collision, ranking, tie and stale draw");
    }

    private static void checkOpeningTongtong() {
        List<Card> noTongtong = Arrays.asList(Card.service(2), Card.service(3),
                Card.monthly(4, 1), Card.monthly(4, 2), Card.monthly(4, 3));
        List<Card> january = monthCards(1);
        List<Card> february = monthCards(2);
        List<Card> may = monthCards(5);
        check(RoundInitializer.classify(noTongtong, Collections.emptyList(), Collections.emptyList())
                == RoundInitializer.Outcome.PLAY, "Three cards and services do not count as tongtong");
        check(RoundInitializer.classify(january, Collections.emptyList(), Collections.emptyList())
                == RoundInitializer.Outcome.DEALER_TONGTONG, "Dealer hand tongtong");
        check(RoundInitializer.classify(Collections.emptyList(), february, Collections.emptyList())
                == RoundInitializer.Outcome.OPPONENT_TONGTONG, "Opponent hand tongtong");
        check(RoundInitializer.classify(Collections.emptyList(), Collections.emptyList(), may)
                == RoundInitializer.Outcome.DEALER_TONGTONG, "Floor tongtong belongs to dealer");
        check(RoundInitializer.classify(january, Collections.emptyList(), may)
                == RoundInitializer.Outcome.DEALER_TONGTONG, "Two tongtong conditions for one player");
        check(RoundInitializer.classify(january, february, Collections.emptyList())
                == RoundInitializer.Outcome.VOID, "Two hand tongtongs invalidate the round");
        check(RoundInitializer.classify(Collections.emptyList(), february, may)
                == RoundInitializer.Outcome.VOID, "Opponent hand and floor tongtong invalidate the round");
        check(RoundInitializer.classify(january, february, may)
                == RoundInitializer.Outcome.VOID, "Dealer hand and floor still count as one side");

        Set<RoundInitializer.Outcome> seen = new HashSet<>();
        GameState.Seat dealer = GameState.Seat.SECOND;
        for (int seed = 0; seed < 20000 && seen.size() < 4; seed++) {
            RoundInitializer opening = RoundInitializer.start(dealer, new Random(seed));
            if (!seen.add(opening.outcome())) {
                continue;
            }
            switch (opening.outcome()) {
                case PLAY:
                    check(opening.game().get().currentTurn() == dealer && !opening.tongtongWinner().isPresent(),
                            "Only normal openings start a turn");
                    break;
                case VOID:
                    check(!opening.game().isPresent() && !opening.tongtongWinner().isPresent(),
                            "Void has neither turns nor winner");
                    RoundInitializer retry = opening.redeal(new Random(0));
                    check(retry.game().get().currentTurn() == dealer, "Redeal retains original dealer");
                    break;
                default:
                    GameState.Seat winner = opening.outcome() == RoundInitializer.Outcome.DEALER_TONGTONG
                            ? dealer : GameState.Seat.FIRST;
                    check(!opening.game().isPresent() && opening.tongtongWinner().get() == winner,
                            "Tongtong wins before any turn");
            }
        }
        check(seen.size() == 4, "All opening outcomes exercised by dealing");
        System.out.println("PASS: hand/floor tongtong, void and redeal with original dealer");
    }

    private static List<Card> monthCards(int month) {
        return Arrays.asList(Card.monthly(month, 1), Card.monthly(month, 2),
                Card.monthly(month, 3), Card.monthly(month, 4));
    }

    private static void checkGameStateTurns() {
        Set<TurnMatcher.Stage> exercised = new HashSet<>();
        for (int seed = 0; seed < 500 && exercised.size() < 3; seed++) {
            DeckManager.InitialDeal deal = DeckManager.shuffleAndDeal(new Random(seed));
            Card revealed = deal.stock().get(0);
            if (revealed.isService()) {
                continue;
            }
            for (Card played : deal.dealerHand()) {
                if (played.isService()) {
                    continue;
                }
                TurnMatcher.Stage expected = TurnMatcher.match(deal.floor(), played, null, revealed, null).stage();
                if (!exercised.add(expected)) {
                    continue;
                }
                GameState.Seat dealer = GameState.Seat.SECOND;
                GameState.Seat opponent = GameState.Seat.FIRST;
                GameState game = new GameState(dealer, deal);
                List<Card> originalHand = game.hand(dealer);
                int originalStock = game.cardsInStock();
                expectRejected(() -> game.play(opponent, played.id()));
                expectRejected(() -> game.play(dealer, deal.opponentHand().get(0).id()));

                TurnState turn = game.play(dealer, played.id());
                check(turn.stage() == expected, "Opening action stage");
                while (turn.stage() != TurnMatcher.Stage.COMPLETE) {
                    check(game.currentTurn() == dealer && game.hand(dealer).equals(originalHand)
                            && game.cardsInStock() == originalStock && game.floor().equals(deal.floor()),
                            "Pending choices do not commit cards");
                    check(turn.revealed().isPresent() == (turn.stage() == TurnMatcher.Stage.STOCK_CHOICE),
                            "Reveal becomes visible only after hand choice");
                    expectRejected(() -> game.chooseMatch(opponent, 1));
                    expectRejected(() -> game.chooseMatch(dealer, -1));
                    turn = game.chooseMatch(dealer, turn.choices().get(0).id());
                }
                check(turn.revealed().get() == revealed && game.currentTurn() == opponent,
                        "Completed turn reveals the stock card and changes seats");
                check(game.hand(dealer).size() == 9 && !game.hand(dealer).contains(played)
                        && game.cardsInStock() == originalStock - 1 && originalHand.size() == 10,
                        "Card movement preserves earlier snapshots");
                check(game.hand(opponent).equals(deal.opponentHand()) && game.captures(opponent).isEmpty(),
                        "Opponent card areas remain unchanged in a normal turn");
                Set<Integer> ids = new HashSet<>();
                int visible = 0;
                for (List<Card> zone : Arrays.asList(game.hand(dealer), game.hand(opponent),
                        game.floor(), game.captures(dealer), game.captures(opponent))) {
                    for (Card card : zone) {
                        check(ids.add(card.id()), "No card was copied between areas");
                        visible++;
                    }
                }
                check(visible + game.cardsInStock() == 50, "Turn preserves all cards");
            }
        }
        check(exercised.size() == 3, "State checks cover direct, hand choice and stock choice");
        System.out.println("PASS: round state, legal choices, card conservation and turn changes");
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid action was accepted");
        } catch (IllegalArgumentException expected) {
            // The round is expected to retain all cards and the pending action.
        }
    }

    private static void checkCardClassification() {
        Map<CardType, Integer> counts = new EnumMap<>(CardType.class);
        Set<Integer> doublePiMonths = new HashSet<>();
        int doublePiCount = 0;
        int rainGwangCount = 0;
        int gukjinCount = 0;
        for (int month = 1; month <= 12; month++) {
            for (int slot = 1; slot <= 4; slot++) {
                Card card = Card.monthly(month, slot);
                check(card.id() == (month - 1) * 4 + slot, "Existing monthly ID");
                check(!card.isService() && card.servicePiValue() == 0, "Monthly card is not a service");
                counts.merge(card.type(), 1, Integer::sum);
                if (card.defaultPiValue() == 2) {
                    check(card.type() == CardType.PI, "Double pi type");
                    doublePiMonths.add(month);
                    doublePiCount++;
                } else {
                    check(card.defaultPiValue() == (card.type() == CardType.PI ? 1 : 0),
                            "Default pi value");
                }
                if (card.isRainGwang()) {
                    check(month == 12 && card.type() == CardType.GWANG, "Rain gwang identity");
                    rainGwangCount++;
                }
                if (card.isGukjin()) {
                    check(month == 9 && card.type() == CardType.YEOLKKEUT, "Gukjin identity");
                    gukjinCount++;
                }
            }
        }
        check(counts.get(CardType.GWANG) == 5 && counts.get(CardType.YEOLKKEUT) == 9
                && counts.get(CardType.TTI) == 10 && counts.get(CardType.PI) == 24, "Card type totals");
        check(doublePiCount == 2 && doublePiMonths.equals(new HashSet<>(Arrays.asList(11, 12))),
                "Monthly double pi cards");
        check(rainGwangCount == 1 && gukjinCount == 1, "Unique rain gwang and gukjin");
        check(Card.monthly(12, 3).type() == CardType.TTI, "December ribbon");
        for (int value = 2; value <= 3; value++) {
            Card service = Card.service(value);
            check(service.id() == 47 + value && service.month() == 0 && service.isService()
                    && service.defaultPiValue() == value && service.servicePiValue() == value,
                    "Service identity and pi value");
        }
        System.out.println("PASS: 48 monthly card classifications and 2 service cards");
    }

    private static void checkTurnMatching() {
        Card hand = Card.monthly(4, 3);
        Card reveal = Card.monthly(4, 4);
        Card first = Card.monthly(4, 1);
        Card second = Card.monthly(4, 2);

        TurnMatcher.Result jjok = TurnMatcher.match(Collections.emptyList(), hand, null, reveal, null);
        check(jjok.events().equals(Arrays.asList(TurnMatcher.Event.JJOK, TurnMatcher.Event.SWEEP))
                && jjok.captures().size() == 2, "Jjok and sweep");

        List<Card> floor = Arrays.asList(first, second);
        TurnMatcher.Result waiting = TurnMatcher.match(floor, hand, null, reveal, null);
        check(waiting.stage() == TurnMatcher.Stage.HAND_CHOICE && waiting.choices().equals(floor),
                "Hand choice pauses");
        TurnMatcher.Result ttadak = TurnMatcher.match(floor, hand, first.id(), reveal, null);
        check(ttadak.events().equals(Arrays.asList(TurnMatcher.Event.TTADAK, TurnMatcher.Event.SWEEP))
                && ttadak.floor().isEmpty() && ttadak.captures().size() == 4, "Ttadak and sweep");
        check(floor.size() == 2, "Input floor stays intact");

        TurnMatcher.Result ppeok = TurnMatcher.match(Collections.singletonList(first), hand, null, reveal, null);
        check(ppeok.events().equals(Collections.singletonList(TurnMatcher.Event.PPEOK))
                && ppeok.floor().size() == 3 && ppeok.captures().isEmpty(), "Ppeok stays on floor");

        Card otherMonth = Card.monthly(8, 1);
        TurnMatcher.Result stockWait = TurnMatcher.match(floor, otherMonth, null, reveal, null);
        check(stockWait.stage() == TurnMatcher.Stage.STOCK_CHOICE && stockWait.choices().equals(floor),
                "Stock choice pauses");
        TurnMatcher.Result stockChosen = TurnMatcher.match(floor, otherMonth, null, reveal, second.id());
        check(stockChosen.captures().contains(second) && stockChosen.floor().contains(first),
                "Stock choice captures only the selected card");
        System.out.println("PASS: turn choices, ppeok, ttadak + sweep, jjok + sweep");
    }

    private static void checkSettlement() {
        check(SettlementCalculator.normalWin(8, 3, 1_000_000, 1_000_000, 2) == 440_000,
                "Go score and multiplied payout");
        check(SettlementCalculator.normalWin(8, 3, 100_000, 300_000, 2) == 100_000,
                "Winner opening balance cap");
        check(SettlementCalculator.normalWin(8, 3, 100_000, 50_000, 2) == 50_000,
                "Loser balance cap");
        check(SettlementCalculator.normalWin(8, 500, 100_000, 200_000, Long.MAX_VALUE) == 100_000,
                "Large multipliers stay capped");
        check(SettlementCalculator.fixedWin(100_000, 50_000) == 50_000, "Fixed payout cap");
        System.out.println("PASS: normal and fixed settlement, both balance caps");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
