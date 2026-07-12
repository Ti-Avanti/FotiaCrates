package gg.fotia.crates.history;

import gg.fotia.crates.FotiaCrates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class HistoryManager {

    private final FotiaCrates plugin;

    public HistoryManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void addHistory(UUID uuid, String playerName, String crateId, String rewardId, String rewardName) {
        plugin.getAsyncPlayerDataManager().queueHistory(uuid, playerName, crateId, rewardId, rewardName);
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
        return getHistory(uuid, null, limit);
    }

    public void getHistoryAsync(UUID uuid, int limit, Consumer<List<HistoryEntry>> callback) {
        getHistoryAsync(uuid, null, limit, callback);
    }

    public void getHistoryAsync(UUID uuid, String crateId, int limit, Consumer<List<HistoryEntry>> callback) {
        plugin.getAsyncPlayerDataManager().getHistoryAsync(uuid, crateId, limit, callback);
    }

    public List<HistoryEntry> getHistory(UUID uuid, String crateId, int limit) {
        List<HistoryEntry> history = new ArrayList<>();
        boolean filterByCrate = crateId != null && !crateId.isBlank();
        String sql = filterByCrate
                ? "SELECT * FROM crate_history WHERE uuid = ? AND crate_id = ? ORDER BY timestamp DESC LIMIT ?"
                : "SELECT * FROM crate_history WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            int parameterIndex = 2;
            if (filterByCrate) {
                stmt.setString(parameterIndex++, crateId);
            }
            stmt.setInt(parameterIndex, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                history.add(mapHistoryEntry(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get history: " + e.getMessage());
        }
        return history;
    }

    public int clearHistory(UUID uuid) {
        return clearHistory(uuid, null);
    }

    public void clearHistoryAsync(UUID uuid, String crateId, IntConsumer callback) {
        plugin.getAsyncPlayerDataManager().clearHistoryAsync(uuid, crateId, callback);
    }

    public int clearHistory(UUID uuid, String crateId) {
        boolean filterByCrate = crateId != null && !crateId.isBlank();
        String sql = filterByCrate
                ? "DELETE FROM crate_history WHERE uuid = ? AND crate_id = ?"
                : "DELETE FROM crate_history WHERE uuid = ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            if (filterByCrate) {
                stmt.setString(2, crateId);
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear history: " + e.getMessage());
            return 0;
        }
    }

    private HistoryEntry mapHistoryEntry(ResultSet rs) throws SQLException {
        return new HistoryEntry(
                rs.getString("uuid"),
                rs.getString("player_name"),
                rs.getString("crate_id"),
                rs.getString("reward_id"),
                rs.getString("reward_name"),
                rs.getLong("timestamp")
        );
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
