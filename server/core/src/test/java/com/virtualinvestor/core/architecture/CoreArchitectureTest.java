package com.virtualinvestor.core.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the determinism contract of {@code core}: no wall-clock reads, no default
 * randomness, and no unordered collections that could leak iteration order into state.
 * The single sanctioned wall-clock accessor is {@link com.virtualinvestor.core.clock.TimeSource#system()},
 * which uses a method reference and therefore is deliberately not matched.
 */
class CoreArchitectureTest {

    private record Rule(String name, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("wall-clock System.currentTimeMillis()", Pattern.compile("System\\.currentTimeMillis\\s*\\(")),
            new Rule("wall-clock System.nanoTime()", Pattern.compile("System\\.nanoTime\\s*\\(")),
            new Rule("default Random", Pattern.compile("new\\s+Random\\s*\\(")),
            new Rule("Math.random", Pattern.compile("Math\\.random\\s*\\(")),
            new Rule("now() clock read", Pattern.compile(
                    "\\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\\.now\\s*\\(")),
            new Rule("HashMap", Pattern.compile("new\\s+HashMap\\s*[<(]")),
            new Rule("HashSet", Pattern.compile("new\\s+HashSet\\s*[<(]")),
            new Rule("Map.of", Pattern.compile("\\bMap\\.of\\s*\\(")),
            new Rule("Set.of", Pattern.compile("\\bSet\\.of\\s*\\(")),
            new Rule("Collectors.toMap", Pattern.compile("Collectors\\.toMap\\s*\\(")),
            new Rule("Collectors.toSet", Pattern.compile("Collectors\\.toSet\\s*\\(")));

    @Test
    void coreHasNoForbiddenPatterns() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "expected " + sourceRoot.toAbsolutePath() + " to exist");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                violations.addAll(scan(file.toString(), Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(violations.isEmpty(), () -> "determinism violations in core:\n" + String.join("\n", violations));
    }

    @Test
    void rulesFlagDeliberateViolations() {
        String offending = """
                class Bad {
                    long now = System.currentTimeMillis();
                    java.util.Random r = new Random(1L);
                    java.util.Map<String, String> m = new HashMap<>();
                    double x = Math.random();
                }
                """;
        List<String> violations = scan("Bad.java", offending);
        assertFalse(violations.isEmpty(), "detector must flag a deliberate violation");
        assertTrue(violations.stream().anyMatch(v -> v.contains("System.currentTimeMillis")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("default Random")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("HashMap")), violations::toString);
    }

    private static List<String> scan(String fileName, String source) {
        List<String> found = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        for (Rule rule : RULES) {
            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = rule.pattern().matcher(lines[i]);
                if (matcher.find()) {
                    found.add(fileName + ":" + (i + 1) + ": " + rule.name());
                }
            }
        }
        return found;
    }
}
