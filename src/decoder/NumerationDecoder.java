package decoder;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import common.ArchiveConstants;
import common.model.SparseHeaderCoder;
import common.arithmetic.ArithmeticDecoder;
import common.io.BitInput;
import common.model.CompositionCoder;
import common.model.FenwickTree;

public class NumerationDecoder {
    public void decode(Path inputFile, Path outputFile) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (BitInput bitInput = new BitInput(
                new BufferedInputStream(new FileInputStream(inputFile.toFile())));
             BufferedOutputStream outputStream = new BufferedOutputStream(
                     new FileOutputStream(outputFile.toFile()))) {

            int fileSignature = (int) bitInput.readBits(32);
            if (fileSignature != ArchiveConstants.FILE_SIGNATURE) throw new IOException("Wrong file format");

            int mode = (int) bitInput.readBits(3);
            int totalLength = (int) bitInput.readBits(32);

            if (totalLength == 0) return;

            int blockSize = ArchiveConstants.getBlockSize(mode, totalLength);
            int remaining = totalLength;

            while (remaining > 0) {
                int blockLength = Math.min(blockSize, remaining);
                int[] frequencies = readBlockHeader(bitInput, blockLength);

                int payloadBitCount = (int) bitInput.readBits(32);
                byte[] payloadBytes = readStoredBits(bitInput, payloadBitCount);

                decodeBlockPayload(payloadBytes, blockLength, frequencies, outputStream);
                remaining -= blockLength;
            }
        }
    }

    private int[] readBlockHeader(BitInput bitInput, int blockLength) throws IOException {
        int headerMode = bitInput.readBit();

        if (headerMode == ArchiveConstants.HEADER_MODE_RANK) {
            int rankBitCount = (int) bitInput.readBits(32);

            BigInteger rank = BigInteger.ZERO;
            for (int i = 0; i < rankBitCount; i++) {
                rank = rank.shiftLeft(1);
                if (bitInput.readBit() == 1) rank = rank.add(BigInteger.ONE);
            }

            return CompositionCoder.unrank(rank, blockLength, ArchiveConstants.BYTE_ALPHABET_SIZE);
        }

        if (headerMode == ArchiveConstants.HEADER_MODE_SPARSE) {
            int q = (int) bitInput.readBits(9);
            int sparseBitCount = (int) bitInput.readBits(32);
            byte[] sparseBytes = readStoredBits(bitInput, sparseBitCount);

            return SparseHeaderCoder.decode(sparseBytes, blockLength, q);
        }

        throw new IOException("Unknown block header mode");
    }

    private void decodeBlockPayload(
            byte[] payloadBytes,
            int blockLength,
            int[] frequencies,
            BufferedOutputStream outputStream
    ) throws IOException {
        if (blockLength == 0) return;

        try (BitInput payloadInput = new BitInput(new ByteArrayInputStream(payloadBytes))) {
            ArithmeticDecoder arithmeticDecoder = new ArithmeticDecoder(payloadInput);
            FenwickTree fenwickTree = new FenwickTree(ArchiveConstants.BYTE_ALPHABET_SIZE);

            int[] currentFrequencies = frequencies.clone();
            int total = blockLength;

            for (int symbol = 0; symbol < ArchiveConstants.BYTE_ALPHABET_SIZE; symbol++)
                if (currentFrequencies[symbol] > 0) fenwickTree.add(symbol, currentFrequencies[symbol]);

            for (int i = 0; i < blockLength; i++) {
                long target = arithmeticDecoder.getTarget(total);
                int symbol = fenwickTree.findByCumulative((int) target);

                int left = fenwickTree.prefixSum(symbol);
                int right = left + currentFrequencies[symbol];

                arithmeticDecoder.removeSymbol(left, right, total);
                outputStream.write(symbol);

                fenwickTree.add(symbol, -1);
                currentFrequencies[symbol]--;
                total--;
            }
        }
    }

    private byte[] readStoredBits(BitInput bitInput, int bitCount) throws IOException {
        int byteCount = (bitCount + 7) >>> 3;
        byte[] bytes = new byte[byteCount];

        for (int i = 0; i < bitCount; i++) {
            int bit = bitInput.readBit();
            if (bit != 0) {
                int byteIndex = i >>> 3;
                int bitIndex = 7 - (i & 7);
                bytes[byteIndex] |= (byte) (1 << bitIndex);
            }
        }
        return bytes;
    }
}
