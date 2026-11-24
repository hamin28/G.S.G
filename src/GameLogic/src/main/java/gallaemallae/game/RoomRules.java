package gallaemallae.game;

import java.util.*;
import gallaemallae.game.GameState.Seat;

/**
 * 방의 규칙만 관리하는 순수 Java 객체. 소켓, Swing, 실제 타이머를 만들지 않는다.
 * 준비·새 상대·선결정·연속 나가리·기권 후 대기를 한 판 엔진 밖에 두어 초기화 범위를 분명히 한다.
 */
public final class RoomRules {
    public enum Status { WAITING, READY, DEALER_SELECTION, PLAYING, CLOSED }
    private final Random random;
    private final boolean[] present = new boolean[2], ready = new boolean[2];
    private final long[] money = new long[2], selectionDeadline = new long[2];
    private DealerSelection selection;
    private MatchEngine game;
    private MatchEngine.Result lastResult;
    private Seat dealer;
    private int drawStreak;
    private long roundSequence;
    private Status status = Status.WAITING;

    public RoomRules(Random random) { this.random = Objects.requireNonNull(random); }

    /** 기존 계정 금액을 받는다. 새 상대가 들어와도 기존 플레이어의 금액을 초기화하지 않는다. */
    public synchronized void join(Seat seat, long balance) {
        Objects.requireNonNull(seat);
        if (status == Status.CLOSED || present[seat.ordinal()] || balance <= 0)
            throw new IllegalStateException("입장할 수 없음");
        if (balance > Long.MAX_VALUE - money[MatchEngine.other(seat).ordinal()])
            throw new IllegalArgumentException("잔액 합계 범위 초과");
        present[seat.ordinal()] = true; money[seat.ordinal()] = balance;
        dealer = null; drawStreak = 0; selection = null; game = null;
        Arrays.fill(ready, false);
        status = present[0] && present[1] ? Status.READY : Status.WAITING;
    }

    /** 상대가 없을 때에는 제한 시간도 패널티도 없다. 두 명 준비 시에만 판 시작 절차에 진입한다. */
    public synchronized void ready(Seat seat, long nowMillis) {
        requirePresent(seat);
        if (status != Status.READY && status != Status.WAITING) throw new IllegalStateException("준비 대기 상태 아님");
        ready[seat.ordinal()] = true;
        if (!present[0] || !present[1] || !ready[0] || !ready[1]) return;
        if (dealer == null) {
            selection = new DealerSelection(random); status = Status.DEALER_SELECTION;
            Arrays.fill(selectionDeadline, Math.addExact(nowMillis, MatchEngine.TIME_LIMIT_MS));
        } else startRound(nowMillis);
    }

    /** 분배 전에는 준비를 취소할 수 있다. 선결정 도중 취소하면 양쪽 준비와 선결정을 다시 시작한다. */
    public synchronized void cancelReady(Seat seat) {
        requirePresent(seat);
        if (status == Status.PLAYING || status == Status.CLOSED) throw new IllegalStateException("분배 후 준비 취소 불가");
        if (status == Status.DEALER_SELECTION) {
            selection = null; dealer = null; Arrays.fill(ready, false);
            status = Status.READY;
        } else ready[seat.ordinal()] = false;
    }

    /** 선결정 충돌은 먼저 접수된 선택을 보존하고 다른 사람에게 새 30초를 부여한다. */
    public synchronized DealerSelection.ChoiceResult chooseDealer(Seat seat, int drawNumber, int cardId, long nowMillis) {
        requirePresent(seat);
        if (status != Status.DEALER_SELECTION) throw new IllegalStateException("선결정 중 아님");
        if (expire(nowMillis)) throw new IllegalStateException("선결정 제한 시간 초과로 종료");
        DealerSelection.ChoiceResult result = selection.choose(seat, drawNumber, cardId);
        if (result == DealerSelection.ChoiceResult.RESELECT)
            selectionDeadline[seat.ordinal()] = Math.addExact(nowMillis, MatchEngine.TIME_LIMIT_MS);
        else if (result == DealerSelection.ChoiceResult.REDRAW)
            Arrays.fill(selectionDeadline, Math.addExact(nowMillis, MatchEngine.TIME_LIMIT_MS));
        else selectionDeadline[seat.ordinal()] = 0;
        if (result == DealerSelection.ChoiceResult.DECIDED) {
            dealer = selection.dealer().get(); startRound(nowMillis);
        }
        return result;
    }

    private void startRound(long now) {
        Arrays.fill(ready, false); selection = null;
        game = new MatchEngine(++roundSequence, dealer, DeckManager.shuffleAndDeal(random),
                money[0], money[1], drawStreak, now);
        status = Status.PLAYING;
        consumeResult();
    }

    public synchronized MatchEngine.View act(Seat seat, long round, long version,
                                             MatchEngine.Action action, int cardId, long nowMillis) {
        requirePresent(seat);
        if (status != Status.PLAYING) throw new IllegalStateException("진행 중인 판 없음");
        MatchEngine.View view = game.submit(seat, round, version, action, cardId, nowMillis);
        consumeResult();
        return view;
    }

    /** 호출자가 주기적으로 전달한 시각으로만 검사한다. 실제 예약 작업 연결은 추후 서버 담당 작업이다. */
    public synchronized boolean expire(long nowMillis) {
        if (status == Status.PLAYING && game.expire(nowMillis)) { consumeResult(); return true; }
        if (status == Status.DEALER_SELECTION) {
            Seat expired = null;
            for (Seat seat : Seat.values()) {
                long deadline = selectionDeadline[seat.ordinal()];
                if (deadline != 0 && nowMillis >= deadline
                        && (expired == null || deadline < selectionDeadline[expired.ordinal()])) expired = seat;
            }
            if (expired != null) { remove(expired); return true; }
        }
        return false;
    }

    /** 패 분배 전에는 선결정 중이어도 무료 퇴장, 분배 후에는 즉시 기권 정산한다. */
    public synchronized void leave(Seat seat) {
        requirePresent(seat);
        if (status == Status.PLAYING) { game.forfeit(seat); consumeResult(); }
        else remove(seat);
    }

    /** 같은 종료 결과를 두 번 적용하지 않도록 PLAYING에서 벗어난 뒤에는 재정산하지 않는다. */
    private void consumeResult() {
        MatchEngine.Result result = game.result();
        if (result == null || status != Status.PLAYING) return;
        lastResult = result;
        for (Seat seat : Seat.values()) money[seat.ordinal()] = result.money(seat);
        dealer = result.nextDealer; drawStreak = result.nextDrawStreak;
        Arrays.fill(ready, false);
        if (result.end == MatchEngine.End.FORFEIT) { remove(MatchEngine.other(result.winner)); return; }
        if (result.bankrupt(Seat.FIRST) || result.bankrupt(Seat.SECOND)) {
            Arrays.fill(present, false); status = Status.CLOSED;
        } else status = Status.READY;
    }

    private void remove(Seat seat) {
        present[seat.ordinal()] = false; Arrays.fill(ready, false);
        dealer = null; drawStreak = 0; selection = null; game = null;
        status = Status.WAITING;
    }
    private void requirePresent(Seat seat) {
        if (seat == null || !present[seat.ordinal()]) throw new IllegalStateException("방에 없는 플레이어");
    }

    public synchronized Status status() { return status; }
    public synchronized boolean present(Seat seat) { return present[seat.ordinal()]; }
    public synchronized boolean isReady(Seat seat) { return ready[seat.ordinal()]; }
    public synchronized long money(Seat seat) { return money[seat.ordinal()]; }
    public synchronized int drawStreak() { return drawStreak; }
    public synchronized Seat dealer() { return dealer; }
    public synchronized int selectionDraw() { return selection == null ? 0 : selection.drawNumber(); }
    public synchronized List<Card> selectionCandidates(Seat seat) {
        return selection == null ? Collections.emptyList() : selection.candidates(seat);
    }
    public synchronized long selectionDeadline(Seat seat) {
        return status == Status.DEALER_SELECTION ? selectionDeadline[seat.ordinal()] : 0;
    }
    public synchronized MatchEngine.View viewFor(Seat seat) {
        requirePresent(seat);
        return game == null ? null : game.viewFor(seat);
    }
    public synchronized MatchEngine.Result lastResult() { return lastResult; }
}
