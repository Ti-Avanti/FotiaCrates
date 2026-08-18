package gg.fotia.crates.key.distribution;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class KeyDistributionSchema {

    private static final String KNOWN_PLAYER_MIGRATION = "key-distribution-known-players-v1";

    private KeyDistributionSchema() {
    }

    public static void createTables(Connection connection, boolean mysql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(mysql ? """
                    CREATE TABLE IF NOT EXISTS crate_schema_migrations (
                        migration_id VARCHAR(96) PRIMARY KEY,
                        applied_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """ : """
                    CREATE TABLE IF NOT EXISTS crate_schema_migrations (
                        migration_id VARCHAR(96) PRIMARY KEY,
                        applied_at BIGINT NOT NULL
                    )
                    """);
            statement.executeUpdate(mysql ? """
                    CREATE TABLE IF NOT EXISTS crate_known_players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        last_name VARCHAR(64) NOT NULL DEFAULT '',
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """ : """
                    CREATE TABLE IF NOT EXISTS crate_known_players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        last_name VARCHAR(64) NOT NULL DEFAULT '',
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL
                    )
                    """);
            statement.executeUpdate(mysql ? """
                    CREATE TABLE IF NOT EXISTS key_distribution_batches (
                        batch_id VARCHAR(36) PRIMARY KEY,
                        scope VARCHAR(16) NOT NULL,
                        key_id VARCHAR(64) NOT NULL,
                        amount INT NOT NULL,
                        key_type VARCHAR(16) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_by VARCHAR(64) NOT NULL,
                        created_at BIGINT NOT NULL,
                        target_count INT NOT NULL DEFAULT 0,
                        delivered_count INT NOT NULL DEFAULT 0,
                        INDEX idx_key_batches_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """ : """
                    CREATE TABLE IF NOT EXISTS key_distribution_batches (
                        batch_id VARCHAR(36) PRIMARY KEY,
                        scope VARCHAR(16) NOT NULL,
                        key_id VARCHAR(64) NOT NULL,
                        amount INTEGER NOT NULL,
                        key_type VARCHAR(16) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_by VARCHAR(64) NOT NULL,
                        created_at BIGINT NOT NULL,
                        target_count INTEGER NOT NULL DEFAULT 0,
                        delivered_count INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate(mysql ? """
                    CREATE TABLE IF NOT EXISTS key_distribution_grants (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        batch_id VARCHAR(36) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at BIGINT NOT NULL,
                        delivered_at BIGINT NULL,
                        UNIQUE KEY unique_key_distribution_grant (batch_id, player_uuid),
                        INDEX idx_key_grants_player_status (player_uuid, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """ : """
                    CREATE TABLE IF NOT EXISTS key_distribution_grants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        batch_id VARCHAR(36) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at BIGINT NOT NULL,
                        delivered_at BIGINT NULL,
                        UNIQUE(batch_id, player_uuid)
                    )
                    """);
            if (!mysql) {
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_key_batches_status "
                        + "ON key_distribution_batches(status)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_key_grants_player_status "
                        + "ON key_distribution_grants(player_uuid, status)");
            }
            if (!isMigrationApplied(statement, KNOWN_PLAYER_MIGRATION)) {
                long now = System.currentTimeMillis();
                backfillKnownPlayers(statement, mysql, now);
                recordMigration(statement, mysql, KNOWN_PLAYER_MIGRATION, now);
            }
        }
    }

    private static boolean isMigrationApplied(Statement statement, String migrationId) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT 1 FROM crate_schema_migrations WHERE migration_id = '" + migrationId + "'")) {
            return resultSet.next();
        }
    }

    private static void recordMigration(Statement statement, boolean mysql,
                                        String migrationId, long appliedAt) throws SQLException {
        String insert = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";
        statement.executeUpdate(insert + " INTO crate_schema_migrations (migration_id, applied_at) VALUES ('"
                + migrationId + "', " + appliedAt + ")");
    }

    private static void backfillKnownPlayers(Statement statement, boolean mysql, long now) throws SQLException {
        String insert = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";
        statement.executeUpdate(insert + " INTO crate_known_players (uuid, last_name, first_seen, last_seen) "
                + "SELECT uuid, MAX(player_name), MIN(timestamp), MAX(timestamp) FROM crate_history GROUP BY uuid");
        for (String table : new String[]{"player_keys", "pity_counter", "player_collected_rewards"}) {
            statement.executeUpdate(insert + " INTO crate_known_players (uuid, last_name, first_seen, last_seen) "
                    + "SELECT DISTINCT uuid, '', " + now + ", " + now + " FROM " + table);
        }
    }
}
