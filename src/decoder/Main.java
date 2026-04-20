package decoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import common.ArchiveConstants;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2 || isBlank(args[0]) || isBlank(args[1])) {
            System.err.println(
                    """
                            Error: you must pass exactly 2 arguments:
                              1) input archive path
                              2) output path
                            If the second argument is an existing directory, the decoded file
                            will be created there with the archive name without the .enc extension."""
            );
            System.exit(1);
        }

        try {
            Path inputFile = resolveInputArchive(args[0]);
            Path outputFile = resolveOutputFile(inputFile, args[1]);

            NumerationDecoder decoder = new NumerationDecoder();
            decoder.decode(inputFile, outputFile);

            System.out.println("Decoded file created: " + outputFile.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Decoding error: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static Path resolveInputArchive(String inputArg) {
        Path inputPath = Path.of(inputArg);

        if (Files.exists(inputPath) && Files.isRegularFile(inputPath)) return inputPath;

        String fileName = inputPath.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(ArchiveConstants.ARCHIVE_EXTENSION)) {
            Path parent = inputPath.getParent();
            String newName = fileName + ArchiveConstants.ARCHIVE_EXTENSION;
            Path withExtension = parent == null ? Path.of(newName) : parent.resolve(newName);

            if (Files.exists(withExtension) && Files.isRegularFile(withExtension)) return withExtension;
        }

        throw new IllegalArgumentException("Input archive does not exist: " + inputPath);
    }

    private static Path resolveOutputFile(Path inputFile, String outputArg) {
        Path outputPath = Path.of(outputArg);

        if (Files.exists(outputPath) && Files.isDirectory(outputPath))
            return outputPath.resolve(getDecodedFileName(inputFile));

        return outputPath;
    }

    private static String getDecodedFileName(Path inputFile) {
        String fileName = inputFile.getFileName().toString();

        if (fileName.toLowerCase(Locale.ROOT).endsWith(ArchiveConstants.ARCHIVE_EXTENSION))
            return fileName.substring(0, fileName.length() - ArchiveConstants.ARCHIVE_EXTENSION.length());

        return fileName;
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
