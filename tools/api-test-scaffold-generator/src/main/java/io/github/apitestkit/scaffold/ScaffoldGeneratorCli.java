package io.github.apitestkit.scaffold;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class ScaffoldGeneratorCli {
    private ScaffoldGeneratorCli() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exitCode = run(args, out, err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            usage(out);
            return 0;
        }
        try {
            switch (args[0]) {
                case "generate":
                    return generate(Arrays.copyOfRange(args, 1, args.length), out);
                case "validate":
                    return validate(Arrays.copyOfRange(args, 1, args.length), out);
                case "example":
                    return example(out);
                default:
                    err.println("Unknown command: " + args[0]);
                    usage(err);
                    return 2;
            }
        } catch (Exception error) {
            err.println("ERROR: " + error.getMessage());
            return 2;
        }
    }

    private static int generate(String[] args, PrintStream out) throws IOException {
        String spec = requiredOption(args, "--spec");
        String project = requiredOption(args, "--project");
        boolean overwrite = hasFlag(args, "--overwrite");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();
        ApiTestScaffoldGenerator.GenerationResult result = generator.generate(
                Path.of(spec), Path.of(project), overwrite);
        out.println("Generated API test scaffold for " + result.getApiCode());
        out.println("Project: " + result.getProjectRoot());
        out.println("Manifest: " + result.getManifest());
        out.println("Executableization plan: " + result.getExecutableizationPlan());
        out.println("Files: " + result.getFiles().size());
        return 0;
    }

    private static int validate(String[] args, PrintStream out) throws IOException {
        String project = requiredOption(args, "--project");
        String manifest = requiredOption(args, "--manifest");
        boolean strict = hasFlag(args, "--strict-checksums");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();
        ApiTestScaffoldGenerator.ValidationReport report = generator.validate(
                Path.of(project), Path.of(manifest), strict);
        out.println("Validation: " + (report.isSuccess() ? "PASS" : "FAIL"));
        out.println("Strict checksums: " + report.isStrictChecksums());
        printList(out, "Missing", report.getMissingFiles());
        printList(out, "Modified", report.getModifiedFiles());
        printList(out, "Malformed", report.getMalformedEntries());
        return report.isSuccess() ? 0 : 1;
    }

    private static int example(PrintStream out) throws IOException {
        try (InputStream input = ScaffoldGeneratorCli.class.getResourceAsStream("/example-spec.yaml")) {
            if (input == null) {
                throw new IOException("example-spec.yaml is missing from the JAR");
            }
            out.print(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return 0;
    }

    private static String requiredOption(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        throw new IllegalArgumentException("Missing required option " + name);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printList(PrintStream out, String name, List<String> values) {
        out.println(name + ": " + values.size());
        for (String value : values) {
            out.println("  - " + value);
        }
    }

    private static void usage(PrintStream out) {
        out.println("API Test Scaffold Generator " + ApiTestScaffoldGenerator.GENERATOR_VERSION);
        out.println("Usage:");
        out.println("  java -jar api-test-scaffold-generator.jar example");
        out.println("  java -jar api-test-scaffold-generator.jar generate --spec SPEC.yaml --project PROJECT [--overwrite]");
        out.println("  java -jar api-test-scaffold-generator.jar validate --project PROJECT --manifest MANIFEST.yaml [--strict-checksums]");
    }
}
