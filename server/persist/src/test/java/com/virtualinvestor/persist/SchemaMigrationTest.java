package com.virtualinvestor.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Integration test: applies the Flyway migrations to the local Postgres (docker compose db) and
 * asserts the expected tables exist. Connection settings can be overridden with
 * -Dvi.db.url / -Dvi.db.user / -Dvi.db.password.
 */
class SchemaMigrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "rounds", "participants", "accounts", "positions", "orders", "fills", "events", "snapshots", "results");

    private static String url() {
        return System.getProperty("vi.db.url", "jdbc:postgresql://localhost:5432/virtualinvestor");
    }

    private static String user() {
        return System.getProperty("vi.db.user", "vi");
    }

    private static String password() {
        return System.getProperty("vi.db.password", "vi");
    }

    @Test
    void migrationsApplyAndCreateExpectedTables() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(url(), user(), password())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        Set<String> tables = new LinkedHashSet<>();
        try (Connection connection = DriverManager.getConnection(url(), user(), password());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "select table_name from information_schema.tables where table_schema = 'public'")) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        assertTrue(tables.containsAll(EXPECTED_TABLES),
                () -> "missing tables: " + EXPECTED_TABLES.stream().filter(t -> !tables.contains(t)).toList());
        assertTrue(flyway.info().applied().length >= 1, "migrations should be applied");
        assertEquals(0, flyway.info().pending().length, "no migration should be left pending");
    }
}
