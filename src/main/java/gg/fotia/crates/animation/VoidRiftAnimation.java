package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import gg.fotia.crates.util.ChestLidUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class VoidRiftAnimation implements Animation {

    private static final double REVEAL_PROGRESS = 0.72;
    private static final double DESCENT_END_PROGRESS = 0.9;

    private final FotiaCrates plugin;
    private final Random random = new Random();
    private final List<ItemDisplay> displays = new ArrayList<>();
    private AnimationCompletion completion = new AnimationCompletion();
    private Player player;
    private Location crateLocation;
    private Location origin;
    private VoidRiftAnimationSettings settings;
    private ItemDisplay winner;
    private BukkitTask animationTask;
    private BukkitTask cleanupTask;
    private boolean running;
    private boolean revealed;

    public VoidRiftAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation,
                      Runnable onComplete) {
        this.player = player;
        this.crateLocation = crateLocation == null ? null : crateLocation.clone();
        this.completion = new AnimationCompletion();
        this.running = true;
        this.revealed = false;

        AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                .getAnimationTemplate(crate.getAnimationTemplate(), AnimationType.VOID_RIFT);
        settings = template != null
                ? template.voidRiftSettings()
                : VoidRiftAnimationSettings.from(null);
        origin = WorldAnimationSupport.resolveOrigin(player, crateLocation);
        List<Reward> rewards = crate.getAvailableRewardsFor(player);
        spawnCandidates(rewards, finalReward);
        openChestLid();

        int totalTicks = Math.max(Math.max(1, crate.getAnimationDuration()) * 20, 70);
        int[] tick = {0};
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) {
                return;
            }
            if (!player.isOnline() || displays.isEmpty()) {
                finishInterrupted(onComplete);
                return;
            }
            double progress = Math.min(1.0, (double) tick[0] / totalTicks);
            spawnRiftParticles(tick[0]);
            updateDisplays(finalReward, progress, tick[0]);
            tick[0]++;
            if (tick[0] >= totalTicks) {
                finish(crate, onComplete);
            }
        }, 0L, 1L);
    }

    private void spawnCandidates(List<Reward> rewards, Reward fallback) {
        for (int index = 0; index < settings.itemCount(); index++) {
            VoidRiftPath.Point point = VoidRiftPath.sample(
                    index, settings.itemCount(), 0.0, settings);
            ItemDisplay display = WorldAnimationSupport.spawnPrivateItem(
                    plugin, player, offset(point), nextDisplayItem(rewards, fallback), 0.48f);
            displays.add(display);
        }
    }

    private void updateDisplays(Reward finalReward, double progress, int tick) {
        if (!revealed && progress >= REVEAL_PROGRESS) {
            revealWinner(finalReward);
        }
        if (revealed) {
            updateWinner(progress, tick);
            return;
        }

        for (int index = 0; index < displays.size(); index++) {
            ItemDisplay display = displays.get(index);
            if (display == null || display.isDead()) {
                continue;
            }
            VoidRiftPath.Point point = VoidRiftPath.sample(
                    index, settings.itemCount(), progress, settings);
            display.teleport(offset(point));
            WorldAnimationSupport.transform(display, 0.48f,
                    (float) (progress * Math.PI * 6.0));
            if ((tick & 1) == 0) {
                player.spawnParticle(Particle.PORTAL, display.getLocation(),
                        2, 0.03, 0.03, 0.03, 0.02);
            }
        }
    }

    private void revealWinner(Reward finalReward) {
        revealed = true;
        winner = displays.get(0);
        for (int index = displays.size() - 1; index >= 1; index--) {
            ItemDisplay removed = displays.remove(index);
            WorldAnimationSupport.remove(removed);
        }
        winner.setItemStack(finalReward.getDisplayItem());
        WorldAnimationSupport.transform(winner, 0.95f, 0.0f);
        Location riftCenter = origin.clone().add(0, settings.riftHeight(), 0);
        player.spawnParticle(Particle.END_ROD, riftCenter,
                34, 0.45, 0.18, 0.45, 0.035);
    }

    private void updateWinner(double progress, int tick) {
        if (winner == null || winner.isDead()) {
            return;
        }
        double descent = WorldAnimationSupport.easeOutCubic(
                (progress - REVEAL_PROGRESS) / (DESCENT_END_PROGRESS - REVEAL_PROGRESS));
        double height = settings.riftHeight()
                + (settings.resultHeight() - settings.riftHeight()) * descent;
        if (progress >= DESCENT_END_PROGRESS) {
            height += Math.sin(tick * 0.16) * 0.08;
        }
        winner.teleport(origin.clone().add(0, height, 0));
        WorldAnimationSupport.transform(winner, 0.95f,
                (float) (tick * 0.08));
    }

    private void spawnRiftParticles(int tick) {
        if ((tick & 1) != 0) {
            return;
        }
        Location center = origin.clone().add(0, settings.riftHeight(), 0);
        for (int index = 0; index < settings.particleCount(); index++) {
            double angle = Math.PI * 2.0 * index / settings.particleCount() + tick * 0.08;
            Location point = center.clone().add(
                    Math.cos(angle) * 1.05,
                    Math.sin(angle * 2.0) * 0.04,
                    Math.sin(angle) * 0.34);
            player.spawnParticle(index % 4 == 0 ? Particle.END_ROD : Particle.PORTAL,
                    point, 1, 0, 0, 0, 0);
        }
    }

    private ItemStack nextDisplayItem(List<Reward> rewards, Reward fallback) {
        Reward selected = RewardProbability.select(rewards, random);
        return (selected != null ? selected : fallback).getDisplayItem();
    }

    private Location offset(VoidRiftPath.Point point) {
        return origin.clone().add(point.x(), point.y(), point.z());
    }

    private void finish(Crate crate, Runnable onComplete) {
        running = false;
        cancelTask(animationTask);
        animationTask = null;
        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
        cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            completion.complete(onComplete);
        }, 30L);
    }

    private void finishInterrupted(Runnable onComplete) {
        cleanup();
        completion.complete(onComplete);
    }

    private void openChestLid() {
        if (crateLocation != null && ChestLidUtil.isPacketEventsAvailable()) {
            ChestLidUtil.openChestLid(player, crateLocation);
        }
    }

    private void cleanup() {
        running = false;
        cancelTask(animationTask);
        cancelTask(cleanupTask);
        animationTask = null;
        cleanupTask = null;
        for (ItemDisplay display : displays) {
            WorldAnimationSupport.remove(display);
        }
        displays.clear();
        winner = null;
        if (player != null && player.isOnline() && crateLocation != null
                && ChestLidUtil.isPacketEventsAvailable()) {
            ChestLidUtil.closeChestLid(player, crateLocation);
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public void cancel() {
        cleanup();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
