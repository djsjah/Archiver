package common;

public final class ArchiveConstants {
    public static final String ARCHIVE_EXTENSION = ".enc";
    public static final int FILE_SIGNATURE = 0x4E544331;

    public static final int BYTE_ALPHABET_SIZE = 256;

    public static final int HEADER_MODE_RANK = 0;
    public static final int HEADER_MODE_SPARSE = 1;

    public static final int MODE_WHOLE = 0;
    public static final int MODE_BLOCK_1K = 1;
    public static final int MODE_BLOCK_4K = 2;
    public static final int MODE_BLOCK_8K = 3;
    public static final int MODE_BLOCK_16K = 4;

    private ArchiveConstants() { }

    public static int getBlockSize(int mode, int totalLength) {
        return switch (mode) {
            case MODE_WHOLE -> Math.max(1, totalLength);
            case MODE_BLOCK_1K -> 1024;
            case MODE_BLOCK_4K -> 4096;
            case MODE_BLOCK_8K -> 8192;
            case MODE_BLOCK_16K -> 16384;
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
    }
}
