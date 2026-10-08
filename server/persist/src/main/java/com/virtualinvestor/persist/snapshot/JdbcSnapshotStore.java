package com.virtualinvestor.persist.snapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * JDBC-backed {@link SnapshotStore}. A snapshot's primary key is (round_id, tick), so saving a
 * snapshot for a tick that already exists replaces it; reading returns the highest sequence.
 */
public final class JdbcSnapshotStore implements SnapshotStore {

    private static final String INSERT =
            "INSERT INTO snapshots (round_id, tick, seq, state) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (round_id, tick) DO UPDATE SET seq = EXCLUDED.seq, state = EXCLUDED.state";
    private static final String LATEST =
            "SELECT tick, seq, state FROM snapshots WHERE round_id = ? ORDER BY seq DESC LIMIT 1";

    private final DataSource dataSource;
    private final String roundId;

    public JdbcSnapshotStore(DataSource dataSource, String roundId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.roundId = Objects.requireNonNull(roundId, "roundId");
    }

    @Override
    public void save(WorldSnapshot snapshot) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, roundId);
            statement.setLong(2, snapshot.tick());
            statement.setLong(3, snapshot.seq());
            statement.setString(4, snapshot.state());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed saving snapshot for round " + roundId, e);
        }
    }

    @Override
    public void deleteBefore(long tick) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM snapshots WHERE round_id = ? AND tick < ?")) {
            statement.setString(1, roundId);
            statement.setLong(2, tick);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed pruning snapshots for round " + roundId, e);
        }
    }

    @Override
    public Optional<WorldSnapshot> latest() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LATEST)) {
            statement.setString(1, roundId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new WorldSnapshot(
                        resultSet.getLong("tick"), resultSet.getLong("seq"), resultSet.getString("state")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed reading latest snapshot for round " + roundId, e);
        }
    }
}
