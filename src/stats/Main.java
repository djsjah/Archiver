package stats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            System.err.println("""
                    Error: you must pass exactly 1 argument:
                      1) path to an existing directory with input test files

                    The program processes all regular files located directly in this directory.
                    It creates (or reuses) the subdirectories "encoded" and "decoded" inside it.
                    """);
            System.exit(1);
        }

        try {
            Path datasetDir = Path.of(args[0]);

            if (!Files.exists(datasetDir) || !Files.isDirectory(datasetDir)) {
                throw new IOException(
                        "Input directory does not exist: " + datasetDir + System.lineSeparator() +
                                "Create this directory and place the source files for encoder/decoder testing into it."
                );
            }

            List<Path> inputFiles = StatsRunner.collectInputFiles(datasetDir);
            if (inputFiles.isEmpty()) {
                throw new IOException(
                        "The directory does not contain any input files to process: " + datasetDir + System.lineSeparator() +
                                "Place one or more regular files into this directory before running stats."
                );
            }

            StatsRunner runner = new StatsRunner();
            runner.run(datasetDir, inputFiles);
        } catch (Exception ex) {
            System.err.println("Stats error: " + ex.getMessage());
            System.exit(1);
        }
    }
}
