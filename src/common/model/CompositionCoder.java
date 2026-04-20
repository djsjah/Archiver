package common.model;

import java.math.BigInteger;

public class CompositionCoder {
    public static BigInteger countCompositions(int n, int alphabetSize) {
        return combination((long) n + alphabetSize - 1, alphabetSize - 1);
    }

    public static BigInteger rank(int[] frequencies, int n, int alphabetSize) {
        long positionsLeft = (long) n + alphabetSize - 1;
        int separatorsLeft = alphabetSize - 1;
        BigInteger rank = BigInteger.ZERO;

        for (int symbol = 0; symbol < alphabetSize - 1; symbol++) {
            positionsLeft -= frequencies[symbol];
            rank = rank.add(combination(positionsLeft - 1, separatorsLeft));
            positionsLeft -= 1;
            separatorsLeft--;
        }

        return rank;
    }

    public static int[] unrank(BigInteger rank, int n, int alphabetSize) {
        int[] frequencies = new int[alphabetSize];
        int remaining = n;

        for (int symbol = 0; symbol < alphabetSize - 1; symbol++) {
            int symbolsLeft = alphabetSize - symbol;
            int frequency = findFrequency(rank, remaining, symbolsLeft);

            frequencies[symbol] = frequency;
            rank = rank.subtract(countBefore(remaining, symbolsLeft, frequency));
            remaining -= frequency;
        }

        frequencies[alphabetSize - 1] = remaining;
        return frequencies;
    }

    private static int findFrequency(BigInteger rank, int remaining, int symbolsLeft) {
        int left = 0;
        int right = remaining;
        int answer = remaining;

        while (left <= right) {
            int middle = (left + right) >>> 1;
            BigInteger before = countBefore(remaining, symbolsLeft, middle);

            if (before.compareTo(rank) <= 0) {
                answer = middle;
                right = middle - 1;
            } else {
                left = middle + 1;
            }
        }

        return answer;
    }

    private static BigInteger countBefore(int remaining, int symbolsLeft, int frequency) {
        return combination((long) remaining - frequency + symbolsLeft - 2, symbolsLeft - 1);
    }

    public static BigInteger combination(long n, int k) {
        if (k < 0 || n < 0 || k > n) {
            return BigInteger.ZERO;
        }
        if (k == 0 || k == n) {
            return BigInteger.ONE;
        }

        k = Math.min(k, (int) n - k);
        BigInteger result = BigInteger.ONE;

        for (int i = 1; i <= k; i++) {
            result = result.multiply(BigInteger.valueOf(n - k + i));
            result = result.divide(BigInteger.valueOf(i));
        }

        return result;
    }
}
