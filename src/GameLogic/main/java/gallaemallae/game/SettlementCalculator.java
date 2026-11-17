package gallaemallae.game;

import java.math.BigInteger;

public final class SettlementCalculator {
    public static final long PRICE_PER_POINT = 10_000;

    private SettlementCalculator() {}

    /** Rule and score determination supply the winner's base score and applicable factors. */
    public static long normalWin(int baseScore, int goCount, long winnerOpeningBalance,
                                 long loserBalance, long... otherMultipliers) {
        if (baseScore < 7 || goCount < 0) {
            throw new IllegalArgumentException("Invalid winning score or go count");
        }
        BigInteger amount = BigInteger.valueOf((long) baseScore + goCount)
                .multiply(BigInteger.valueOf(PRICE_PER_POINT));
        long cap = cap(winnerOpeningBalance, loserBalance);
        for (long multiplier : otherMultipliers) {
            if (multiplier < 1) {
                throw new IllegalArgumentException("Multipliers must be positive");
            }
            amount = amount.multiply(BigInteger.valueOf(multiplier));
        }
        int exponent = Math.max(0, goCount - 2);
        BigInteger ceiling = BigInteger.valueOf(cap);
        if (exponent >= ceiling.bitLength()) {
            return cap;
        }
        return amount.shiftLeft(exponent).min(ceiling).longValueExact();
    }

    /** Tongtong, three ppeok and forfeit currently share the same fixed amount. */
    public static long fixedWin(long winnerOpeningBalance, long loserBalance) {
        return Math.min(10 * PRICE_PER_POINT, cap(winnerOpeningBalance, loserBalance));
    }

    private static long cap(long winnerOpeningBalance, long loserBalance) {
        if (winnerOpeningBalance < 0 || loserBalance < 0) {
            throw new IllegalArgumentException("Balances cannot be negative");
        }
        return Math.min(winnerOpeningBalance, loserBalance);
    }
}
