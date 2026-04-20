package encoder;

import java.nio.file.Files;
import java.nio.file.Path;

import common.ArchiveConstants;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2 || args[0].isBlank() || args[1].isBlank()) {
            System.err.println(
                    """
                            Error: you must pass exactly 2 arguments:
                              1) input file path
                              2) output path
                            You passed invalid or missing arguments."""
            );
            System.exit(1);
        }

        try {
            Path inputFile = Path.of(args[0]);

            if (!Files.exists(inputFile) || !Files.isRegularFile(inputFile)) {
                throw new IllegalArgumentException("Input file does not exist: " + inputFile);
            }

            Path outputFile = resolveOutputFile(inputFile, args[1]);

            NumerationEncoder encoder = new NumerationEncoder();
            encoder.encode(inputFile, outputFile);

            System.out.println("Archive created: " + outputFile.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Encoding error: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static Path resolveOutputFile(Path inputFile, String outputArg) {
        Path outputPath = Path.of(outputArg);

        if (Files.exists(outputPath) && Files.isDirectory(outputPath)) {
            return outputPath.resolve(inputFile.getFileName().toString() + ArchiveConstants.ARCHIVE_EXTENSION);
        }

        String fileName = outputPath.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(ArchiveConstants.ARCHIVE_EXTENSION)) {
            Path parent = outputPath.getParent();
            String newName = fileName + ArchiveConstants.ARCHIVE_EXTENSION;
            return parent == null ? Path.of(newName) : parent.resolve(newName);
        }

        return outputPath;
    }
}
