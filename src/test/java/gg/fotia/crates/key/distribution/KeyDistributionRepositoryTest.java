package gg.fotia.crates.key.distribution;

import gg.fotia.crates.key.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyDistributionRepositoryTest {

    private Path databaseFile;
    private KeyDistributionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        databaseFile = Files.createTempFile("fotiacrates-distribution", ".db");
        SqlConnectionProvider connections = () -> DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        try (Connection connection = connections.get(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE player_keys (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        key_id VARCHAR(64) NOT NULL,
                        amount INTEGER DEFAULT 0,
                        UNIQUE(uuid, key_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE crate_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        crate_id VARCHAR(64) NOT NULL,
                        reward_id VARCHAR(64) NOT NULL,
                        reward_name VARCHAR(128) NOT NULL,
                        timestamp BIGINT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE pity_counter (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        crate_id VARCHAR(64) NOT NULL,
                        count INTEGER DEFAULT 0,
                        UNIQUE(uuid, crate_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE player_collected_rewards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        crate_id VARCHAR(64) NOT NULL,
                        reward_id VARCHAR(64) NOT NULL,
                        UNIQUE(uuid, crate_id, reward_id)
                    )
                    """);
            KeyDistributionSchema.createTables(connection, false);
        }
        repository = new KeyDistributionRepository(connections, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(databaseFile);
    }

    @Test
    void allPlayerBatchSurvivesRepositoryRestart() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.registerKnownPlayer(first, "First", 1_000L);
        repository.registerKnownPlayer(second, "Second", 2_000L);

        KeyDistributionBatch batch = repository.createBatch(
                KeyDistributionScope.ALL, "common_key", 3, KeyType.PHYSICAL,
                "CONSOLE", List.of(), 3_000L);

        SqlConnectionProvider reopenedConnections =
                () -> DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        KeyDistributionRepository reopened = new KeyDistributionRepository(reopenedConnections, false);

        assertEquals(2, batch.targetCount());
        assertEquals(1, reopened.findPendingPhysicalGrants(first, 10).size());
        assertEquals(1, reopened.findPendingPhysicalGrants(second, 10).size());
    }

    @Test
    void onlineBatchOnlyTargetsItsPlayerSnapshot() throws Exception {
        UUID online = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        repository.registerKnownPlayer(online, "Online", 1_000L);
        repository.registerKnownPlayer(offline, "Offline", 1_000L);

        KeyDistributionBatch batch = repository.createBatch(
                KeyDistributionScope.ONLINE, "rare_key", 1, KeyType.PHYSICAL,
                "Admin", List.of(online), 2_000L);

        assertEquals(1, batch.targetCount());
        assertEquals(1, repository.findPendingPhysicalGrants(online, 10).size());
        assertTrue(repository.findPendingPhysicalGrants(offline, 10).isEmpty());
    }

    @Test
    void virtualGrantIsAppliedExactlyOnce() throws Exception {
        UUID playerId = UUID.randomUUID();
        repository.registerKnownPlayer(playerId, "Player", 1_000L);
        repository.createBatch(KeyDistributionScope.ALL, "common_key", 5, KeyType.VIRTUAL,
                "CONSOLE", List.of(), 2_000L);

        List<AppliedVirtualKeyGrant> firstClaim = repository.claimPendingVirtualGrants(playerId, 10);
        List<AppliedVirtualKeyGrant> secondClaim = repository.claimPendingVirtualGrants(playerId, 10);

        assertEquals(1, firstClaim.size());
        assertTrue(secondClaim.isEmpty());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT amount FROM player_keys WHERE uuid = '" + playerId + "' AND key_id = 'common_key'")) {
            assertTrue(resultSet.next());
            assertEquals(5, resultSet.getInt("amount"));
        }
    }

    @Test
    void physicalGrantRemainsPendingUntilFinalized() throws Exception {
        UUID playerId = UUID.randomUUID();
        repository.registerKnownPlayer(playerId, "Player", 1_000L);
        repository.createBatch(KeyDistributionScope.ALL, "rare_key", 2, KeyType.PHYSICAL,
                "CONSOLE", List.of(), 2_000L);

        KeyDistributionGrant grant = repository.findPendingPhysicalGrants(playerId, 10).get(0);
        assertEquals(1, repository.findPendingPhysicalGrants(playerId, 10).size());

        assertTrue(repository.markPhysicalGrantDelivered(grant.id(), 3_000L));
        assertTrue(repository.findPendingPhysicalGrants(playerId, 10).isEmpty());
    }

    @Test
    void schemaRecordsTheOneTimeKnownPlayerBackfill() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile)) {
            KeyDistributionSchema.createTables(connection, false);
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM crate_schema_migrations "
                                 + "WHERE migration_id = 'key-distribution-known-players-v1'")) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }
        }
    }
}
