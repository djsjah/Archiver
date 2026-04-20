package stats;

public record EncodeStats(
        long headerBits,
        long payloadBits,
        long totalBits,
        long paddingBits,
        long totalBytes
) { }
