package gg.fotia.crates.modelengine;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ModelEngine integration manager.
 * Uses reflection to avoid a hard dependency on ModelEngine.
 */
public class ModelEngineManager {

    private final FotiaCrates plugin;
    private final Map<Location, UUID> crateModels = new HashMap<>();
    private boolean modelEngineAvailable = false;

    private Object modelEngineAPI;
    private Method createModeledEntityMethod;
    private Method getBlueprintMethod;
    private Method getModeledEntityMethod;

    public ModelEngineManager(FotiaCrates plugin) {
        this.plugin = plugin;
        initializeReflection();
    }

    private void initializeReflection() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            plugin.getLogger().info("ModelEngine not found, model features disabled.");
            return;
        }

        try {
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Method apiMethod = modelEngineAPIClass.getMethod("api");
            modelEngineAPI = apiMethod.invoke(null);

            getBlueprintMethod = modelEngineAPIClass.getMethod("getBlueprint", String.class);
            createModeledEntityMethod = modelEngineAPIClass.getMethod("createModeledEntity", Entity.class);
            getModeledEntityMethod = modelEngineAPIClass.getMethod("getModeledEntity", Entity.class);

            modelEngineAvailable = true;
            plugin.getLogger().info("ModelEngine integration enabled (R4.x API).");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("ModelEngine API class not found. Model features disabled.");
            modelEngineAvailable = false;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("ModelEngine API method not found: " + e.getMessage() + ". Trying alternative API...");
            tryAlternativeAPI();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize ModelEngine integration: " + e.getMessage());
            modelEngineAvailable = false;
        }
    }

    private void tryAlternativeAPI() {
        try {
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");

            getBlueprintMethod = modelEngineAPIClass.getMethod("getBlueprint", String.class);

            try {
                createModeledEntityMethod = modelEngineAPIClass.getMethod("createModeledEntity", Entity.class);
            } catch (NoSuchMethodException e) {
                createModeledEntityMethod = modelEngineAPIClass.getMethod("createModeledEntity", org.bukkit.entity.LivingEntity.class);
            }

            try {
                getModeledEntityMethod = modelEngineAPIClass.getMethod("getModeledEntity", Entity.class);
            } catch (NoSuchMethodException e) {
                getModeledEntityMethod = modelEngineAPIClass.getMethod("getModeledEntity", org.bukkit.entity.LivingEntity.class);
            }

            modelEngineAvailable = true;
            plugin.getLogger().info("ModelEngine integration enabled (alternative API).");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize alternative ModelEngine API: " + e.getMessage());
            modelEngineAvailable = false;
        }
    }

    public boolean isAvailable() {
        return modelEngineAvailable;
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
        if (!modelEngineAvailable || !crate.isModelEngineEnabled()) {
            return;
        }

        Location blockLoc = location.getBlock().getLocation();

        try {
            Object blueprintOptional = getBlueprintMethod.invoke(null, crate.getModelEngineId());

            Object blueprint = null;
            if (blueprintOptional instanceof java.util.Optional<?> opt) {
                if (opt.isEmpty()) {
                    plugin.getLogger().warning("ModelEngine blueprint not found: " + crate.getModelEngineId());
                    return;
                }
                blueprint = opt.get();
            } else if (blueprintOptional != null) {
                blueprint = blueprintOptional;
            } else {
                plugin.getLogger().warning("ModelEngine blueprint not found: " + crate.getModelEngineId());
                return;
            }

            Object activeModel = createActiveModel(blueprint, crate.getModelEngineId());
            if (activeModel == null) {
                plugin.getLogger().warning("Failed to create ActiveModel for: " + crate.getModelEngineId());
                return;
            }

            // Clear any stale model entity before spawning a new one at the same location.
            removeCrateModelInternal(blockLoc, false);

            blockLoc.getBlock().setType(Material.BARRIER);

            Location spawnLoc = blockLoc.clone().add(0.5, 0, 0.5);
            spawnLoc.setYaw(yaw);

            Entity baseEntity = blockLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.ArmorStand.class, armorStand -> {
                armorStand.setVisible(false);
                armorStand.setGravity(false);
                armorStand.setInvulnerable(true);
                armorStand.setMarker(true);
                armorStand.setPersistent(true);
            });

            Object modeledEntity = createModeledEntityMethod.invoke(null, baseEntity);

            Class<?> activeModelClass = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Method addModelMethod = modeledEntity.getClass().getMethod("addModel", activeModelClass, boolean.class);
            addModelMethod.invoke(modeledEntity, activeModel, true);

            setModelViewRange(modeledEntity, crate.getModelEngineViewRange());
            playAnimation(activeModel, crate.getModelEngineIdleAnimation(), true);

            crateModels.put(blockLoc, baseEntity.getUniqueId());
            plugin.getLogger().info("Spawned ModelEngine model for crate: " + crate.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn ModelEngine model: " + e.getMessage());
        }
    }

    public void removeCrateModel(Location location) {
        removeCrateModelInternal(location.getBlock().getLocation(), true);
    }

    private boolean removeCrateModelInternal(Location blockLoc, boolean clearBarrierBlock) {
        crateModels.remove(blockLoc);

        boolean removedAny = false;
        for (Entity entity : findNearbyModelBaseEntities(blockLoc)) {
            removeModeledEntity(entity);
            entity.remove();
            removedAny = true;
        }

        if (clearBarrierBlock && blockLoc.getBlock().getType() == Material.BARRIER) {
            blockLoc.getBlock().setType(Material.AIR);
        }

        return removedAny;
    }

    private void removeModeledEntity(Entity entity) {
        if (!modelEngineAvailable || modelEngineAPI == null) {
            return;
        }

        try {
            Method removeMethod = modelEngineAPI.getClass().getMethod("removeModeledEntity", Entity.class);
            removeMethod.invoke(modelEngineAPI, entity);
        } catch (Exception ignored) {
        }
    }

    public void playOpenAnimation(Crate crate, Location location, Player openingPlayer) {
        if (!modelEngineAvailable || !crate.isModelEngineEnabled()) {
            return;
        }

        Entity entity = resolveModelEntity(location.getBlock().getLocation(), true);
        if (entity == null) {
            return;
        }

        try {
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) {
                return;
            }

            Method getModels = modeledEntity.getClass().getMethod("getModels");
            Object models = getModels.invoke(modeledEntity);

            if (models instanceof Map<?, ?> modelMap && !modelMap.isEmpty()) {
                Object activeModel = modelMap.values().iterator().next();
                String idleAnim = crate.getModelEngineIdleAnimation();
                String openAnim = crate.getModelEngineOpenAnimation();

                stopAnimation(activeModel, idleAnim);
                playAnimation(activeModel, openAnim, true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play open animation: " + e.getMessage());
        }
    }

    public void playOpenAnimation(Crate crate, Location location) {
        playOpenAnimation(crate, location, null);
    }

    public void playIdleAnimation(Crate crate, Location location) {
        if (!modelEngineAvailable || !crate.isModelEngineEnabled()) {
            return;
        }

        Entity entity = resolveModelEntity(location.getBlock().getLocation(), true);
        if (entity == null) {
            return;
        }

        try {
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) {
                return;
            }

            Method getModels = modeledEntity.getClass().getMethod("getModels");
            Object models = getModels.invoke(modeledEntity);

            if (models instanceof Map<?, ?> modelMap && !modelMap.isEmpty()) {
                Object activeModel = modelMap.values().iterator().next();
                String idleAnim = crate.getModelEngineIdleAnimation();
                String openAnim = crate.getModelEngineOpenAnimation();

                stopAnimation(activeModel, openAnim);
                playAnimation(activeModel, idleAnim, true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play idle animation: " + e.getMessage());
        }
    }

    private void setModelViewRange(Object modeledEntity, int viewRange) {
        try {
            Method getBase = modeledEntity.getClass().getMethod("getBase");
            Object baseEntity = getBase.invoke(modeledEntity);
            if (baseEntity != null) {
                try {
                    Method setRenderRadius = baseEntity.getClass().getMethod("setRenderRadius", int.class);
                    setRenderRadius.invoke(baseEntity, viewRange);
                    plugin.getLogger().info("Set model view range to " + viewRange + " via BaseEntity.setRenderRadius");
                    return;
                } catch (NoSuchMethodException e) {
                    plugin.getLogger().warning("BaseEntity.setRenderRadius not found");
                }
            }

            plugin.getLogger().warning("Could not set model view range - BaseEntity not available");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set model view range: " + e.getMessage());
        }
    }

    private void stopAnimation(Object activeModel, String animationName) {
        try {
            Method getAnimationHandler = activeModel.getClass().getMethod("getAnimationHandler");
            Object animationHandler = getAnimationHandler.invoke(activeModel);

            try {
                Method stopAnimation = animationHandler.getClass().getMethod("stopAnimation", String.class);
                stopAnimation.invoke(animationHandler, animationName);
            } catch (NoSuchMethodException e) {
                try {
                    Method forceStop = animationHandler.getClass().getMethod("forceStopAnimation", String.class);
                    forceStop.invoke(animationHandler, animationName);
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void playAnimation(Object activeModel, String animationName, boolean loop) {
        try {
            Method getAnimationHandler = activeModel.getClass().getMethod("getAnimationHandler");
            Object animationHandler = getAnimationHandler.invoke(activeModel);

            try {
                Method playAnimation = animationHandler.getClass().getMethod(
                        "playAnimation", String.class, double.class, double.class, double.class, boolean.class);
                playAnimation.invoke(animationHandler, animationName, 0.0, 0.0, 1.0, loop);
            } catch (NoSuchMethodException e) {
                Method playAnimation = animationHandler.getClass().getMethod("playAnimation", String.class, boolean.class);
                playAnimation.invoke(animationHandler, animationName, loop);
            }
        } catch (Exception ignored) {
        }
    }

    private Object createActiveModel(Object blueprint, String modelId) {
        try {
            Method createActiveModelMethod = blueprint.getClass().getMethod("createActiveModel");
            return createActiveModelMethod.invoke(blueprint);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            plugin.getLogger().warning("createActiveModel() failed: " + e.getMessage());
        }

        try {
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Method createMethod = modelEngineAPIClass.getMethod("createActiveModel", String.class);
            return createMethod.invoke(null, modelId);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            plugin.getLogger().warning("ModelEngineAPI.createActiveModel() failed: " + e.getMessage());
        }

        try {
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Method getBlueprintRegistry = modelEngineAPIClass.getMethod("getBlueprintRegistry");
            Object registry = getBlueprintRegistry.invoke(null);

            Method getMethod = registry.getClass().getMethod("get", String.class);
            Object bp = getMethod.invoke(registry, modelId);

            if (bp != null) {
                Method createMethod = bp.getClass().getMethod("createActiveModel");
                return createMethod.invoke(bp);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("BlueprintRegistry approach failed: " + e.getMessage());
        }

        try {
            if (modelEngineAPI != null) {
                Method createMethod = modelEngineAPI.getClass().getMethod("createActiveModel", String.class);
                return createMethod.invoke(modelEngineAPI, modelId);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean isModelBaseEntity(Entity entity, Location blockLoc) {
        if (!(entity instanceof org.bukkit.entity.ArmorStand armorStand)) {
            return false;
        }
        if (armorStand.isVisible() || !armorStand.isMarker()) {
            return false;
        }
        if (armorStand.hasGravity() || !armorStand.isInvulnerable()) {
            return false;
        }

        Location entityBlockLoc = armorStand.getLocation().getBlock().getLocation();
        return entityBlockLoc.equals(blockLoc);
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
                removeModeledEntity(entity);
                entity.remove();
            }
            plugin.getLogger().warning("Removed duplicate ModelEngine base entities at " + formatLocation(blockLoc));
        }

        return primary;
    }

    private String formatLocation(Location location) {
        return location.getWorld() == null
                ? "(unknown)"
                : location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    public boolean hasModel(Location location) {
        return resolveModelEntity(location.getBlock().getLocation(), true) != null;
    }

    public void cleanup() {
        Set<Location> locationsToCleanup = new HashSet<>(crateModels.keySet());

        if (plugin.getCrateManager() != null) {
            for (var crateLocation : plugin.getCrateManager().getCrateLocations()) {
                var world = plugin.getServer().getWorld(crateLocation.getWorld());
                if (world == null) {
                    continue;
                }
                locationsToCleanup.add(crateLocation.toLocation(world).getBlock().getLocation());
            }
        }

        for (Location location : locationsToCleanup) {
            removeCrateModelInternal(location, false);
        }
        crateModels.clear();
    }
}
