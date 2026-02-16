package gg.fotia.crates.pity;

import gg.fotia.crates.FotiaCrates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PityManager {

    private final FotiaCrates plugin;

    public PityManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public int getPityCount(UUID uuid, String crateId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT count FROM pity_counter WHERE uuid = ? AND crate_id = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, crateId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get pity count: " + e.getMessage());
        }
        return 0;
    }

    public void setPityCount(UUID uuid, String crateId, int count) {
        String sql = plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql")
                ? "INSERT INTO pity_counter (uuid, crate_id, count) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE count = VALUES(count)"
                : "INSERT OR REPLACE INTO pity_counter (uuid, crate_id, count) VALUES (?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, crateId);
            stmt.setInt(3, count);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set pity count: " + e.getMessage());
        }
    }

    public void incrementPityCount(UUID uuid, String crateId) {
        int current = getPityCount(uuid, crateId);
        setPityCount(uuid, crateId, current + 1);
    }

    public void resetPityCount(UUID uuid, String crateId) {
        setPityCount(uuid, crateId, 0);
    }

    public boolean shouldTriggerPity(UUID uuid, String crateId, int pityThreshold) {
        return getPityCount(uuid, crateId) >= pityThreshold - 1;
    }
}
