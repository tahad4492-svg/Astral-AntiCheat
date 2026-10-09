package me.mrtahadarvish.astral;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Collects the result of every scenario into target/astral-test-report.md. */
final class Report {

    private static final Path FILE = Path.of("target", "astral-test-report.md");

    private Report() {
    }

    static synchronized void add(String kind, String scenario, String result, boolean passed) {
        write("| " + kind + " | " + scenario + " | " + (passed ? "PASS" : "FAIL") + " | " + result.replace("|", "/") + " |");
    }

    static synchronized void note(String text) {
        write("<!-- " + text + " -->");
    }

    private static void write(String line) {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                Files.writeString(FILE, "| Type | Scenario | Result | Astral output |\n|---|---|---|---|\n", StandardCharsets.UTF_8);
            }
            Files.writeString(FILE, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // report only
        }
        System.out.println(line);
    }
}
