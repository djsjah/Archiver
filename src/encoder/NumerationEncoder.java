package encoder;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import common.ArchiveConstants;
import common.model.SparseHeaderCoder;
import common.arithmetic.ArithmeticEncoder;
import common.io.BitOutput;
import common.model.CompositionCoder;
import common.model.FenwickTree;
import stats.EncodeStats;

public class NumerationEncoder {
    private record HeaderPlan(
            int mode,
            int rankBitCount,
            BigInteger rank,
            SparseHeaderCoder.SparsePrepared sparsePrepared
    ) { }

    private record BlockEncoded(
            HeaderPlan headerPlan,
            long payloadBitCount,
            byte[] payloadBytes
    ) { }

    private record CandidateResult(
            int mode,
            byte[] archiveBytes,
            long headerBits,
            long payloadBits,
            long totalBits,
            long paddingBits,
            long totalBytes
    ) { }

    public EncodeStats encode(Path inputFile, Path outputFile) throws IOException {
        byte[] data = Files.readAllBytes(inputFile);
        CandidateResult best = buildBestCandidate(data);

        Path parent = outputFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile.toFile()))) {
            out.write(best.archiveBytes());
        }

        return new EncodeStats(
                best.headerBits(),
                best.payloadBits(),
                best.totalBits(),
                best.paddingBits(),
                best.totalBytes()
        );
    }

    private CandidateResult buildBestCandidate(byte[] data) throws IOException {
        List<Callable<CandidateResult>> tasks = new ArrayList<>();

        for (int mode = ArchiveConstants.MODE_WHOLE; mode <= ArchiveConstants.MODE_BLOCK_16K; mode++) {
            int blockSize = ArchiveConstants.getBlockSize(mode, data.length);
            if (mode != ArchiveConstants.MODE_WHOLE && data.length > 0 && blockSize >= data.length) continue;

            final int currentMode = mode;
            tasks.add(() -> buildCandidate(data, currentMode));
        }

        ExecutorService executor = createExecutor(tasks.size());

        try {
            List<Future<CandidateResult>> futures = executor.invokeAll(tasks);

            CandidateResult best = null;
            for (Future<CandidateResult> future : futures) {
                CandidateResult current;
                try {
                    current = future.get();
                } catch (ExecutionException ex) {
                    throw new IOException("Failed to build candidate", ex.getCause());
                }

                if (best == null
                        || current.totalBits() < best.totalBits()
                        || (current.totalBits() == best.totalBits() && current.totalBytes() < best.totalBytes())
                ) {
                    best = current;
                }
            }

            return best;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while building candidates", ex);
        } finally {
            executor.shutdown();
        }
    }

    private ExecutorService createExecutor(int taskCount) {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int threadCount = Math.max(1, Math.min(cpuCount, taskCount));
        return Executors.newFixedThreadPool(threadCount);
    }

    private CandidateResult buildCandidate(byte[] data, int mode) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             BitOutput bitOutput = new BitOutput(buffer)) {

            long headerBits = 0;
            long payloadBits = 0;

            long beforeTopHeader = bitOutput.getBitsWritten();
            writeTopHeader(bitOutput, mode, data.length);
            long afterTopHeader = bitOutput.getBitsWritten();
            headerBits += afterTopHeader - beforeTopHeader;

            if (data.length > 0) {
                int blockSize = ArchiveConstants.getBlockSize(mode, data.length);

                for (int offset = 0; offset < data.length; offset += blockSize) {
                    int length = Math.min(blockSize, data.length - offset);
                    byte[] block = Arrays.copyOfRange(data, offset, offset + length);

                    BlockEncoded encodedBlock = encodeBlock(block);

                    long beforeBlockHeader = bitOutput.getBitsWritten();
                    writeBlockHeader(bitOutput, encodedBlock.headerPlan());
                    bitOutput.writeBits(encodedBlock.payloadBitCount() & 0xFFFFFFFFL, 32);
                    long afterBlockHeader = bitOutput.getBitsWritten();
                    headerBits += afterBlockHeader - beforeBlockHeader;

                    long beforePayload = bitOutput.getBitsWritten();
                    writeStoredBits(bitOutput, encodedBlock.payloadBytes(), encodedBlock.payloadBitCount());
                    long afterPayload = bitOutput.getBitsWritten();
                    payloadBits += afterPayload - beforePayload;
                }
            }

            long totalBits = bitOutput.getBitsWritten();
            long paddingBits = bitOutput.getPaddingBitsIfFlushedNow();
            long totalBytes = bitOutput.getTotalBytesIfFlushedNow();

            bitOutput.flush();
            return new CandidateResult(
                    mode,
                    buffer.toByteArray(),
                    headerBits,
                    payloadBits,
                    totalBits,
                    paddingBits,
                    totalBytes
            );
        }
    }

    private void writeTopHeader(BitOutput bitOutput, int mode, int length) throws IOException {
        bitOutput.writeBits(ArchiveConstants.FILE_SIGNATURE & 0xFFFFFFFFL, 32);
        bitOutput.writeBits(mode, 3);
        bitOutput.writeBits(length & 0xFFFFFFFFL, 32);
    }

    private BlockEncoded encodeBlock(byte[] block) throws IOException {
        int[] frequencies = countFrequencies(block);
        HeaderPlan headerPlan = chooseHeaderPlan(block.length, frequencies);

        byte[] payloadBytes;
        long payloadBitCount;

        try (ByteArrayOutputStream payloadBuffer = new ByteArrayOutputStream();
             BitOutput payloadOutput = new BitOutput(payloadBuffer)) {

            if (block.length > 0) {
                ArithmeticEncoder arithmeticEncoder = new ArithmeticEncoder(payloadOutput);
                FenwickTree fenwickTree = new FenwickTree(ArchiveConstants.BYTE_ALPHABET_SIZE);

                int[] currentFrequencies = frequencies.clone();
                int total = block.length;

                for (int symbol = 0; symbol < ArchiveConstants.BYTE_ALPHABET_SIZE; symbol++) {
                    if (currentFrequencies[symbol] > 0) fenwickTree.add(symbol, currentFrequencies[symbol]);
                }

                for (byte value : block) {
                    int symbol = value & 0xFF;
                    int symbolFrequency = currentFrequencies[symbol];

                    if (symbolFrequency <= 0)
                        throw new IllegalStateException("Invalid model state for symbol " + symbol);

                    int left = fenwickTree.prefixSum(symbol);
                    int right = left + symbolFrequency;

                    arithmeticEncoder.encode(left, right, total);

                    fenwickTree.add(symbol, -1);
                    currentFrequencies[symbol]--;
                    total--;
                }

                arithmeticEncoder.finish();
            }

            payloadBitCount = payloadOutput.getBitsWritten();
            payloadOutput.flush();
            payloadBytes = payloadBuffer.toByteArray();
        }

        return new BlockEncoded(headerPlan, payloadBitCount, payloadBytes);
    }

    private int[] countFrequencies(byte[] data) {
        int[] frequencies = new int[ArchiveConstants.BYTE_ALPHABET_SIZE];
        for (byte value : data) frequencies[value & 0xFF]++;
        return frequencies;
    }

    private HeaderPlan chooseHeaderPlan(int length, int[] frequencies) throws IOException {
        BigInteger stateCount = CompositionCoder.countCompositions(length, ArchiveConstants.BYTE_ALPHABET_SIZE);
        BigInteger rank = CompositionCoder.rank(frequencies, length, ArchiveConstants.BYTE_ALPHABET_SIZE);

        int rankBitCount;
        if (stateCount.equals(BigInteger.ONE)) {
            rankBitCount = 0;
        } else {
            rankBitCount = stateCount.subtract(BigInteger.ONE).bitLength();
        }

        SparseHeaderCoder.SparsePrepared sparsePrepared = SparseHeaderCoder.prepare(frequencies);

        long rankBits = 1L + 32L + rankBitCount;
        long sparseBits = 1L + 9L + 32L + sparsePrepared.encodedBits();

        if (sparseBits < rankBits)
            return new HeaderPlan(ArchiveConstants.HEADER_MODE_SPARSE, 0, BigInteger.ZERO, sparsePrepared);

        return new HeaderPlan(ArchiveConstants.HEADER_MODE_RANK, rankBitCount, rank, null);
    }

    private void writeBlockHeader(BitOutput bitOutput, HeaderPlan headerPlan) throws IOException {
        bitOutput.writeBit(headerPlan.mode());

        if (headerPlan.mode() == ArchiveConstants.HEADER_MODE_RANK) {
            bitOutput.writeBits(headerPlan.rankBitCount() & 0xFFFFFFFFL, 32);

            BigInteger rank = headerPlan.rank();
            for (int bit = headerPlan.rankBitCount() - 1; bit >= 0; bit--)
                bitOutput.writeBit(rank.testBit(bit) ? 1 : 0);
            return;
        }

        SparseHeaderCoder.SparsePrepared sparsePrepared = headerPlan.sparsePrepared();
        bitOutput.writeBits(sparsePrepared.q(), 9);
        bitOutput.writeBits((int) sparsePrepared.encodedBits(), 32);
        writeStoredBits(bitOutput, sparsePrepared.encodedBytes(), sparsePrepared.encodedBits());
    }

    private void writeStoredBits(BitOutput bitOutput, byte[] bytes, long bitCount) throws IOException {
        for (long i = 0; i < bitCount; i++) {
            int byteIndex = (int) (i >>> 3);
            int bitIndex = 7 - (int) (i & 7);
            int bit = (bytes[byteIndex] >>> bitIndex) & 1;
            bitOutput.writeBit(bit);
        }
    }
}
