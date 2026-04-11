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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 寰呴鍙栧鍔辩鐞嗗櫒
 * 褰撶帺瀹跺湪鎶藉鍔ㄧ敾鏈熼棿绂荤嚎鏃讹紝濂栧姳浼氬瓨鍏ュ緟棰嗗彇鍒楄〃
 */
public class PendingRewardManager {

    private final FotiaCrates plugin;

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

    /**
     * 娣诲姞寰呴鍙栧鍔?
     */
    public void addPendingReward(UUID playerUuid, String crateId, Reward reward) {
        String sql = "INSERT INTO pending_rewards (uuid, crate_id, reward_id, reward_data) VALUES (?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, crateId);
            stmt.setString(3, reward.getId());
            stmt.setString(4, serializeReward(reward));
            stmt.executeUpdate();

            plugin.getLogger().info("Stored pending reward for offline player: " + playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add pending reward: " + e.getMessage());
        }
    }

    /**
     * 鑾峰彇鐜╁鐨勫緟棰嗗彇濂栧姳鏁伴噺
     */
    public int getPendingRewardCount(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM pending_rewards WHERE uuid = ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get pending reward count: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 鑾峰彇鐜╁鐨勫緟棰嗗彇濂栧姳鍒楄〃
     */
    public List<PendingReward> getPendingRewards(UUID playerUuid) {
        List<PendingReward> rewards = new ArrayList<>();
        String sql = "SELECT id, crate_id, reward_id, reward_data FROM pending_rewards WHERE uuid = ?";

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
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get pending rewards: " + e.getMessage());
        }
        return rewards;
    }

    /**
     * 棰嗗彇骞剁Щ闄ゅ緟棰嗗彇濂栧姳
     */
    public boolean claimPendingReward(Player player, int rewardId) {
        String selectSql = "SELECT crate_id, reward_id, reward_data FROM pending_rewards WHERE id = ? AND uuid = ?";
        String deleteSql = "DELETE FROM pending_rewards WHERE id = ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String crateId = null;
            String rewardIdStr = null;
            String rewardData = null;

            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setInt(1, rewardId);
                stmt.setString(2, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    crateId = rs.getString("crate_id");
                    rewardIdStr = rs.getString("reward_id");
                    rewardData = rs.getString("reward_data");
                }
            }

            if (crateId == null || rewardIdStr == null) {
                return false;
            }

            Reward reward = resolvePendingReward(crateId, rewardIdStr, rewardData);
            if (reward == null) {
                plugin.getLogger().warning("Failed to restore pending reward " + rewardIdStr + " for " + player.getUniqueId());
                return false;
            }

            reward.give(player);
            plugin.getLanguageManager().send(player, "pending-reward-claimed",
                    gg.fotia.crates.lang.LanguageManager.placeholders("reward", reward.getDisplayName()));

            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, rewardId);
                stmt.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to claim pending reward: " + e.getMessage());
            return false;
        }
    }

    /**
     * 棰嗗彇鎵€鏈夊緟棰嗗彇濂栧姳
     */
    public ClaimSummary claimAllPendingRewards(Player player) {
        List<PendingReward> rewards = getPendingRewards(player.getUniqueId());
        int claimedCount = 0;
        int failedCount = 0;

        for (PendingReward pending : rewards) {
            if (claimPendingReward(player, pending.id())) {
                claimedCount++;
            } else {
                failedCount++;
            }
        }

        return new ClaimSummary(claimedCount, failedCount);
    }

    /**
     * 鐜╁鐧诲綍鏃舵鏌ュ苟閫氱煡寰呴鍙栧鍔?
     */
    public void onPlayerJoin(Player player) {
        int count = getPendingRewardCount(player.getUniqueId());
        if (count > 0) {
            plugin.getLanguageManager().send(player, "pending-rewards-available",
                    gg.fotia.crates.lang.LanguageManager.placeholders("count", String.valueOf(count)));
        }
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
     * 寰呴鍙栧鍔辫褰?
     */
    public record PendingReward(int id, String crateId, String rewardId, String rewardData) {}

    public record ClaimSummary(int claimedCount, int failedCount) {}
}
