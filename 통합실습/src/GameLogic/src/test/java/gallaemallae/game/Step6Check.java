package gallaemallae.game;

import java.util.*;

/** 6단계 점수 계산 독립 검사. 12월 띠 제외와 국진의 단일 분류를 확인한다. */
public final class Step6Check {
    public static void main(String[] args) {
        List<Card> cards = Arrays.asList(Card.monthly(4,2),Card.monthly(5,2),Card.monthly(12,3));
        check(ScoreCalculator.calculate(cards,false).chodan == 0, "12월 초단 제외");
        cards = Arrays.asList(Card.monthly(4,2),Card.monthly(5,2),Card.monthly(7,2));
        check(ScoreCalculator.calculate(cards,false).chodan == 3, "4·5·7 초단");
        cards = Arrays.asList(Card.monthly(2,1),Card.monthly(4,1),Card.monthly(8,2),Card.monthly(9,1));
        check(ScoreCalculator.calculate(cards,false).godori == 5, "고도리");
        check(ScoreCalculator.calculate(cards,true).animalCount == 3, "국진 열끗 제외");
        check(ScoreCalculator.calculate(cards,true).effectivePi == 2, "국진 유효피2");
        cards = Arrays.asList(Card.monthly(1,1),Card.monthly(3,1),Card.monthly(12,1));
        check(ScoreCalculator.calculate(cards,false).gwang == 2, "비광3광");
        cards = Arrays.asList(Card.monthly(1,1),Card.monthly(3,1),Card.monthly(8,1));
        check(ScoreCalculator.calculate(cards,false).gwang == 3, "일반3광");
        System.out.println("PASS: step6 score components (7 checks)");
    }
    private static void check(boolean valid,String message) { if (!valid) throw new AssertionError(message); }
}
