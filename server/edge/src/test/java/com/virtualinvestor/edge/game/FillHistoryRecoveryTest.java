package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.JdbcEventLog;
import com.virtualinvestor.persist.event.SequencedEvent;
import com.virtualinvestor.persist.session.RoundRecoveryService;
import com.virtualinvestor.persist.snapshot.JdbcSnapshotStore;
import com.virtualinvestor.runtime.FeeSchedule;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Portfolio-tab 2.2: history folded from a recovered journal matches the live run,
 * so returning players see exactly what the stream carried.
 */
class FillHistoryRecoveryTest {

    private static final String ROUND = "test-fill-recovery";

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

    private static GameEvent.OrderReport fill(String orderId, String participant, long filled, double price) {
        return new GameEvent.OrderReport(orderId, participant, "NVDO", OrderSide.BUY, OrderType.MARKET, 10L,
                price, filled, 10L - filled, OrderStatus.FILLED, OrderEventKind.FILL, null, 9L);
    }

    @Test
    void recoveredHistoryFoldsIdenticallyToTheLiveStream() {
        List<GameEvent> live = List.of(
                fill("o1", "human", 10L, 100.5),
                fill("o2", "rival-1", 10L, 101.0),
                fill("o1", "human", 10L, 100.5),
                new GameEvent.Tick(9L, 900L));

        // Live run: fold the stream as produced.
        List<SequencedEvent> liveSequenced = new ArrayList<>();
        for (int i = 0; i < live.size(); i++) {
            liveSequenced.add(new SequencedEvent(i + 1L, live.get(i)));
        }
        List<FillHistory.FillRow> liveRows =
                FillHistory.fold(liveSequenced, "human", FeeSchedule.fixed(0.01, 2.0));

        // Restart: fresh instances rebuild from the journal.
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        writer.appendAll(live);
        writer.flush();
        RoundRecoveryService recovered = new RoundRecoveryService(new JdbcEventLog(dataSource, ROUND),
                new JdbcSnapshotStore(dataSource, ROUND), new BatchedEventLogWriter(
                        new JdbcEventLog(dataSource, ROUND), 100));
        List<SequencedEvent> history = recovered.recover().history().stream()
                .map(event -> new SequencedEvent(0L, event))
                .toList();
        List<FillHistory.FillRow> recoveredRows =
                FillHistory.fold(history, "human", FeeSchedule.fixed(0.01, 2.0));

        assertFalse(liveRows.isEmpty());
        assertEquals(liveRows, recoveredRows);
    }
}
