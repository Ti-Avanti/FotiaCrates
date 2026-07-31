package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.data.PlayerDataCache;
import gg.fotia.crates.key.KeyManager;
import gg.fotia.crates.key.KeyType;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.pity.PityResetPolicy;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrateOpenService {

    private final FotiaCrates plugin;

    public CrateOpenService(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public boolean hasOpenPermission(Player player, Crate crate) {
        if (player.hasPermission("fotiacrates.open.*")) {
            return true;
        }

        String customPermission = crate.getPermission();
        if (customPermission == null || customPermission.isBlank()) {
            return true;
        }

        return player.hasPermission(customPermission);
    }

    public OpenAttempt prepareOpen(Player player, Crate crate) {
        return prepareOpen(player, crate, createSelectionContext(player, crate));
    }

    OpenAttempt prepareOpen(Player player, Crate crate, MultiOpenPermissionContext permissionContext) {
        if (!plugin.getAsyncPlayerDataManager().isReady(player.getUniqueId())) {
            return OpenAttempt.failure(OpenFailureReason.PLAYER_DATA_PENDING);
        }

        PlayerDataCache.Snapshot previousData = plugin.getAsyncPlayerDataManager().snapshot(player.getUniqueId());
        ResolvedReward resolvedReward = resolveRewardResult(player, crate, permissionContext);
        if (resolvedReward == null) {
            return OpenAttempt.failure(OpenFailureReason.NO_AVAILABLE_REWARD);
        }

        KeyManager.ConsumedKey consumedKey = plugin.getKeyManager()
                .consumeKeyForCrateDetailed(player, crate.getId(), KeyType.ALL);
        if (consumedKey == null) {
            return OpenAttempt.failure(OpenFailureReason.NO_KEY);
        }

        if (crate.isUniqueDrawEnabled()) {
            plugin.getAsyncPlayerDataManager().collectReward(
                    player.getUniqueId(),
                    crate.getId(),
                    resolvedReward.rewardResult().getDisplayReward().getId()
            );
        }
        updatePityCounter(player, crate, resolvedReward);
        return OpenAttempt.success(resolvedReward.rewardResult(), previousData,
                consumedKey.physical() ? List.of(consumedKey.keyId()) : List.of());
    }

    MultiOpenPermissionContext createSelectionContext(Player player, Crate crate) {
        return new MultiOpenPermissionContext(
                player::hasPermission,
                crate.isUniqueDrawEnabled(),
                crate.isUniqueDrawEnabled()
                        ? plugin.getAsyncPlayerDataManager().getCollectedRewardIds(
                                player.getUniqueId(), crate.getId())
                        : java.util.Set.of()
        );
    }

    public void commitOpen(Player player, OpenAttempt openAttempt, Runnable onSuccess, Runnable onFailure) {
        if (!openAttempt.isSuccess()) {
            onFailure.run();
            return;
        }

        plugin.getAsyncPlayerDataManager().commitNow(player.getUniqueId(), onSuccess, () -> {
            // 精确回滚：数据快照还原虚拟钥匙/保底计数，物理钥匙按消耗明细退还。
            // 不再整包覆盖背包——提交是异步的，覆盖会把期间拾取/丢弃的物品抹掉或复活
            plugin.getAsyncPlayerDataManager().restore(player.getUniqueId(), openAttempt.previousData());
            refundPhysicalKeys(player, openAttempt.consumedPhysicalKeyIds());
            plugin.getLanguageManager().send(player, "player-data-save-failed");
            onFailure.run();
        });
    }

    private void refundPhysicalKeys(Player player, List<String> keyIds) {
        if (keyIds == null || keyIds.isEmpty()) {
            return;
        }
        Map<String, Integer> grouped = new HashMap<>();
        for (String keyId : keyIds) {
            grouped.merge(keyId, 1, Integer::sum);
        }
        if (player.isOnline()) {
            grouped.forEach((keyId, amount) -> plugin.getKeyManager().givePhysicalKeys(player, keyId, amount));
            return;
        }

        grouped.forEach((keyId, amount) -> {
            var key = plugin.getKeyManager().getKey(keyId);
            var keyItem = plugin.getKeyManager().createPhysicalKey(keyId, amount);
            if (key == null || keyItem == null) {
                plugin.getLogger().severe("Could not create physical key refund for offline player "
                        + player.getUniqueId() + ": unknown key " + keyId);
                return;
            }
            plugin.getPendingRewardManager().addPendingPhysicalKeyRefund(
                    player.getUniqueId(), keyId, key.getDisplayName(), keyItem);
        });
        plugin.getLogger().info("Stored " + keyIds.size() + " physical key refund(s) for offline player "
                + player.getUniqueId() + '.');
    }

    public void sendOpenFailure(Player player, OpenFailureReason reason) {
        if (reason == OpenFailureReason.PLAYER_DATA_PENDING) {
            plugin.getLanguageManager().send(player, "player-data-loading");
            return;
        }
        if (reason == OpenFailureReason.NO_AVAILABLE_REWARD) {
            plugin.getLanguageManager().send(player, "no-available-reward");
            return;
        }

        plugin.getLanguageManager().send(player, "no-key");
    }

    public void deliverReward(Player player, Crate crate, RewardResult rewardResult) {
        deliverReward(player, crate, rewardResult, plugin.getParticleManager().resolveCrateLocation(player, crate));
    }

    public void deliverReward(Player player, Crate crate, RewardResult rewardResult, Location crateLocation) {
        deliverReward(player, crate, rewardResult, crateLocation, true);
    }

    public void deliverReward(Player player, Crate crate, RewardResult rewardResult, Location crateLocation,
                              boolean playPresentation) {
        Reward displayReward = rewardResult.getDisplayReward();
        Reward actualReward = rewardResult.getActualReward();

        actualReward.give(player);

        if (rewardResult.wasReplaced()) {
            plugin.getLanguageManager().send(player, "reward-replaced",
                    LanguageManager.placeholders(
                            "original", displayReward.getDisplayName(),
                            "actual", actualReward.getDisplayName()
                    ));
        } else {
            plugin.getLanguageManager().send(player, "reward-received",
                    LanguageManager.placeholders("reward", actualReward.getDisplayName()));
        }

        if (displayReward.shouldBroadcast() && plugin.getConfigManager().isBroadcastRareRewards()) {
            var message = plugin.getLanguageManager().getMessage(player, "broadcast-rare",
                    LanguageManager.placeholders(
                            "player", player.getName(),
                            "crate", crate.getName(),
                            "reward", displayReward.getDisplayName()
                    ));
            plugin.getServer().broadcast(message);
        }

        plugin.getHistoryManager().addHistory(
                player.getUniqueId(),
                player.getName(),
                crate.getId(),
                displayReward.getId(),
                displayReward.getDisplayName()
        );

        if (playPresentation) {
            plugin.getParticleManager().playStage(ParticleStage.REWARD, player, crate, crateLocation);
            if (crate.getWinSound() != null) {
                player.playSound(player.getLocation(), crate.getWinSound(),
                        crate.getWinVolume(), crate.getWinPitch());
            }
        }
    }

    public void deliverRewardSafely(UUID playerUuid, String playerName, Crate crate, RewardResult rewardResult) {
        deliverRewardSafely(playerUuid, playerName, crate, rewardResult, null);
    }

    public void deliverRewardSafely(UUID playerUuid, String playerName, Crate crate,
                                    RewardResult rewardResult, Location crateLocation) {
        deliverRewardSafely(playerUuid, playerName, crate, rewardResult, crateLocation, true);
    }

    public void deliverRewardSafely(UUID playerUuid, String playerName, Crate crate,
                                    RewardResult rewardResult, Location crateLocation,
                                    boolean playPresentation) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        Reward displayReward = rewardResult.getDisplayReward();
        Reward actualReward = rewardResult.getActualReward();

        if (player == null || !player.isOnline()) {
            plugin.getPendingRewardManager().addPendingReward(playerUuid, crate.getId(), actualReward);
            plugin.getLogger().info("Player " + playerName + " is offline, reward stored for later claim.");

            plugin.getHistoryManager().addHistory(
                    playerUuid,
                    playerName,
                    crate.getId(),
                    displayReward.getId(),
                    displayReward.getDisplayName()
            );

            if (displayReward.shouldBroadcast() && plugin.getConfigManager().isBroadcastRareRewards()) {
                var message = plugin.getLanguageManager().getMessage("broadcast-rare",
                        LanguageManager.placeholders(
                                "player", playerName,
                                "crate", crate.getName(),
                                "reward", displayReward.getDisplayName()
                        ));
                plugin.getServer().broadcast(message);
            }
            return;
        }

        Location resolvedCrateLocation = crateLocation != null ? crateLocation
                : plugin.getParticleManager().resolveCrateLocation(player, crate);
        deliverReward(player, crate, rewardResult, resolvedCrateLocation, playPresentation);
    }

    private ResolvedReward resolveRewardResult(Player player, Crate crate,
                                               MultiOpenPermissionContext permissionContext) {
        if (crate.isPityEnabled() && crate.hasPityTiers()) {
            UUID playerId = player.getUniqueId();
            String crateId = crate.getId();
            plugin.getPityManager().ensureTierMigration(playerId, crate);

            // 共享计数器仅用于 PAPI/GUI 显示的兼容维护；触发判定走各档独立计数
            int legacyCount = plugin.getPityManager().getPityCount(playerId, crateId) + 1;
            Crate.PityTier triggeredTier = crate.selectTriggeredTier(tier ->
                    plugin.getPityManager().getTierCount(playerId, crateId, tier) + 1);
            if (triggeredTier != null) {
                RewardResult rewardResult = crate.rollPityRewardWithPermissionCheckResult(
                        permissionContext, triggeredTier.getRarity());
                return rewardResult != null ? new ResolvedReward(rewardResult, true, legacyCount) : null;
            }

            RewardResult rewardResult = crate.rollRewardWithPermissionCheckResult(permissionContext);
            return rewardResult != null ? new ResolvedReward(rewardResult, false, legacyCount) : null;
        }

        RewardResult rewardResult = crate.rollRewardWithPermissionCheckResult(permissionContext);
        return rewardResult != null ? new ResolvedReward(rewardResult, false, 0) : null;
    }

    private void updatePityCounter(Player player, Crate crate, ResolvedReward resolvedReward) {
        if (!crate.isPityEnabled() || !crate.hasPityTiers()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String crateId = crate.getId();
        String rewardRarity = resolvedReward.rewardResult().getActualReward().getRarity();
        boolean pityTriggered = resolvedReward.pityTriggered();
        boolean earlyResetEnabled = crate.isResetPityOnEarlyQualifyingReward();

        // 分层计数：各档独立推进，互不清空。
        // 归零条件：该档达到阈值；或本次奖励稀有度已满足该档目标
        //（保底触发时低档视为被满足；自然抽出仅在开启早重置时连带归零）
        for (Crate.PityTier tier : crate.getPityTiers()) {
            int current = plugin.getPityManager().getTierCount(playerId, crateId, tier) + 1;
            boolean satisfiedByReward = (pityTriggered || earlyResetEnabled)
                    && crate.isRarityHigherOrEqual(rewardRarity, tier.getRarity());
            if (current >= tier.getCount() || satisfiedByReward) {
                plugin.getPityManager().setTierCount(playerId, crateId, tier, 0);
            } else {
                plugin.getPityManager().setTierCount(playerId, crateId, tier, current);
            }
        }

        // 兼容层：共享计数器按旧语义继续维护，供 PAPI 变量与 GUI 显示
        int legacyCurrent = resolvedReward.currentCount();
        int maxPityCount = crate.getMaxPityCount();
        if (maxPityCount > 0 && legacyCurrent >= maxPityCount) {
            plugin.getPityManager().resetPityCount(playerId, crateId);
            return;
        }

        if (PityResetPolicy.shouldResetEarly(
                earlyResetEnabled,
                pityTriggered,
                rewardRarity,
                crate.getMinimumPityRarity(),
                crate.getRarityOrder())) {
            plugin.getPityManager().resetPityCount(playerId, crateId);
            return;
        }

        plugin.getPityManager().incrementPityCount(playerId, crateId);
    }

    private record ResolvedReward(RewardResult rewardResult, boolean pityTriggered, int currentCount) {
    }

    public enum OpenFailureReason {
        NO_KEY,
        NO_AVAILABLE_REWARD,
        PLAYER_DATA_PENDING
    }

    public record OpenAttempt(RewardResult rewardResult, OpenFailureReason failureReason,
                              PlayerDataCache.Snapshot previousData, List<String> consumedPhysicalKeyIds) {

        public static OpenAttempt success(RewardResult rewardResult, PlayerDataCache.Snapshot previousData,
                                          List<String> consumedPhysicalKeyIds) {
            return new OpenAttempt(rewardResult, null, previousData, consumedPhysicalKeyIds);
        }

        public static OpenAttempt failure(OpenFailureReason failureReason) {
            return new OpenAttempt(null, failureReason, null, List.of());
        }

        /**
         * 多连抽合并提交：数据快照取首抽的（整批开始前状态），物理钥匙消耗明细取全批并集。
         */
        public static OpenAttempt mergeForCommit(List<OpenAttempt> attempts) {
            OpenAttempt first = attempts.get(0);
            List<String> mergedKeys = new ArrayList<>();
            for (OpenAttempt attempt : attempts) {
                mergedKeys.addAll(attempt.consumedPhysicalKeyIds());
            }
            return new OpenAttempt(first.rewardResult(), null, first.previousData(), mergedKeys);
        }

        public boolean isSuccess() {
            return rewardResult != null;
        }
    }
}
