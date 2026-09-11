package gg.fotia.crates.reward.settlement;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.RewardResult;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Coordinates durable reservations and bounded server-thread reward delivery. */
public final class RewardSettlementCoordinator {
    private final FotiaCrates plugin;
    private final OpenRewardJournal journal;
    private final Set<UUID> active = new HashSet<>();
    private final Set<UUID> unstarted = new HashSet<>();
    private final Set<UUID> acknowledgements = ConcurrentHashMap.newKeySet();
    private final Set<UUID> releases = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private volatile boolean stopping;

    public RewardSettlementCoordinator(FotiaCrates plugin) throws IOException, SQLException {
        this.plugin = plugin;
        this.journal = new OpenRewardJournal(plugin);
    }

    public OpenRewardJournal journal() {
        return journal;
    }

    public void deliver(UUID playerId, List<RewardResult> results,
                        BiConsumer<Player, RewardResult> award, Consumer<RewardResult> deferred,
                        Runnable completion) {
        List<RewardResult> pending = results.stream().filter(result -> active.add(result.getSettlementId())).toList();
        pending.forEach(result -> unstarted.add(result.getSettlementId()));
        Runnable finish = () -> {
            pending.forEach(result -> active.remove(result.getSettlementId()));
            completion.run();
        };
        deliverBatch(playerId, pending, 0, award, deferred, finish);
    }

    private void deliverBatch(UUID playerId, List<RewardResult> results, int offset,
                              BiConsumer<Player, RewardResult> award, Consumer<RewardResult> deferred,
                              Runnable completion) {
        if (offset >= results.size()) {
            completion.run();
            return;
        }
        Player player = onlinePlayer(playerId);
        if (stopping || player == null) {
            defer(results.subList(offset, results.size()), deferred);
            completion.run();
            return;
        }
        int end = Math.min(results.size(), offset + plugin.getConfigManager().getMultiOpenDeliveryBatchSize());
        List<RewardResult> batch = results.subList(offset, end);
        List<UUID> ids = batch.stream().map(RewardResult::getSettlementId).toList();
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> journal.reserve(ids), reserved -> {
            Player current = onlinePlayer(playerId);
            if (stopping || current == null) {
                defer(results.subList(offset, results.size()), deferred);
                completion.run();
                return;
            }
            for (RewardResult result : batch) {
                UUID id = result.getSettlementId();
                if (!reserved.contains(id)) {
                    unstarted.remove(id);
                    continue;
                }
                // From this point an exception may mean a partially executed external command.
                unstarted.remove(id);
                try {
                    award.accept(current, result);
                    acknowledgements.add(id);
                } catch (RuntimeException exception) {
                    boolean retry = plugin.getConfigManager().shouldRetryPendingRewardDeliveryErrors();
                    if (retry) releases.add(id);
                    plugin.getLogger().severe("Reward delivery failed for " + playerId + ", operation " + id
                            + (retry ? "; moved to pending delivery: " : "; snapshot locked for review: ")
                            + exception.getMessage());
                    plugin.getLanguageManager().send(current, retry ? "reward-delivery-pending" : "reward-delivery-review");
                }
            }
            flushPending(0);
            if (end == results.size()) completion.run();
            else plugin.getServer().getScheduler().runTask(plugin,
                    () -> deliverBatch(playerId, results, end, award, deferred, completion));
        }, exception -> {
            plugin.getLogger().warning("Could not reserve crate awards for " + playerId + ": " + exception.getMessage());
            defer(results.subList(offset, results.size()), deferred);
            Player current = onlinePlayer(playerId);
            if (current != null) plugin.getLanguageManager().send(current, "reward-delivery-pending");
            completion.run();
        });
    }

    private void defer(List<RewardResult> results, Consumer<RewardResult> deferred) {
        for (RewardResult result : results) {
            UUID id = result.getSettlementId();
            unstarted.remove(id);
            releases.add(id);
            try {
                deferred.accept(result);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not present deferred reward " + id + ": " + exception.getMessage());
            }
        }
        flushPending(0);
    }

    private void flushPending(int attempt) {
        if (stopping || (acknowledgements.isEmpty() && releases.isEmpty())
                || !flushQueued.compareAndSet(false, true)) return;
        List<UUID> confirmed = List.copyOf(acknowledgements);
        List<UUID> deferred = List.copyOf(releases);
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            try {
                journal.acknowledge(confirmed);
                acknowledgements.removeAll(confirmed);
                journal.defer(deferred);
                releases.removeAll(deferred);
            } finally {
                flushQueued.set(false);
            }
            return null;
        }, ignored -> flushPending(0), exception -> {
            flushQueued.set(false);
            if (!stopping && attempt < plugin.getConfigManager().getPendingRewardFinalizeRetryCount()) {
                long delay = Math.min(600L, plugin.getConfigManager().getPendingRewardFinalizeRetryDelayTicks()
                        * (1L << Math.min(attempt, 5)));
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> flushPending(attempt + 1), delay);
            } else {
                plugin.getLogger().severe("Could not finalize crate awards; durable snapshots retained: " + exception.getMessage());
            }
        });
    }

    public void beginShutdown() {
        stopping = true;
    }

    /** Only call after the persistence worker has stopped. */
    public void finishShutdown() {
        try {
            journal.acknowledge(List.copyOf(acknowledgements));
            acknowledgements.clear();
            releases.addAll(unstarted);
            journal.defer(List.copyOf(releases));
            releases.clear();
            journal.recoverReady();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not finish reward settlement at shutdown; journal retained: " + exception.getMessage());
        }
        active.clear();
        unstarted.clear();
    }

    private Player onlinePlayer(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        return player != null && player.isOnline() ? player : null;
    }
}
