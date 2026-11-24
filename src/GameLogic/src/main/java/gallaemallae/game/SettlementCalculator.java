package gallaemallae.game;

import java.math.BigInteger;

/**
 * 정산 기준 점수와 배율에서 실제 지급액을 계산한다.
 * 승패·박 조건 판정과 실제 잔액 변경은 호출자가 담당한다.
 */
public final class SettlementCalculator {
    public static final long PRICE_PER_POINT = 10_000;

    private SettlementCalculator() {}

    /**
     * 기본 점수에 고 횟수를 더한 뒤 점당 금액과 외부 배율, 고 배율을 곱한다.
     * otherMultipliers에는 고 배율을 제외한 승자에게 적용할 배율을 전달한다.
     */
    public static long normalWin(int baseScore, int goCount, long winnerOpeningBalance,
                                 long loserBalance, long... otherMultipliers) {
        if (baseScore < 7 || goCount < 0) {
            throw new IllegalArgumentException("Invalid winning score or go count");
        }
        // 큰 고 횟수와 중복 배율에서도 정수 범위를 넘지 않도록 BigInteger를 쓴다.
        BigInteger amount = BigInteger.valueOf((long) baseScore + goCount)
                .multiply(BigInteger.valueOf(PRICE_PER_POINT));
        long cap = cap(winnerOpeningBalance, loserBalance);
        for (long multiplier : otherMultipliers) {
            if (multiplier < 1) {
                throw new IllegalArgumentException("Multipliers must be positive");
            }
            amount = amount.multiply(BigInteger.valueOf(multiplier));
        }
        // 1·2고는 배율 1, 3고부터 2의 (고 횟수 - 2)승이다.
        int exponent = Math.max(0, goCount - 2);
        BigInteger ceiling = BigInteger.valueOf(cap);
        // 고 배율만으로 상한을 넘으면 거대한 숫자를 추가로 만들지 않고 상한을 반환한다.
        if (exponent >= ceiling.bitLength()) {
            return cap;
        }
        return amount.shiftLeft(exponent).min(ceiling).longValueExact();
    }

    /** 총통·3뻑·기권의 기준 금액 10만 원에도 양쪽 보유 금액 상한을 적용한다. */
    public static long fixedWin(long winnerOpeningBalance, long loserBalance) {
        return Math.min(10 * PRICE_PER_POINT, cap(winnerOpeningBalance, loserBalance));
    }

    /** 승자는 판 시작 보유액까지만 딸 수 있고, 패자는 현재 잔액까지만 지급할 수 있다. */
    private static long cap(long winnerOpeningBalance, long loserBalance) {
        if (winnerOpeningBalance < 0 || loserBalance < 0) {
            throw new IllegalArgumentException("Balances cannot be negative");
        }
        return Math.min(winnerOpeningBalance, loserBalance);
    }
}
