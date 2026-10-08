package com.virtualinvestor.persist.event;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.event.GameEventCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * JDBC-backed {@link EventLog}. Appends are a single batched insert per call, so the writer can
 * buffer off the hot path (see {@link BatchedEventLogWriter}); the primary key (round_id, seq)
 * makes a duplicate sequence number fail rather than silently overwrite.
 */
public final class JdbcEventLog implements EventLog {

    private static final String INSERT = "INSERT INTO events (round_id, seq, tick, type, payload) VALUES (?, ?, ?, ?, ?)";
    private static final String MAX_SEQ = "SELECT COALESCE(MAX(seq), 0) FROM events WHERE round_id = ?";
    private static final String READ_FROM =
            "SELECT seq, type, payload FROM events WHERE round_id = ? AND seq >= ? ORDER BY seq";

    private final DataSource dataSource;
    private final String roundId;

    public JdbcEventLog(DataSource dataSource, String roundId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.roundId = Objects.requireNonNull(roundId, "roundId");
    }

    @Override
    public void appendBatch(List<SequencedEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT)) {
            for (SequencedEvent sequenced : events) {
                GameEvent event = sequenced.event();
                statement.setString(1, roundId);
                statement.setLong(2, sequenced.seq());
                statement.setLong(3, event.tick());
                statement.setString(4, GameEventCodec.typeOf(event));
                statement.setString(5, GameEventCodec.encode(event));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("failed appending events for round " + roundId, e);
        }
    }

    @Override
    public long latestSeq() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(MAX_SEQ)) {
            statement.setString(1, roundId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed reading latest sequence for round " + roundId, e);
        }
    }

    /**
     * Delete rows strictly below a snapshotted sequence (compaction, not mutation:
     * the snapshot already carries that prefix, and recovery replays the tail).
     * The boundary row stays so sequence numbering never restarts into reuse —
     * a fresh writer seeds from the log's high-water mark. Keeps checkpoint cost
     * bounded as rounds grow.
     */
    public void pruneBelow(long seq) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM events WHERE round_id = ? AND seq < ?")) {
            statement.setString(1, roundId);
            statement.setLong(2, seq);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed pruning events for round " + roundId, e);
        }
    }

    @Override
    public List<SequencedEvent> readFrom(long fromSeq) {
        List<SequencedEvent> events = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(READ_FROM)) {
            statement.setString(1, roundId);
            statement.setLong(2, fromSeq);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long seq = resultSet.getLong("seq");
                    String type = resultSet.getString("type");
                    String payload = resultSet.getString("payload");
                    events.add(new SequencedEvent(seq, GameEventCodec.decode(type, payload)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed reading events for round " + roundId, e);
        }
        return List.copyOf(events);
    }
}
