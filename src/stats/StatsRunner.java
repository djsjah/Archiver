package stats;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import common.ArchiveConstants;
import decoder.NumerationDecoder;
import encoder.NumerationEncoder;

public class StatsRunner {
    private static final double LOG_2 = Math.log(2.0);

    private record StatsRow(
            String fileName,
            double h0,
            double h1,
            double h2,
            long originalSize,
            long compressedSize,
            double storedBitsPerSymbol,
            double exactBitsPerSymbol,
            long headerBits,
            long payloadBits,
            long totalBits,
            long paddingBits
    ) { }

    public static List<Path> collectInputFiles(Path datasetDir) throws IOException {
        try (Stream<Path> stream = Files.list(datasetDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(StatsConstants.CSV_FILE_NAME))
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
        }
    }

    public void run(Path datasetDir, List<Path> inputFiles) throws IOException {
        Path encodedDir = datasetDir.resolve(StatsConstants.ENCODED_DIR_NAME);
        Path decodedDir = datasetDir.resolve(StatsConstants.DECODED_DIR_NAME);
        Path csvFile = datasetDir.resolve(StatsConstants.CSV_FILE_NAME);

        Files.createDirectories(encodedDir);
        Files.createDirectories(decodedDir);

        NumerationEncoder encoder = new NumerationEncoder();
        NumerationDecoder decoder = new NumerationDecoder();

        long totalCompressedSize = 0;
        long totalExactBits = 0;

        printHeader();

        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            writer.write("file,hx,hx_given_x,hx_given_xx,original_bytes,compressed_bytes,"
                    + "stored_bits_per_symbol,exact_bits_per_symbol,header_bits,payload_bits,total_bits,padding_bits");
            writer.newLine();

            for (Path inputFile : inputFiles) {
                String fileName = inputFile.getFileName().toString();

                try {
                    Path encodedFile = encodedDir.resolve(fileName + ArchiveConstants.ARCHIVE_EXTENSION);
                    Path decodedFile = decodedDir.resolve(fileName);

                    EncodeStats encodeStats = encoder.encode(inputFile, encodedFile);
                    decoder.decode(encodedFile, decodedFile);

                    if (!sameFiles(inputFile, decodedFile)) {
                        throw new IllegalStateException("Decoded file differs from original");
                    }

                    StatsRow row = buildStatsRow(fileName, inputFile, encodedFile, encodeStats);
                    totalCompressedSize += row.compressedSize();
                    totalExactBits += row.totalBits();

                    printDataRow(row);
                    writeCsvRow(writer, row);
                } catch (Exception ex) {
                    printErrorRow(fileName);
                    System.err.println("Error for file " + fileName + ": " + ex.getMessage());
                }
            }
        }

        System.out.println();
        System.out.println("Processed input directory: " + datasetDir.toAbsolutePath());
        System.out.println("Encoded files directory:  " + encodedDir.toAbsolutePath());
        System.out.println("Decoded files directory:  " + decodedDir.toAbsolutePath());
        System.out.println("Total compressed size (all files): " + totalCompressedSize + " bytes");
        System.out.println("Total exact bits (all files): " + totalExactBits + " bits");
        System.out.println("CSV saved to: " + csvFile.toAbsolutePath());
    }

    private StatsRow buildStatsRow(String fileName, Path inputFile, Path encodedFile, EncodeStats encodeStats)
            throws IOException {
        byte[] data = Files.readAllBytes(inputFile);

        double h0 = computeH0(data);
        double h1 = computeH1(data);
        double h2 = computeH2(data);

        long originalSize = Files.size(inputFile);
        long compressedSize = Files.size(encodedFile);

        double storedBitsPerSymbol = originalSize == 0
                ? 0.0
                : (8.0 * compressedSize) / originalSize;

        double exactBitsPerSymbol = originalSize == 0
                ? 0.0
                : (double) encodeStats.totalBits() / originalSize;

        return new StatsRow(
                fileName,
                h0,
                h1,
                h2,
                originalSize,
                compressedSize,
                storedBitsPerSymbol,
                exactBitsPerSymbol,
                encodeStats.headerBits(),
                encodeStats.payloadBits(),
                encodeStats.totalBits(),
                encodeStats.paddingBits()
        );
    }

    private void printHeader() {
        System.out.printf(
                Locale.US,
                "%-20s %10s %10s %10s %12s %12s %12s %12s %12s %12s%n",
                "File", "H(X)", "H(X|X)", "H(X|XX)", "Size(B)", "Comp(B)",
                "Bits/sym", "ExactBits", "HdrBits", "PadBits"
        );
        System.out.printf(
                Locale.US,
                "%-20s %10s %10s %10s %12s %12s %12s %12s %12s %12s%n",
                "--------------------", "----------", "----------", "----------",
                "------------", "------------", "------------", "------------",
                "------------", "------------"
        );
    }

    private void printErrorRow(String fileName) {
        System.out.printf(
                Locale.US,
                "%-20s %10s %10s %10s %12s %12s %12s %12s %12s %12s%n",
                fileName, "ERROR", "ERROR", "ERROR", "ERROR", "ERROR",
                "ERROR", "ERROR", "ERROR", "ERROR"
        );
    }

    private void printDataRow(StatsRow row) {
        System.out.printf(
                Locale.US,
                "%-20s %10.4f %10.4f %10.4f %12d %12d %12.4f %12.4f %12d %12d%n",
                row.fileName(),
                row.h0(),
                row.h1(),
                row.h2(),
                row.originalSize(),
                row.compressedSize(),
                row.storedBitsPerSymbol(),
                row.exactBitsPerSymbol(),
                row.headerBits(),
                row.paddingBits()
        );
    }

    private void writeCsvRow(BufferedWriter writer, StatsRow row) throws IOException {
        writer.write(String.format(
                Locale.US,
                "%s,%.8f,%.8f,%.8f,%d,%d,%.8f,%.8f,%d,%d,%d,%d",
                row.fileName(),
                row.h0(),
                row.h1(),
                row.h2(),
                row.originalSize(),
                row.compressedSize(),
                row.storedBitsPerSymbol(),
                row.exactBitsPerSymbol(),
                row.headerBits(),
                row.payloadBits(),
                row.totalBits(),
                row.paddingBits()
        ));
        writer.newLine();
    }

    private double computeH0(byte[] data) {
        int n = data.length;
        if (n == 0) return 0.0;

        int[] counts = new int[ArchiveConstants.BYTE_ALPHABET_SIZE];
        for (byte value : data) {
            counts[value & 0xFF]++;
        }

        double result = 0.0;
        for (int count : counts) {
            if (count == 0) continue;

            double probability = (double) count / n;
            result -= probability * log2(probability);
        }
        return result;
    }

    private double computeH1(byte[] data) {
        int n = data.length;
        if (n < 2) return 0.0;

        int[] pairCounts = new int[ArchiveConstants.BYTE_ALPHABET_SIZE * ArchiveConstants.BYTE_ALPHABET_SIZE];
        int[] contextCounts = new int[ArchiveConstants.BYTE_ALPHABET_SIZE];

        for (int i = 1; i < n; i++) {
            int previous = data[i - 1] & 0xFF;
            int current = data[i] & 0xFF;
            int pairIndex = (previous << 8) | current;

            pairCounts[pairIndex]++;
            contextCounts[previous]++;
        }

        return computeH1FromCounts(n, pairCounts, contextCounts);
    }

    private double computeH1FromCounts(int n, int[] pairCounts, int[] contextCounts) {
        double result = 0.0;
        double totalPairs = n - 1;

        for (int pairIndex = 0; pairIndex < pairCounts.length; pairIndex++) {
            int count = pairCounts[pairIndex];
            if (count == 0) continue;

            int previous = pairIndex >>> 8;
            double pairProbability = count / totalPairs;
            double conditionalProbability = (double) count / contextCounts[previous];

            result -= pairProbability * log2(conditionalProbability);
        }

        return result;
    }

    private double computeH2(byte[] data) {
        int n = data.length;
        if (n < 3) return 0.0;

        Map<Integer, Integer> tripleCounts = new HashMap<>();
        int[] contextCounts = new int[ArchiveConstants.BYTE_ALPHABET_SIZE * ArchiveConstants.BYTE_ALPHABET_SIZE];

        for (int i = 2; i < n; i++) {
            int first = data[i - 2] & 0xFF;
            int second = data[i - 1] & 0xFF;
            int third = data[i] & 0xFF;

            int context = (first << 8) | second;
            int triple = (first << 16) | (second << 8) | third;

            contextCounts[context]++;
            tripleCounts.merge(triple, 1, Integer::sum);
        }

        double result = 0.0;
        double totalTriples = n - 2;

        for (Map.Entry<Integer, Integer> entry : tripleCounts.entrySet()) {
            int triple = entry.getKey();
            int count = entry.getValue();

            int context = triple >>> 8;
            double tripleProbability = count / totalTriples;
            double conditionalProbability = (double) count / contextCounts[context];

            result -= tripleProbability * log2(conditionalProbability);
        }

        return result;
    }

    private double log2(double value) {
        return Math.log(value) / LOG_2;
    }

    private boolean sameFiles(Path first, Path second) throws IOException {
        long firstSize = Files.size(first);
        long secondSize = Files.size(second);

        if (firstSize != secondSize) return false;

        try (InputStream in1 = new BufferedInputStream(Files.newInputStream(first));
             InputStream in2 = new BufferedInputStream(Files.newInputStream(second))) {

            while (true) {
                int b1 = in1.read();
                int b2 = in2.read();

                if (b1 != b2) return false;
                if (b1 == -1) return true;
            }
        }
    }
}
