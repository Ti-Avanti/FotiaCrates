package gg.fotia.crates.history;

import gg.fotia.crates.FotiaCrates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HistoryManager {

    private final FotiaCrates plugin;

    public HistoryManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void addHistory(UUID uuid, String playerName, String crateId, String rewardId, String rewardName) {
        if (!plugin.getConfigManager().isSaveHistory()) {
            return;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO crate_history (uuid, player_name, crate_id, reward_id, reward_name, timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, crateId);
            stmt.setString(4, rewardId);
            stmt.setString(5, rewardName);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();

            cleanupOldHistory(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add history: " + e.getMessage());
        }
    }

    private void cleanupOldHistory(UUID uuid) {
        int maxEntries = plugin.getConfigManager().getMaxHistoryEntries();
        if (maxEntries <= 0) return;

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            List<Long> keepIds = new ArrayList<>();
            try (PreparedStatement selectStmt = conn.prepareStatement(
                    "SELECT id FROM crate_history WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
                selectStmt.setString(1, uuid.toString());
                selectStmt.setInt(2, maxEntries);
                ResultSet rs = selectStmt.executeQuery();
                while (rs.next()) {
                    keepIds.add(rs.getLong("id"));
                }
            }

            if (keepIds.isEmpty()) return;

            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < keepIds.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }

            String deleteSql = "DELETE FROM crate_history WHERE uuid = ? AND id NOT IN (" + placeholders + ")";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, uuid.toString());
                for (int i = 0; i < keepIds.size(); i++) {
                    deleteStmt.setLong(i + 2, keepIds.get(i));
                }
                deleteStmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to cleanup history: " + e.getMessage());
        }
    }

    public List<HistoryEntry> getHistory(UUID uuid, int limit) {
        List<HistoryEntry> history = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM crate_history WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                history.add(new HistoryEntry(
                        rs.getString("uuid"),
                        rs.getString("player_name"),
                        rs.getString("crate_id"),
                        rs.getString("reward_id"),
                        rs.getString("reward_name"),
                        rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get history: " + e.getMessage());
        }
        return history;
    }

    public static class HistoryEntry {
        private final String uuid;
        private final String playerName;
        private final String crateId;
        private final String rewardId;
        private final String rewardName;
        private final long timestamp;

        public HistoryEntry(String uuid, String playerName, String crateId, String rewardId, String rewardName, long timestamp) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.crateId = crateId;
            this.rewardId = rewardId;
            this.rewardName = rewardName;
            this.timestamp = timestamp;
        }

        public String getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public String getCrateId() { return crateId; }
        public String getRewardId() { return rewardId; }
        public String getRewardName() { return rewardName; }
        public long getTimestamp() { return timestamp; }

        // Record-style accessor methods
        public String crateId() { return crateId; }
        public String rewardName() { return rewardName; }

        public String formattedTime() {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new java.util.Date(timestamp));
        }
    }
}
