package gg.fotia.crates.key;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.basictool.assets.CrateKeyStore;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** 共享钥匙目录和在线缓存刷新；查询、发布均通过现有有界数据库线程执行。 */
public final class CrateKeyNetwork implements AutoCloseable {
    private final FotiaCrates plugin;
    private final UUID node;
    private final Deque<UUID> refreshQueue = new ArrayDeque<>();
    private Set<String> published;
    private boolean publishing;
    private long nextWarning;
    private BukkitTask task;
    public CrateKeyNetwork(FotiaCrates plugin, UUID node) { this.plugin = plugin; this.node = node; }
    public void start() {
        if (task != null) task.cancel();
        long interval = Math.max(20, plugin.getConfig().getLong("key-network.refresh-interval-ticks", 40));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, interval);
    }
    private void tick() {
        Set<String> keys = Set.copyOf(plugin.getKeyManager().getKeyIds());
        if (!publishing && !keys.equals(published)) {
            publishing = true;
            plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
                try (var c = plugin.getDatabaseManager().getConnection()) {
                    c.setAutoCommit(false);
                    try { CrateKeyStore.publish(c, plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql"), node, keys); c.commit(); }
                    catch (Exception failure) { c.rollback(); throw failure; }
                }
                return keys;
            }, result -> { published = result; publishing = false; }, error -> {
                publishing = false;
                if (System.currentTimeMillis() >= nextWarning) {
                    nextWarning = System.currentTimeMillis() + 30000;
                    plugin.getLogger().warning("钥匙目录暂未同步，稍后重试: " + error.getClass().getSimpleName());
                }
            });
        }
        if (refreshQueue.isEmpty()) plugin.getServer().getOnlinePlayers().forEach(player -> refreshQueue.add(player.getUniqueId()));
        int maximum = Math.max(1, Math.min(500, plugin.getConfig().getInt("key-network.max-players-per-refresh", 50)));
        for (int i = 0; i < maximum && !refreshQueue.isEmpty(); i++) plugin.getAsyncPlayerDataManager().refreshVirtualKeys(refreshQueue.removeFirst());
    }
    @Override public void close() { if (task != null) task.cancel(); refreshQueue.clear(); }
}
