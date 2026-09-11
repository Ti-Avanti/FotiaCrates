package gg.fotia.crates.data;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-thread retry scheduling; SQL remains owned by the persistence worker. */
final class PlayerLoadRetries {
    private final FotiaCrates plugin;
    private final Consumer<UUID> loader;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private boolean stopping;

    PlayerLoadRetries(FotiaCrates plugin, Consumer<UUID> loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    void schedule(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (stopping || tasks.containsKey(playerId) || player == null || !player.isOnline()) {
            return;
        }
        int attempt = attempts.merge(playerId, 1, Integer::sum);
        long base = plugin.getConfigManager().getPlayerLoadRetryDelayTicks();
        long maximum = plugin.getConfigManager().getPlayerLoadMaxRetryDelayTicks();
        long delay = Math.min(maximum, base * (1L << Math.min(attempt - 1, 10)));
        tasks.put(playerId, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(playerId);
            Player current = plugin.getServer().getPlayer(playerId);
            if (current != null && current.isOnline()) loader.accept(playerId);
            else clear(playerId);
        }, delay));
    }

    void clear(UUID playerId) {
        BukkitTask task = tasks.remove(playerId);
        if (task != null) task.cancel();
        attempts.remove(playerId);
    }

    void shutdown() {
        stopping = true;
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
        attempts.clear();
    }
}
