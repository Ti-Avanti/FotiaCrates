package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 管理玩家离线时暂存的抽奖奖励。
 */
public class PendingRewardManager {

    private final FotiaCrates plugin;
    private final Set<UUID> claimingPlayers = new HashSet<>();
    private final Map<UUID, PendingInsert> pendingInserts = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public PendingRewardManager(FotiaCrates plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS pending_rewards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid VARCHAR(36) NOT NULL,
                crate_id VARCHAR(64) NOT NULL,
                reward_id VARCHAR(64) NOT NULL,
                reward_data TEXT NOT NULL,
                claim_token VARCHAR(36),
                claim_started_at BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        if (plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql")) {
            sql = """
                CREATE TABLE IF NOT EXISTS pending_rewards (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    reward_data TEXT NOT NULL,
                    claim_token VARCHAR(36),
                    claim_started_at BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_uuid (uuid)
                )
                """;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create pending_rewards table: " + e.getMessage());
        }

        ensureClaimColumns();
        ensureClaimIndex();
        auditLockedClaims();
    }

    private void ensureClaimColumns() {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            ensureColumn(connection, "claim_token", "VARCHAR(36)");
            ensureColumn(connection, "claim_started_at", "BIGINT");
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to migrate pending reward claims: " + exception.getMessage());
        }
    }

    private void ensureColumn(Connection connection, String column, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM pending_rewards WHERE 1 = 0")) {
            statement.executeQuery();
            return;
        } catch (SQLException ignored) {
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE pending_rewards ADD COLUMN " + column + " " + definition)) {
            statement.executeUpdate();
        }
    }

    private void ensureClaimIndex() {
        if (plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql")) {
            return;
        }
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE INDEX IF NOT EXISTS idx_pending_rewards_uuid_claim "
                             + "ON pending_rewards (uuid, claim_token, id)")) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to create pending reward claim index: " + exception.getMessage());
        }
    }

    public void addPendingReward(UUID playerUuid, String crateId, Reward reward) {
        PendingInsert pendingInsert = new PendingInsert(
                UUID.randomUUID(), playerUuid, crateId, reward.getId(), serializeReward(reward));
        pendingInserts.put(pendingInsert.operationId(), pendingInsert);
        submitPendingInsert(pendingInsert, 0);
    }

    private void submitPendingInsert(PendingInsert pendingInsert, int attempt) {
        if (shuttingDown) {
            return;
        }

        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            insertPendingReward(pendingInsert);
            pendingInserts.remove(pendingInsert.operationId(), pendingInsert);
            return null;
        }, ignored -> plugin.getLogger().info(
                "Stored pending reward for offline player: " + pendingInsert.playerUuid()),
                exception -> schedulePendingInsertRetry(pendingInsert, attempt, exception));
    }

    private void schedulePendingInsertRetry(PendingInsert pendingInsert, int attempt, Exception exception) {
        if (shuttingDown || !pendingInserts.containsKey(pendingInsert.operationId())) {
            return;
        }
        if (attempt == 0 || attempt % 10 == 0) {
            plugin.getLogger().warning("Failed to store pending reward for " + pendingInsert.playerUuid()
                    + "; retrying without dropping the reward: " + exception.getMessage());
        }

        long baseDelay = plugin.getConfigManager().getPendingRewardInsertRetryDelayTicks();
        long delay = Math.min(baseDelay << Math.min(attempt, 5), 20L * 30L);
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> submitPendingInsert(pendingInsert, attempt + 1), delay);
        } catch (RuntimeException ignored) {
            // Plugin shutdown performs a synchronous final flush of this in-memory outbox.
        }
    }

    private void insertPendingReward(PendingInsert pendingInsert) throws SQLException {
        String sql = "INSERT INTO pending_rewards (uuid, crate_id, reward_id, reward_data) VALUES (?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindPendingInsert(stmt, pendingInsert);
            stmt.executeUpdate();
        }
    }

    private void bindPendingInsert(PreparedStatement statement, PendingInsert pendingInsert) throws SQLException {
        statement.setString(1, pendingInsert.playerUuid().toString());
        statement.setString(2, pendingInsert.crateId());
        statement.setString(3, pendingInsert.rewardId());
        statement.setString(4, pendingInsert.rewardData());
    }

    public void beginShutdown() {
        shuttingDown = true;
    }

    public void flushPendingInserts() {
        List<PendingInsert> remaining = new ArrayList<>(pendingInserts.values());
        if (remaining.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO pending_rewards (uuid, crate_id, reward_id, reward_data) VALUES (?, ?, ?, ?)";
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (PendingInsert pendingInsert : remaining) {
                    bindPendingInsert(statement, pendingInsert);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
                for (PendingInsert pendingInsert : remaining) {
                    pendingInserts.remove(pendingInsert.operationId(), pendingInsert);
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to flush " + remaining.size()
                    + " pending reward inserts during shutdown: " + exception.getMessage());
        }
    }

    public int getUnflushedInsertCount() {
        return pendingInserts.size();
    }

    private void auditLockedClaims() {
        String sql = "SELECT COUNT(*) FROM pending_rewards WHERE claim_token IS NOT NULL";
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                plugin.getLogger().warning("Found " + resultSet.getInt(1)
                        + " locked pending rewards from an interrupted or uncertain delivery. "
                        + "They remain locked to prevent duplicate rewards; review claim_token before manual recovery.");
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to audit locked pending rewards: " + exception.getMessage());
        }
    }

    public void getPendingRewardCountAsync(UUID playerUuid, IntConsumer onSuccess) {
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(
                () -> getPendingRewardCount(playerUuid),
                onSuccess::accept,
                exception -> plugin.getLogger().severe(
                        "Failed to get pending reward count: " + exception.getMessage())
        );
    }

    private int getPendingRewardCount(UUID playerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM pending_rewards WHERE uuid = ? AND claim_token IS NULL";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public boolean claimAllPendingRewards(Player player, Consumer<ClaimSummary> onComplete) {
        UUID playerUuid = player.getUniqueId();
        if (!claimingPlayers.add(playerUuid)) {
            return false;
        }

        claimNextBatch(player, UUID.randomUUID(), new ClaimProgress(0, 0), onComplete);
        return true;
    }

    private void claimNextBatch(Player player, UUID claimToken, ClaimProgress progress,
                                Consumer<ClaimSummary> onComplete) {
        UUID playerUuid = player.getUniqueId();
        if (!player.isOnline()) {
            finishClaim(playerUuid, progress, onComplete);
            return;
        }

        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(
                () -> reservePendingRewards(playerUuid, claimToken),
                pendingRewards -> {
                    if (pendingRewards.isEmpty()) {
                        finishClaim(playerUuid, progress, onComplete);
                        return;
                    }
                    givePendingRewards(player, claimToken, pendingRewards, progress, onComplete);
                },
                exception -> failClaim(playerUuid, progress, onComplete, exception)
        );
    }

    private List<PendingReward> reservePendingRewards(UUID playerUuid, UUID claimToken) throws SQLException {
        List<PendingReward> candidates = new ArrayList<>();
        List<PendingReward> reserved = new ArrayList<>();
        String selectSql = "SELECT id, crate_id, reward_id, reward_data FROM pending_rewards "
                + "WHERE uuid = ? AND claim_token IS NULL ORDER BY id LIMIT ?";
        String reserveSql = "UPDATE pending_rewards SET claim_token = ?, claim_started_at = ? "
                + "WHERE id = ? AND uuid = ? AND claim_token IS NULL";

        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    select.setString(1, playerUuid.toString());
                    select.setInt(2, plugin.getConfigManager().getPendingRewardClaimBatchSize());
                    try (ResultSet resultSet = select.executeQuery()) {
                        while (resultSet.next()) {
                            candidates.add(new PendingReward(
                                    resultSet.getInt("id"),
                                    resultSet.getString("crate_id"),
                                    resultSet.getString("reward_id"),
                                    resultSet.getString("reward_data")
                            ));
                        }
                    }
                }

                try (PreparedStatement reserve = connection.prepareStatement(reserveSql)) {
                    for (PendingReward candidate : candidates) {
                        reserve.setString(1, claimToken.toString());
                        reserve.setLong(2, System.currentTimeMillis());
                        reserve.setInt(3, candidate.id());
                        reserve.setString(4, playerUuid.toString());
                        if (reserve.executeUpdate() == 1) {
                            reserved.add(candidate);
                        }
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return reserved;
    }

    private void givePendingRewards(Player player, UUID claimToken, List<PendingReward> pendingRewards,
                                    ClaimProgress progress, Consumer<ClaimSummary> onComplete) {
        UUID playerUuid = player.getUniqueId();
        if (!player.isOnline()) {
            List<Integer> reservedIds = pendingRewards.stream().map(PendingReward::id).toList();
            finalizeClaimBatch(player, claimToken, List.of(), reservedIds, progress, false, onComplete, 0);
            return;
        }

        List<Integer> claimedIds = new ArrayList<>();
        List<Integer> retryIds = new ArrayList<>();
        int claimedCount = progress.claimedCount();
        int failedCount = progress.failedCount();

        for (PendingReward pending : pendingRewards) {
            Reward reward = resolvePendingReward(pending.crateId(), pending.rewardId(), pending.rewardData());
            if (reward == null) {
                plugin.getLogger().warning("Failed to restore pending reward " + pending.rewardId()
                        + " for " + playerUuid);
                retryIds.add(pending.id());
                failedCount++;
                continue;
            }

            try {
                reward.give(player);
                plugin.getLanguageManager().send(player, "pending-reward-claimed",
                        gg.fotia.crates.lang.LanguageManager.placeholders("reward", reward.getDisplayName()));
                claimedIds.add(pending.id());
                claimedCount++;
            } catch (RuntimeException exception) {
                if (plugin.getConfigManager().shouldRetryPendingRewardDeliveryErrors()) {
                    retryIds.add(pending.id());
                    plugin.getLogger().warning("Failed to give pending reward " + pending.rewardId()
                            + " to " + playerUuid + "; reward row " + pending.id()
                            + " will be released for a later retry. A partial custom command may run again: "
                            + exception.getMessage());
                } else {
                    plugin.getLogger().severe("Failed to give pending reward " + pending.rewardId()
                            + " to " + playerUuid
                            + "; claim token " + claimToken + " and reward row " + pending.id()
                            + " remain locked because delivery may be partial: "
                            + exception.getMessage());
                }
                failedCount++;
            }
        }

        ClaimProgress updatedProgress = new ClaimProgress(claimedCount, failedCount);
        boolean continueClaim = retryIds.isEmpty() && player.isOnline();
        finalizeClaimBatch(player, claimToken, claimedIds, retryIds, updatedProgress,
                continueClaim, onComplete, 0);
    }

    private void finalizeClaimBatch(Player player, UUID claimToken, List<Integer> claimedIds,
                                    List<Integer> retryIds, ClaimProgress progress, boolean continueClaim,
                                    Consumer<ClaimSummary> onComplete, int attempt) {
        UUID playerUuid = player.getUniqueId();
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            finalizeReservedRewards(playerUuid, claimToken, claimedIds, retryIds);
            return null;
        }, ignored -> {
            if (continueClaim && player.isOnline()) {
                claimNextBatch(player, claimToken, progress, onComplete);
                return;
            }
            finishClaim(playerUuid, progress, onComplete);
        }, exception -> {
            int maxRetries = plugin.getConfigManager().getPendingRewardFinalizeRetryCount();
            if (attempt < maxRetries && plugin.isEnabled()) {
                long baseDelay = plugin.getConfigManager().getPendingRewardFinalizeRetryDelayTicks();
                long delay = Math.min(baseDelay << Math.min(attempt, 5), 20L * 30L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> finalizeClaimBatch(
                        player, claimToken, claimedIds, retryIds, progress, continueClaim,
                        onComplete, attempt + 1), delay);
                return;
            }

            plugin.getLogger().severe("Failed to finalize claimed pending rewards for " + playerUuid
                    + "; claim token " + claimToken + ", delivered rows " + claimedIds
                    + " and retry rows " + retryIds
                    + " were left locked to prevent duplicate delivery: "
                    + exception.getMessage());
            finishClaim(playerUuid, new ClaimProgress(progress.claimedCount(),
                    progress.failedCount() + 1), onComplete);
        });
    }

    private void finalizeReservedRewards(UUID playerUuid, UUID claimToken, List<Integer> claimedIds,
                                         List<Integer> retryIds) throws SQLException {
        String deleteSql = "DELETE FROM pending_rewards WHERE id = ? AND uuid = ? AND claim_token = ?";
        String releaseSql = "UPDATE pending_rewards SET claim_token = NULL, claim_started_at = NULL "
                + "WHERE id = ? AND uuid = ? AND claim_token = ?";
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!claimedIds.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                        for (int rewardId : claimedIds) {
                            statement.setInt(1, rewardId);
                            statement.setString(2, playerUuid.toString());
                            statement.setString(3, claimToken.toString());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }

                if (!retryIds.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(releaseSql)) {
                        for (int rewardId : retryIds) {
                            statement.setInt(1, rewardId);
                            statement.setString(2, playerUuid.toString());
                            statement.setString(3, claimToken.toString());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void finishClaim(UUID playerUuid, ClaimProgress progress, Consumer<ClaimSummary> onComplete) {
        claimingPlayers.remove(playerUuid);
        onComplete.accept(new ClaimSummary(progress.claimedCount(), progress.failedCount()));
    }

    private void failClaim(UUID playerUuid, ClaimProgress progress, Consumer<ClaimSummary> onComplete,
                           Exception exception) {
        plugin.getLogger().severe("Failed to load pending rewards for " + playerUuid + ": "
                + exception.getMessage());
        finishClaim(playerUuid, new ClaimProgress(progress.claimedCount(),
                progress.failedCount() + 1), onComplete);
    }

    public void onPlayerJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        getPendingRewardCountAsync(playerUuid, count -> {
            if (count > 0 && player.isOnline() && playerUuid.equals(player.getUniqueId())) {
                plugin.getLanguageManager().send(player, "pending-rewards-available",
                        gg.fotia.crates.lang.LanguageManager.placeholders("count", String.valueOf(count)));
            }
        });
    }

    private String serializeReward(Reward reward) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", reward.getId());
        config.set("display-name", reward.getDisplayName());
        config.set("rarity", reward.getRarity());
        config.set("chance", reward.getChance());
        config.set("broadcast", reward.shouldBroadcast());
        config.set("type", reward.getType().name());
        config.set("display-item", reward.getDisplayItem());
        config.set("commands", reward.getCommands());
        config.set("item", reward.getItem());
        config.set("extra-items", reward.getExtraItems());

        switch (reward.getType()) {
            case ITEM, COMMAND -> {
            }
            case MONEY -> {
                if (reward instanceof MoneyReward moneyReward) {
                    config.set("amount", moneyReward.getAmount());
                }
            }
            case EXPERIENCE -> {
                if (reward instanceof ExperienceReward experienceReward) {
                    config.set("amount", experienceReward.getAmount());
                    config.set("levels", experienceReward.isLevels());
                }
            }
        }

        return config.saveToString();
    }

    private Reward resolvePendingReward(String crateId, String rewardId, String rewardData) {
        Reward storedReward = deserializeReward(rewardData);
        if (storedReward != null) {
            return storedReward;
        }

        var crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            return null;
        }

        return crate.getRewards().stream()
                .filter(reward -> reward.getId().equals(rewardId))
                .findFirst()
                .orElse(null);
    }

    private Reward deserializeReward(String rewardData) {
        if (rewardData == null || rewardData.isBlank()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(rewardData));
        String typeName = config.getString("type");
        if (typeName == null || typeName.isBlank()) {
            return null;
        }

        RewardType rewardType;
        try {
            rewardType = RewardType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String id = config.getString("id", "pending_reward");
        String displayName = config.getString("display-name", id);
        String rarity = config.getString("rarity", plugin.getConfigManager().getDefaultRarityId());
        double chance = config.getDouble("chance", 0D);
        boolean broadcast = config.getBoolean("broadcast", false);
        ItemStack displayItem = config.getItemStack("display-item");
        if (displayItem == null) {
            displayItem = new ItemStack(Material.PAPER);
        }

        ItemStack item = config.getItemStack("item");
        List<ItemStack> extraItems = new ArrayList<>();
        for (Object extraItem : config.getList("extra-items", List.of())) {
            if (extraItem instanceof ItemStack extraStack) {
                extraItems.add(extraStack);
            }
        }
        List<String> commands = config.getStringList("commands");

        return switch (rewardType) {
            case ITEM -> new ItemReward(id, displayName, rarity, chance, broadcast, displayItem,
                    item, extraItems, commands);
            case COMMAND -> new CommandReward(id, displayName, rarity, chance, broadcast, displayItem,
                    commands, item, extraItems);
            case MONEY -> new MoneyReward(id, displayName, rarity, chance, broadcast, displayItem,
                    config.getDouble("amount", 0D));
            case EXPERIENCE -> new ExperienceReward(id, displayName, rarity, chance, broadcast, displayItem,
                    config.getInt("amount", 0), config.getBoolean("levels", false));
        };
    }

    /**
     * 数据库中的待领取奖励快照。
     */
    public record PendingReward(int id, String crateId, String rewardId, String rewardData) {}

    private record ClaimProgress(int claimedCount, int failedCount) {}

    private record PendingInsert(UUID operationId, UUID playerUuid, String crateId,
                                 String rewardId, String rewardData) {}

    public record ClaimSummary(int claimedCount, int failedCount) {}
}
