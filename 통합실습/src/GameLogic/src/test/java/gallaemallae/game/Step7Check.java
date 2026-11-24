package gallaemallae.game;

import java.util.*;
import gallaemallae.game.ScoreCalculator.Score;
import gallaemallae.game.GoStopRules.Settlement;

/** 7단계 고 재선택·배율·상한의 독립 검사. 선택 대기와 마지막 턴 연결은 8단계에서 검사한다. */
public final class Step7Check {
    public static void main(String[] args) {
        check(GoStopRules.eligible(7,0,0), "첫7점");
        check(!GoStopRules.eligible(7,1,7), "고 보너스는 기본점수 증가 아님");
        check(GoStopRules.eligible(8,1,7), "1점 상승 재선택");
        Score winner = ScoreCalculator.calculate(Arrays.asList(Card.monthly(1,1),Card.monthly(3,1),
                Card.monthly(8,1),Card.monthly(11,1),Card.monthly(1,2),Card.monthly(2,2),Card.monthly(3,2)),false);
        Score loser = ScoreCalculator.calculate(Collections.emptyList(),false);
        Settlement s = GoStopRules.normal(winner,loser,3,1,true,1,2,2,900000,400000);
        check(s.factors.get("고").intValue() == 2, "3고2배");
        check(s.factors.get("역고").intValue() == 2 && s.factors.get("폭탄").intValue() == 4, "역고·폭탄");
        check(s.factors.get("나가리").intValue() == 4 && s.factors.get("고박").intValue() == 2, "나가리·고박");
        check(s.paidAmount == 400000, "패자 잔액 상한");
        check(GoStopRules.fixed(20000,50000).paidAmount == 20000, "고정 승리 승자 상한");
        check(GoStopRules.fixed(1000000,1000000).factors.isEmpty(), "고정 배율 제외");
        System.out.println("PASS: step7 go-stop and settlement (9 checks)");
    }
    private static void check(boolean valid,String message) { if (!valid) throw new AssertionError(message); }
}
