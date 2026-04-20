package common.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class BitInput implements Closeable {
    private final InputStream inputStream;
    private int currentByte = 0;
    private int bitsRemaining = 0;

    public BitInput(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public int readBit() throws IOException {
        if (bitsRemaining == 0) {
            currentByte = inputStream.read();
            if (currentByte == -1) return 0;
            bitsRemaining = 8;
        }

        bitsRemaining--;
        return (currentByte >>> bitsRemaining) & 1;
    }

    public long readBits(int bitCount) throws IOException {
        long value = 0;
        for (int i = 0; i < bitCount; i++) value = (value << 1) | readBit();
        return value;
    }

    @Override
    public void close() throws IOException { inputStream.close(); }
}
