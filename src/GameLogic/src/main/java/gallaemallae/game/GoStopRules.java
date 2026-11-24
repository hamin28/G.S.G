package gallaemallae.game;

import java.math.BigInteger;
import java.util.*;
import gallaemallae.game.ScoreCalculator.Score;

/** 7단계: 고 재선택 조건과 정산 근거를 판정한다. 숫자가 커져도 배율이 넘치지 않게 계산한다. */
public final class GoStopRules {
    private GoStopRules() {}

    public static boolean eligible(int baseScore, int goCount, int lastGoBase) {
        return baseScore >= 7 && (goCount == 0 || baseScore > lastGoBase);
    }

    public static Settlement normal(Score winner, Score loser, int goCount, int loserGo,
                                    boolean inverseGo, int shakes, int bombs, int draws,
                                    long winnerOpeningMoney, long loserMoney) {
        if (winner.total < 7 || goCount < 0 || loserGo < 0 || shakes < 0 || bombs < 0 || draws < 0)
            throw new IllegalArgumentException("정산 조건 오류");
        Map<String, BigInteger> factors = new LinkedHashMap<>();
        factor(factors, "고", Math.max(0, goCount - 2));
        factor(factors, "역고", inverseGo ? 1 : 0);
        factor(factors, "흔들기", shakes);
        factor(factors, "폭탄", bombs);
        factor(factors, "나가리", draws);
        factor(factors, "피박", winner.pi > 0 && loser.effectivePi <= 7 ? 1 : 0);
        factor(factors, "광박", winner.gwang > 0 && loser.gwangCount == 0 ? 1 : 0);
        factor(factors, "멍박", winner.animals + winner.godori > 0 && loser.animalCount == 0 ? 1 : 0);
        factor(factors, "고박", loserGo > 0 ? 1 : 0);
        BigInteger amount = BigInteger.valueOf((long) winner.total + goCount).multiply(BigInteger.valueOf(10000));
        for (BigInteger multiplier : factors.values()) amount = amount.multiply(multiplier);
        return new Settlement(winner.total, goCount, factors, amount, winnerOpeningMoney, loserMoney);
    }

    public static Settlement fixed(long winnerOpeningMoney, long loserMoney) {
        return new Settlement(10, 0, Collections.emptyMap(), BigInteger.valueOf(100000), winnerOpeningMoney, loserMoney);
    }

    private static void factor(Map<String, BigInteger> values, String name, int exponent) {
        // 실제 판의 카드 수로 고·흔들기·폭탄은 작고, 나가리는 연속 판 수만큼 누적한다.
        values.put(name, BigInteger.ONE.shiftLeft(exponent));
    }

    public static final class Settlement {
        public final int baseScore, goBonus;
        public final Map<String, BigInteger> factors;
        public final BigInteger calculatedAmount;
        public final long paidAmount;
        private Settlement(int base, int go, Map<String, BigInteger> factors, BigInteger amount, long opening, long loser) {
            if (opening < 0 || loser < 0) throw new IllegalArgumentException("잔액은 음수일 수 없음");
            baseScore = base; goBonus = go;
            this.factors = Collections.unmodifiableMap(new LinkedHashMap<>(factors));
            calculatedAmount = amount;
            paidAmount = amount.min(BigInteger.valueOf(Math.min(opening, loser))).longValueExact();
        }
    }
}
