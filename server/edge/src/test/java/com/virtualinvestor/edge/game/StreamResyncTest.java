package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.JdbcEventLog;
import com.virtualinvestor.persist.event.SequencedEvent;
import com.virtualinvestor.persist.session.ReconnectService;
import com.virtualinvestor.persist.session.ResyncPayload;
import com.virtualinvestor.persist.session.SessionRegistry;
import com.virtualinvestor.persist.session.SessionToken;
import com.virtualinvestor.edge.wire.WireMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Group 5.3: streamed events carry sequence/tick, and a reconnecting client receives only the
 * events it missed.
 */
class StreamResyncTest {

    private static final String ROUND = "round-stream";

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
    void reconnectReturnsOnlyMissedEventsWithSequenceAndTick() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        writer.appendAll(List.of(
                new GameEvent.Tick(1L, 100L),
                new GameEvent.TradePrinted("NVDO", 100.0, 10L, com.virtualinvestor.core.domain.OrderSide.BUY, 2L),
                new GameEvent.TradePrinted("NVDO", 101.0, 5L, com.virtualinvestor.core.domain.OrderSide.SELL, 3L)));
        writer.flush();

        SessionRegistry registry = new SessionRegistry();
        registry.register("human", SessionToken.issue("token"));
        ResyncPayload payload = new ReconnectService(log, registry).reconnect("human", "token", 1L);

        assertEquals(2, payload.missedEvents().size());
        assertEquals(2L, payload.fromSeq());
        assertEquals(3L, payload.toSeq());
        assertEquals(3L, payload.tick());

        // Shape it as a wire ResyncResult with each event's sequence and tick.
        List<WireMessage.Event> events = payload.missedEvents().stream()
                .map(sequenced -> new WireMessage.Event(
                        sequenced.seq(), sequenced.event().tick(),
                        sequenced.event().getClass().getSimpleName(), null))
                .toList();
        WireMessage.ResyncResult result = new WireMessage.ResyncResult(
                payload.fromSeq(), payload.toSeq(), payload.tick(), events);

        assertEquals(2, result.events().size());
        assertEquals(2L, result.events().get(0).seq());
        assertEquals(3L, result.events().get(1).tick());
        assertTrue(result.events().stream().allMatch(e -> e.seq() > 1L));
    }

    @Test
    void resyncMessagesRoundTripThroughJson() {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        WireMessage.ResyncRequest request = new WireMessage.ResyncRequest("human", "token", 5L);
        String json = mapper.writeValueAsString(request);
        WireMessage.Payload decoded = mapper.readValue(json, WireMessage.Payload.class);
        assertEquals(request, decoded);
    }
}
