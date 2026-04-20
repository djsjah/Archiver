package common.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import common.ArchiveConstants;
import common.arithmetic.ArithmeticDecoder;
import common.arithmetic.ArithmeticEncoder;
import common.io.BitInput;
import common.io.BitOutput;

public class SparseHeaderCoder {
    public record SparsePrepared(
            int q,
            long encodedBits,
            byte[] encodedBytes
    ) { }

    public static SparsePrepared prepare(int[] frequencies) throws IOException {
        int q = 0;
        for (int value : frequencies) if (value > 0) q++;

        if (q == 0) return new SparsePrepared(0, 0, new byte[0]);

        int[] symbols = new int[q];
        int[] counts = new int[q];

        int pos = 0;
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            int count = frequencies[symbol];
            if (count > 0) {
                symbols[pos] = symbol;
                counts[pos] = count;
                pos++;
            }
        }

        sortByCountDesc(symbols, counts);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (BitOutput bitOutput = new BitOutput(buffer)) {
            ArithmeticEncoder encoder = new ArithmeticEncoder(bitOutput);
            encodeSortedCounts(encoder, counts);
            encodeSymbols(encoder, symbols);
            encoder.finish();

            long bitCount = bitOutput.getBitsWritten();
            bitOutput.flush();
            return new SparsePrepared(q, bitCount, buffer.toByteArray());
        }
    }

    public static int[] decode(byte[] encodedBytes, int dataLength, int q) throws IOException {
        int[] frequencies = new int[ArchiveConstants.BYTE_ALPHABET_SIZE];
        if (q == 0) return frequencies;

        try (BitInput bitInput = new BitInput(new ByteArrayInputStream(encodedBytes))) {
            ArithmeticDecoder decoder = new ArithmeticDecoder(bitInput);

            int[] counts = decodeSortedCounts(decoder, dataLength, q);
            int[] symbols = decodeSymbols(decoder, q);

            for (int i = 0; i < q; i++) frequencies[symbols[i]] = counts[i];
        }

        return frequencies;
    }

    private static void encodeSortedCounts(ArithmeticEncoder encoder, int[] counts) throws IOException {
        int remainingSum = 0;
        for (int value : counts) remainingSum += value;

        int partsLeft = counts.length;
        int previous = remainingSum;

        for (int i = 0; i < counts.length - 1; i++) {
            int minValue = ceilDiv(remainingSum, partsLeft);
            int maxValue = Math.min(previous, remainingSum - (partsLeft - 1));

            int value = counts[i];
            if (value < minValue || value > maxValue)
                throw new IllegalStateException("Bad sparse header state");

            int total = maxValue - minValue + 1;
            int left = value - minValue;
            encoder.encode(left, left + 1, total);

            remainingSum -= value;
            partsLeft--;
            previous = value;
        }
    }

    private static int[] decodeSortedCounts(ArithmeticDecoder decoder, int dataLength, int q) throws IOException {
        int[] counts = new int[q];
        int remainingSum = dataLength;
        int partsLeft = q;
        int previous = dataLength;

        for (int i = 0; i < q - 1; i++) {
            int minValue = ceilDiv(remainingSum, partsLeft);
            int maxValue = Math.min(previous, remainingSum - (partsLeft - 1));

            int total = maxValue - minValue + 1;
            int target = (int) decoder.getTarget(total);
            int value = minValue + target;

            decoder.removeSymbol(target, target + 1, total);

            counts[i] = value;
            remainingSum -= value;
            partsLeft--;
            previous = value;
        }

        counts[q - 1] = remainingSum;
        return counts;
    }

    private static void encodeSymbols(ArithmeticEncoder encoder, int[] symbols) throws IOException {
        int[] pool = new int[ArchiveConstants.BYTE_ALPHABET_SIZE];
        for (int i = 0; i < ArchiveConstants.BYTE_ALPHABET_SIZE; i++) pool[i] = i;

        int poolSize = ArchiveConstants.BYTE_ALPHABET_SIZE;

        for (int symbol : symbols) {
            int index = indexOf(pool, poolSize, symbol);
            if (index < 0)
                throw new IllegalStateException("Symbol not found in sparse header pool");

            encoder.encode(index, index + 1, poolSize);
            removeAt(pool, poolSize, index);
            poolSize--;
        }
    }

    private static int[] decodeSymbols(ArithmeticDecoder decoder, int q) throws IOException {
        int[] symbols = new int[q];
        int[] pool = new int[ArchiveConstants.BYTE_ALPHABET_SIZE];
        for (int i = 0; i < ArchiveConstants.BYTE_ALPHABET_SIZE; i++) pool[i] = i;

        int poolSize = ArchiveConstants.BYTE_ALPHABET_SIZE;

        for (int i = 0; i < q; i++) {
            int index = (int) decoder.getTarget(poolSize);
            decoder.removeSymbol(index, index + 1, poolSize);

            symbols[i] = pool[index];
            removeAt(pool, poolSize, index);
            poolSize--;
        }

        return symbols;
    }

    private static void sortByCountDesc(int[] symbols, int[] counts) {
        Integer[] order = new Integer[counts.length];
        for (int i = 0; i < order.length; i++) order[i] = i;

        Arrays.sort(order, (a, b) -> {
            if (counts[a] != counts[b]) return Integer.compare(counts[b], counts[a]);
            return Integer.compare(symbols[a], symbols[b]);
        });

        int[] sortedSymbols = new int[symbols.length];
        int[] sortedCounts = new int[counts.length];

        for (int i = 0; i < order.length; i++) {
            sortedSymbols[i] = symbols[order[i]];
            sortedCounts[i] = counts[order[i]];
        }

        System.arraycopy(sortedSymbols, 0, symbols, 0, symbols.length);
        System.arraycopy(sortedCounts, 0, counts, 0, counts.length);
    }

    private static int indexOf(int[] array, int size, int value) {
        for (int i = 0; i < size; i++) if (array[i] == value) return i;
        return -1;
    }

    private static void removeAt(int[] array, int size, int index) {
        for (int i = index + 1; i < size; i++) array[i - 1] = array[i];
    }

    private static int ceilDiv(int a, int b) { return (a + b - 1) / b; }
}
