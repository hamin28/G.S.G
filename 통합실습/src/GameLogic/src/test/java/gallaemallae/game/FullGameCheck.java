package gallaemallae.game;

import java.lang.reflect.*;
import java.util.*;
import gallaemallae.game.GameState.Seat;
import gallaemallae.game.MatchEngine.*;

/**
 * 외부 라이브러리 없이 main으로 실행하는 최종 규칙 회귀 검사.
 * 드문 경계 상황은 이 테스트 내부의 Fixture로만 배치한다. 제품 API에는 패 조작 기능이 없다.
 */
public final class FullGameCheck {
    private static int checks;
    private FullGameCheck() {}
    public static void main(String[] args) throws Exception {
        scoreRules();
        moneyRules();
        servicesAndRequests();
        matchingAndSpecials();
        decisionsAndEndings();
        roomLifecycle();
        additionalBoundaries();
        randomGames(2000);
        System.out.println("PASS: 최종 게임 로직 " + checks + "개 검증, 무작위 완주 2000판");
    }
    private static Card c(int m, int s) { return Card.monthly(m, s); }
    private static List<Card> cards(Card... values) { return new ArrayList<>(Arrays.asList(values)); }
    private static void ok(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void reject(Runnable task, String message) {
        boolean rejected = false;
        try { task.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        ok(rejected, message);
    }

    private static void scoreRules() {
        ok(ScoreCalculator.calculate(cards(c(1,1), c(3,1), c(8,1)), false).gwang == 3, "비광 없는 3광");
        ok(ScoreCalculator.calculate(cards(c(1,1), c(3,1), c(12,1)), false).gwang == 2, "비광 포함 3광");
        ok(ScoreCalculator.calculate(cards(c(1,1), c(3,1), c(8,1), c(11,1)), false).gwang == 4, "4광");
        ok(ScoreCalculator.calculate(cards(c(1,1), c(3,1), c(8,1), c(11,1), c(12,1)), false).gwang == 15, "5광");
        ok(ScoreCalculator.calculate(cards(c(4,2), c(5,2), c(12,3)), false).chodan == 0, "12월 초단 제외");
        ok(ScoreCalculator.calculate(cards(c(4,2), c(5,2), c(7,2)), false).chodan == 3, "4·5·7월 초단");
        List<Card> ribbons = cards(c(1,2),c(2,2),c(3,2),c(4,2),c(5,2),c(6,2),c(7,2),c(9,2),c(10,2),c(12,3));
        ok(ScoreCalculator.calculate(ribbons, false).total == 15, "10띠 6점+세 단 9점");
        List<Card> animals = cards(c(2,1),c(4,1),c(5,1),c(7,1),c(8,2));
        ok(ScoreCalculator.calculate(animals,false).total == 6, "5열끗+고도리 중복");
        animals.add(c(9,1));
        ok(ScoreCalculator.calculate(animals,true).animalCount == 5, "쌍피 국진 열끗 제외");
        ok(ScoreCalculator.calculate(animals,true).effectivePi == 2, "국진 유효피 2");
        List<Card> pi = cards(c(1,3),c(1,4),c(2,3),c(2,4),c(11,4),Card.service(2),Card.service(3));
        ok(ScoreCalculator.calculate(pi,false).pi == 2, "유효피 11장=2점");
        List<Card> from = cards(Card.service(3),Card.service(2),c(11,4),c(1,3));
        List<Card> to = new ArrayList<>();
        SpecialRules.stealPi(from,to,8,false);
        ok(to.size() == 4 && to.get(0).id() == c(1,3).id() && to.get(1).id() == c(11,4).id()
                && to.get(2).id() == 49 && to.get(3).id() == 50, "피 이동 순위·부족분 제한");
        ok(!GoStopRules.eligible(7,1,7) && GoStopRules.eligible(8,1,7), "고 기본점수 1점 증가");
        ok(!GoStopRules.eligible(6,0,0) && GoStopRules.eligible(7,0,0), "첫 고 7점");
        System.out.println("PASS: 광·띠·고도리·국진·피 점수 및 고 재선택");
    }

    private static void moneyRules() {
        List<Card> win = cards(c(1,1),c(3,1),c(8,1),c(11,1),c(12,1),c(2,1),c(4,1),c(8,2),
                c(1,3),c(1,4),c(2,3),c(2,4),c(3,3),c(3,4),c(11,4),Card.service(2));
        GoStopRules.Settlement s = GoStopRules.normal(ScoreCalculator.calculate(win,false),
                ScoreCalculator.calculate(cards(),false),4,1,true,2,2,3,1000000,1000000);
        ok(s.factors.get("피박").intValue() == 2 && s.factors.get("광박").intValue() == 2
                && s.factors.get("멍박").intValue() == 2 && s.factors.get("고박").intValue() == 2, "네 박 독립 적용");
        ok(s.factors.get("고").intValue() == 4 && s.factors.get("흔들기").intValue() == 4
                && s.factors.get("폭탄").intValue() == 4 && s.factors.get("나가리").intValue() == 8, "발생별 누적");
        ok(s.paidAmount == 1000000 && s.calculatedAmount.longValue() > s.paidAmount, "승자 상한");
        ok(GoStopRules.fixed(50000,30000).paidAmount == 30000, "고정 정산 양쪽 상한");
        ok(GoStopRules.fixed(1000000,1000000).factors.isEmpty(), "고정 정산 배율 없음");
        s = GoStopRules.normal(ScoreCalculator.calculate(win,false),ScoreCalculator.calculate(cards(c(12,1)),false),
                0,0,false,0,0,0,50000,1000000);
        ok(s.factors.get("광박").intValue() == 1 && s.paidAmount == 50000, "비광 있으면 광박 없음");
        System.out.println("PASS: 박·배율 누적·고정금·지급 상한");
    }

    private static void servicesAndRequests() throws Exception {
        Fixture f = new Fixture().hand(0,Card.service(2),c(1,1)).hand(1,c(2,1))
                .capture(1,c(3,3)).floor(c(4,1)).stock(Card.service(3),c(5,1));
        MatchEngine e = f.build();
        final MatchEngine requestEngine = e;
        View before = e.viewFor(Seat.FIRST);
        reject(() -> act(requestEngine,Seat.SECOND,Action.PLAY,c(2,1).id(),1), "상대 차례 거절");
        reject(() -> act(requestEngine,Seat.FIRST,Action.PLAY,999,1), "없는 패 거절");
        ok(e.version() == before.version && e.viewFor(Seat.FIRST).deadlineMillis == before.deadlineMillis, "무효 행동 타이머 보존");
        View v = act(e,Seat.FIRST,Action.SERVICE,49,10);
        ok(v.turn == Seat.FIRST && v.ownHand.size() == 2 && v.ownHand.stream().anyMatch(Card::isService), "서비스 보충 손패·동일 차례");
        ok(v.opponentCaptures.isEmpty() && v.ownCaptures.size() == 2, "상대 손패 대신 획득피 이동");
        // 보충 서비스패를 강제 자동 사용하지 않는다. 다른 정상패를 제출할 수 있다.
        v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),20);
        ok(v.ownHand.size() == 1 && v.ownHand.get(0).id() == 50, "서비스 보관 자유");
        long currentVersion = e.version();
        reject(() -> requestEngine.submit(requestEngine.viewFor(Seat.FIRST).turn,1,currentVersion-1,Action.PLAY,0,21), "중복 버전 거절");
        ok(!e.expire(30019) && e.expire(30020), "정확히 30초에 만료");
        long paid = e.result().settlement.paidAmount;
        e.forfeit(Seat.FIRST); e.expire(Long.MAX_VALUE);
        ok(e.result().settlement.paidAmount == paid && e.result().end == End.FORFEIT, "중복 종료 재정산 없음");

        e = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).floor(c(4,1))
                .capture(1,c(3,3),c(3,4)).stock(Card.service(2),Card.service(3),c(5,1)).build();
        v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        ok(v.ownCaptures.size() == 4 && v.opponentCaptures.isEmpty(), "더미 서비스 연속 획득·각각 피 이동");
        System.out.println("PASS: 서비스패·요청 검증·30초·중복 종료");
    }

    private static void matchingAndSpecials() throws Exception {
        MatchEngine e = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).stock(c(1,2))
                .capture(1,c(3,3),c(3,4)).build();
        View v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        ok(v.ownCaptures.size() == 4 && has(v,"쪽") && has(v,"쓸"), "빈 바닥 쪽+쓸 피2장");
        e = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).floor(c(1,2),c(1,3))
                .stock(c(1,4)).capture(1,c(3,3),c(3,4)).build();
        v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        ok(v.phase == Phase.HAND_CHOICE && v.revealed == null, "손패 선택 전에 더미 미공개");
        final MatchEngine waiting = e; final long waitVersion = e.version();
        reject(() -> act(waiting,Seat.FIRST,Action.SELECT,999,2), "선택 아닌 패 거절");
        ok(e.version() == waitVersion, "선택 오류 상태 보존");
        v = act(e,Seat.FIRST,Action.SELECT,c(1,2).id(),2);
        ok(v.ownCaptures.size() == 6 && has(v,"따닥") && has(v,"쓸"), "따닥+쓸 피2장");

        Fixture f = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).floor(c(1,2)).stock(c(1,3));
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        ok(v.ownPpeok == 1 && v.floor.size() == 3 && v.ownCaptures.isEmpty(), "뻑은 세 장 바닥 유지");

        for (boolean own : new boolean[] {false,true}) {
            f = new Fixture().hand(0,c(1,4)).hand(1,c(2,1)).floor(c(1,1),c(1,2),c(1,3))
                    .stock(c(3,1)).capture(1,c(4,3),c(4,4),c(5,3));
            f.owners.put(1,own ? Seat.FIRST : Seat.SECOND);
            e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(1,4).id(),1);
            ok(v.ownCaptures.size() == (own ? 6 : 5), own ? "자뻑 피2장" : "상대 뻑 피1장");
        }
        f = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).floor(c(1,2)).stock(c(1,3));
        f.ppeokCount = 2; e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        ok(v.result.end == End.THREE_PPEOK && v.result.settlement.paidAmount == 100000, "누적 3뻑 즉시 고정승");

        e = new Fixture().hand(0,c(1,1),c(1,2),c(1,3),c(4,1)).hand(1,c(2,1))
                .floor(c(1,4)).stock(c(3,1),c(5,1)).capture(1,c(6,3)).build();
        v = act(e,Seat.FIRST,Action.BOMB,c(1,1).id(),1);
        ok(v.ownTokens == 2 && v.ownBombs == 1 && v.ownShakes == 0 && v.ownHand.size() == 1, "폭탄 배율만·기회2회");
        // 상대 일반 행동 후 기회를 쓰되 손에 남은 정상패를 강제로 내지 않는다.
        act(e,Seat.SECOND,Action.PLAY,c(2,1).id(),2);
        v = act(e,Seat.FIRST,Action.DECK_ONLY,0,3);
        ok(v.ownTokens == 1 && v.ownHand.size() == 1, "기회는 선택 사용·한 턴1회");
        e = new Fixture().hand(0,c(1,1),c(1,2),c(1,3)).hand(1,c(2,1)).stock(c(3,1)).build();
        v = act(e,Seat.FIRST,Action.SHAKE,c(1,1).id(),1);
        ok(v.ownShakes == 1 && v.ownBombs == 0 && v.ownHand.size() == 2, "흔들기와 패제출 함께 처리");
        System.out.println("PASS: 매칭 선택·뻑/자뻑·3뻑·쪽·따닥·쓸·흔들기·폭탄 기회");
    }

    private static void decisionsAndEndings() throws Exception {
        List<Card> seven = cards(c(1,1),c(3,1),c(8,1),c(11,1),c(1,2),c(2,2),c(3,2));
        Fixture f = new Fixture().hand(0,c(4,1)).hand(1,c(5,1)).floor(c(6,1)).stock(c(7,1));
        f.captures[0].addAll(seven);
        MatchEngine e = f.build(); View v = act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1);
        ok(v.phase == Phase.GO_STOP && v.ownScore.total == 7, "7점 선택 대기");
        v = act(e,Seat.FIRST,Action.GO,0,2);
        ok(v.ownGo == 1 && v.turn == Seat.SECOND, "고 후 턴 넘김");
        f = new Fixture().hand(0,c(4,1)).hand(1,c(5,1)).floor(c(6,1)).stock(c(7,1));
        f.captures[0].addAll(seven); f.go = 1; f.lastGoBase = 7;
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1);
        ok(v.phase == Phase.PLAY && v.turn == Seat.SECOND, "점수 그대로면 재선택 없음");

        f = new Fixture().hand(0,c(4,1)).floor(c(6,1)); f.captures[0].addAll(seven); f.emptyStock = true;
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1);
        ok(v.phase == Phase.FINISHED && v.result.end == End.NORMAL, "마지막 턴 조건 충족 자동정산");
        f = new Fixture().hand(0,c(4,1)).floor(c(6,1)); f.captures[0].addAll(seven);
        f.emptyStock = true; f.go = 1; f.lastGoBase = 7; f.draws = 2;
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1);
        ok(v.result.end == End.DRAW && v.result.nextDrawStreak == 3 && v.result.nextDealer == Seat.FIRST,
                "이전 고 점수 그대로 소진 시 나가리 누적·선 유지");

        // 4광=4점, 유효피10=1점, 국진 전환으로 유효피12=3점이 되어 7점이다.
        f = new Fixture().hand(0,c(4,1)).hand(1,c(5,1)).floor(c(6,1)).stock(c(7,1));
        f.capture(0,c(1,1),c(3,1),c(8,1),c(11,1),c(9,1),c(1,3),c(1,4),c(2,3),c(2,4),c(3,3),c(3,4),c(4,3),c(4,4),c(11,4));
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1);
        ok(v.phase == Phase.GUKJIN && v.ownScore.total == 5, "국진 전환 선택 선행");
        v = act(e,Seat.FIRST,Action.GUKJIN_PI,0,2);
        ok(v.gukjinPi && v.phase == Phase.GO_STOP && v.ownScore.total == 7, "국진 전환 후 고스톱");
        System.out.println("PASS: 고 기준점·국진 선택·마지막 턴 자동정산·나가리");
    }

    private static void roomLifecycle() {
        RoomRules room = new RoomRules(new Random(1));
        room.join(Seat.FIRST,1000000); room.ready(Seat.FIRST,0);
        ok(room.status() == RoomRules.Status.WAITING && !room.expire(Long.MAX_VALUE), "혼자 대기 무제한");
        room.leave(Seat.FIRST);
        ok(room.money(Seat.FIRST) == 1000000, "혼자 퇴장 무료");
        room.join(Seat.FIRST,1000000); room.join(Seat.SECOND,1000000);
        room.ready(Seat.FIRST,0);
        ok(room.status() == RoomRules.Status.READY, "한 명 준비로 시작 안 함");
        room.ready(Seat.SECOND,0);
        ok(room.status() == RoomRules.Status.DEALER_SELECTION, "두 명 준비 후 선결정");
        int draw = room.selectionDraw(), id = room.selectionCandidates(Seat.FIRST).get(0).id();
        room.chooseDealer(Seat.FIRST,draw,id,1);
        ok(room.chooseDealer(Seat.SECOND,draw,id,2) == DealerSelection.ChoiceResult.RESELECT, "동일 패 선착순");
        ok(room.selectionDeadline(Seat.SECOND) == 30002 && room.selectionCandidates(Seat.SECOND).size() == 4,
                "재선택 새30초·4후보");
        room.leave(Seat.SECOND);
        ok(room.money(Seat.FIRST) == 1000000 && room.money(Seat.SECOND) == 1000000
                && room.present(Seat.FIRST) && !room.present(Seat.SECOND), "선결정 무료 퇴장 후 새 상대 대기");
        room.join(Seat.SECOND,900000);
        ok(room.money(Seat.FIRST) == 1000000 && room.dealer() == null && room.drawStreak() == 0, "새 상대 돈 보존·선 새로");
        room.ready(Seat.FIRST,10); room.ready(Seat.SECOND,10);
        room.cancelReady(Seat.FIRST);
        ok(room.status() == RoomRules.Status.READY && !room.isReady(Seat.FIRST) && !room.isReady(Seat.SECOND), "선결정 중 준비 취소");
        room.ready(Seat.FIRST,20); room.ready(Seat.SECOND,20);
        room.chooseDealer(Seat.FIRST,room.selectionDraw(),room.selectionCandidates(Seat.FIRST).get(0).id(),21);
        room.expire(30020);
        ok(room.money(Seat.FIRST) == 1000000 && room.money(Seat.SECOND) == 900000 && !room.present(Seat.SECOND),
                "선결정 시간 초과도 금액 차감 없음");
        System.out.println("PASS: 준비·선결정 충돌·기권·새 상대 대기");
    }

    private static void additionalBoundaries() throws Exception {
        // 쪽 없이 시작된 빈 바닥에서는 서비스패만으로 쓸 보상이 생기면 안 된다.
        Fixture f = new Fixture().hand(0,Card.service(2)).hand(1,c(2,1));
        f.emptyStock = true;
        MatchEngine e = f.build(); View v = act(e,Seat.FIRST,Action.SERVICE,49,1);
        ok(!has(v,"쓸") && v.turn == Seat.SECOND, "더미 없는 마지막 서비스: 효과 후 턴 종료, 허위 쓸 없음");
        f = new Fixture().hand(0,Card.service(2)).hand(1,c(2,1)).stock(Card.service(3),c(3,1));
        e = f.build(); v = act(e,Seat.FIRST,Action.SERVICE,49,1);
        ok(v.ownHand.size() == 1 && v.ownHand.get(0).isService(), "보충 서비스만 남은 손패");
        final MatchEngine chain = e;
        reject(() -> act(chain,Seat.FIRST,Action.PLAY,50,2), "서비스를 일반 제출로 사용할 수 없음");
        v = act(e,Seat.FIRST,Action.SERVICE,50,2);
        ok(v.ownHand.get(0).id() == c(3,1).id() && v.turn == Seat.FIRST, "서비스 연속 사용 후 정상패 보충");

        f = new Fixture().hand(0,c(1,4)).hand(1,c(2,1)).floor(c(1,1),c(1,2),c(1,3));
        f.owners.put(1,Seat.FIRST); f.emptyStock = true;
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(1,4).id(),1);
        ok(has(v,"쓸") && v.events.stream().anyMatch(s -> s.contains("요청 3장")), "자뻑+쓸 보상3장");

        f = new Fixture().hand(0,c(1,1),c(1,2),c(1,3),c(4,1),c(4,2),c(4,3))
                .hand(1,c(2,1),c(5,1)).floor(c(1,4),c(4,4)).stock(c(3,1),c(6,1),c(7,1));
        e = f.build(); act(e,Seat.FIRST,Action.BOMB,c(1,1).id(),1);
        act(e,Seat.SECOND,Action.PLAY,c(2,1).id(),2);
        v = act(e,Seat.FIRST,Action.BOMB,c(4,1).id(),3);
        ok(v.ownTokens == 4 && v.ownBombs == 2 && v.ownShakes == 0, "폭탄 두 번 기회4회 누적");

        f = new Fixture().hand(1,c(2,1)).floor(c(1,1)).stock(c(1,2)); f.tokens = 1;
        e = f.build(); v = act(e,Seat.FIRST,Action.DECK_ONLY,0,1);
        ok(v.ownTokens == 0 && !has(v,"쪽") && !has(v,"따닥") && v.ownCaptures.size() == 2,
                "손패 없이 기회 사용, 새 쪽·따닥 없음");

        f = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).floor(c(1,2),c(1,3)).stock(c(3,1));
        e = f.build(); act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        e.forfeit(Seat.FIRST); e.checkConservation();
        ok(e.result().end == End.FORFEIT, "손패 매칭 선택 중 기권·50장 보존");
        f = new Fixture().hand(0,c(1,1)).hand(1,c(2,1)).floor(c(3,1),c(3,2)).stock(c(3,3));
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(1,1).id(),1);
        ok(v.phase == Phase.STOCK_CHOICE, "더미 두 후보 선택 대기");
        e.expire(30001); e.checkConservation();
        ok(e.result().winner == Seat.SECOND, "더미 매칭 시간 초과 즉시 기권");

        // 고 후 재선택이 가능할 때 역고 선언자를 기록한다.
        f = new Fixture().hand(0,c(4,1)).hand(1,c(5,1)).floor(c(6,1)).stock(c(7,1));
        f.capture(0,c(1,1),c(3,1),c(8,1),c(11,1),c(1,2),c(2,2),c(3,2)); f.otherGo = 1;
        e = f.build(); act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1); act(e,Seat.FIRST,Action.GO,0,2);
        Object[] players = (Object[])get(get(e,"state"),"players");
        ok((Boolean)get(players[0],"inverseGo"), "상대 고 이후 고 선언은 역고");

        // 일반 승리로 파산하면 방을 닫고 두 플레이어가 방을 떠난다.
        f = new Fixture().hand(0,c(4,1)).floor(c(6,1)); f.emptyStock = true;
        f.money1 = 50000; f.capture(0,c(1,1),c(3,1),c(8,1),c(11,1),c(1,2),c(2,2),c(3,2));
        e = f.build(); RoomRules room = new RoomRules(new Random(1));
        room.join(Seat.FIRST,1000000); room.join(Seat.SECOND,50000);
        set(room,"game",e); set(room,"status",RoomRules.Status.PLAYING);
        room.act(Seat.FIRST,1,0,Action.PLAY,c(4,1).id(),1);
        ok(room.status() == RoomRules.Status.CLOSED && !room.present(Seat.FIRST) && room.money(Seat.SECOND) == 0,
                "정상 파산 방 종료·잔액0·양쪽 이동");
        f = new Fixture().hand(0,c(4,1)).floor(c(6,1)).stock(c(7,1),Card.service(2)); f.emptyStock = true;
        e = f.build(); v = act(e,Seat.FIRST,Action.PLAY,c(4,1).id(),1);
        ok(v.result.end == End.DRAW && v.stockCount == 1 && v.ownCaptures.isEmpty(),
                "행동 소진 뒤 맨 아래 서비스는 획득·피 이동 없이 현재 점수 종료");
        System.out.println("PASS: 서비스·폭탄 누적·선택 중 기권·역고·파산 경계 조건");
    }

    /** 실제 API로만 2,000판을 완주한다. 매 행동 50장 보존·공개 범위·잔액 보존을 확인한다. */
    private static void randomGames(int count) throws Exception {
        EnumMap<End,Integer> endings = new EnumMap<>(End.class);
        for (int seed = 0; seed < count; seed++) {
            Random r = new Random(seed);
            MatchEngine e = new MatchEngine(1,seed % 2 == 0 ? Seat.FIRST : Seat.SECOND,
                    DeckManager.shuffleAndDeal(r),1000000,1000000,seed % 4,0);
            int actions = 0;
            while (e.result() == null) {
                if (++actions > 250) throw new AssertionError("진행 정체 seed=" + seed);
                View v = e.viewFor(e.viewFor(Seat.FIRST).turn);
                Action action; int id = 0;
                switch (v.phase) {
                    case HAND_CHOICE: case STOCK_CHOICE:
                        action = Action.SELECT; id = v.choices.get(r.nextInt(v.choices.size())).id(); break;
                    case GUKJIN: action = r.nextBoolean() ? Action.GUKJIN_PI : Action.GUKJIN_ANIMAL; break;
                    case GO_STOP: action = r.nextInt(4) == 0 ? Action.STOP : Action.GO; break;
                    case PLAY:
                        if (v.ownTokens > 0 && (v.ownHand.isEmpty() || r.nextBoolean())) { action = Action.DECK_ONLY; break; }
                        Card card = v.ownHand.get(r.nextInt(v.ownHand.size())); id = card.id();
                        action = card.isService() ? Action.SERVICE : Action.PLAY;
                        if (!card.isService() && SpecialRules.month(v.ownHand,card.month()).size() == 3) {
                            int floorCount = SpecialRules.month(v.floor,card.month()).size();
                            if (floorCount == 1) action = Action.BOMB;
                            else if (floorCount == 0 && !v.ownShakenMonths.contains(card.month())) action = Action.SHAKE;
                        }
                        break;
                    default: throw new AssertionError("예상치 못한 상태");
                }
                try { e.submit(v.turn,1,v.version,action,id,actions); }
                catch (RuntimeException error) { throw new AssertionError("seed="+seed+" action="+actions+" "+action+" "+id,error); }
                e.checkConservation();
                View other = e.viewFor(MatchEngine.other(v.turn));
                ok(other.choices.isEmpty(), "상대 선택 후보 비공개");
            }
            Result result = e.result();
            ok(result.money(Seat.FIRST) + result.money(Seat.SECOND) == 2000000, "잔액 합계 보존");
            ok(result.money(Seat.FIRST) >= 0 && result.money(Seat.SECOND) >= 0, "음수 잔액 없음");
            if (result.end == End.DRAW) {
                @SuppressWarnings("unchecked") Collection<Card> remaining = (Collection<Card>)get(get(e,"state"),"stock");
                ok(remaining.stream().allMatch(Card::isService), "나가리 후 정상 월 더미패가 남으면 안 됨 seed="+seed);
            }
            e.checkConservation();
            endings.put(result.end,endings.getOrDefault(result.end,0)+1);
        }
        System.out.println("PASS: 무작위 완주 종료 분포 " + endings);
    }
    private static View act(MatchEngine e, Seat seat, Action action, int id, long now) {
        return e.submit(seat,e.roundId(),e.version(),action,id,now);
    }
    private static boolean has(View v, String text) { return v.events.stream().anyMatch(s -> s.contains(text)); }

    /** 테스트에서만 사용하는 상태 배치 도구. 미사용 카드는 더미 또는 상대 획득패에 보관해 50장을 유지한다. */
    private static final class Fixture {
        @SuppressWarnings("unchecked") final List<Card>[] hands = new List[] {cards(),cards()};
        @SuppressWarnings("unchecked") final List<Card>[] captures = new List[] {cards(),cards()};
        final List<Card> floor = cards(), stock = cards();
        final Map<Integer,Seat> owners = new HashMap<>();
        boolean emptyStock; int go,lastGoBase,ppeokCount,draws,tokens,otherGo;
        long money0 = 1000000, money1 = 1000000;
        Fixture hand(int seat, Card... cards) { hands[seat].addAll(Arrays.asList(cards)); return this; }
        Fixture capture(int seat, Card... cards) { captures[seat].addAll(Arrays.asList(cards)); return this; }
        Fixture floor(Card... cards) { floor.addAll(Arrays.asList(cards)); return this; }
        Fixture stock(Card... cards) { stock.addAll(Arrays.asList(cards)); return this; }
        MatchEngine build() throws Exception {
            MatchEngine e = new MatchEngine(1,Seat.FIRST,DeckManager.shuffleAndDeal(new Random(1)),money0,money1,draws,0);
            Object state = get(e,"state"); Object[] players = (Object[])get(state,"players");
            Set<Integer> ids = new HashSet<>();
            for (List<Card> list : Arrays.asList(hands[0],hands[1],captures[0],captures[1],floor,stock))
                for (Card c : list) if (!ids.add(c.id())) throw new AssertionError("fixture 중복 " + c.id());
            List<Card> all = DeckManager.standardCards(); all.add(Card.service(2)); all.add(Card.service(3));
            for (Card c : all) if (!ids.contains(c.id())) (emptyStock ? captures[1] : stock).add(c);
            for (int i = 0; i < 2; i++) { replace(players[i],"hand",hands[i]); replace(players[i],"captures",captures[i]); }
            replace(state,"floor",floor); replace(state,"stock",stock);
            set(players[0],"go",go); set(players[0],"lastGoBase",lastGoBase); set(players[0],"ppeokCount",ppeokCount);
            set(players[0],"tokens",tokens); set(players[1],"go",otherGo);
            @SuppressWarnings("unchecked") Map<Integer,Seat> map = (Map<Integer,Seat>)get(state,"ppeokOwners"); map.putAll(owners);
            e.checkConservation();
            return e;
        }
    }
    private static Object get(Object target,String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    @SuppressWarnings("unchecked") private static void replace(Object target,String name,Collection<Card> cards) throws Exception {
        Collection<Card> values = (Collection<Card>)get(target,name); values.clear(); values.addAll(cards);
    }
}
