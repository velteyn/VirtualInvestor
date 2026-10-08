package com.virtualinvestor.persist.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.virtualinvestor.core.event.GameEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class EventLogTest {

    private static final String ROUND = "test-event-log";

    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void migrate() {
        String url = System.getProperty("vi.db.url", "jdbc:postgresql://localhost:5432/virtualinvestor");
        String user = System.getProperty("vi.db.user", "vi");
        String password = System.getProperty("vi.db.password", "vi");
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();

        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
    }

    @BeforeEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM events WHERE round_id = ?")) {
            statement.setString(1, ROUND);
            statement.executeUpdate();
        }
    }

    @Test
    void eventsAppendInOrderAndReadBack() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 2);

        List<GameEvent> original = List.of(
                new GameEvent.Tick(1L, 100L),
                new GameEvent.OrderReport("o1", "p1", "ACME", com.virtualinvestor.core.domain.OrderSide.BUY,
                        com.virtualinvestor.core.domain.OrderType.LIMIT, 10L, 100.0, 0L, 10L,
                        com.virtualinvestor.core.domain.OrderStatus.WORKING,
                        com.virtualinvestor.core.domain.OrderEventKind.NEW, null, 1L),
                new GameEvent.TradePrinted("ACME", 100.0, 10L, com.virtualinvestor.core.domain.OrderSide.BUY, 2L),
                new GameEvent.BookChanged("ACME", 99.9, 100L, 100.1, 100L, 100.0, 3L),
                new GameEvent.Tick(4L, 400L));
        writer.appendAll(original);
        writer.flush();

        assertEquals(0, writer.pending());
        assertEquals(5L, log.latestSeq());

        List<SequencedEvent> read = log.readFrom(1L);
        assertEquals(5, read.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(i + 1L, read.get(i).seq());
            assertEquals(original.get(i), read.get(i).event());
        }

        assertEquals(2, log.readFrom(4L).size());
    }

    @Test
    void duplicateSequenceIsRefused() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        log.appendBatch(List.of(new SequencedEvent(1L, new GameEvent.Tick(1L, 100L))));

        assertThrows(IllegalStateException.class,
                () -> log.appendBatch(List.of(new SequencedEvent(1L, new GameEvent.Tick(1L, 100L)))));
        assertEquals(1L, log.latestSeq());
    }
}
