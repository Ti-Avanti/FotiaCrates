package gg.fotia.crates.particle;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ParticleManager {

    private final FotiaCrates plugin;
    private final ParticleEffectRenderer renderer = new ParticleEffectRenderer();
    private BukkitTask idleTask;
    private long tickCounter;

    public ParticleManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void start() {
        cancel();
        idleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickIdleEffects, 20L, 1L);
    }

    public void restart() {
        start();
    }

    public void cancel() {
        if (idleTask != null) {
            idleTask.cancel();
            idleTask = null;
        }
    }

    public void playStage(ParticleStage stage, Player player, Crate crate, Location crateLocation) {
        if (crate == null || !crate.isParticlesEnabled()) {
            return;
        }

        CrateParticleEffect effect = crate.getParticleEffect(stage);
        if (effect == null || !effect.isEnabled()) {
            return;
        }

        Location origin = resolveOrigin(effect, player, crateLocation);
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        renderer.render(effect, origin, player, crateLocation, 0);
        if (effect.getDuration() <= effect.getInterval()) {
            return;
        }

        new BukkitRunnable() {
            private int elapsed = effect.getInterval();

            @Override
            public void run() {
                if (elapsed >= effect.getDuration()) {
                    cancel();
                    return;
                }
                Location frameOrigin = resolveOrigin(effect, player, crateLocation);
                if (frameOrigin != null && frameOrigin.getWorld() != null) {
                    renderer.render(effect, frameOrigin, player, crateLocation, elapsed);
                }
                elapsed += effect.getInterval();
            }
        }.runTaskTimer(plugin, effect.getInterval(), effect.getInterval());
    }

    public void previewStage(Player player, Crate crate, ParticleStage stage) {
        playStage(stage, player, crate, player.getLocation());
    }

    public void previewAll(Player player, Crate crate) {
        for (ParticleStage stage : ParticleStage.values()) {
            previewStage(player, crate, stage);
        }
    }

    private void tickIdleEffects() {
        tickCounter++;
        for (CrateLocation crateLocation : plugin.getCrateManager().getCrateLocations()) {
            Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
            if (crate == null || !crate.isParticlesEnabled()) {
                continue;
            }

            CrateParticleEffect effect = crate.getParticleEffect(ParticleStage.IDLE);
            if (effect == null || !effect.isEnabled() || tickCounter % effect.getInterval() != 0) {
                continue;
            }

            World world = plugin.getServer().getWorld(crateLocation.getWorld());
            if (world == null) {
                continue;
            }

            Location blockLocation = crateLocation.toLocation(world);
            Location origin = resolveOrigin(effect, null, blockLocation);
            renderer.render(effect, origin, null, blockLocation, (int) (tickCounter % Integer.MAX_VALUE));
        }
    }

    private Location resolveOrigin(CrateParticleEffect effect, Player player, Location crateLocation) {
        return switch (effect.getTarget()) {
            case PLAYER -> player != null ? player.getLocation().clone().add(0, 1.0, 0)
                    : normalizeCrateLocation(crateLocation).add(0, 0.5, 0);
            case CRATE_TOP -> normalizeCrateLocation(crateLocation).add(0, 1.1, 0);
            case CRATE -> normalizeCrateLocation(crateLocation).add(0, 0.5, 0);
        };
    }

    private Location normalizeCrateLocation(Location crateLocation) {
        if (crateLocation == null) {
            return null;
        }
        return new Location(crateLocation.getWorld(),
                crateLocation.getBlockX() + 0.5,
                crateLocation.getBlockY(),
                crateLocation.getBlockZ() + 0.5,
                crateLocation.getYaw(),
                crateLocation.getPitch());
    }
}
