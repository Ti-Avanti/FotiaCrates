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

    /**
     * 附近宝箱扫描的刷新周期；渲染门控仍逐 tick 判定，仅空间查询降频
     */
    private static final long IDLE_SCAN_REFRESH_TICKS = 5L;

    private final FotiaCrates plugin;
    private final ParticleEffectRenderer renderer = new ParticleEffectRenderer();
    private final Map<String, Long> idleSuppressedUntil = new HashMap<>();
    private final Map<StageEffectKey, BukkitTask> activeStageTasks = new HashMap<>();
    private final Set<CrateLocation> nearbyIdleLocations = new LinkedHashSet<>();
    private final List<CrateLocation> idleCandidates = new ArrayList<>();
    private BukkitTask idleTask;
    private long tickCounter;
    private long lastIdleScanTick;
    private int idleCursor;
    private double idleViewDistance;
    private int maxIdleCratesPerTick;
    private int maxActiveStageEffects;
    private double stageLocationSearchDistance;

    public ParticleManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void start() {
        cancel();
        refreshSettings();
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
        nearbyIdleLocations.clear();
        idleCandidates.clear();
        idleCursor = 0;
        lastIdleScanTick = 0L;
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

        if (activeStageTasks.size() >= maxActiveStageEffects) {
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

        // 每 tick 对全部在线玩家做空间查询开销过高，且大多数 tick 的结果会被 interval 门控丢弃；
        // 候选列表按周期刷新（32 格视距下 5 tick 的滞后不可感知）
        if (lastIdleScanTick == 0L || tickCounter - lastIdleScanTick >= IDLE_SCAN_REFRESH_TICKS) {
            lastIdleScanTick = tickCounter;
            nearbyIdleLocations.clear();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Location location = player.getLocation();
                nearbyIdleLocations.addAll(plugin.getCrateManager().getNearbyCrateLocations(
                        player.getWorld().getName(), location.getBlockX(), location.getBlockZ(), idleViewDistance));
            }
            idleCandidates.clear();
            idleCandidates.addAll(nearbyIdleLocations);
        }

        if (idleCandidates.isEmpty()) {
            return;
        }
        int startIndex = Math.floorMod(idleCursor, idleCandidates.size());
        idleCursor = (startIndex + maxIdleCratesPerTick) % idleCandidates.size();
        int rendered = 0;

        for (int offset = 0; offset < idleCandidates.size(); offset++) {
            if (rendered >= maxIdleCratesPerTick) {
                break;
            }
            CrateLocation crateLocation = idleCandidates.get((startIndex + offset) % idleCandidates.size());
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
        for (CrateLocation crateLocation : plugin.getCrateManager().getNearbyCrateLocations(
                player.getWorld().getName(), playerLocation.getBlockX(), playerLocation.getBlockZ(),
                stageLocationSearchDistance)) {
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

    private void refreshSettings() {
        idleViewDistance = plugin.getConfigManager().getParticleIdleViewDistance();
        maxIdleCratesPerTick = plugin.getConfigManager().getMaxIdleParticleCratesPerTick();
        maxActiveStageEffects = plugin.getConfigManager().getMaxActiveParticleStageEffects();
        stageLocationSearchDistance = plugin.getConfigManager().getParticleStageLocationSearchDistance();
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
