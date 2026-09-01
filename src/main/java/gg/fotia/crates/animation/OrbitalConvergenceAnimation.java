package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import gg.fotia.crates.util.ChestLidUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class OrbitalConvergenceAnimation implements Animation {

    private final FotiaCrates plugin;
    private final Random random = new Random();
    private final List<ItemDisplay> displays = new ArrayList<>();
    private AnimationCompletion completion = new AnimationCompletion();
    private Player player;
    private Location crateLocation;
    private BukkitTask animationTask;
    private BukkitTask cleanupTask;
    private boolean running;
    private boolean converged;

    public OrbitalConvergenceAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation,
                      Runnable onComplete) {
        this.player = player;
        this.crateLocation = crateLocation == null ? null : crateLocation.clone();
        this.completion = new AnimationCompletion();
        this.running = true;
        this.converged = false;

        AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                .getAnimationTemplate(crate.getAnimationTemplate(), AnimationType.ORBITAL_CONVERGENCE);
        OrbitalAnimationSettings settings = template != null
                ? template.orbitalSettings()
                : OrbitalAnimationSettings.from(null);
        Location origin = resolveOrigin(player, crateLocation);
        List<Reward> rewards = crate.getAvailableRewardsFor(player);
        spawnDisplays(origin, settings, rewards, finalReward);
        if (this.crateLocation != null && ChestLidUtil.isPacketEventsAvailable()) {
            ChestLidUtil.openChestLid(player, this.crateLocation);
        }

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
            updateDisplays(origin, settings, finalReward, progress);
            spawnTrailParticles(origin, settings, progress);
            tick[0]++;
            if (tick[0] >= totalTicks) {
                finish(crate, onComplete);
            }
        }, 0L, 1L);
    }

    private void spawnDisplays(Location origin, OrbitalAnimationSettings settings,
                               List<Reward> rewards, Reward fallback) {
        for (int index = 0; index < settings.itemCount(); index++) {
            OrbitalPath.Point point = OrbitalPath.sample(
                    index, settings.itemCount(), 0.0,
                    settings.radius(), settings.height(), settings.rotations());
            Location location = offset(origin, point);
            ItemStack item = nextDisplayItem(rewards, fallback);
            ItemDisplay display = origin.getWorld().spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setTransformation(transformation(0.48f, 0.0f));
                entity.setViewRange(48.0f);
                entity.setShadowRadius(0.0f);
                entity.setShadowStrength(0.0f);
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
            });
            player.showEntity(plugin, display);
            displays.add(display);
        }
    }

    private void updateDisplays(Location origin, OrbitalAnimationSettings settings,
                                Reward finalReward, double progress) {
        if (!converged && progress >= 0.82) {
            converged = true;
            for (int index = displays.size() - 1; index >= 1; index--) {
                ItemDisplay display = displays.remove(index);
                if (!display.isDead()) {
                    display.remove();
                }
            }
            ItemDisplay winner = displays.get(0);
            winner.setItemStack(finalReward.getDisplayItem());
            winner.setTransformation(transformation(0.95f, 0.0f));
            player.spawnParticle(Particle.END_ROD,
                    origin.clone().add(0, settings.height(), 0),
                    28, 0.35, 0.35, 0.35, 0.03);
        }

        for (int index = 0; index < displays.size(); index++) {
            ItemDisplay display = displays.get(index);
            if (display == null || display.isDead()) {
                continue;
            }
            OrbitalPath.Point point = OrbitalPath.sample(
                    index, settings.itemCount(), progress,
                    settings.radius(), settings.height(), settings.rotations());
            display.teleport(offset(origin, point));
            if (!converged) {
                display.setTransformation(transformation(0.48f,
                        (float) (progress * Math.PI * 4.0)));
            }
        }
    }

    private void spawnTrailParticles(Location origin, OrbitalAnimationSettings settings,
                                     double progress) {
        if (((int) (progress * 1000)) % 3 != 0) {
            return;
        }
        for (int index = 0; index < Math.min(displays.size(), 8); index++) {
            OrbitalPath.Point point = OrbitalPath.sample(
                    index, settings.itemCount(), progress,
                    settings.radius(), settings.height(), settings.rotations());
            player.spawnParticle(Particle.PORTAL, offset(origin, point),
                    2, 0.02, 0.02, 0.02, 0.01);
        }
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
        }, 35L);
    }

    private void finishInterrupted(Runnable onComplete) {
        cleanup();
        completion.complete(onComplete);
    }

    private ItemStack nextDisplayItem(List<Reward> rewards, Reward fallback) {
        Reward selected = RewardProbability.select(rewards, random);
        return (selected != null ? selected : fallback).getDisplayItem();
    }

    private Location resolveOrigin(Player player, Location configured) {
        if (configured != null && configured.getWorld() != null) {
            return configured.clone().add(0.5, 0.0, 0.5);
        }
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) {
            direction.setX(0).setY(0).setZ(1);
        } else {
            direction.normalize();
        }
        location.add(direction.multiply(2.0));
        return location;
    }

    private Location offset(Location origin, OrbitalPath.Point point) {
        return origin.clone().add(point.x(), point.y(), point.z());
    }

    private Transformation transformation(float scale, float rotation) {
        return new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(rotation, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        );
    }

    private void cleanup() {
        running = false;
        cancelTask(animationTask);
        cancelTask(cleanupTask);
        animationTask = null;
        cleanupTask = null;
        for (ItemDisplay display : displays) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        displays.clear();
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
