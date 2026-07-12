package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.data.PlayerDataCache;
import gg.fotia.crates.key.KeyType;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.pity.PityResetPolicy;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
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
        if (!plugin.getAsyncPlayerDataManager().isReady(player.getUniqueId())) {
            return OpenAttempt.failure(OpenFailureReason.PLAYER_DATA_PENDING);
        }

        PlayerDataCache.Snapshot previousData = plugin.getAsyncPlayerDataManager().snapshot(player.getUniqueId());
        ItemStack[] previousInventory = copyInventory(player.getInventory().getContents());
        ResolvedReward resolvedReward = resolveRewardResult(player, crate);
        if (resolvedReward == null) {
            return OpenAttempt.failure(OpenFailureReason.NO_AVAILABLE_REWARD);
        }

        if (!plugin.getKeyManager().consumeKeyForCrate(player, crate.getId(), KeyType.ALL)) {
            return OpenAttempt.failure(OpenFailureReason.NO_KEY);
        }

        updatePityCounter(player, crate, resolvedReward);
        return OpenAttempt.success(resolvedReward.rewardResult(), previousData, previousInventory);
    }

    public void commitOpen(Player player, OpenAttempt openAttempt, Runnable onSuccess, Runnable onFailure) {
        if (!openAttempt.isSuccess()) {
            onFailure.run();
            return;
        }

        plugin.getAsyncPlayerDataManager().commitNow(player.getUniqueId(), onSuccess, () -> {
            plugin.getAsyncPlayerDataManager().restore(player.getUniqueId(), openAttempt.previousData());
            player.getInventory().setContents(copyInventory(openAttempt.previousInventory()));
            plugin.getLanguageManager().send(player, "player-data-save-failed");
            onFailure.run();
        });
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

        plugin.getParticleManager().playStage(ParticleStage.REWARD, player, crate, crateLocation);

        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
    }

    public void deliverRewardSafely(UUID playerUuid, String playerName, Crate crate, RewardResult rewardResult) {
        deliverRewardSafely(playerUuid, playerName, crate, rewardResult, null);
    }

    public void deliverRewardSafely(UUID playerUuid, String playerName, Crate crate,
                                    RewardResult rewardResult, Location crateLocation) {
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
        deliverReward(player, crate, rewardResult, resolvedCrateLocation);
    }

    private ResolvedReward resolveRewardResult(Player player, Crate crate) {
        if (crate.isPityEnabled() && !crate.getPityTiers().isEmpty()) {
            int currentCount = plugin.getPityManager().getPityCount(player.getUniqueId(), crate.getId()) + 1;
            Crate.PityTier triggeredTier = crate.getTriggeredPityTier(currentCount);
            if (triggeredTier != null) {
                RewardResult rewardResult = crate.rollPityRewardWithPermissionCheckResult(player, triggeredTier.getRarity());
                return rewardResult != null ? new ResolvedReward(rewardResult, true) : null;
            }
        }

        RewardResult rewardResult = crate.rollRewardWithPermissionCheckResult(player);
        return rewardResult != null ? new ResolvedReward(rewardResult, false) : null;
    }

    private void updatePityCounter(Player player, Crate crate, ResolvedReward resolvedReward) {
        if (!crate.isPityEnabled() || crate.getPityTiers().isEmpty()) {
            return;
        }

        int currentCount = plugin.getPityManager().getPityCount(player.getUniqueId(), crate.getId()) + 1;
        int maxPityCount = crate.getMaxPityCount();
        if (maxPityCount > 0 && currentCount >= maxPityCount) {
            plugin.getPityManager().resetPityCount(player.getUniqueId(), crate.getId());
            return;
        }

        if (PityResetPolicy.shouldResetEarly(
                crate.isResetPityOnEarlyQualifyingReward(),
                resolvedReward.pityTriggered(),
                resolvedReward.rewardResult().getActualReward().getRarity(),
                crate.getMinimumPityRarity(),
                crate.getRarityOrder())) {
            plugin.getPityManager().resetPityCount(player.getUniqueId(), crate.getId());
            return;
        }

        plugin.getPityManager().incrementPityCount(player.getUniqueId(), crate.getId());
    }

    private record ResolvedReward(RewardResult rewardResult, boolean pityTriggered) {
    }

    private ItemStack[] copyInventory(ItemStack[] contents) {
        return Arrays.stream(contents)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    public enum OpenFailureReason {
        NO_KEY,
        NO_AVAILABLE_REWARD,
        PLAYER_DATA_PENDING
    }

    public record OpenAttempt(RewardResult rewardResult, OpenFailureReason failureReason,
                              PlayerDataCache.Snapshot previousData, ItemStack[] previousInventory) {

        public static OpenAttempt success(RewardResult rewardResult, PlayerDataCache.Snapshot previousData,
                                          ItemStack[] previousInventory) {
            return new OpenAttempt(rewardResult, null, previousData, previousInventory);
        }

        public static OpenAttempt failure(OpenFailureReason failureReason) {
            return new OpenAttempt(null, failureReason, null, null);
        }

        public boolean isSuccess() {
            return rewardResult != null;
        }
    }
}
