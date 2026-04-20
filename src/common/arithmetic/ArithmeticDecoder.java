package common.arithmetic;

import java.io.IOException;

import common.io.BitInput;

public class ArithmeticDecoder {
    private static final long TOP = 0x7FFFFFFFL;
    private static final long FIRST_QUARTER = TOP / 4 + 1;
    private static final long HALF = 2 * FIRST_QUARTER;
    private static final long THIRD_QUARTER = 3 * FIRST_QUARTER;

    private final BitInput bitInput;

    private long low = 0;
    private long high = TOP;
    private long code = 0;

    public ArithmeticDecoder(BitInput bitInput) throws IOException {
        this.bitInput = bitInput;
        for (int i = 0; i < 31; i++) code = (code << 1) | bitInput.readBit();
    }

    public long getTarget(long total) {
        long range = high - low + 1;
        return ((code - low + 1) * total - 1) / range;
    }

    public void removeSymbol(long left, long right, long total) throws IOException {
        long range = high - low + 1;
        high = low + (range * right) / total - 1;
        low = low + (range * left) / total;

        while (shouldNormalize()) normalizeOnce();
    }

    private boolean shouldNormalize() {
        if (high < HALF) return true;
        if (low >= HALF) {
            low -= HALF;
            high -= HALF;
            code -= HALF;
            return true;
        }
        if (low >= FIRST_QUARTER && high < THIRD_QUARTER) {
            low -= FIRST_QUARTER;
            high -= FIRST_QUARTER;
            code -= FIRST_QUARTER;
            return true;
        }
        return false;
    }

    private void normalizeOnce() throws IOException {
        low <<= 1;
        high = (high << 1) + 1;
        code = (code << 1) | bitInput.readBit();
    }
}