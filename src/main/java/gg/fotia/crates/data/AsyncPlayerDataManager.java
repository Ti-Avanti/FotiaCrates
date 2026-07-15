package gg.fotia.crates.data;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.history.HistoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Owns asynchronous persistence while the server thread works only with cached player data.
 */
public final class AsyncPlayerDataManager {

    private final FotiaCrates plugin;
    private final PlayerDataCache cache = new PlayerDataCache();
    private HistoryWriteBuffer historyBuffer;
    private final ExecutorService executor;
    private final Set<UUID> loadingPlayers = new HashSet<>();
    private final Set<UUID> committingPlayers = new HashSet<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();
    private final Map<UUID, Long> nextHistoryCleanupAt = new HashMap<>();
    private final boolean mysql;
    private volatile boolean saveHistory;
    private volatile int historyBatchSize;
    private volatile int maxHistoryEntries;
    private volatile long historyCleanupIntervalMillis;
    private volatile long shutdownFlushTimeoutMillis;
    private BukkitTask flushTask;
    private long lastHistoryOverflowWarning;

    public AsyncPlayerDataManager(FotiaCrates plugin) {
        this.plugin = plugin;
        refreshSettings();
        this.historyBuffer = new HistoryWriteBuffer(plugin.getConfigManager().getPersistenceHistoryQueueCapacity());
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FotiaCrates-Data");
            thread.setDaemon(true);
            return thread;
        });
        this.mysql = plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql");
    }

    public void start() {
        refreshSettings();
        if (flushTask != null) {
            flushTask.cancel();
        }
        long interval = plugin.getConfigManager().getPersistenceFlushIntervalTicks();
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushQueued, interval, interval);
        for (var player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    public void reload() {
        refreshSettings();
        flushQueued();
        List<HistoryWriteBuffer.Record> pendingHistory = historyBuffer.drainAll();
        historyBuffer = new HistoryWriteBuffer(Math.max(
                plugin.getConfigManager().getPersistenceHistoryQueueCapacity(), pendingHistory.size()));
        for (HistoryWriteBuffer.Record record : pendingHistory) {
            historyBuffer.offer(record);
        }
        start();
    }

    public void loadPlayer(UUID playerId) {
        if (cache.isLoaded(playerId) || !loadingPlayers.add(playerId)) {
            return;
        }

        executor.execute(() -> {
            try {
                LoadedPlayerData loaded = loadPlayerData(playerId);
                runOnServerThread(() -> {
                    loadingPlayers.remove(playerId);
                    if (!isPlayerOnline(playerId)) {
                        return;
                    }
                    cache.load(playerId, loaded.virtualKeys(), loaded.pityCounts());
                });
            } catch (SQLException exception) {
                plugin.getLogger().severe("Failed to load player data for " + playerId + ": " + exception.getMessage());
                runOnServerThread(() -> loadingPlayers.remove(playerId));
            }
        });
    }

    public <T> void executeDatabaseOperation(DatabaseOperation<T> operation,
                                             Consumer<T> onSuccess,
                                             Consumer<Exception> onFailure) {
        try {
            executor.execute(() -> {
                try {
                    T result = operation.execute();
                    if (onSuccess != null) {
                        runOnServerThread(() -> onSuccess.accept(result));
                    }
                } catch (Exception exception) {
                    if (onFailure != null) {
                        runOnServerThread(() -> onFailure.accept(exception));
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            if (onFailure != null) {
                runOnServerThread(() -> onFailure.accept(exception));
            }
        }
    }

    public boolean isReady(UUID playerId) {
        return cache.isLoaded(playerId) && !committingPlayers.contains(playerId);
    }

    public int getVirtualKeys(UUID playerId, String keyId) {
        return cache.getVirtualKeys(playerId, keyId);
    }

    public boolean setVirtualKeys(UUID playerId, String keyId, int amount) {
        if (!cache.isLoaded(playerId)) {
            return false;
        }
        cache.setVirtualKeys(playerId, keyId, amount);
        dirtyPlayers.add(playerId);
        return true;
    }

    public boolean addVirtualKeys(UUID playerId, String keyId, int amount) {
        if (!cache.isLoaded(playerId)) {
            return false;
        }
        setVirtualKeys(playerId, keyId, getVirtualKeys(playerId, keyId) + amount);
        return true;
    }

    public boolean removeVirtualKeys(UUID playerId, String keyId, int amount) {
        if (!cache.isLoaded(playerId)) {
            return false;
        }
        setVirtualKeys(playerId, keyId, Math.max(0, getVirtualKeys(playerId, keyId) - amount));
        return true;
    }

    public int getPityCount(UUID playerId, String crateId) {
        return cache.getPityCount(playerId, crateId);
    }

    public boolean setPityCount(UUID playerId, String crateId, int count) {
        if (!cache.isLoaded(playerId)) {
            return false;
        }
        cache.setPityCount(playerId, crateId, count);
        dirtyPlayers.add(playerId);
        return true;
    }

    public PlayerDataCache.Snapshot snapshot(UUID playerId) {
        return cache.snapshot(playerId);
    }

    public void restore(UUID playerId, PlayerDataCache.Snapshot snapshot) {
        cache.restore(playerId, snapshot);
        dirtyPlayers.remove(playerId);
    }

    public void commitNow(UUID playerId, Runnable onSuccess, Runnable onFailure) {
        if (!cache.isLoaded(playerId) || !committingPlayers.add(playerId)) {
            onFailure.run();
            return;
        }

        PlayerDataCache.Snapshot snapshot = cache.snapshot(playerId);
        dirtyPlayers.remove(playerId);
        executor.execute(() -> {
            try {
                persistPlayerState(playerId, snapshot);
                runOnServerThread(() -> {
                    committingPlayers.remove(playerId);
                    onSuccess.run();
                    unloadIfOffline(playerId);
                });
            } catch (SQLException exception) {
                plugin.getLogger().severe("Failed to persist player data for " + playerId + ": " + exception.getMessage());
                runOnServerThread(() -> {
                    committingPlayers.remove(playerId);
                    onFailure.run();
                    dirtyPlayers.add(playerId);
                });
            }
        });
    }

    public void queueHistory(UUID playerId, String playerName, String crateId, String rewardId, String rewardName) {
        if (!saveHistory) {
            return;
        }

        HistoryWriteBuffer.Record record = new HistoryWriteBuffer.Record(
                playerId, playerName, crateId, rewardId, rewardName, System.currentTimeMillis());
        if (historyBuffer.offer(record)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHistoryOverflowWarning >= 60_000L) {
            lastHistoryOverflowWarning = now;
            plugin.getLogger().warning("History write queue is full; new history entries are being skipped to protect TPS.");
        }
    }

    public void getHistoryAsync(UUID playerId, String crateId, int limit,
                                Consumer<List<HistoryManager.HistoryEntry>> callback) {
        executor.execute(() -> {
            List<HistoryManager.HistoryEntry> entries;
            try {
                entries = readHistory(playerId, crateId, limit);
            } catch (SQLException exception) {
                plugin.getLogger().severe("Failed to read history for " + playerId + ": " + exception.getMessage());
                entries = List.of();
            }
            List<HistoryManager.HistoryEntry> result = entries;
            runOnServerThread(() -> callback.accept(result));
        });
    }

    public void clearHistoryAsync(UUID playerId, String crateId, IntConsumer callback) {
        executor.execute(() -> {
            int cleared = 0;
            try {
                cleared = deleteHistory(playerId, crateId);
            } catch (SQLException exception) {
                plugin.getLogger().severe("Failed to clear history for " + playerId + ": " + exception.getMessage());
            }
            int result = cleared;
            runOnServerThread(() -> callback.accept(result));
        });
    }

    public void flushAndUnload(UUID playerId) {
        if (!cache.isLoaded(playerId) || committingPlayers.contains(playerId)) {
            return;
        }
        PlayerDataCache.Snapshot snapshot = cache.snapshot(playerId);
        dirtyPlayers.remove(playerId);
        loadingPlayers.remove(playerId);
        executor.execute(() -> {
            try {
                persistPlayerState(playerId, snapshot);
                runOnServerThread(() -> unloadIfOffline(playerId));
            } catch (SQLException exception) {
                plugin.getLogger().severe("Failed to flush player data for " + playerId + " on quit: " + exception.getMessage());
                runOnServerThread(() -> dirtyPlayers.add(playerId));
            }
        });
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushAllQueued();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownFlushTimeoutMillis, TimeUnit.MILLISECONDS)) {
                plugin.getLogger().warning("Timed out while flushing player data during shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void flushQueued() {
        Map<UUID, PlayerDataCache.Snapshot> stateSnapshots = createStateSnapshot();
        List<HistoryWriteBuffer.Record> historyBatch = historyBuffer.drain(historyBatchSize);
        submitFlush(stateSnapshots, historyBatch);
    }

    private void flushAllQueued() {
        Map<UUID, PlayerDataCache.Snapshot> stateSnapshots = createStateSnapshot();
        List<HistoryWriteBuffer.Record> allHistory = historyBuffer.drainAll();
        if (allHistory.isEmpty()) {
            submitFlush(stateSnapshots, List.of());
            return;
        }

        for (int start = 0; start < allHistory.size(); start += historyBatchSize) {
            int end = Math.min(start + historyBatchSize, allHistory.size());
            List<HistoryWriteBuffer.Record> batch = List.copyOf(allHistory.subList(start, end));
            submitFlush(start == 0 ? stateSnapshots : Map.of(), batch);
        }
    }

    private Map<UUID, PlayerDataCache.Snapshot> createStateSnapshot() {
        Map<UUID, PlayerDataCache.Snapshot> snapshots = new HashMap<>();
        for (UUID playerId : new HashSet<>(dirtyPlayers)) {
            if (!cache.isLoaded(playerId) || committingPlayers.contains(playerId)) {
                continue;
            }
            snapshots.put(playerId, cache.snapshot(playerId));
            dirtyPlayers.remove(playerId);
        }
        return snapshots;
    }

    private void submitFlush(Map<UUID, PlayerDataCache.Snapshot> stateSnapshots, List<HistoryWriteBuffer.Record> historyBatch) {
        if (stateSnapshots.isEmpty() && historyBatch.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                for (Map.Entry<UUID, PlayerDataCache.Snapshot> entry : stateSnapshots.entrySet()) {
                    persistPlayerState(entry.getKey(), entry.getValue());
                }
                persistHistory(historyBatch);
            } catch (SQLException exception) {
                plugin.getLogger().severe("Failed to flush asynchronous player data: " + exception.getMessage());
                runOnServerThread(() -> {
                    dirtyPlayers.addAll(stateSnapshots.keySet());
                    for (HistoryWriteBuffer.Record record : historyBatch) {
                        historyBuffer.offer(record);
                    }
                });
            }
        });
    }

    private LoadedPlayerData loadPlayerData(UUID playerId) throws SQLException {
        Map<String, Integer> virtualKeys = new HashMap<>();
        Map<String, Integer> pityCounts = new HashMap<>();
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT key_id, amount FROM player_keys WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        virtualKeys.put(resultSet.getString("key_id"), resultSet.getInt("amount"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT crate_id, count FROM pity_counter WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        pityCounts.put(resultSet.getString("crate_id"), resultSet.getInt("count"));
                    }
                }
            }
        }
        return new LoadedPlayerData(virtualKeys, pityCounts);
    }

    private void persistPlayerState(UUID playerId, PlayerDataCache.Snapshot snapshot) throws SQLException {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertVirtualKeys(connection, playerId, snapshot.virtualKeys());
                upsertPityCounts(connection, playerId, snapshot.pityCounts());
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private void upsertVirtualKeys(Connection connection, UUID playerId, Map<String, Integer> virtualKeys) throws SQLException {
        if (virtualKeys.isEmpty()) {
            return;
        }
        String sql = mysql
                ? "INSERT INTO player_keys (uuid, key_id, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)"
                : "INSERT OR REPLACE INTO player_keys (uuid, key_id, amount) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : virtualKeys.entrySet()) {
                statement.setString(1, playerId.toString());
                statement.setString(2, entry.getKey());
                statement.setInt(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertPityCounts(Connection connection, UUID playerId, Map<String, Integer> pityCounts) throws SQLException {
        if (pityCounts.isEmpty()) {
            return;
        }
        String sql = mysql
                ? "INSERT INTO pity_counter (uuid, crate_id, count) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE count = VALUES(count)"
                : "INSERT OR REPLACE INTO pity_counter (uuid, crate_id, count) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : pityCounts.entrySet()) {
                statement.setString(1, playerId.toString());
                statement.setString(2, entry.getKey());
                statement.setInt(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void persistHistory(List<HistoryWriteBuffer.Record> records) throws SQLException {
        if (records.isEmpty()) {
            return;
        }
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO crate_history (uuid, player_name, crate_id, reward_id, reward_name, timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (HistoryWriteBuffer.Record record : records) {
                    statement.setString(1, record.playerId().toString());
                    statement.setString(2, record.playerName());
                    statement.setString(3, record.crateId());
                    statement.setString(4, record.rewardId());
                    statement.setString(5, record.rewardName());
                    statement.setLong(6, record.timestamp());
                    statement.addBatch();
                }
                statement.executeBatch();
                cleanupHistory(connection, records);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private List<HistoryManager.HistoryEntry> readHistory(UUID playerId, String crateId, int limit) throws SQLException {
        boolean filterByCrate = crateId != null && !crateId.isBlank();
        String sql = filterByCrate
                ? "SELECT * FROM crate_history WHERE uuid = ? AND crate_id = ? ORDER BY timestamp DESC, id DESC LIMIT ?"
                : "SELECT * FROM crate_history WHERE uuid = ? ORDER BY timestamp DESC, id DESC LIMIT ?";
        List<HistoryManager.HistoryEntry> entries = new ArrayList<>();
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            int parameterIndex = 2;
            if (filterByCrate) {
                statement.setString(parameterIndex++, crateId);
            }
            statement.setInt(parameterIndex, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new HistoryManager.HistoryEntry(
                            resultSet.getString("uuid"),
                            resultSet.getString("player_name"),
                            resultSet.getString("crate_id"),
                            resultSet.getString("reward_id"),
                            resultSet.getString("reward_name"),
                            resultSet.getLong("timestamp")
                    ));
                }
            }
        }
        return entries;
    }

    private int deleteHistory(UUID playerId, String crateId) throws SQLException {
        boolean filterByCrate = crateId != null && !crateId.isBlank();
        String sql = filterByCrate
                ? "DELETE FROM crate_history WHERE uuid = ? AND crate_id = ?"
                : "DELETE FROM crate_history WHERE uuid = ?";
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            if (filterByCrate) {
                statement.setString(2, crateId);
            }
            return statement.executeUpdate();
        }
    }

    private void cleanupHistory(Connection connection, List<HistoryWriteBuffer.Record> records) throws SQLException {
        int maxEntries = maxHistoryEntries;
        if (maxEntries <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<UUID> affectedPlayers = new HashSet<>();
        for (HistoryWriteBuffer.Record record : records) {
            affectedPlayers.add(record.playerId());
        }
        for (UUID playerId : affectedPlayers) {
            long nextCleanup = nextHistoryCleanupAt.getOrDefault(playerId, 0L);
            if (nextCleanup > now) {
                continue;
            }
            cleanupHistoryForPlayer(connection, playerId, maxEntries);
            nextHistoryCleanupAt.put(playerId, now + historyCleanupIntervalMillis);
        }
    }

    private void cleanupHistoryForPlayer(Connection connection, UUID playerId, int maxEntries) throws SQLException {
        Long cutoffId = null;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM crate_history WHERE uuid = ? ORDER BY timestamp DESC, id DESC LIMIT 1 OFFSET ?")) {
            select.setString(1, playerId.toString());
            select.setInt(2, maxEntries);
            try (ResultSet resultSet = select.executeQuery()) {
                if (resultSet.next()) {
                    cutoffId = resultSet.getLong("id");
                }
            }
        }
        if (cutoffId == null) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM crate_history WHERE uuid = ? AND id <= ?")) {
            delete.setString(1, playerId.toString());
            delete.setLong(2, cutoffId);
            delete.executeUpdate();
        }
    }

    private void runOnServerThread(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private void refreshSettings() {
        saveHistory = plugin.getConfigManager().isSaveHistory();
        historyBatchSize = plugin.getConfigManager().getPersistenceHistoryBatchSize();
        maxHistoryEntries = plugin.getConfigManager().getMaxHistoryEntries();
        historyCleanupIntervalMillis = plugin.getConfigManager().getHistoryCleanupIntervalMillis();
        shutdownFlushTimeoutMillis = plugin.getConfigManager().getPersistenceShutdownFlushTimeoutMillis();
    }

    private boolean isPlayerOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    private void unloadIfOffline(UUID playerId) {
        if (!isPlayerOnline(playerId)) {
            cache.unload(playerId);
        }
    }

    private record LoadedPlayerData(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts) {
    }

    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws Exception;
    }
}
