package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationManager;
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
        plugin.getLanguageManager().send(player, "multi-open-start",
                LanguageManager.placeholders("amount", String.valueOf(amount)));
        plugin.getParticleManager().playStage(ParticleStage.OPEN, player, crate, crateLocation);

        List<RewardResult> rewardResults = prepareRewards(player, crate, amount);
        if (rewardResults.isEmpty()) {
            onComplete.run();
            return;
        }

        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Runnable finish = () -> {
            for (RewardResult rewardResult : rewardResults) {
                crateOpenService.deliverRewardSafely(playerUuid, playerName, crate, rewardResult, crateLocation);
            }
            if (player.isOnline() && crate.isMultiOpenAnimationEnabled() && rewardResults.size() > 1) {
                plugin.getGuiManager().openMultiOpenResultGui(player, crate, rewardResults);
            }
            onComplete.run();
        };

        if (MultiOpenAnimationPolicy.shouldPlayFirstDrawAnimation(
                crate.isMultiOpenAnimationEnabled(), rewardResults.size()) && player.isOnline()) {
            new AnimationManager(plugin).playAnimation(
                    player,
                    crate,
                    rewardResults.get(0).getDisplayReward(),
                    crateLocation,
                    finish
            );
            return;
        }

        finish.run();
    }

    private List<RewardResult> prepareRewards(Player player, Crate crate, int amount) {
        List<RewardResult> rewardResults = new ArrayList<>();
        for (int index = 0; index < amount; index++) {
            CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(player, crate);
            if (!openAttempt.isSuccess()) {
                if (rewardResults.isEmpty()) {
                    crateOpenService.sendOpenFailure(player, openAttempt.failureReason());
                }
                break;
            }
            rewardResults.add(openAttempt.rewardResult());
        }
        return rewardResults;
    }
}
