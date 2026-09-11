package gg.fotia.crates.reward.settlement;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardSnapshotCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Durable award intents. READY rows are recoverable; DELIVERING rows require confirmation. */
public final class OpenRewardJournal {
    private final FotiaCrates plugin;
    private final String ownerId;

    public OpenRewardJournal(FotiaCrates plugin) throws IOException, SQLException {
        this.plugin = plugin;
        Path identity = plugin.getDataFolder().toPath().resolve("settlement-server.id");
        if (!Files.exists(identity)) {
            Files.writeString(identity, UUID.randomUUID().toString(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        ownerId = UUID.fromString(Files.readString(identity, StandardCharsets.UTF_8).trim()).toString();
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS crate_open_rewards (
                         operation_id VARCHAR(36) PRIMARY KEY,
                         owner_id VARCHAR(36) NOT NULL,
                         uuid VARCHAR(36) NOT NULL,
                         crate_id VARCHAR(64) NOT NULL,
                         reward_id VARCHAR(64) NOT NULL,
                         reward_data TEXT NOT NULL,
                         delivery_state INTEGER NOT NULL DEFAULT 0
                     )
                     """)) {
            statement.executeUpdate();
        }
        recoverReady();
        auditUncertainDeliveries();
    }

    public List<Entry> snapshot(UUID playerId, String crateId, List<RewardResult> rewards) {
        Map<Reward, String> snapshots = new IdentityHashMap<>();
        return rewards.stream().map(result -> new Entry(result.getSettlementId(), playerId, crateId,
                result.getActualReward().getId(), snapshots.computeIfAbsent(
                        result.getActualReward(), RewardSnapshotCodec::serialize))).toList();
    }

    /** Called within the same transaction as the key and pity changes. */
    public void insert(Connection connection, List<Entry> entries) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO crate_open_rewards
                (operation_id, owner_id, uuid, crate_id, reward_id, reward_data, delivery_state)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """)) {
            for (Entry entry : entries) {
                statement.setString(1, entry.operationId().toString());
                statement.setString(2, ownerId);
                statement.setString(3, entry.playerId().toString());
                statement.setString(4, entry.crateId());
                statement.setString(5, entry.rewardId());
                statement.setString(6, entry.rewardData());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public Set<UUID> reserve(Collection<UUID> ids) throws SQLException {
        Set<UUID> reserved = new HashSet<>();
        transact(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE crate_open_rewards SET delivery_state = 1
                    WHERE operation_id = ? AND owner_id = ? AND delivery_state = 0
                    """)) {
                for (UUID id : ids) {
                    statement.setString(1, id.toString());
                    statement.setString(2, ownerId);
                    if (statement.executeUpdate() == 1) reserved.add(id);
                }
            }
        });
        return reserved;
    }

    public void acknowledge(Collection<UUID> ids) throws SQLException {
        if (ids.isEmpty()) return;
        transact(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM crate_open_rewards WHERE operation_id = ? AND owner_id = ? AND delivery_state = 1")) {
                for (UUID id : ids) {
                    statement.setString(1, id.toString());
                    statement.setString(2, ownerId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    /** Move undelivered awards to the existing claim queue without an insert/delete gap. */
    public void defer(Collection<UUID> ids) throws SQLException {
        if (ids.isEmpty()) return;
        transact(connection -> {
            for (UUID id : ids) {
                moveToPending(connection, "operation_id = ? AND owner_id = ?", id.toString());
            }
        });
    }

    public void recoverReady() throws SQLException {
        transact(connection -> moveToPending(connection, "delivery_state = 0 AND owner_id = ?", null));
    }

    private void moveToPending(Connection connection, String filter, String operationId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO pending_rewards (uuid, crate_id, reward_id, reward_data)
                SELECT uuid, crate_id, reward_id, reward_data FROM crate_open_rewards WHERE
                """ + filter);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM crate_open_rewards WHERE " + filter)) {
            for (PreparedStatement statement : List.of(insert, delete)) {
                int parameter = 1;
                if (operationId != null) statement.setString(parameter++, operationId);
                statement.setString(parameter, ownerId);
                statement.executeUpdate();
            }
        }
    }

    private void auditUncertainDeliveries() throws SQLException {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM crate_open_rewards WHERE owner_id = ? AND delivery_state = 1")) {
            statement.setString(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next() && rows.getInt(1) > 0) {
                    plugin.getLogger().warning("Found " + rows.getInt(1)
                            + " unconfirmed crate awards in crate_open_rewards for server " + ownerId
                            + ". Their snapshots remain locked for review to avoid duplicate delivery.");
                }
            }
        }
    }

    private void transact(SqlAction action) throws SQLException {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                action.run(connection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public record Entry(UUID operationId, UUID playerId, String crateId, String rewardId, String rewardData) {
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection connection) throws SQLException;
    }
}
