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

        List<CrateOpenService.OpenAttempt> openAttempts = prepareRewards(player, crate, amount);
        if (openAttempts.isEmpty()) {
            onComplete.run();
            return;
        }

        List<RewardResult> rewardResults = openAttempts.stream()
                .map(CrateOpenService.OpenAttempt::rewardResult)
                .toList();

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

        crateOpenService.commitOpen(player, openAttempts.get(0), () -> {
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
        }, onComplete);
    }

    private List<CrateOpenService.OpenAttempt> prepareRewards(Player player, Crate crate, int amount) {
        List<CrateOpenService.OpenAttempt> openAttempts = new ArrayList<>();
        for (int index = 0; index < amount; index++) {
            CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(player, crate);
            if (!openAttempt.isSuccess()) {
                if (openAttempts.isEmpty()) {
                    crateOpenService.sendOpenFailure(player, openAttempt.failureReason());
                }
                break;
            }
            openAttempts.add(openAttempt);
        }
        return openAttempts;
    }
}
