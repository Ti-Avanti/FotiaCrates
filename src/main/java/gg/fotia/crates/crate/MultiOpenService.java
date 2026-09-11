package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates batch reward confirmation, optional batch-aware animation and safe delivery.
 */
public final class MultiOpenService {

    private final FotiaCrates plugin;
    private final CrateOpenService crateOpenService;

    public MultiOpenService(FotiaCrates plugin, CrateOpenService crateOpenService) {
        this.plugin = plugin;
        this.crateOpenService = crateOpenService;
    }

    public void open(Player player, Crate crate, int amount, Location crateLocation, Runnable onComplete) {
        List<CrateOpenService.OpenAttempt> openAttempts = prepareRewards(player, crate, amount);
        if (openAttempts.isEmpty()) {
            onComplete.run();
            return;
        }

        // 校验通过后才播放开箱演出，避免首抽即失败时误导玩家；部分成功时告知实际抽数
        plugin.getLanguageManager().send(player, "multi-open-start",
                LanguageManager.placeholders("amount", String.valueOf(openAttempts.size())));
        if (openAttempts.size() < amount) {
            plugin.getLanguageManager().send(player, "multi-open-partial",
                    LanguageManager.placeholders(
                            "actual", String.valueOf(openAttempts.size()),
                            "requested", String.valueOf(amount)));
        }

        List<RewardResult> rewardResults = openAttempts.stream()
                .map(CrateOpenService.OpenAttempt::rewardResult)
                .toList();

        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Runnable finish = () -> crateOpenService.deliverRewardsSafely(playerUuid, playerName, crate,
                rewardResults, crateLocation, () -> {
                    Player current = plugin.getServer().getPlayer(playerUuid);
                    try {
                        if (plugin.isEnabled() && current != null && current.isOnline()
                                && crate.isMultiOpenAnimationEnabled() && rewardResults.size() > 1) {
                            plugin.getGuiManager().openMultiOpenResultGui(current, crate, rewardResults);
                        }
                    } finally {
                        onComplete.run();
                    }
                });

        // 合并提交：回滚时数据快照取首抽（整批开始前），物理钥匙退还取全批消耗并集
        crateOpenService.commitOpen(player, CrateOpenService.OpenAttempt.mergeForCommit(openAttempts), () -> {
            plugin.getCratePresentationManager().play(playerUuid, crate,
                    rewardResults.stream().map(RewardResult::getDisplayReward).toList(), crateLocation,
                    false, MultiOpenAnimationPolicy.shouldPlayAnimation(
                            crate.isMultiOpenAnimationEnabled(), rewardResults.size()), finish);
        }, onComplete);
    }

    private List<CrateOpenService.OpenAttempt> prepareRewards(Player player, Crate crate, int amount) {
        List<CrateOpenService.OpenAttempt> openAttempts = new ArrayList<>();
        if (!plugin.getAsyncPlayerDataManager().isReady(player.getUniqueId())) {
            crateOpenService.sendOpenFailure(player, crate, CrateOpenService.OpenFailureReason.PLAYER_DATA_PENDING);
            return openAttempts;
        }
        var batchSnapshot = plugin.getAsyncPlayerDataManager().snapshot(player.getUniqueId());
        MultiOpenPermissionContext permissionContext = crateOpenService.createSelectionContext(player, crate);
        for (int index = 0; index < amount; index++) {
            CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(
                    player, crate, permissionContext, batchSnapshot);
            if (!openAttempt.isSuccess()) {
                if (openAttempts.isEmpty()) {
                    crateOpenService.sendOpenFailure(player, crate, openAttempt.failureReason());
                }
                break;
            }
            openAttempts.add(openAttempt);
            permissionContext.recordAward(openAttempt.rewardResult());
        }
        return openAttempts;
    }
}
