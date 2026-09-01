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

public final class MeteorJudgmentAnimation implements Animation {

    private final FotiaCrates plugin;
    private final Random random = new Random();
    private final List<MeteorFlight> flights = new ArrayList<>();
    private AnimationCompletion completion = new AnimationCompletion();
    private Player player;
    private Location crateLocation;
    private Location origin;
    private MeteorAnimationSettings settings;
    private Reward finalReward;
    private BukkitTask animationTask;
    private BukkitTask cleanupTask;
    private boolean running;

    public MeteorJudgmentAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation,
                      Runnable onComplete) {
        this.player = player;
        this.crateLocation = crateLocation == null ? null : crateLocation.clone();
        this.finalReward = finalReward;
        this.completion = new AnimationCompletion();
        this.running = true;

        AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                .getAnimationTemplate(crate.getAnimationTemplate(), AnimationType.METEOR_JUDGMENT);
        settings = template != null
                ? template.meteorSettings()
                : MeteorAnimationSettings.from(null);
        origin = WorldAnimationSupport.resolveOrigin(player, crateLocation);
        prepareFlights(crate.getAvailableRewardsFor(player), finalReward);
        openChestLid();

        int sequenceTicks = settings.decoyCount() * settings.staggerTicks()
                + settings.fallTicks() + 25;
        int totalTicks = Math.max(Math.max(1, crate.getAnimationDuration()) * 20, sequenceTicks);
        int[] tick = {0};
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) {
                return;
            }
            if (!player.isOnline()) {
                finishInterrupted(onComplete);
                return;
            }
            updateFlights(crate, tick[0]);
            tick[0]++;
            if (tick[0] >= totalTicks) {
                finish(onComplete);
            }
        }, 0L, 1L);
    }

    private void prepareFlights(List<Reward> rewards, Reward fallback) {
        flights.clear();
        for (int index = 0; index < settings.decoyCount(); index++) {
            double angle = Math.PI * 2.0 * index / settings.decoyCount() + random.nextDouble() * 0.5;
            double impactRadius = settings.impactRadius() * (0.65 + random.nextDouble() * 0.35);
            MeteorPath.Point end = new MeteorPath.Point(
                    Math.cos(angle) * impactRadius,
                    0.2,
                    Math.sin(angle) * impactRadius);
            MeteorPath.Point start = startPoint(end, index);
            flights.add(new MeteorFlight(start, end,
                    index * settings.staggerTicks(), false,
                    nextDisplayItem(rewards, fallback)));
        }

        MeteorPath.Point winnerEnd = new MeteorPath.Point(0, settings.resultHeight(), 0);
        MeteorPath.Point winnerStart = new MeteorPath.Point(
                -settings.spreadRadius() * 0.9,
                settings.skyHeight() + 1.0,
                -settings.spreadRadius() * 0.55);
        flights.add(new MeteorFlight(winnerStart, winnerEnd,
                settings.decoyCount() * settings.staggerTicks(), true,
                nextDisplayItem(rewards, fallback)));
    }

    private MeteorPath.Point startPoint(MeteorPath.Point end, int index) {
        double direction = index % 2 == 0 ? -1.0 : 1.0;
        return new MeteorPath.Point(
                end.x() + direction * settings.spreadRadius() * (0.75 + random.nextDouble() * 0.35),
                settings.skyHeight() + random.nextDouble() * 1.4,
                end.z() - settings.spreadRadius() * (0.45 + random.nextDouble() * 0.4));
    }

    private void updateFlights(Crate crate, int tick) {
        for (MeteorFlight flight : flights) {
            if (tick < flight.startTick || flight.impacted) {
                if (flight.winner && flight.impacted) {
                    hoverWinner(flight, tick);
                }
                continue;
            }
            if (flight.display == null) {
                spawnFlight(crate, flight);
            }
            double progress = WorldAnimationSupport.clamp01(
                    (double) (tick - flight.startTick) / settings.fallTicks());
            MeteorPath.Point point = MeteorPath.sample(flight.start, flight.end, progress);
            Location location = offset(point);
            flight.display.teleport(location);
            WorldAnimationSupport.transform(flight.display, flight.winner ? 0.7f : 0.48f,
                    (float) (tick * 0.16));
            player.spawnParticle(flight.winner ? Particle.END_ROD : Particle.FLAME,
                    location, settings.trailParticleCount(), 0.04, 0.04, 0.04, 0.015);
            if (progress >= 1.0) {
                impact(crate, flight);
            }
        }
    }

    private void spawnFlight(Crate crate, MeteorFlight flight) {
        flight.display = WorldAnimationSupport.spawnPrivateItem(
                plugin, player, offset(flight.start), flight.item, flight.winner ? 0.7f : 0.48f);
        if (crate.getSpinSound() != null) {
            player.playSound(player.getLocation(), crate.getSpinSound(),
                    crate.getSpinVolume(), Math.min(2.0f,
                            crate.getSpinPitch() + flight.startTick * 0.006f));
        }
    }

    private void impact(Crate crate, MeteorFlight flight) {
        flight.impacted = true;
        Location impact = offset(flight.end);
        if (!flight.winner) {
            WorldAnimationSupport.remove(flight.display);
            flight.display = null;
            player.spawnParticle(Particle.CLOUD, impact,
                    10, 0.22, 0.08, 0.22, 0.025);
            return;
        }

        flight.display.setItemStack(finalReward.getDisplayItem());
        WorldAnimationSupport.transform(flight.display, 1.0f, 0.0f);
        spawnWinnerImpact(impact);
        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
    }

    private void hoverWinner(MeteorFlight flight, int tick) {
        if (flight.display == null || flight.display.isDead()) {
            return;
        }
        double bob = Math.sin(tick * 0.16) * 0.08;
        flight.display.teleport(origin.clone().add(0, settings.resultHeight() + bob, 0));
        WorldAnimationSupport.transform(flight.display, 1.0f, (float) (tick * 0.08));
    }

    private void spawnWinnerImpact(Location impact) {
        player.spawnParticle(Particle.CLOUD, impact,
                22, 0.4, 0.12, 0.4, 0.04);
        for (int index = 0; index < 20; index++) {
            double angle = Math.PI * 2.0 * index / 20.0;
            Location point = impact.clone().add(
                    Math.cos(angle) * 1.15, 0.08, Math.sin(angle) * 1.15);
            player.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }

    private ItemStack nextDisplayItem(List<Reward> rewards, Reward fallback) {
        Reward selected = RewardProbability.select(rewards, random);
        return (selected != null ? selected : fallback).getDisplayItem();
    }

    private Location offset(MeteorPath.Point point) {
        return origin.clone().add(point.x(), point.y(), point.z());
    }

    private void finish(Runnable onComplete) {
        running = false;
        cancelTask(animationTask);
        animationTask = null;
        cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            completion.complete(onComplete);
        }, 25L);
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
        for (MeteorFlight flight : flights) {
            WorldAnimationSupport.remove(flight.display);
            flight.display = null;
        }
        flights.clear();
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

    private static final class MeteorFlight {
        private final MeteorPath.Point start;
        private final MeteorPath.Point end;
        private final int startTick;
        private final boolean winner;
        private final ItemStack item;
        private ItemDisplay display;
        private boolean impacted;

        private MeteorFlight(MeteorPath.Point start, MeteorPath.Point end, int startTick,
                             boolean winner, ItemStack item) {
            this.start = start;
            this.end = end;
            this.startTick = startTick;
            this.winner = winner;
            this.item = item;
        }
    }
}
