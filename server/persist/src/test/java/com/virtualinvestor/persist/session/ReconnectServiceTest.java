package com.virtualinvestor.persist.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.JdbcEventLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class ReconnectServiceTest {

    private static final String ROUND = "test-reconnect";

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
    void reconnectReturnsOnlyMissedEvents() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        writer.appendAll(java.util.List.of(
                new GameEvent.Tick(1L, 100L),
                new GameEvent.TradePrinted("ACME", 100.0, 10L, com.virtualinvestor.core.domain.OrderSide.BUY, 2L),
                new GameEvent.TradePrinted("ACME", 100.5, 5L, com.virtualinvestor.core.domain.OrderSide.SELL, 3L)));
        writer.flush();

        SessionRegistry registry = new SessionRegistry();
        registry.register("h1", SessionToken.issue("secret-token"));
        ReconnectService service = new ReconnectService(log, registry);

        ResyncPayload payload = service.reconnect("h1", "secret-token", 1L);

        assertEquals("h1", payload.participantId());
        assertEquals(2, payload.missedEvents().size());
        assertEquals(2L, payload.fromSeq());
        assertEquals(3L, payload.toSeq());
        assertEquals(3L, payload.tick());
        assertTrue(payload.missedEvents().stream().allMatch(e -> e.seq() >= 2L));
    }

    @Test
    void wrongTokenIsRejected() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        SessionRegistry registry = new SessionRegistry();
        registry.register("h1", SessionToken.issue("secret-token"));
        ReconnectService service = new ReconnectService(log, registry);

        assertThrows(SecurityException.class, () -> service.reconnect("h1", "wrong", 0L));
        assertThrows(SecurityException.class, () -> service.reconnect("nobody", "secret-token", 0L));
    }

    @Test
    void caughtUpClientGetsNoEvents() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        writer.append(new GameEvent.Tick(1L, 100L));
        writer.flush();

        SessionRegistry registry = new SessionRegistry();
        registry.register("h1", SessionToken.issue("t"));
        ResyncPayload payload = new ReconnectService(log, registry).reconnect("h1", "t", 1L);

        assertTrue(payload.missedEvents().isEmpty());
        assertEquals(1L, payload.toSeq());
    }
}
