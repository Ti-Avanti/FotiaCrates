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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 管理玩家离线时暂存的抽奖奖励。
 */
public class PendingRewardManager {

    private final FotiaCrates plugin;
    private final Set<UUID> claimingPlayers = new HashSet<>();

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
    }

    public void addPendingReward(UUID playerUuid, String crateId, Reward reward) {
        String rewardId = reward.getId();
        String rewardData = serializeReward(reward);
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            insertPendingReward(playerUuid, crateId, rewardId, rewardData);
            return null;
        }, ignored -> plugin.getLogger().info("Stored pending reward for offline player: " + playerUuid),
                exception -> plugin.getLogger().severe("Failed to add pending reward: " + exception.getMessage()));
    }

    private void insertPendingReward(UUID playerUuid, String crateId, String rewardId, String rewardData)
            throws SQLException {
        String sql = "INSERT INTO pending_rewards (uuid, crate_id, reward_id, reward_data) VALUES (?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, crateId);
            stmt.setString(3, rewardId);
            stmt.setString(4, rewardData);
            stmt.executeUpdate();
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
        String sql = "SELECT COUNT(*) FROM pending_rewards WHERE uuid = ?";

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

    private List<PendingReward> getPendingRewards(UUID playerUuid) throws SQLException {
        List<PendingReward> rewards = new ArrayList<>();
        String sql = "SELECT id, crate_id, reward_id, reward_data FROM pending_rewards WHERE uuid = ? ORDER BY id";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rewards.add(new PendingReward(
                        rs.getInt("id"),
                        rs.getString("crate_id"),
                        rs.getString("reward_id"),
                        rs.getString("reward_data")
                ));
            }
        }
        return rewards;
    }

    public boolean claimAllPendingRewards(Player player, Consumer<ClaimSummary> onComplete) {
        UUID playerUuid = player.getUniqueId();
        if (!claimingPlayers.add(playerUuid)) {
            return false;
        }

        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(
                () -> getPendingRewards(playerUuid),
                pendingRewards -> givePendingRewards(player, pendingRewards, onComplete),
                exception -> failClaim(playerUuid, onComplete, exception)
        );
        return true;
    }

    private void givePendingRewards(Player player, List<PendingReward> pendingRewards,
                                    Consumer<ClaimSummary> onComplete) {
        UUID playerUuid = player.getUniqueId();
        if (!player.isOnline()) {
            claimingPlayers.remove(playerUuid);
            return;
        }

        List<Integer> claimedIds = new ArrayList<>();
        int claimedCount = 0;
        int failedCount = 0;

        for (PendingReward pending : pendingRewards) {
            Reward reward = resolvePendingReward(pending.crateId(), pending.rewardId(), pending.rewardData());
            if (reward == null) {
                plugin.getLogger().warning("Failed to restore pending reward " + pending.rewardId()
                        + " for " + playerUuid);
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
                plugin.getLogger().severe("Failed to give pending reward " + pending.rewardId()
                        + " to " + playerUuid + ": " + exception.getMessage());
                failedCount++;
            }
        }

        ClaimSummary summary = new ClaimSummary(claimedCount, failedCount);
        if (claimedIds.isEmpty()) {
            claimingPlayers.remove(playerUuid);
            onComplete.accept(summary);
            return;
        }

        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            deletePendingRewards(playerUuid, claimedIds);
            return summary;
        }, completedSummary -> {
            claimingPlayers.remove(playerUuid);
            onComplete.accept(completedSummary);
        }, exception -> {
            claimingPlayers.remove(playerUuid);
            plugin.getLogger().severe("Failed to delete claimed pending rewards for " + playerUuid
                    + ": " + exception.getMessage());
            onComplete.accept(new ClaimSummary(summary.claimedCount(),
                    summary.failedCount() + summary.claimedCount()));
        });
    }

    private void deletePendingRewards(UUID playerUuid, List<Integer> rewardIds) throws SQLException {
        String sql = "DELETE FROM pending_rewards WHERE id = ? AND uuid = ?";
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int rewardId : rewardIds) {
                    statement.setInt(1, rewardId);
                    statement.setString(2, playerUuid.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void failClaim(UUID playerUuid, Consumer<ClaimSummary> onComplete, Exception exception) {
        claimingPlayers.remove(playerUuid);
        plugin.getLogger().severe("Failed to load pending rewards for " + playerUuid + ": "
                + exception.getMessage());
        onComplete.accept(new ClaimSummary(0, 1));
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

    public record ClaimSummary(int claimedCount, int failedCount) {}
}
