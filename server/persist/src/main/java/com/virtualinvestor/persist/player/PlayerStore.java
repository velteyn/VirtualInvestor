package com.virtualinvestor.persist.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Registered players, their banks and their persistent purse (player-accounts 1.2).
 * Humans register as PENDING and wait for approval; bots are ensured pre-approved.
 * The purse (cash, fees paid, holdings) is round-independent: round start loads it,
 * settlement writes it back.
 */
public final class PlayerStore {

    public enum Kind {
        HUMAN,
        BOT
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    public record Player(String playerId, String username, Kind kind, Status status, String bankId,
            boolean mustChangePassword) {
    }

    public record Bank(String bankId, String displayName, double feePerShare, double ticketMinimum,
            double custodyRate) {
    }

    public record Purse(double cash, double feesPaid, Map<String, Holding> holdings) {
    }

    public record Holding(String symbol, long qty, double avgPrice, double realized) {
    }

    /** Backwards-compatible holding without booked gains. */
    public static Holding holding(String symbol, long qty, double avgPrice) {
        return new Holding(symbol, qty, avgPrice, 0.0);
    }

    private final DataSource dataSource;

    public PlayerStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Register a human player as PENDING with an empty purse. */
    public Player register(String username, String password, String bankId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        Bank bank = bank(bankId)
                .orElseThrow(() -> new IllegalArgumentException("unknown bank: " + bankId));
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        String playerId = UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO players (player_id, username, password_hash, password_salt, kind, status, bank_id)"
                            + " VALUES (?, ?, ?, ?, 'HUMAN', 'PENDING', ?)")) {
                statement.setString(1, playerId);
                statement.setString(2, username);
                statement.setString(3, hash.hashHex());
                statement.setString(4, hash.saltHex());
                statement.setString(5, bank.bankId());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalArgumentException("username is taken: " + username, e);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_accounts (player_id, cash, fees_paid) VALUES (?, 0, 0)")) {
                statement.setString(1, playerId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("player registration failed", e);
        }
        return new Player(playerId, username, Kind.HUMAN, Status.PENDING, bank.bankId(), false);
    }

    /**
     * Game-master creation (player-accounts 3.5): approved at once, but the
     * admin-set password forces a change on first login.
     */
    public Player createApproved(String username, String password, String bankId) {
        Player player = register(username, password, bankId);
        approve(player.playerId());
        setFlag(player.playerId(), true);
        return findById(player.playerId()).orElseThrow();
    }

    /** Ensure an approved BOT row exists (player-accounts 1.3); re-running changes nothing. */
    public Player ensureBot(String username, String bankId) {
        Optional<Player> existing = findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        Bank bank = bank(bankId)
                .orElseThrow(() -> new IllegalArgumentException("unknown bank: " + bankId));
        PasswordHasher.Hash hash = PasswordHasher.hash(UUID.randomUUID().toString());
        String playerId = UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO players (player_id, username, password_hash, password_salt, kind, status, bank_id)"
                            + " VALUES (?, ?, ?, ?, 'BOT', 'APPROVED', ?) ON CONFLICT DO NOTHING")) {
                statement.setString(1, playerId);
                statement.setString(2, username);
                statement.setString(3, hash.hashHex());
                statement.setString(4, hash.saltHex());
                statement.setString(5, bank.bankId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_accounts (player_id, cash, fees_paid) VALUES (?, 0, 0)"
                            + " ON CONFLICT DO NOTHING")) {
                statement.setString(1, playerId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("bot registration failed", e);
        }
        return findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("bot registration failed"));
    }

    public Optional<Player> findById(String playerId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_id, username, kind, status, bank_id, must_change_password FROM players WHERE player_id = ?")) {
            statement.setString(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(readPlayer(rows));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("player lookup failed", e);
        }
    }

    /** All players, humans and bots, for the game-master approvals queue. */
    public java.util.List<Player> all() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_id, username, kind, status, bank_id, must_change_password FROM players ORDER BY created_at")) {
            try (ResultSet rows = statement.executeQuery()) {
                java.util.List<Player> players = new java.util.ArrayList<>();
                while (rows.next()) {
                    players.add(readPlayer(rows));
                }
                return players;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("player listing failed", e);
        }
    }

    private static Player readPlayer(ResultSet rows) throws SQLException {
        return new Player(rows.getString(1), rows.getString(2), Kind.valueOf(rows.getString(3)),
                Status.valueOf(rows.getString(4)), rows.getString(5), rows.getBoolean(6));
    }

    public Optional<Player> findByUsername(String username) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_id, username, kind, status, bank_id, must_change_password FROM players WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(readPlayer(rows));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("player lookup failed", e);
        }
    }

    public boolean checkPassword(String username, String password) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT password_hash, password_salt FROM players WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return false;
                }
                return PasswordHasher.matches(password, rows.getString(2), rows.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("password check failed", e);
        }
    }

    public void approve(String playerId) {
        setStatus(playerId, Status.APPROVED);
    }

    public void reject(String playerId) {
        setStatus(playerId, Status.REJECTED);
    }

    /**
     * Set a new password, for game-master resets (existing sessions are invalidated
     * by callers). An admin-set password always forces a change on next login.
     */
    public void resetPassword(String playerId, String newPassword) {
        setPassword(playerId, newPassword, true);
    }

    /**
     * Player-chosen password change: verifies the old password and clears the
     * forced-change flag.
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        Player player = findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!checkPassword(username, oldPassword)) {
            throw new IllegalArgumentException("invalid credentials");
        }
        setPassword(player.playerId(), newPassword, false);
    }

    private void setPassword(String playerId, String newPassword, boolean mustChange) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        PasswordHasher.Hash hash = PasswordHasher.hash(newPassword);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE players SET password_hash = ?, password_salt = ?, must_change_password = ?"
                                + " WHERE player_id = ?")) {
            statement.setString(1, hash.hashHex());
            statement.setString(2, hash.saltHex());
            statement.setBoolean(3, mustChange);
            statement.setString(4, playerId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("unknown player: " + playerId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("password reset failed", e);
        }
    }

    private void setFlag(String playerId, boolean mustChange) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE players SET must_change_password = ? WHERE player_id = ?")) {
            statement.setBoolean(1, mustChange);
            statement.setString(2, playerId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("flag update failed", e);
        }
    }

    /**
     * Rename and/or reassign the bank (player-accounts 3.5). The new schedule
     * applies to future fills only. Null arguments keep the current value.
     */
    public Player updatePlayer(String playerId, String newUsername, String newBankId) {
        Player current = findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("unknown player: " + playerId));
        String username = newUsername == null || newUsername.isBlank() ? current.username() : newUsername;
        String bankId = newBankId == null || newBankId.isBlank() ? current.bankId() : newBankId;
        if (bank(bankId).isEmpty()) {
            throw new IllegalArgumentException("unknown bank: " + bankId);
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE players SET username = ?, bank_id = ? WHERE player_id = ?")) {
            statement.setString(1, username);
            statement.setString(2, bankId);
            statement.setString(3, playerId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalArgumentException("username is taken: " + username, e);
        }
        return findById(playerId).orElseThrow();
    }

    /** Delete the player, purse, holdings and (via callers) sessions. */
    public void deletePlayer(String playerId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM players WHERE player_id = ?")) {
            statement.setString(1, playerId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("unknown player: " + playerId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("player deletion failed", e);
        }
    }

    /** Restore a purse to starting capital with no holdings and no fees. */
    public void resetPurse(String playerId, double startingCapital) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement holdings = connection.prepareStatement(
                    "DELETE FROM player_holdings WHERE player_id = ?")) {
                holdings.setString(1, playerId);
                holdings.executeUpdate();
            }
            try (PreparedStatement account = connection.prepareStatement(
                    "UPDATE player_accounts SET cash = ?, fees_paid = 0, updated_at = now() WHERE player_id = ?")) {
                account.setDouble(1, startingCapital);
                account.setString(2, playerId);
                if (account.executeUpdate() != 1) {
                    throw new IllegalArgumentException("unknown player: " + playerId);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("purse reset failed", e);
        }
    }

    public Purse loadPurse(String playerId) {
        try (Connection connection = dataSource.getConnection()) {
            double cash;
            double feesPaid;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT cash, fees_paid FROM player_accounts WHERE player_id = ?")) {
                statement.setString(1, playerId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new IllegalArgumentException("unknown player: " + playerId);
                    }
                    cash = rows.getDouble(1);
                    feesPaid = rows.getDouble(2);
                }
            }
            Map<String, Holding> holdings = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT symbol, qty, avg_price, realized FROM player_holdings WHERE player_id = ?")) {
                statement.setString(1, playerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        holdings.put(rows.getString(1), new Holding(rows.getString(1), rows.getLong(2),
                                rows.getDouble(3), rows.getDouble(4)));
                    }
                }
            }
            return new Purse(cash, feesPaid, holdings);
        } catch (SQLException e) {
            throw new IllegalStateException("purse load failed", e);
        }
    }

    public void savePurse(String playerId, double cash, double feesPaid, Map<String, Holding> holdings) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement account = connection.prepareStatement(
                    "UPDATE player_accounts SET cash = ?, fees_paid = ?, updated_at = now() WHERE player_id = ?")) {
                account.setDouble(1, cash);
                account.setDouble(2, feesPaid);
                account.setString(3, playerId);
                if (account.executeUpdate() != 1) {
                    throw new IllegalArgumentException("unknown player: " + playerId);
                }
            }
            try (PreparedStatement clear = connection.prepareStatement(
                    "DELETE FROM player_holdings WHERE player_id = ?")) {
                clear.setString(1, playerId);
                clear.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_holdings (player_id, symbol, qty, avg_price, realized)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                for (Holding holding : holdings.values()) {
                    insert.setString(1, playerId);
                    insert.setString(2, holding.symbol());
                    insert.setLong(3, holding.qty());
                    insert.setDouble(4, holding.avgPrice());
                    insert.setDouble(5, holding.realized());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("purse save failed", e);
        }
    }

    /** All banks in id order (deterministic roster assignment and choice lists). */
    public java.util.List<Bank> banks() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT bank_id, display_name, fee_per_share, ticket_minimum, custody_rate"
                                + " FROM banks ORDER BY bank_id")) {
            try (ResultSet rows = statement.executeQuery()) {
                java.util.List<Bank> banks = new java.util.ArrayList<>();
                while (rows.next()) {
                    banks.add(new Bank(rows.getString(1), rows.getString(2), rows.getDouble(3),
                            rows.getDouble(4), rows.getDouble(5)));
                }
                return banks;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("bank listing failed", e);
        }
    }

    public Optional<Bank> bank(String bankId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT bank_id, display_name, fee_per_share, ticket_minimum, custody_rate"
                                + " FROM banks WHERE bank_id = ?")) {
            statement.setString(1, bankId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Bank(rows.getString(1), rows.getString(2), rows.getDouble(3),
                        rows.getDouble(4), rows.getDouble(5)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("bank lookup failed", e);
        }
    }

    private void setStatus(String playerId, Status status) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE players SET status = ? WHERE player_id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, playerId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("unknown player: " + playerId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("status update failed", e);
        }
    }
}
