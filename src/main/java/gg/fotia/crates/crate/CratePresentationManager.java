package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns both the model delay and GUI animation completion, including disconnects. */
public final class CratePresentationManager {
    private final FotiaCrates plugin;
    private final Map<UUID, Presentation> active = new HashMap<>();

    public CratePresentationManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void play(UUID playerId, Crate crate, List<Reward> rewards, Location location,
                     boolean placedCrate, boolean animate, Runnable completion) {
        cancel(playerId);
        Presentation presentation = new Presentation(playerId, crate, location, completion);
        active.put(playerId, presentation);
        Player player = plugin.getServer().getPlayer(playerId);
        if (!plugin.isEnabled() || player == null || !player.isOnline()) {
            presentation.finish();
            return;
        }
        try {
            plugin.getParticleManager().playStage(ParticleStage.OPEN, player, crate, location);
            if (placedCrate && location != null) {
                presentation.hidden = true;
                plugin.getHologramManager().hideHologram(location);
                if (crate.isModelEnabled() && plugin.getModelEngineManager().ensureCrateModel(crate, location)) {
                    plugin.getModelEngineManager().playOpenAnimation(crate, location, player);
                    presentation.delay = plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> presentation.startAnimation(rewards, animate), crate.getModelEngineOpenDelay());
                    return;
                }
            }
            presentation.startAnimation(rewards, animate);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Crate presentation failed: " + exception.getMessage());
            presentation.finish();
        }
    }

    public void cancel(UUID playerId) {
        Presentation presentation = active.get(playerId);
        if (presentation != null) presentation.finish();
    }

    public void cancelAll() {
        List.copyOf(active.values()).forEach(Presentation::finish);
    }

    private final class Presentation {
        private final UUID playerId;
        private final Crate crate;
        private final Location location;
        private final Runnable completion;
        private BukkitTask delay;
        private boolean hidden;
        private boolean completed;

        private Presentation(UUID playerId, Crate crate, Location location, Runnable completion) {
            this.playerId = playerId;
            this.crate = crate;
            this.location = location;
            this.completion = completion;
        }

        private void startAnimation(List<Reward> rewards, boolean animate) {
            if (completed) return;
            Player player = plugin.getServer().getPlayer(playerId);
            try {
                if (!animate || player == null || !player.isOnline()
                        || !plugin.getAnimationManager().playAnimation(player, crate, rewards, location, this::finish)) {
                    finish();
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Crate animation failed: " + exception.getMessage());
                finish();
            }
        }

        private void finish() {
            if (completed) return;
            completed = true;
            if (delay != null) delay.cancel();
            active.remove(playerId, this);
            try {
                if (hidden) {
                    plugin.getHologramManager().showHologram(location, crate.getId());
                    if (plugin.isEnabled() && crate.isModelEnabled() && plugin.getCrateManager().isLocationSet(location)) {
                        plugin.getModelEngineManager().playIdleAnimation(crate, location);
                    }
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not restore crate presentation: " + exception.getMessage());
            } finally {
                completion.run();
            }
        }
    }
}
