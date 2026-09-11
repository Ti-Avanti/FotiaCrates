package gg.fotia.crates.key.distribution;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.data.PlayerDataCache;
import gg.fotia.crates.key.Key;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class KeyDistributionManager {

    private final FotiaCrates plugin;
    private final KeyDistributionRepository repository;
    private final KeyDistributionConfirmationStore confirmations = new KeyDistributionConfirmationStore();
    private final Set<UUID> deliveriesInProgress = new HashSet<>();
    private final Consumer<UUID> playerReadyListener = this::deliverPending;
    private boolean stopping;

    public KeyDistributionManager(FotiaCrates plugin) {
        this.plugin = plugin;
        this.repository = new KeyDistributionRepository(
                plugin.getDatabaseManager()::getConnection,
                plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql")
        );
    }

    public void start() {
        stopping = false;
        plugin.getAsyncPlayerDataManager().addPlayerReadyListener(playerReadyListener);
        for (Player player : Bukkit.getOnlinePlayers()) {
            registerPlayer(player);
        }
    }

    public void shutdown() {
        stopping = true;
        plugin.getAsyncPlayerDataManager().removePlayerReadyListener(playerReadyListener);
    }

    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            registerPlayer(player);
            deliverPending(player.getUniqueId());
        }
    }

    public void registerPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        long seenAt = System.currentTimeMillis();
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            repository.registerKnownPlayer(playerId, playerName, seenAt);
            return null;
        }, ignored -> {
        }, exception -> plugin.getLogger().warning("Failed to register known player "
                + playerId + ": " + exception.getMessage()));
    }

    public KeyDistributionConfirmation requestConfirmation(KeyDistributionRequest request) {
        return confirmations.create(
                request,
                System.currentTimeMillis(),
                plugin.getConfigManager().getKeyDistributionConfirmationTimeoutMillis()
        );
    }

    public KeyDistributionRequest consumeConfirmation(String creatorId, String token) {
        return confirmations.consume(creatorId, token, System.currentTimeMillis()).orElse(null);
    }

    public void createDistribution(KeyDistributionRequest request,
                                   Consumer<KeyDistributionBatch> onSuccess,
                                   Consumer<Exception> onFailure) {
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(
                () -> repository.createBatch(
                        request.scope(), request.keyId(), request.amount(), request.keyType(),
                        request.creatorName(), request.onlineRecipients(), System.currentTimeMillis()),
                batch -> {
                    onSuccess.accept(batch);
                    Collection<UUID> onlineTargets = request.scope() == KeyDistributionScope.ONLINE
                            ? request.onlineRecipients()
                            : Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
                    queueOnlineDeliveries(onlineTargets);
                },
                onFailure
        );
    }

    public void deliverPending(UUID playerId) {
        if (stopping || !plugin.getAsyncPlayerDataManager().isReady(playerId)
                || !deliveriesInProgress.add(playerId)) {
            return;
        }

        int limit = plugin.getConfigManager().getKeyDistributionBatchSize();
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(
                () -> repository.findPendingGrants(playerId, limit),
                grants -> applyPendingGrants(playerId, grants, limit),
                exception -> {
                    plugin.getLogger().warning("Failed to load pending key distributions for "
                            + playerId + ": " + exception.getMessage());
                    finishDelivery(playerId, true);
                }
        );
    }

    private void applyPendingGrants(UUID playerId, PendingKeyDistributionGrants grants, int limit) {
        if (grants.isEmpty() || !plugin.getAsyncPlayerDataManager().isReady(playerId)) {
            finishDelivery(playerId, false);
            return;
        }
        if (grants.virtualGrants().isEmpty()) {
            deliverPhysicalGrants(playerId, grants.physicalGrants(), limit, false);
            return;
        }

        PlayerDataCache.Snapshot before = plugin.getAsyncPlayerDataManager().snapshot(playerId);
        Map<String, Integer> virtualAmounts = aggregateAmounts(grants.virtualGrants());
        for (Map.Entry<String, Integer> entry : virtualAmounts.entrySet()) {
            if (!plugin.getKeyManager().addVirtualKeys(playerId, entry.getKey(), entry.getValue())) {
                plugin.getAsyncPlayerDataManager().restore(playerId, before);
                finishDelivery(playerId, true);
                return;
            }
        }

        List<Long> virtualGrantIds = grants.virtualGrants().stream()
                .map(KeyDistributionGrant::id)
                .toList();
        plugin.getAsyncPlayerDataManager().commitNow(
                playerId,
                connection -> repository.markVirtualGrantsDelivered(
                        connection, virtualGrantIds, System.currentTimeMillis()),
                () -> {
                    notifyPlayer(playerId, virtualAmounts);
                    deliverPhysicalGrants(playerId, grants.physicalGrants(), limit,
                            grants.virtualGrants().size() >= limit);
                },
                () -> {
                    plugin.getAsyncPlayerDataManager().restore(playerId, before);
                    finishDelivery(playerId, true);
                }
        );
    }

    private void deliverPhysicalGrants(UUID playerId, List<KeyDistributionGrant> grants,
                                       int limit, boolean moreVirtualMayRemain) {
        if (stopping || !plugin.isEnabled()) {
            finishDelivery(playerId, false);
            return;
        }
        if (grants.isEmpty()) {
            finishDelivery(playerId, moreVirtualMayRemain);
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            finishDelivery(playerId, false);
            return;
        }

        List<Long> deliveredIds = new ArrayList<>();
        Map<String, Integer> deliveredAmounts = new HashMap<>();
        boolean missingKey = false;
        for (KeyDistributionGrant grant : grants) {
            Key key = plugin.getKeyManager().getKey(grant.keyId());
            if (key == null) {
                missingKey = true;
                plugin.getLogger().warning("Pending key distribution " + grant.id()
                        + " references missing key " + grant.keyId());
                continue;
            }
            plugin.getKeyManager().givePhysicalKeys(player, grant.keyId(), grant.amount());
            deliveredIds.add(grant.id());
            deliveredAmounts.merge(grant.keyId(), grant.amount(), Integer::sum);
        }

        notifyPlayer(playerId, deliveredAmounts);
        if (deliveredIds.isEmpty()) {
            finishDelivery(playerId, false);
            return;
        }

        boolean moreMayRemain = moreVirtualMayRemain
                || (!missingKey && grants.size() >= limit);
        finalizePhysicalGrants(playerId, deliveredIds, moreMayRemain, 0);
    }

    private void finalizePhysicalGrants(UUID playerId, List<Long> grantIds,
                                        boolean checkAgain, int attempt) {
        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(
                () -> repository.markPhysicalGrantsDelivered(grantIds, System.currentTimeMillis()),
                ignored -> finishDelivery(playerId, checkAgain),
                exception -> {
                    int maxRetries = plugin.getConfigManager().getPendingRewardFinalizeRetryCount();
                    if (!stopping && attempt < maxRetries) {
                        long delay = plugin.getConfigManager().getPendingRewardFinalizeRetryDelayTicks()
                                * (1L << Math.min(attempt, 6));
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> finalizePhysicalGrants(
                                        playerId, grantIds, checkAgain, attempt + 1), delay);
                        return;
                    }
                    plugin.getLogger().severe("Failed to finalize " + grantIds.size()
                            + " physical key grants for " + playerId + ": " + exception.getMessage());
                    finishDelivery(playerId, false);
                }
        );
    }

    private Map<String, Integer> aggregateAmounts(List<KeyDistributionGrant> grants) {
        Map<String, Integer> amounts = new HashMap<>();
        for (KeyDistributionGrant grant : grants) {
            amounts.merge(grant.keyId(), grant.amount(), Integer::sum);
        }
        return amounts;
    }

    private void notifyPlayer(UUID playerId, Map<String, Integer> amounts) {
        if (stopping || !plugin.getConfigManager().isKeyDistributionRecipientNotificationEnabled()
                || amounts.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
            Key key = plugin.getKeyManager().getKey(entry.getKey());
            String keyName = key == null ? entry.getKey() : key.getName();
            plugin.getLanguageManager().send(player, "key-given",
                    LanguageManager.placeholders(
                            "amount", String.valueOf(entry.getValue()),
                            "key", keyName));
        }
    }

    private void queueOnlineDeliveries(Collection<UUID> recipients) {
        List<UUID> targets = new ArrayList<>(new LinkedHashSet<>(recipients));
        int batchSize = plugin.getConfigManager().getKeyDistributionBatchSize();
        queueOnlineDeliveryBatch(targets, 0, batchSize);
    }

    private void queueOnlineDeliveryBatch(List<UUID> targets, int start, int batchSize) {
        if (stopping || start >= targets.size()) {
            return;
        }
        int end = Math.min(start + batchSize, targets.size());
        for (int index = start; index < end; index++) {
            deliverPending(targets.get(index));
        }
        if (end < targets.size()) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> queueOnlineDeliveryBatch(targets, end, batchSize), 1L);
        }
    }

    private void finishDelivery(UUID playerId, boolean checkAgain) {
        deliveriesInProgress.remove(playerId);
        if (!stopping && checkAgain) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> deliverPending(playerId), 1L);
        }
    }
}
