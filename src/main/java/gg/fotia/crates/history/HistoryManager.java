package gg.fotia.crates.history;

import gg.fotia.crates.FotiaCrates;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 历史记录门面：全部读写走 AsyncPlayerDataManager 的异步单线程队列，主线程不做任何 JDBC。
 */
public class HistoryManager {

    private final FotiaCrates plugin;

    public HistoryManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void addHistory(UUID uuid, String playerName, String crateId, String rewardId, String rewardName) {
        plugin.getAsyncPlayerDataManager().queueHistory(uuid, playerName, crateId, rewardId, rewardName);
    }

    public void getHistoryAsync(UUID uuid, int limit, Consumer<List<HistoryEntry>> callback) {
        getHistoryAsync(uuid, null, limit, callback);
    }

    public void getHistoryAsync(UUID uuid, String crateId, int limit, Consumer<List<HistoryEntry>> callback) {
        plugin.getAsyncPlayerDataManager().getHistoryAsync(uuid, crateId, limit, callback);
    }

    public void clearHistoryAsync(UUID uuid, String crateId, IntConsumer callback) {
        plugin.getAsyncPlayerDataManager().clearHistoryAsync(uuid, crateId, callback);
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
