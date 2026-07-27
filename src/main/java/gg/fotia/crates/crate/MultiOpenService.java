package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.particle.ParticleStage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates batch reward confirmation, optional first-draw animation and safe delivery.
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
        plugin.getParticleManager().playStage(ParticleStage.OPEN, player, crate, crateLocation);

        List<RewardResult> rewardResults = openAttempts.stream()
                .map(CrateOpenService.OpenAttempt::rewardResult)
                .toList();

        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Runnable finish = () -> {
            for (int index = 0; index < rewardResults.size(); index++) {
                crateOpenService.deliverRewardSafely(playerUuid, playerName, crate,
                        rewardResults.get(index), crateLocation, index == 0);
            }
            if (player.isOnline() && crate.isMultiOpenAnimationEnabled() && rewardResults.size() > 1) {
                plugin.getGuiManager().openMultiOpenResultGui(player, crate, rewardResults);
            }
            onComplete.run();
        };

        // 合并提交：回滚时数据快照取首抽（整批开始前），物理钥匙退还取全批消耗并集
        crateOpenService.commitOpen(player, CrateOpenService.OpenAttempt.mergeForCommit(openAttempts), () -> {
            if (MultiOpenAnimationPolicy.shouldPlayFirstDrawAnimation(
                    crate.isMultiOpenAnimationEnabled(), rewardResults.size()) && player.isOnline()) {
                boolean started = plugin.getAnimationManager().playAnimation(
                        player,
                        crate,
                        rewardResults.get(0).getDisplayReward(),
                        crateLocation,
                        finish
                );
                if (!started) {
                    finish.run();
                }
                return;
            }
            finish.run();
        }, onComplete);
    }

    private List<CrateOpenService.OpenAttempt> prepareRewards(Player player, Crate crate, int amount) {
        List<CrateOpenService.OpenAttempt> openAttempts = new ArrayList<>();
        MultiOpenPermissionContext permissionContext = new MultiOpenPermissionContext(player::hasPermission);
        for (int index = 0; index < amount; index++) {
            CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(
                    player, crate, permissionContext);
            if (!openAttempt.isSuccess()) {
                if (openAttempts.isEmpty()) {
                    crateOpenService.sendOpenFailure(player, openAttempt.failureReason());
                }
                break;
            }
            openAttempts.add(openAttempt);
            permissionContext.recordAward(openAttempt.rewardResult());
        }
        return openAttempts;
    }
}
