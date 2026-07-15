package gg.fotia.crates.particle;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ParticleManager {

    private final FotiaCrates plugin;
    private final ParticleEffectRenderer renderer = new ParticleEffectRenderer();
    private final Map<String, Long> idleSuppressedUntil = new HashMap<>();
    private final Map<StageEffectKey, BukkitTask> activeStageTasks = new HashMap<>();
    private BukkitTask idleTask;
    private long tickCounter;
    private int idleCursor;

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
        for (BukkitTask task : activeStageTasks.values()) {
            task.cancel();
        }
        activeStageTasks.clear();
        idleSuppressedUntil.clear();
        idleCursor = 0;
    }

    public void playStage(ParticleStage stage, Player player, Crate crate, Location crateLocation) {
        if (crate == null || !crate.isParticlesEnabled()) {
            return;
        }

        CrateParticleEffect effect = crate.getParticleEffect(stage);
        if (effect == null || !effect.isEnabled()) {
            return;
        }

        if (stage != ParticleStage.IDLE) {
            suppressIdle(crateLocation, Math.max(effect.getDuration(), effect.getInterval()) + 20L);
        }

        StageEffectKey taskKey = new StageEffectKey(
                player != null ? player.getUniqueId() : null,
                crate.getId(),
                stage
        );
        BukkitTask previousTask = activeStageTasks.remove(taskKey);
        if (previousTask != null) {
            previousTask.cancel();
        }

        Location origin = resolveOrigin(effect, player, crateLocation);
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        renderer.render(effect, origin, player, crateLocation, 0);
        if (effect.getDuration() <= effect.getInterval()) {
            return;
        }

        int maxActiveTasks = Math.max(1, plugin.getConfigManager().getConfig()
                .getInt("performance.particles.max-active-stage-effects", 128));
        if (activeStageTasks.size() >= maxActiveTasks) {
            return;
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            private int elapsed = effect.getInterval();

            @Override
            public void run() {
                if (elapsed >= effect.getDuration() || (player != null && !player.isOnline())) {
                    finish();
                    return;
                }
                Location frameOrigin = resolveOrigin(effect, player, crateLocation);
                if (frameOrigin != null && frameOrigin.getWorld() != null) {
                    renderer.render(effect, frameOrigin, player, crateLocation, elapsed);
                }
                elapsed += effect.getInterval();
            }

            private void finish() {
                cancel();
                activeStageTasks.remove(taskKey);
            }
        };
        activeStageTasks.put(taskKey,
                runnable.runTaskTimer(plugin, effect.getInterval(), effect.getInterval()));
    }

    public void previewStage(Player player, Crate crate, ParticleStage stage) {
        playStage(stage, player, crate, resolveCrateLocation(player, crate));
    }

    public void previewAll(Player player, Crate crate) {
        for (ParticleStage stage : ParticleStage.values()) {
            previewStage(player, crate, stage);
        }
    }

    private void tickIdleEffects() {
        tickCounter++;
        if (tickCounter % 200L == 0L) {
            idleSuppressedUntil.values().removeIf(until -> until <= tickCounter);
        }

        double viewDistance = Math.max(1.0, plugin.getConfigManager().getConfig()
                .getDouble("performance.particles.idle-view-distance", 32.0));
        int maxCratesPerTick = Math.max(1, plugin.getConfigManager().getConfig()
                .getInt("performance.particles.max-idle-crates-per-tick", 128));
        Set<CrateLocation> nearbyLocations = new LinkedHashSet<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location location = player.getLocation();
            nearbyLocations.addAll(plugin.getCrateManager().getNearbyCrateLocations(
                    player.getWorld().getName(), location.getBlockX(), location.getBlockZ(), viewDistance));
        }

        List<CrateLocation> candidates = new ArrayList<>(nearbyLocations);
        if (candidates.isEmpty()) {
            return;
        }
        int startIndex = Math.floorMod(idleCursor, candidates.size());
        idleCursor = (startIndex + maxCratesPerTick) % candidates.size();
        int rendered = 0;

        for (int offset = 0; offset < candidates.size(); offset++) {
            if (rendered >= maxCratesPerTick) {
                break;
            }
            CrateLocation crateLocation = candidates.get((startIndex + offset) % candidates.size());
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
            if (!world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16),
                    Math.floorDiv(crateLocation.getZ(), 16))) {
                continue;
            }

            Location blockLocation = crateLocation.toLocation(world);
            if (isIdleSuppressed(blockLocation)) {
                continue;
            }
            Location origin = resolveOrigin(effect, null, blockLocation);
            renderer.render(effect, origin, null, blockLocation, (int) (tickCounter % Integer.MAX_VALUE));
            rendered++;
        }
    }

    public Location resolveCrateLocation(Player player, Crate crate) {
        Location nearest = findNearestCrateLocation(player, crate);
        if (nearest != null) {
            return nearest;
        }
        return player != null ? player.getLocation() : null;
    }

    public Location findNearestCrateLocation(Player player, Crate crate) {
        if (player == null || player.getWorld() == null || crate == null) {
            return null;
        }

        Location playerLocation = player.getLocation();
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double searchDistance = Math.max(1.0, plugin.getConfigManager().getConfig()
                .getDouble("performance.particles.stage-location-search-distance", 64.0));
        for (CrateLocation crateLocation : plugin.getCrateManager().getNearbyCrateLocations(
                player.getWorld().getName(), playerLocation.getBlockX(), playerLocation.getBlockZ(), searchDistance)) {
            if (!crate.getId().equals(crateLocation.getCrateId())) {
                continue;
            }

            Location location = crateLocation.toLocation(player.getWorld());
            if (location == null) {
                continue;
            }

            double distance = location.distanceSquared(playerLocation);
            if (distance < nearestDistance) {
                nearest = location;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private Location resolveOrigin(CrateParticleEffect effect, Player player, Location crateLocation) {
        Location normalizedCrate = normalizeCrateLocation(crateLocation);
        return switch (effect.getTarget()) {
            case PLAYER -> player != null ? player.getLocation().clone().add(0, 1.0, 0)
                    : addOrNull(normalizedCrate, 0, 0.5, 0);
            case CRATE_TOP -> {
                Location origin = addOrNull(normalizedCrate, 0, 1.1, 0);
                yield origin != null ? origin : fallbackPlayerOrigin(player);
            }
            case CRATE -> {
                Location origin = addOrNull(normalizedCrate, 0, 0.5, 0);
                yield origin != null ? origin : fallbackPlayerOrigin(player);
            }
        };
    }

    private Location addOrNull(Location location, double x, double y, double z) {
        return location == null ? null : location.clone().add(x, y, z);
    }

    private Location fallbackPlayerOrigin(Player player) {
        return player != null ? player.getLocation().clone().add(0, 1.0, 0) : null;
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

    private void suppressIdle(Location crateLocation, long ticks) {
        String key = locationKey(crateLocation);
        if (key == null) {
            return;
        }
        idleSuppressedUntil.put(key, tickCounter + Math.max(1L, ticks));
    }

    private boolean isIdleSuppressed(Location crateLocation) {
        String key = locationKey(crateLocation);
        if (key == null) {
            return false;
        }
        Long until = idleSuppressedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until <= tickCounter) {
            idleSuppressedUntil.remove(key);
            return false;
        }
        return true;
    }

    private String locationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + ':' + location.getBlockX() + ':' + location.getBlockY() + ':' + location.getBlockZ();
    }

    private record StageEffectKey(UUID playerId, String crateId, ParticleStage stage) {
    }
}
