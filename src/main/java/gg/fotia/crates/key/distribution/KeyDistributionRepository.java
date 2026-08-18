package gg.fotia.crates.key.distribution;

import gg.fotia.crates.key.KeyType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class KeyDistributionRepository {

    private final SqlConnectionProvider connections;
    private final boolean mysql;

    public KeyDistributionRepository(SqlConnectionProvider connections, boolean mysql) {
        this.connections = connections;
        this.mysql = mysql;
    }

    public void registerKnownPlayer(UUID playerId, String playerName, long seenAt) throws SQLException {
        String sql = mysql
                ? "INSERT INTO crate_known_players (uuid, last_name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE last_name = VALUES(last_name), "
                    + "last_seen = GREATEST(last_seen, VALUES(last_seen))"
                : "INSERT INTO crate_known_players (uuid, last_name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET last_name = excluded.last_name, "
                    + "last_seen = MAX(last_seen, excluded.last_seen)";
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setLong(3, seenAt);
            statement.setLong(4, seenAt);
            statement.executeUpdate();
        }
    }

    public KeyDistributionBatch createBatch(KeyDistributionScope scope, String keyId, int amount,
                                             KeyType keyType, String createdBy,
                                             Collection<UUID> onlineRecipients, long createdAt) throws SQLException {
        if (amount < 1 || keyType == KeyType.ALL) {
            throw new IllegalArgumentException("Distribution requires a positive amount and one concrete key type");
        }
        String batchId = UUID.randomUUID().toString();
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertBatch(connection, batchId, scope, keyId, amount, keyType, createdBy, createdAt);
                if (scope == KeyDistributionScope.ALL) {
                    insertAllPlayerGrants(connection, batchId, createdAt);
                } else {
                    insertRecipientGrants(connection, batchId, onlineRecipients, createdAt);
                }
                int targetCount = countBatchTargets(connection, batchId);
                String status = targetCount == 0 ? "COMPLETED" : "READY";
                updateBatchReady(connection, batchId, targetCount, status);
                connection.commit();
                return new KeyDistributionBatch(batchId, scope, keyId, amount, keyType,
                        createdBy, createdAt, targetCount, status);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public List<AppliedVirtualKeyGrant> claimPendingVirtualGrants(UUID playerId, int limit) throws SQLException {
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<KeyDistributionGrant> grants = findPendingGrants(
                        connection, playerId, KeyType.VIRTUAL, limit);
                List<AppliedVirtualKeyGrant> applied = new ArrayList<>();
                for (KeyDistributionGrant grant : grants) {
                    if (!markProcessing(connection, grant.id())) {
                        continue;
                    }
                    incrementVirtualKeys(connection, playerId, grant.keyId(), grant.amount());
                    markDelivered(connection, grant.id(), grant.batchId(), System.currentTimeMillis(), "PROCESSING");
                    applied.add(new AppliedVirtualKeyGrant(
                            grant.id(), grant.batchId(), grant.keyId(), grant.amount()));
                }
                connection.commit();
                return applied;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public PendingKeyDistributionGrants findPendingGrants(UUID playerId, int limit) throws SQLException {
        try (Connection connection = connections.get()) {
            return new PendingKeyDistributionGrants(
                    findPendingGrants(connection, playerId, KeyType.VIRTUAL, limit),
                    findPendingGrants(connection, playerId, KeyType.PHYSICAL, limit)
            );
        }
    }

    public void markVirtualGrantsDelivered(Connection connection, Collection<Long> grantIds,
                                           long deliveredAt) throws SQLException {
        for (long grantId : grantIds) {
            String batchId = findPendingGrantBatchId(connection, grantId);
            if (batchId == null
                    || !markDelivered(connection, grantId, batchId, deliveredAt, "PENDING")) {
                throw new SQLException("Virtual key grant is no longer pending: " + grantId);
            }
        }
    }

    public List<KeyDistributionGrant> findPendingPhysicalGrants(UUID playerId, int limit) throws SQLException {
        try (Connection connection = connections.get()) {
            return findPendingGrants(connection, playerId, KeyType.PHYSICAL, limit);
        }
    }

    public boolean markPhysicalGrantDelivered(long grantId, long deliveredAt) throws SQLException {
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String batchId = findPendingGrantBatchId(connection, grantId);
                if (batchId == null) {
                    connection.rollback();
                    return false;
                }
                boolean updated = markDelivered(connection, grantId, batchId, deliveredAt, "PENDING");
                connection.commit();
                return updated;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public int markPhysicalGrantsDelivered(Collection<Long> grantIds, long deliveredAt) throws SQLException {
        if (grantIds == null || grantIds.isEmpty()) {
            return 0;
        }
        try (Connection connection = connections.get()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int delivered = 0;
                for (long grantId : grantIds) {
                    String batchId = findPendingGrantBatchId(connection, grantId);
                    if (batchId != null
                            && markDelivered(connection, grantId, batchId, deliveredAt, "PENDING")) {
                        delivered++;
                    }
                }
                connection.commit();
                return delivered;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private void insertBatch(Connection connection, String batchId, KeyDistributionScope scope,
                             String keyId, int amount, KeyType keyType, String createdBy,
                             long createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO key_distribution_batches
                (batch_id, scope, key_id, amount, key_type, status, created_by, created_at, target_count, delivered_count)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, 0, 0)
                """)) {
            statement.setString(1, batchId);
            statement.setString(2, scope.name());
            statement.setString(3, keyId);
            statement.setInt(4, amount);
            statement.setString(5, keyType.name());
            statement.setString(6, createdBy);
            statement.setLong(7, createdAt);
            statement.executeUpdate();
        }
    }

    private void insertAllPlayerGrants(Connection connection, String batchId, long createdAt) throws SQLException {
        String insert = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (PreparedStatement statement = connection.prepareStatement(insert + " INTO key_distribution_grants "
                + "(batch_id, player_uuid, status, created_at) "
                + "SELECT ?, uuid, 'PENDING', ? FROM crate_known_players WHERE first_seen <= ?")) {
            statement.setString(1, batchId);
            statement.setLong(2, createdAt);
            statement.setLong(3, createdAt);
            statement.executeUpdate();
        }
    }

    private void insertRecipientGrants(Connection connection, String batchId,
                                       Collection<UUID> recipients, long createdAt) throws SQLException {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        String insert = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (PreparedStatement statement = connection.prepareStatement(insert
                + " INTO key_distribution_grants (batch_id, player_uuid, status, created_at) "
                + "VALUES (?, ?, 'PENDING', ?)")) {
            for (UUID recipient : recipients) {
                statement.setString(1, batchId);
                statement.setString(2, recipient.toString());
                statement.setLong(3, createdAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private int countBatchTargets(Connection connection, String batchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM key_distribution_grants WHERE batch_id = ?")) {
            statement.setString(1, batchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private void updateBatchReady(Connection connection, String batchId, int targetCount,
                                  String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE key_distribution_batches SET target_count = ?, status = ? WHERE batch_id = ?")) {
            statement.setInt(1, targetCount);
            statement.setString(2, status);
            statement.setString(3, batchId);
            statement.executeUpdate();
        }
    }

    private List<KeyDistributionGrant> findPendingGrants(Connection connection, UUID playerId,
                                                         KeyType type, int limit) throws SQLException {
        List<KeyDistributionGrant> grants = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT g.id, g.batch_id, g.player_uuid, b.key_id, b.amount, b.key_type, b.created_at
                FROM key_distribution_grants g
                JOIN key_distribution_batches b ON b.batch_id = g.batch_id
                WHERE g.player_uuid = ? AND g.status = 'PENDING'
                  AND b.status = 'READY' AND b.key_type = ?
                ORDER BY b.created_at, g.id
                LIMIT ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, type.name());
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    grants.add(new KeyDistributionGrant(
                            resultSet.getLong("id"),
                            resultSet.getString("batch_id"),
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("key_id"),
                            resultSet.getInt("amount"),
                            KeyType.valueOf(resultSet.getString("key_type")),
                            resultSet.getLong("created_at")
                    ));
                }
            }
        }
        return grants;
    }

    private boolean markProcessing(Connection connection, long grantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE key_distribution_grants SET status = 'PROCESSING' WHERE id = ? AND status = 'PENDING'")) {
            statement.setLong(1, grantId);
            return statement.executeUpdate() == 1;
        }
    }

    private void incrementVirtualKeys(Connection connection, UUID playerId,
                                      String keyId, int amount) throws SQLException {
        String sql = mysql
                ? "INSERT INTO player_keys (uuid, key_id, amount) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE amount = player_keys.amount + VALUES(amount)"
                : "INSERT INTO player_keys (uuid, key_id, amount) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid, key_id) DO UPDATE SET amount = player_keys.amount + excluded.amount";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, keyId);
            statement.setInt(3, amount);
            statement.executeUpdate();
        }
    }

    private String findPendingGrantBatchId(Connection connection, long grantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT batch_id FROM key_distribution_grants WHERE id = ? AND status = 'PENDING'")) {
            statement.setLong(1, grantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("batch_id") : null;
            }
        }
    }

    private boolean markDelivered(Connection connection, long grantId, String batchId,
                                  long deliveredAt, String expectedStatus) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE key_distribution_grants SET status = 'DELIVERED', delivered_at = ? "
                        + "WHERE id = ? AND status = ?")) {
            statement.setLong(1, deliveredAt);
            statement.setLong(2, grantId);
            statement.setString(3, expectedStatus);
            if (statement.executeUpdate() != 1) {
                return false;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE key_distribution_batches SET delivered_count = delivered_count + 1 WHERE batch_id = ?")) {
            statement.setString(1, batchId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE key_distribution_batches SET status = 'COMPLETED' "
                        + "WHERE batch_id = ? AND delivered_count >= target_count")) {
            statement.setString(1, batchId);
            statement.executeUpdate();
        }
        return true;
    }
}
