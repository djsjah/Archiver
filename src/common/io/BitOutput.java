package common.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

public class BitOutput implements Closeable, Flushable {
    private final OutputStream outputStream;

    private int currentByte;
    private int bitsFilled;
    private long bitsWritten;

    public BitOutput(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void writeBit(int bit) throws IOException {
        currentByte = (currentByte << 1) | (bit & 1);
        bitsFilled++;
        bitsWritten++;

        if (bitsFilled == 8) {
            outputStream.write(currentByte);
            currentByte = 0;
            bitsFilled = 0;
        }
    }

    public void writeBits(long value, int bitCount) throws IOException {
        for (int bit = bitCount - 1; bit >= 0; bit--) { writeBit((int) ((value >>> bit) & 1L)); }
    }

    public long getBitsWritten() { return bitsWritten; }

    public long getPaddingBitsIfFlushedNow() { return bitsFilled == 0 ? 0 : (8 - bitsFilled); }

    public long getTotalBytesIfFlushedNow() { return (bitsWritten + getPaddingBitsIfFlushedNow()) / 8; }

    @Override
    public void flush() throws IOException {
        if (bitsFilled > 0) {
            currentByte <<= (8 - bitsFilled);
            outputStream.write(currentByte);
            currentByte = 0;
            bitsFilled = 0;
        }
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
        outputStream.close();
    }
}
