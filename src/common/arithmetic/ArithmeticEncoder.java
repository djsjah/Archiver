package common.arithmetic;

import java.io.IOException;

import common.io.BitOutput;

public class ArithmeticEncoder {
    private static final long TOP = 0x7FFFFFFFL;
    private static final long FIRST_QUARTER = TOP / 4 + 1;
    private static final long HALF = 2 * FIRST_QUARTER;
    private static final long THIRD_QUARTER = 3 * FIRST_QUARTER;

    private final BitOutput bitOutput;

    private long low = 0;
    private long high = TOP;
    private long bitsToFollow = 0;

    public ArithmeticEncoder(BitOutput bitOutput) {
        this.bitOutput = bitOutput;
    }

    public void encode(long left, long right, long total) throws IOException {
        long range = high - low + 1;
        high = low + (range * right) / total - 1;
        low = low + (range * left) / total;

        while (true) {
            if (high < HALF) {
                writeBitWithFollow(0);
            }
            else if (low >= HALF) {
                writeBitWithFollow(1);
                low -= HALF;
                high -= HALF;
            }
            else if (low >= FIRST_QUARTER && high < THIRD_QUARTER) {
                bitsToFollow++;
                low -= FIRST_QUARTER;
                high -= FIRST_QUARTER;
            }
            else {
                break;
            }

            low <<= 1;
            high = (high << 1) + 1;
        }
    }

    public void finish() throws IOException {
        bitsToFollow++;
        if (low < FIRST_QUARTER) writeBitWithFollow(0);
        else writeBitWithFollow(1);
    }

    private void writeBitWithFollow(int bit) throws IOException {
        bitOutput.writeBit(bit);
        int oppositeBit = bit ^ 1;
        while (bitsToFollow > 0) {
            bitOutput.writeBit(oppositeBit);
            bitsToFollow--;
        }
    }
}
