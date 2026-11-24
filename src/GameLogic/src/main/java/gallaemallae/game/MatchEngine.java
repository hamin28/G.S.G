package gallaemallae.game;

import java.util.*;
import gallaemallae.game.GameState.Seat;
import gallaemallae.game.ScoreCalculator.Score;
import gallaemallae.game.GoStopRules.Settlement;

/**
 * 8단계의 한 판 전체 로직. 서버가 이 객체 하나를 방별로 소유한다.
 * 4단계 TurnEngine은 당시의 실습용으로 보존하며, 최종 연동은 이 클래스를 사용한다.
 * UI·소켓·스레드 생성은 하지 않고 요청 검증, 선택 대기, 판정과 정산을 책임진다.
 */
public final class MatchEngine {
    public static final long TIME_LIMIT_MS = 30000;
    public enum Phase { PLAY, HAND_CHOICE, STOCK_CHOICE, GUKJIN, GO_STOP, FINISHED }
    public enum Action { PLAY, SERVICE, SHAKE, BOMB, DECK_ONLY, SELECT, GUKJIN_PI, GUKJIN_ANIMAL, GO, STOP }
    public enum End { NORMAL, TONGTONG, THREE_PPEOK, DRAW, VOID, FORFEIT }

    private final long roundId;
    private final Seat dealer;
    private final long[] opening;
    private final int drawStreak;
    private long version;
    private State state;

    /** 초기 바닥 서비스패 처리 후 총통을 먼저 검사하여 정상 턴 진입 여부를 정한다. */
    public MatchEngine(long roundId, Seat dealer, DeckManager.InitialDeal deal,
                       long firstMoney, long secondMoney, int drawStreak, long nowMillis) {
        if (roundId < 1 || dealer == null || deal == null || firstMoney <= 0 || secondMoney <= 0 || drawStreak < 0)
            throw new IllegalArgumentException("판 시작 정보 오류");
        // 두 잔액의 합도 long 범위에 들어야 입출금 중 넘침이 없다.
        Math.addExact(firstMoney, secondMoney);
        this.roundId = roundId; this.dealer = dealer;
        this.opening = new long[] { firstMoney, secondMoney }; this.drawStreak = drawStreak;
        state = new State(dealer, deal, nowMillis);
        RoundInitializer.Outcome outcome = RoundInitializer.classify(deal.dealerHand(), deal.opponentHand(), deal.floor());
        if (outcome == RoundInitializer.Outcome.VOID) finishNoWinner(End.VOID);
        else if (outcome != RoundInitializer.Outcome.PLAY)
            finish(outcome == RoundInitializer.Outcome.DEALER_TONGTONG ? dealer : other(dealer), End.TONGTONG);
    }

    /**
     * 서버 수신 순서대로 호출한다. 이전 판·중복 버전·비정상 행동은 거절한다.
     * 복사본에만 실행하므로 잘못된 선택은 패, 점수, 버전, 마감 시각을 바꾸지 않는다.
     * nowMillis는 클라이언트가 보낸 값 대신 서버의 단조 증가 시계를 사용한다.
     */
    public synchronized View submit(Seat seat, long expectedRound, long expectedVersion,
                                    Action action, int cardId, long nowMillis) {
        if (seat == null || action == null) throw new IllegalArgumentException("요청 정보 없음");
        if (expectedRound != roundId || expectedVersion != version) throw new IllegalStateException("지난 판 또는 지난 상태의 요청");
        if (state.phase == Phase.FINISHED) throw new IllegalStateException("이미 종료된 판");
        if (expire(nowMillis)) return viewFor(seat);
        if (seat != state.turn) throw new IllegalStateException("자기 차례가 아님");
        State original = state;
        state = new State(original);
        try {
            state.events.clear();
            perform(action, cardId);
            state.deadline = Math.addExact(nowMillis, TIME_LIMIT_MS);
            checkConservation();
            version++;
        } catch (RuntimeException error) {
            state = original;
            throw error;
        }
        return viewFor(seat);
    }

    /** 서버 예약 작업에서 호출한다. 요청이 오지 않아도 30초 시점에 즉시 기권 처리한다. */
    public synchronized boolean expire(long nowMillis) {
        if (state.phase != Phase.FINISHED && nowMillis >= state.deadline) {
            finish(other(state.turn), End.FORFEIT);
            version++;
            return true;
        }
        return false;
    }

    /** 연결 해제·창 닫기·진행 중 퇴장을 모두 같은 단일 정산 경로로 처리한다. */
    public synchronized void forfeit(Seat loser) {
        Objects.requireNonNull(loser);
        if (state.phase != Phase.FINISHED) { finish(other(loser), End.FORFEIT); version++; }
    }

    private void perform(Action action, int cardId) {
        Player p = player(state.turn);
        if (state.phase == Phase.HAND_CHOICE || state.phase == Phase.STOCK_CHOICE) {
            if (action != Action.SELECT) throw new IllegalStateException("매칭 대상부터 선택");
            Card choice = find(state.choices, cardId);
            boolean hand = state.phase == Phase.HAND_CHOICE;
            Card submitted = hand ? state.played : state.revealed;
            state.phase = Phase.PLAY;
            capture(submitted, Collections.singletonList(choice));
            state.choices.clear();
            if (hand) flipStock(); else completeTurn();
            return;
        }
        if (state.phase == Phase.GUKJIN) {
            if (action != Action.GUKJIN_PI && action != Action.GUKJIN_ANIMAL)
                throw new IllegalStateException("국진 분류부터 선택");
            if (action == Action.GUKJIN_PI) { state.gukjinPi = true; state.events.add("국진 쌍피 확정"); }
            evaluateGoStop();
            return;
        }
        if (state.phase == Phase.GO_STOP) {
            if (action == Action.STOP) { finish(state.turn, End.NORMAL); return; }
            if (action != Action.GO) throw new IllegalStateException("고 또는 스톱 선택 필요");
            p.go++;
            p.lastGoBase = score(state.turn).total;
            if (player(other(state.turn)).go > 0) p.inverseGo = true;
            state.events.add("고 " + p.go + "회, 기준 " + p.lastGoBase + "점");
            advanceTurn();
            return;
        }
        if (state.phase != Phase.PLAY) throw new IllegalStateException("행동 대기 상태 아님");
        state.announced.clear();
        resetTurn();
        if (action == Action.SERVICE) {
            Card c = find(p.hand, cardId);
            if (!c.isService()) throw new IllegalArgumentException("서비스패가 아님");
            p.hand.remove(c); p.captures.add(c); steal(1);
            if (!state.stock.isEmpty()) p.hand.add(state.stock.removeFirst());
            state.events.add("서비스패 사용: 보충패는 손에 보관하고 같은 차례 계속");
            if (!canAct(state.turn)) completeTurn();
            return;
        }
        if (action == Action.DECK_ONLY) {
            if (p.tokens == 0) throw new IllegalStateException("폭탄 뒤집기 기회 없음");
            p.tokens--;
            state.events.add("폭탄 기회 1회 사용");
            flipStock();
            return;
        }
        if (action != Action.PLAY && action != Action.SHAKE && action != Action.BOMB)
            throw new IllegalStateException("현재 허용되지 않는 행동");
        Card c = find(p.hand, cardId);
        if (c.isService()) throw new IllegalArgumentException("서비스패는 SERVICE로 사용");
        List<Card> sameHand = SpecialRules.month(p.hand, c.month());
        int sameFloor = SpecialRules.month(state.floor, c.month()).size();
        if (action == Action.SHAKE) {
            if (!SpecialRules.canShake(p.hand, state.floor, c.month(), p.shakenMonths))
                throw new IllegalStateException("흔들기 조건 불충족 또는 중복 선언");
            p.shakenMonths.add(c.month());
            p.shakes++;
            state.announced = new ArrayList<>(sameHand);
            state.events.add("흔들기: 해당 월 3장 공개 후 패 제출, 배율 2배 추가");
        }
        if (action == Action.BOMB) {
            if (!SpecialRules.canBomb(p.hand, state.floor, c.month())) throw new IllegalStateException("폭탄 조건 불충족");
            p.hand.removeAll(sameHand); state.taken.addAll(sameHand);
            List<Card> floorCards = SpecialRules.month(state.floor, c.month());
            state.floor.removeAll(floorCards); state.taken.addAll(floorCards);
            p.bombs++; p.tokens += SpecialRules.bombOpportunities(1); state.theft++;
            state.events.add("폭탄: 4장 획득, 피 1장, 다음 차례부터 기회 2회");
            // 폭탄은 별도의 배율만 추가한다. 흔들기를 자동으로 선언하지 않는다.
            flipStock();
            return;
        }
        state.played = c; state.initialMatches = sameFloor;
        p.hand.remove(c);
        match(c, true);
    }

    private void resetTurn() {
        state.played = null; state.revealed = null; state.initialMatches = -1;
        state.theft = 0; state.taken.clear(); state.choices.clear();
    }

    /** 손패 매칭을 확정하기 전에는 더미 맨 위를 공개하거나 소비하지 않는다. */
    private void match(Card c, boolean hand) {
        List<Card> candidates = SpecialRules.month(state.floor, c.month());
        if (candidates.size() == 2) {
            state.choices = candidates;
            state.phase = hand ? Phase.HAND_CHOICE : Phase.STOCK_CHOICE;
            return;
        }
        if (candidates.isEmpty()) state.floor.add(c);
        else capture(c, candidates);
        if (hand) flipStock(); else completeTurn();
    }

    /** 뻑 묶음의 생성자를 기억하여 자뻑 2장과 상대 뻑 1장을 구별한다. */
    private void capture(Card c, List<Card> targets) {
        if (targets.size() == 3 && state.ppeokOwners.containsKey(c.month())) {
            Seat owner = state.ppeokOwners.remove(c.month());
            state.theft += SpecialRules.ppeokReward(owner == state.turn);
            state.events.add(owner == state.turn ? "자뻑 회수: 피 2장" : "상대 뻑 회수: 피 1장");
        }
        state.floor.removeAll(targets);
        state.taken.add(c); state.taken.addAll(targets);
    }

    /** 더미 서비스패는 즉시 획득·피 이동 후 정상패가 나올 때까지 계속 뒤집는다. */
    private void flipStock() {
        state.phase = Phase.PLAY;
        while (!state.stock.isEmpty()) {
            Card c = state.stock.removeFirst();
            state.revealed = c;
            if (c.isService()) {
                player(state.turn).captures.add(c); steal(1);
                state.events.add("더미 서비스패 획득: 피 1장, 계속 뒤집기");
                continue;
            }
            if (state.played != null && c.month() == state.played.month()) {
                if (state.initialMatches == 1) {
                    state.floor.addAll(state.taken); state.taken.clear(); state.floor.add(c);
                    state.ppeokOwners.put(c.month(), state.turn);
                    Player p = player(state.turn); p.ppeokCount++;
                    state.events.add("뻑 " + p.ppeokCount + "회");
                    if (p.ppeokCount == 3) { finish(state.turn, End.THREE_PPEOK); return; }
                    completeTurn();
                    return;
                }
                if (state.initialMatches == 0 || state.initialMatches == 2) {
                    state.theft++;
                    state.events.add(state.initialMatches == 0 ? "쪽: 피 1장" : "따닥: 피 1장");
                }
            }
            match(c, false);
            return;
        }
        completeTurn();
    }

    /** 획득 확정→특수 피 이동→점수 순으로 계산해야 피 이동으로 바뀐 점수도 반영된다. */
    private void completeTurn() {
        boolean captured = !state.taken.isEmpty();
        player(state.turn).captures.addAll(state.taken); state.taken.clear();
        if (captured && state.floor.isEmpty()) { state.theft++; state.events.add("쓸: 피 1장"); }
        if (state.theft > 0) steal(state.theft);
        state.theft = 0;
        Player p = player(state.turn);
        if (!state.gukjinPi && p.captures.stream().anyMatch(Card::isGukjin)) {
            Score converted = ScoreCalculator.calculate(p.captures, true);
            if (GoStopRules.eligible(converted.total, p.go, p.lastGoBase)) {
                state.phase = Phase.GUKJIN;
                return;
            }
        }
        evaluateGoStop();
    }

    private void evaluateGoStop() {
        Player p = player(state.turn);
        boolean eligible = GoStopRules.eligible(score(state.turn).total, p.go, p.lastGoBase);
        if (eligible) {
            if (exhausted()) finish(state.turn, End.NORMAL);
            else state.phase = Phase.GO_STOP;
        } else if (exhausted()) finishNoWinner(End.DRAW);
        else advanceTurn();
    }

    private void advanceTurn() {
        if (exhausted()) { finishNoWinner(End.DRAW); return; }
        if (canAct(other(state.turn))) state.turn = other(state.turn);
        state.phase = Phase.PLAY;
    }

    private void steal(int count) {
        List<Card> moved = SpecialRules.stealPi(player(other(state.turn)).captures,
                player(state.turn).captures, count, state.gukjinPi);
        state.events.add("상대 획득피 " + moved.size() + "장 이동 (요청 " + count + "장)");
    }

    private void finish(Seat winner, End end) {
        // 선택 대기 중 기권하면 아직 바닥/획득 구역에 없는 제출패도 보존한다.
        if (state.phase == Phase.HAND_CHOICE) state.taken.add(state.played);
        if (state.phase == Phase.STOCK_CHOICE) state.taken.add(state.revealed);
        state.choices.clear();
        Seat loser = other(winner);
        Player p = player(winner);
        Settlement settlement = end == End.NORMAL
                ? GoStopRules.normal(score(winner), score(loser), p.go, player(loser).go,
                    p.inverseGo, p.shakes, p.bombs, drawStreak, opening[winner.ordinal()], opening[loser.ordinal()])
                : GoStopRules.fixed(opening[winner.ordinal()], opening[loser.ordinal()]);
        long[] money = opening.clone();
        money[winner.ordinal()] += settlement.paidAmount;
        money[loser.ordinal()] -= settlement.paidAmount;
        state.result = new Result(end, winner, settlement, money, winner, 0);
        state.phase = Phase.FINISHED;
        state.events.add(end + ": " + winner + " 승리, " + settlement.paidAmount + "원 지급");
    }

    private void finishNoWinner(End end) {
        state.result = new Result(end, null, null, opening, dealer,
                end == End.DRAW ? Math.addExact(drawStreak, 1) : drawStreak);
        state.phase = Phase.FINISHED;
        state.events.add(end == End.DRAW ? "나가리: 다음 판 배율 누적" : "동시 총통: 무효, 선·나가리 누적 유지");
    }

    public synchronized View viewFor(Seat seat) {
        Objects.requireNonNull(seat);
        return new View(this, seat);
    }

    public long roundId() { return roundId; }
    public synchronized long version() { return version; }
    public synchronized Result result() { return state.result; }
    private Player player(Seat seat) { return state.players[seat.ordinal()]; }
    private Score score(Seat seat) { return ScoreCalculator.calculate(player(seat).captures, state.gukjinPi); }
    private boolean canAct(Seat seat) { Player p = player(seat); return !p.hand.isEmpty() || p.tokens > 0; }
    /** 양쪽 행동이 끝나면 바닥 더미의 서비스패 꼬리는 획득하지 않고 현재 점수로 종료한다. */
    private boolean exhausted() { return !canAct(Seat.FIRST) && !canAct(Seat.SECOND); }
    public static Seat other(Seat seat) { return seat == Seat.FIRST ? Seat.SECOND : Seat.FIRST; }
    private static Card find(List<Card> cards, int id) {
        for (Card c : cards) if (c.id() == id) return c;
        throw new IllegalArgumentException("선택 가능한 패 ID가 아님: " + id);
    }
    private static List<Card> snapshot(List<Card> cards) { return Collections.unmodifiableList(new ArrayList<>(cards)); }

    /** 중간 선택 상태에서도 50장의 중복·유실을 검사한다. 상대에게 더미나 손패를 공개하지 않는다. */
    synchronized void checkConservation() {
        List<Card> all = new ArrayList<>(state.floor); all.addAll(state.stock); all.addAll(state.taken);
        for (Player p : state.players) { all.addAll(p.hand); all.addAll(p.captures); }
        if (state.phase == Phase.HAND_CHOICE) all.add(state.played);
        if (state.phase == Phase.STOCK_CHOICE) all.add(state.revealed);
        Set<Integer> ids = new HashSet<>();
        for (Card c : all) if (!ids.add(c.id())) throw new IllegalStateException("카드 중복: " + c.id());
        if (all.size() != 50) throw new IllegalStateException("카드 유실: " + all.size());
    }

    /** 패를 보유하지 않는 상대 뷰는 손패 장수만 갖는다. 목록은 모두 수정 불가 복사본이다. */
    public static final class View {
        public final long roundId, version, deadlineMillis;
        public final Seat turn, viewer;
        public final Phase phase;
        public final List<Card> ownHand, floor, ownCaptures, opponentCaptures, choices, pendingCaptures, announcedCards;
        public final int opponentHandCount, stockCount, ownTokens, opponentTokens;
        public final int ownGo, opponentGo, ownPpeok, opponentPpeok, ownShakes, ownBombs, opponentShakes, opponentBombs;
        public final Map<Integer,Seat> ppeokOwners;
        public final Set<Integer> ownShakenMonths;
        public final Score ownScore, opponentScore;
        public final boolean gukjinPi;
        public final Card played, revealed;
        public final List<String> events;
        public final Result result;
        private View(MatchEngine engine, Seat viewer) {
            State s = engine.state; Player p = engine.player(viewer), o = engine.player(other(viewer));
            this.viewer = viewer; roundId = engine.roundId; version = engine.version; deadlineMillis = s.deadline;
            turn = s.turn; phase = s.phase; ownHand = snapshot(p.hand); floor = snapshot(s.floor);
            ownCaptures = snapshot(p.captures); opponentCaptures = snapshot(o.captures);
            choices = viewer == s.turn ? snapshot(s.choices) : Collections.emptyList();
            pendingCaptures = snapshot(s.taken);
            announcedCards = snapshot(s.announced);
            opponentHandCount = o.hand.size(); stockCount = s.stock.size();
            ownTokens = p.tokens; opponentTokens = o.tokens; ownGo = p.go; opponentGo = o.go;
            ownPpeok = p.ppeokCount; opponentPpeok = o.ppeokCount; ownShakes = p.shakes; ownBombs = p.bombs;
            opponentShakes = o.shakes; opponentBombs = o.bombs;
            ppeokOwners = Collections.unmodifiableMap(new HashMap<>(s.ppeokOwners));
            ownShakenMonths = Collections.unmodifiableSet(new HashSet<>(p.shakenMonths));
            ownScore = engine.score(viewer); opponentScore = engine.score(other(viewer)); gukjinPi = s.gukjinPi;
            played = s.played; revealed = s.revealed; events = Collections.unmodifiableList(new ArrayList<>(s.events));
            result = s.result;
        }
    }

    public static final class Result {
        public final End end;
        public final Seat winner, nextDealer;
        public final Settlement settlement;
        public final int nextDrawStreak;
        private final long[] money;
        private Result(End end, Seat winner, Settlement settlement, long[] money, Seat dealer, int draws) {
            this.end = end; this.winner = winner; this.settlement = settlement;
            this.money = money.clone(); nextDealer = dealer; nextDrawStreak = draws;
        }
        public long money(Seat seat) { return money[seat.ordinal()]; }
        public boolean bankrupt(Seat seat) { return money(seat) == 0; }
    }

    private static final class Player {
        final List<Card> hand = new ArrayList<>(), captures = new ArrayList<>();
        final Set<Integer> shakenMonths = new HashSet<>();
        int tokens, shakes, bombs, ppeokCount, go, lastGoBase;
        boolean inverseGo;
        Player() {}
        Player(Player p) {
            hand.addAll(p.hand); captures.addAll(p.captures); shakenMonths.addAll(p.shakenMonths);
            tokens = p.tokens; shakes = p.shakes; bombs = p.bombs; ppeokCount = p.ppeokCount;
            go = p.go; lastGoBase = p.lastGoBase; inverseGo = p.inverseGo;
        }
    }

    private static final class State {
        Player[] players = { new Player(), new Player() };
        List<Card> floor = new ArrayList<>(), taken = new ArrayList<>(), choices = new ArrayList<>(), announced = new ArrayList<>();
        Deque<Card> stock = new ArrayDeque<>();
        Map<Integer, Seat> ppeokOwners = new HashMap<>();
        List<String> events = new ArrayList<>();
        Seat turn;
        Phase phase = Phase.PLAY;
        Card played, revealed;
        int initialMatches = -1, theft;
        boolean gukjinPi;
        long deadline;
        Result result;
        State(Seat dealer, DeckManager.InitialDeal deal, long now) {
            turn = dealer; deadline = Math.addExact(now, TIME_LIMIT_MS);
            players[dealer.ordinal()].hand.addAll(deal.dealerHand());
            players[other(dealer).ordinal()].hand.addAll(deal.opponentHand());
            players[dealer.ordinal()].captures.addAll(deal.dealerCaptures());
            floor.addAll(deal.floor()); stock.addAll(deal.stock());
        }
        State(State s) {
            players = new Player[] { new Player(s.players[0]), new Player(s.players[1]) };
            floor.addAll(s.floor); taken.addAll(s.taken); choices.addAll(s.choices); stock.addAll(s.stock); announced.addAll(s.announced);
            ppeokOwners.putAll(s.ppeokOwners); events.addAll(s.events);
            turn = s.turn; phase = s.phase; played = s.played; revealed = s.revealed;
            initialMatches = s.initialMatches; theft = s.theft; gukjinPi = s.gukjinPi;
            deadline = s.deadline; result = s.result;
        }
    }
}
