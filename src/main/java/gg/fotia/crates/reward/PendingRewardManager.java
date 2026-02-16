package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 待领取奖励管理器
 * 当玩家在抽奖动画期间离线时，奖励会存入待领取列表
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
     * 添加待领取奖励
     */
    public void addPendingReward(UUID playerUuid, String crateId, Reward reward) {
        String sql = "INSERT INTO pending_rewards (uuid, crate_id, reward_id, reward_data) VALUES (?, ?, ?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, crateId);
            stmt.setString(3, reward.getId());
            stmt.setString(4, reward.getDisplayName()); // 简单存储显示名称
            stmt.executeUpdate();

            plugin.getLogger().info("Stored pending reward for offline player: " + playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add pending reward: " + e.getMessage());
        }
    }

    /**
     * 获取玩家的待领取奖励数量
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
     * 获取玩家的待领取奖励列表
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
     * 领取并移除待领取奖励
     */
    public void claimPendingReward(Player player, int rewardId) {
        // 先获取奖励信息
        String selectSql = "SELECT crate_id, reward_id FROM pending_rewards WHERE id = ? AND uuid = ?";
        String deleteSql = "DELETE FROM pending_rewards WHERE id = ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // 获取奖励
            String crateId = null;
            String rewardIdStr = null;

            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setInt(1, rewardId);
                stmt.setString(2, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    crateId = rs.getString("crate_id");
                    rewardIdStr = rs.getString("reward_id");
                }
            }

            if (crateId == null || rewardIdStr == null) {
                return;
            }

            // 给予奖励
            var crate = plugin.getCrateManager().getCrate(crateId);
            if (crate != null) {
                final String finalRewardIdStr = rewardIdStr;
                Reward reward = crate.getRewards().stream()
                        .filter(r -> r.getId().equals(finalRewardIdStr))
                        .findFirst()
                        .orElse(null);

                if (reward != null) {
                    reward.give(player);
                    plugin.getLanguageManager().send(player, "pending-reward-claimed",
                            gg.fotia.crates.lang.LanguageManager.placeholders("reward", reward.getDisplayName()));
                }
            }

            // 删除记录
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, rewardId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to claim pending reward: " + e.getMessage());
        }
    }

    /**
     * 领取所有待领取奖励
     */
    public void claimAllPendingRewards(Player player) {
        List<PendingReward> rewards = getPendingRewards(player.getUniqueId());
        for (PendingReward pending : rewards) {
            claimPendingReward(player, pending.id());
        }
    }

    /**
     * 玩家登录时检查并通知待领取奖励
     */
    public void onPlayerJoin(Player player) {
        int count = getPendingRewardCount(player.getUniqueId());
        if (count > 0) {
            plugin.getLanguageManager().send(player, "pending-rewards-available",
                    gg.fotia.crates.lang.LanguageManager.placeholders("count", String.valueOf(count)));
        }
    }

    /**
     * 待领取奖励记录
     */
    public record PendingReward(int id, String crateId, String rewardId, String rewardData) {}
}
