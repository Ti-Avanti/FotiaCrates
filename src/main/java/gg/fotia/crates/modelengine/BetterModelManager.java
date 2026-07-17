package gg.fotia.crates.modelengine;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.Tracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BetterModel integration manager.
 */
public class BetterModelManager {

    private static final String BETTER_MODEL_BASE_TAG = "fotiacrates_bettermodel";
    private static final String MODEL_ENGINE_BASE_TAG = "fotiacrates_modelengine";

    private final FotiaCrates plugin;
    private final Map<Location, UUID> crateModels = new HashMap<>();
    private final TrackerModifier crateTrackerModifier = TrackerModifier.builder()
            .sightTrace(false)
            .damageAnimation(false)
            .damageTint(false)
            .build();
    private boolean betterModelAvailable;

    public BetterModelManager(FotiaCrates plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            return;
        }

        try {
            BetterModel.platform();
            betterModelAvailable = true;
            plugin.getLogger().info("BetterModel integration enabled.");
        } catch (RuntimeException | LinkageError e) {
            betterModelAvailable = false;
            plugin.getLogger().warning("Failed to initialize BetterModel integration: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return betterModelAvailable;
    }

    public void spawnCrateModel(Crate crate, Location location, Player placer) {
        float yaw = 0f;
        if (placer != null) {
            Location spawnLoc = location.clone().add(0.5, 0, 0.5);
            Location playerLoc = placer.getLocation();
            double dx = playerLoc.getX() - spawnLoc.getX();
            double dz = playerLoc.getZ() - spawnLoc.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        spawnCrateModel(crate, location, yaw);
    }

    public void spawnCrateModel(Crate crate, Location location, float yaw) {
        if (!betterModelAvailable || !crate.isBetterModelEnabled()) {
            return;
        }

        Location blockLoc = location.getBlock().getLocation();
        try {
            var renderer = BetterModel.model(crate.getModelEngineId()).orElse(null);
            if (renderer == null) {
                plugin.getLogger().warning("BetterModel model not found: " + crate.getModelEngineId());
                return;
            }

            removeCrateModelInternal(blockLoc, false);
            blockLoc.getBlock().setType(Material.BARRIER);

            Location spawnLoc = blockLoc.clone().add(0.5, 0, 0.5);
            spawnLoc.setYaw(yaw);

            ArmorStand baseEntity = blockLoc.getWorld().spawn(spawnLoc, ArmorStand.class, armorStand -> {
                armorStand.setVisible(false);
                armorStand.setGravity(false);
                armorStand.setInvulnerable(true);
                armorStand.setMarker(true);
                armorStand.setPersistent(true);
                armorStand.addScoreboardTag(BETTER_MODEL_BASE_TAG);
            });

            EntityTracker tracker = renderer.create(BukkitAdapter.adapt(baseEntity), crateTrackerModifier);
            playAnimation(tracker, crate.getModelEngineIdleAnimation(), AnimationModifier.DEFAULT);

            crateModels.put(blockLoc, baseEntity.getUniqueId());
            plugin.getLogger().info("Spawned BetterModel model for crate: " + crate.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn BetterModel model: " + e.getMessage());
        }
    }

    public void removeCrateModel(Location location) {
        removeCrateModelInternal(location.getBlock().getLocation(), true);
    }

    void removeCrateModel(Location location, boolean clearBarrierBlock) {
        removeCrateModelInternal(location.getBlock().getLocation(), clearBarrierBlock);
    }

    private boolean removeCrateModelInternal(Location blockLoc, boolean clearBarrierBlock) {
        crateModels.remove(blockLoc);

        boolean removedAny = false;
        for (Entity entity : findNearbyModelBaseEntities(blockLoc)) {
            removeBetterModelRegistry(entity);
            entity.remove();
            removedAny = true;
        }

        if (clearBarrierBlock && blockLoc.getBlock().getType() == Material.BARRIER) {
            blockLoc.getBlock().setType(Material.AIR);
        }

        return removedAny;
    }

    private void removeBetterModelRegistry(Entity entity) {
        if (!betterModelAvailable) {
            return;
        }

        try {
            BetterModel.registry(entity.getUniqueId()).ifPresent(EntityTrackerRegistry::close);
        } catch (RuntimeException ignored) {
        }
    }

    public void playOpenAnimation(Crate crate, Location location, Player openingPlayer) {
        if (!betterModelAvailable || !crate.isBetterModelEnabled()) {
            return;
        }

        EntityTracker tracker = resolveTracker(location.getBlock().getLocation(), true);
        if (tracker == null) {
            return;
        }

        stopAnimation(tracker, crate.getModelEngineIdleAnimation());
        playAnimation(tracker, crate.getModelEngineOpenAnimation(), AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
    }

    public void playIdleAnimation(Crate crate, Location location) {
        if (!betterModelAvailable || !crate.isBetterModelEnabled()) {
            return;
        }

        EntityTracker tracker = resolveTracker(location.getBlock().getLocation(), true);
        if (tracker == null) {
            return;
        }

        stopAnimation(tracker, crate.getModelEngineOpenAnimation());
        playAnimation(tracker, crate.getModelEngineIdleAnimation(), AnimationModifier.DEFAULT);
    }

    private void stopAnimation(Tracker tracker, String animationName) {
        if (animationName == null || animationName.isBlank()) {
            return;
        }
        tracker.stopAnimation(animationName);
    }

    private void playAnimation(Tracker tracker, String animationName, AnimationModifier modifier) {
        if (animationName == null || animationName.isBlank()) {
            return;
        }
        tracker.animate(animationName, modifier);
    }

    private EntityTracker resolveTracker(Location blockLoc, boolean removeDuplicates) {
        Entity entity = resolveModelEntity(blockLoc, removeDuplicates);
        if (entity == null || !betterModelAvailable) {
            return null;
        }

        try {
            EntityTrackerRegistry registry = BetterModel.registry(entity.getUniqueId()).orElse(null);
            return registry != null ? registry.first() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Entity resolveModelEntity(Location blockLoc, boolean removeDuplicates) {
        UUID trackedUuid = crateModels.get(blockLoc);
        List<Entity> nearbyEntities = findNearbyModelBaseEntities(blockLoc);
        if (nearbyEntities.isEmpty()) {
            crateModels.remove(blockLoc);
            return null;
        }

        Entity primary = null;
        if (trackedUuid != null) {
            for (Entity entity : nearbyEntities) {
                if (entity.getUniqueId().equals(trackedUuid)) {
                    primary = entity;
                    break;
                }
            }
        }
        if (primary == null) {
            primary = nearbyEntities.get(0);
        }

        crateModels.put(blockLoc, primary.getUniqueId());

        if (removeDuplicates && nearbyEntities.size() > 1) {
            for (Entity entity : nearbyEntities) {
                if (entity.getUniqueId().equals(primary.getUniqueId())) {
                    continue;
                }
                removeBetterModelRegistry(entity);
                entity.remove();
            }
            plugin.getLogger().warning("Removed duplicate BetterModel base entities at " + formatLocation(blockLoc));
        }

        return primary;
    }

    private boolean isModelBaseEntity(Entity entity, Location blockLoc) {
        if (!(entity instanceof ArmorStand armorStand)) {
            return false;
        }
        if (armorStand.isVisible() || !armorStand.isMarker()) {
            return false;
        }
        if (armorStand.hasGravity() || !armorStand.isInvulnerable()) {
            return false;
        }

        Location entityBlockLoc = armorStand.getLocation().getBlock().getLocation();
        if (!entityBlockLoc.equals(blockLoc)) {
            return false;
        }

        Set<String> tags = armorStand.getScoreboardTags();
        if (tags.contains(MODEL_ENGINE_BASE_TAG)) {
            return false;
        }
        if (tags.contains(BETTER_MODEL_BASE_TAG)) {
            return true;
        }

        UUID trackedUuid = crateModels.get(blockLoc);
        return trackedUuid != null && trackedUuid.equals(entity.getUniqueId());
    }

    private List<Entity> findNearbyModelBaseEntities(Location blockLoc) {
        if (blockLoc.getWorld() == null) {
            return List.of();
        }

        List<Entity> entities = new ArrayList<>();
        Location searchLoc = blockLoc.clone().add(0.5, 0, 0.5);
        for (Entity nearby : blockLoc.getWorld().getNearbyEntities(searchLoc, 0.5, 1.0, 0.5)) {
            if (isModelBaseEntity(nearby, blockLoc)) {
                entities.add(nearby);
            }
        }
        return entities;
    }

    private String formatLocation(Location location) {
        return location.getWorld() == null
                ? "(unknown)"
                : location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    public boolean hasModel(Location location) {
        return resolveTracker(location.getBlock().getLocation(), true) != null;
    }

    public void cleanup() {
        Set<Location> locationsToCleanup = new HashSet<>(crateModels.keySet());

        if (plugin.getCrateManager() != null) {
            for (var crateLocation : plugin.getCrateManager().getCrateLocations()) {
                var world = plugin.getServer().getWorld(crateLocation.getWorld());
                if (world == null || !world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16),
                        Math.floorDiv(crateLocation.getZ(), 16))) {
                    continue;
                }
                locationsToCleanup.add(crateLocation.toLocation(world).getBlock().getLocation());
            }
        }

        locationsToCleanup.removeIf(location -> location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        for (Location location : locationsToCleanup) {
            removeCrateModelInternal(location, false);
        }
        crateModels.clear();
    }
}
