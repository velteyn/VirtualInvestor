package com.virtualinvestor.edge.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the dependency direction (design D2): the edge depends on the core, never the reverse.
 * The core module must not name any edge, Spring or JPA type.
 */
class EdgeArchitectureTest {

    private static final Path CORE_SOURCE = Path.of("../core/src/main/java");

    @Test
    void coreDoesNotDependOnEdgeOrFrameworks() throws IOException {
        assertTrue(Files.isDirectory(CORE_SOURCE), "expected " + CORE_SOURCE.toAbsolutePath());
        StringBuilder offending = new StringBuilder();
        try (Stream<Path> files = Files.walk(CORE_SOURCE)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("com.virtualinvestor.edge")
                        || source.contains("org.springframework")
                        || source.contains("jakarta.persistence")) {
                    offending.append(file).append('\n');
                }
            }
        }
        assertFalse(offending.length() > 0, () -> "core references edge/framework types:\n" + offending);
    }

    @Test
    void edgeUsesCoreTypesOnlyThroughItsOwnMapping() throws IOException {
        Path mapper = Path.of("src/main/java/com/virtualinvestor/edge/api/WireMapper.java");
        assertTrue(Files.exists(mapper), "the wire mapper is the designated translation point");
    }
}
