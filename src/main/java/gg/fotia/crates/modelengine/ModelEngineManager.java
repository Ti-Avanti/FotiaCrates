package gg.fotia.crates.modelengine;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
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

    private static final String MODEL_ENGINE_BASE_TAG = "fotiacrates_modelengine";
    private static final String BETTER_MODEL_BASE_TAG = "fotiacrates_bettermodel";

    private final FotiaCrates plugin;
    // 以世界名+坐标为键：Location 作长期键会强引用 World，世界卸载后条目残留且查找失配
    private final Map<ModelPosition, UUID> crateModels = new HashMap<>();
    private final Map<ModelPosition, ModelIdentity> modelIdentities = new HashMap<>();
    private final Set<ModelPosition> staleModelPositions = new HashSet<>();
    private BetterModelManager betterModelManager;
    private boolean modelEngineAvailable = false;
    private BukkitTask healthCheckTask;
    private int healthCheckCursor;

    private Object modelEngineAPI;
    private Method createModeledEntityMethod;
    private Method getBlueprintMethod;
    private Method getModeledEntityMethod;
    // 以下方法按运行期实际类懒解析并缓存（ModelEngine 版本运行期不变），避免热路径逐次 getMethod 字符串查找
    private Method getModelsMethod;
    private Method removeModeledEntityMethod;
    private Method getAnimationHandlerMethod;
    private Method stopAnimationMethod;
    private Method playAnimationExtendedMethod;
    private Method playAnimationSimpleMethod;

    public ModelEngineManager(FotiaCrates plugin) {
        this.plugin = plugin;
        initializeReflection();
        initializeBetterModel();
    }

    private void initializeBetterModel() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            plugin.getLogger().info("BetterModel not found, BetterModel model features disabled.");
            return;
        }

        try {
            betterModelManager = new BetterModelManager(plugin);
            if (!betterModelManager.isAvailable()) {
                betterModelManager = null;
            }
        } catch (LinkageError e) {
            betterModelManager = null;
            plugin.getLogger().warning("BetterModel API class not found. BetterModel model features disabled.");
        }
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
        return modelEngineAvailable || isBetterModelAvailable();
    }

    private boolean isBetterModelAvailable() {
        return betterModelManager != null && betterModelManager.isAvailable();
    }

    public void startHealthCheck() {
        stopHealthCheck();
        healthCheckCursor = 0;
        long interval = plugin.getConfigManager().getModelHealthCheckIntervalTicks();
        healthCheckTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::checkModelHealthBatch, interval, interval);
    }

    public void restartHealthCheck() {
        startHealthCheck();
    }

    public void stopHealthCheck() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }
    }

    public boolean ensureCrateModel(Crate crate, Location location, float yaw) {
        if (crate == null || location == null || location.getWorld() == null || !crate.isModelEnabled()) {
            return false;
        }

        Location blockLoc = location.getBlock().getLocation();
        if (!blockLoc.getWorld().isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
            return false;
        }

        if (!plugin.getCustomBlockSupport().isReady() || usesCustomBlock(crate, blockLoc)) return false;

        ModelIdentity desired = ModelIdentity.from(crate);
        if (desired.equals(modelIdentities.get(positionOf(blockLoc))) && isProviderModelHealthy(crate, blockLoc)) {
            return true;
        }

        removeAllProviderModels(blockLoc, false);
        spawnCrateModel(crate, blockLoc, yaw);
        if (!isProviderModelHealthy(crate, blockLoc)) {
            removeAllProviderModels(blockLoc, false);
            modelIdentities.remove(positionOf(blockLoc));
            if (blockLoc.getBlock().getType() == Material.BARRIER) {
                blockLoc.getBlock().setType(crate.getBlockMaterial());
            }
            return false;
        }

        modelIdentities.put(positionOf(blockLoc), desired);
        return true;
    }

    public boolean ensureCrateModel(Crate crate, Location location) {
        return ensureCrateModel(crate, location, resolveSavedYaw(location));
    }

    public void reconcileLoadedModels(Collection<CrateLocation> previousLocations) {
        Set<ModelPosition> currentPositions = new HashSet<>();
        for (CrateLocation crateLocation : plugin.getCrateManager().getCrateLocations()) {
            currentPositions.add(ModelPosition.of(crateLocation));
        }

        if (previousLocations != null) {
            for (CrateLocation previous : previousLocations) {
                ModelPosition previousPosition = ModelPosition.of(previous);
                if (currentPositions.contains(previousPosition)) {
                    continue;
                }
                staleModelPositions.add(previousPosition);
                World world = plugin.getServer().getWorld(previous.getWorld());
                if (world == null || !world.isChunkLoaded(Math.floorDiv(previous.getX(), 16),
                        Math.floorDiv(previous.getZ(), 16))) {
                    continue;
                }
                removeCrateModel(previous.toLocation(world));
                staleModelPositions.remove(previousPosition);
            }
        }

        for (CrateLocation crateLocation : plugin.getCrateManager().getCrateLocations()) {
            reconcileCrateLocation(crateLocation);
        }
        healthCheckCursor = 0;
    }

    public void reconcileChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        for (ModelPosition position : Set.copyOf(staleModelPositions)) {
            if (!position.isInChunk(world.getName(), chunkX, chunkZ)) {
                continue;
            }
            removeCrateModel(position.toLocation(world));
            staleModelPositions.remove(position);
        }
        for (CrateLocation crateLocation : plugin.getCrateManager().getCrateLocationsInChunk(
                world.getName(), chunkX, chunkZ)) {
            reconcileCrateLocation(crateLocation);
        }
    }

    /**
     * 该区块内是否存在等待清理的残留模型位置（供区块加载监听器做空短路判断）
     */
    public boolean hasStaleModelsInChunk(String worldName, int chunkX, int chunkZ) {
        for (ModelPosition position : staleModelPositions) {
            if (position.isInChunk(worldName, chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    public void removeCrateModels(Collection<CrateLocation> locations) {
        if (locations == null) {
            return;
        }
        for (CrateLocation crateLocation : locations) {
            ModelPosition position = ModelPosition.of(crateLocation);
            World world = plugin.getServer().getWorld(crateLocation.getWorld());
            if (world == null || !world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16),
                    Math.floorDiv(crateLocation.getZ(), 16))) {
                staleModelPositions.add(position);
                continue;
            }
            removeCrateModel(crateLocation.toLocation(world));
            staleModelPositions.remove(position);
        }
    }

    private void checkModelHealthBatch() {
        List<CrateLocation> locations = plugin.getCrateManager().getCrateLocations();
        if (locations.isEmpty()) {
            healthCheckCursor = 0;
            return;
        }

        int maxChecks = Math.min(plugin.getConfigManager().getModelHealthChecksPerRun(), locations.size());
        int start = Math.floorMod(healthCheckCursor, locations.size());
        for (int offset = 0; offset < maxChecks; offset++) {
            reconcileCrateLocation(locations.get((start + offset) % locations.size()));
        }
        healthCheckCursor = (start + maxChecks) % locations.size();
    }

    private void reconcileCrateLocation(CrateLocation crateLocation) {
        World world = plugin.getServer().getWorld(crateLocation.getWorld());
        if (world == null || !world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16),
                Math.floorDiv(crateLocation.getZ(), 16))) {
            return;
        }

        Location location = crateLocation.toLocation(world).getBlock().getLocation();
        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            removeCrateModel(location);
            return;
        }
        if (!plugin.getCustomBlockSupport().isReady()) return;
        if (usesCustomBlock(crate, location)) {
            removeAllProviderModels(location, false);
            modelIdentities.remove(positionOf(location));
            return;
        }
        if (!crate.isModelEnabled()) {
            removeAllProviderModels(location, false);
            modelIdentities.remove(positionOf(location));
            if (location.getBlock().getType() == Material.BARRIER) {
                location.getBlock().setType(crate.getBlockMaterial());
            }
            return;
        }

        ensureCrateModel(crate, location, crateLocation.getYaw());
    }

    private boolean isProviderModelHealthy(Crate crate, Location blockLoc) {
        if (crate.isBetterModelEnabled()) {
            return isBetterModelAvailable() && betterModelManager.hasModel(blockLoc);
        }
        return crate.usesModelEngineProvider() && hasHealthyModelEngineModel(blockLoc);
    }

    private boolean hasHealthyModelEngineModel(Location blockLoc) {
        if (!modelEngineAvailable || getModeledEntityMethod == null) {
            return false;
        }

        Entity entity = resolveModelEntity(blockLoc, true);
        if (entity == null) {
            return false;
        }

        try {
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) {
                return false;
            }
            Object models = invokeGetModels(modeledEntity);
            return models instanceof Map<?, ?> modelMap && !modelMap.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object invokeGetModels(Object modeledEntity) throws Exception {
        if (getModelsMethod == null) {
            getModelsMethod = modeledEntity.getClass().getMethod("getModels");
        }
        return getModelsMethod.invoke(modeledEntity);
    }

    private void removeAllProviderModels(Location blockLoc, boolean clearBarrierBlock) {
        if (isBetterModelAvailable()) {
            betterModelManager.removeCrateModel(blockLoc, false);
        }
        removeCrateModelInternal(blockLoc.getBlock().getLocation(), clearBarrierBlock);
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
        ensureCrateModel(crate, location, yaw);
    }

    private boolean usesCustomBlock(Crate crate, Location location) {
        return plugin.getCustomBlockSupport().isCustomBlock(location.getBlock())
                || plugin.getCustomBlockSupport().isCustomBlockItem(crate.getBlockItemTemplate());
    }

    public void spawnCrateModel(Crate crate, Location location, float yaw) {
        if (!plugin.getCustomBlockSupport().isReady() || usesCustomBlock(crate, location)) return;
        if (crate.isBetterModelEnabled()) {
            if (isBetterModelAvailable()) {
                removeCrateModelInternal(location.getBlock().getLocation(), false);
                betterModelManager.spawnCrateModel(crate, location, yaw);
            }
            return;
        }

        if (!modelEngineAvailable || !crate.usesModelEngineProvider()) {
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
            if (isBetterModelAvailable()) {
                betterModelManager.removeCrateModel(blockLoc, false);
            }
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
                armorStand.addScoreboardTag(MODEL_ENGINE_BASE_TAG);
            });

            Object modeledEntity = createModeledEntityMethod.invoke(null, baseEntity);

            Class<?> activeModelClass = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Method addModelMethod = modeledEntity.getClass().getMethod("addModel", activeModelClass, boolean.class);
            addModelMethod.invoke(modeledEntity, activeModel, true);

            setModelViewRange(modeledEntity, crate.getModelEngineViewRange());
            playAnimation(activeModel, crate.getModelEngineIdleAnimation(), true);

            crateModels.put(positionOf(blockLoc), baseEntity.getUniqueId());
            plugin.getLogger().info("Spawned ModelEngine model for crate: " + crate.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn ModelEngine model: " + e.getMessage());
        }
    }

    public void removeCrateModel(Location location) {
        Location blockLoc = location.getBlock().getLocation();
        removeAllProviderModels(blockLoc, plugin.getCustomBlockSupport().isReady()
                && !plugin.getCustomBlockSupport().isCustomBlock(blockLoc.getBlock()));
        modelIdentities.remove(positionOf(blockLoc));
    }

    private boolean removeCrateModelInternal(Location blockLoc, boolean clearBarrierBlock) {
        crateModels.remove(positionOf(blockLoc));

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
            if (removeModeledEntityMethod == null) {
                removeModeledEntityMethod = modelEngineAPI.getClass().getMethod("removeModeledEntity", Entity.class);
            }
            removeModeledEntityMethod.invoke(modelEngineAPI, entity);
        } catch (Exception ignored) {
        }
    }

    public void playOpenAnimation(Crate crate, Location location, Player openingPlayer) {
        if (!ensureCrateModel(crate, location, resolveSavedYaw(location))) {
            return;
        }
        if (crate.isBetterModelEnabled()) {
            if (isBetterModelAvailable()) {
                betterModelManager.playOpenAnimation(crate, location, openingPlayer);
            }
            return;
        }

        if (!modelEngineAvailable || !crate.usesModelEngineProvider()) {
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

            Object models = invokeGetModels(modeledEntity);

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
        if (!ensureCrateModel(crate, location, resolveSavedYaw(location))) {
            return;
        }
        if (crate.isBetterModelEnabled()) {
            if (isBetterModelAvailable()) {
                betterModelManager.playIdleAnimation(crate, location);
            }
            return;
        }

        if (!modelEngineAvailable || !crate.usesModelEngineProvider()) {
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

            Object models = invokeGetModels(modeledEntity);

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
            Object animationHandler = resolveAnimationHandler(activeModel);

            if (stopAnimationMethod == null) {
                try {
                    stopAnimationMethod = animationHandler.getClass().getMethod("stopAnimation", String.class);
                } catch (NoSuchMethodException e) {
                    try {
                        stopAnimationMethod = animationHandler.getClass().getMethod("forceStopAnimation", String.class);
                    } catch (NoSuchMethodException ignored) {
                        return;
                    }
                }
            }
            stopAnimationMethod.invoke(animationHandler, animationName);
        } catch (Exception ignored) {
        }
    }

    private void playAnimation(Object activeModel, String animationName, boolean loop) {
        try {
            Object animationHandler = resolveAnimationHandler(activeModel);

            if (playAnimationExtendedMethod == null && playAnimationSimpleMethod == null) {
                try {
                    playAnimationExtendedMethod = animationHandler.getClass().getMethod(
                            "playAnimation", String.class, double.class, double.class, double.class, boolean.class);
                } catch (NoSuchMethodException e) {
                    playAnimationSimpleMethod = animationHandler.getClass().getMethod("playAnimation", String.class, boolean.class);
                }
            }
            if (playAnimationExtendedMethod != null) {
                playAnimationExtendedMethod.invoke(animationHandler, animationName, 0.0, 0.0, 1.0, loop);
            } else {
                playAnimationSimpleMethod.invoke(animationHandler, animationName, loop);
            }
        } catch (Exception ignored) {
        }
    }

    private Object resolveAnimationHandler(Object activeModel) throws Exception {
        if (getAnimationHandlerMethod == null) {
            getAnimationHandlerMethod = activeModel.getClass().getMethod("getAnimationHandler");
        }
        return getAnimationHandlerMethod.invoke(activeModel);
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
        if (!entityBlockLoc.equals(blockLoc)) {
            return false;
        }

        Set<String> tags = armorStand.getScoreboardTags();
        if (tags.contains(BETTER_MODEL_BASE_TAG)) {
            return false;
        }
        if (tags.contains(MODEL_ENGINE_BASE_TAG)) {
            return true;
        }

        // Legacy base entities had no tag; only accept them when ModelEngine still owns them.
        if (modelEngineAvailable && getModeledEntityMethod != null) {
            try {
                return getModeledEntityMethod.invoke(null, armorStand) != null;
            } catch (Exception ignored) {
            }
        }
        UUID trackedUuid = crateModels.get(positionOf(blockLoc));
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

    private Entity resolveModelEntity(Location blockLoc, boolean removeDuplicates) {
        ModelPosition position = positionOf(blockLoc);
        UUID trackedUuid = crateModels.get(position);
        List<Entity> nearbyEntities = findNearbyModelBaseEntities(blockLoc);
        if (nearbyEntities.isEmpty()) {
            crateModels.remove(position);
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

        crateModels.put(position, primary.getUniqueId());

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
        return hasHealthyModelEngineModel(location.getBlock().getLocation())
                || (isBetterModelAvailable() && betterModelManager.hasModel(location));
    }

    public void cleanup() {
        stopHealthCheck();
        if (isBetterModelAvailable()) {
            betterModelManager.cleanup();
        }

        Set<Location> locationsToCleanup = new HashSet<>();
        for (ModelPosition position : crateModels.keySet()) {
            World world = plugin.getServer().getWorld(position.world());
            if (world != null) {
                locationsToCleanup.add(position.toLocation(world));
            }
        }
        Map<Location, Material> restoreMaterials = new HashMap<>();

        if (plugin.getCrateManager() != null) {
            for (var crateLocation : plugin.getCrateManager().getCrateLocations()) {
                var world = plugin.getServer().getWorld(crateLocation.getWorld());
                if (world == null || !world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16),
                        Math.floorDiv(crateLocation.getZ(), 16))) {
                    continue;
                }
                Location location = crateLocation.toLocation(world).getBlock().getLocation();
                locationsToCleanup.add(location);
                Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
                restoreMaterials.put(location, crate != null ? crate.getBlockMaterial() : Material.AIR);
            }
        }

        locationsToCleanup.removeIf(location -> location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        for (Location location : locationsToCleanup) {
            removeCrateModelInternal(location, false);
            if (location.getBlock().getType() == Material.BARRIER) {
                location.getBlock().setType(restoreMaterials.getOrDefault(location, Material.AIR));
            }
        }
        crateModels.clear();
        modelIdentities.clear();
        staleModelPositions.clear();
    }

    private float resolveSavedYaw(Location location) {
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        return crateLocation != null ? crateLocation.getYaw() : location.getYaw();
    }

    private ModelPosition positionOf(Location blockLoc) {
        return new ModelPosition(blockLoc.getWorld().getName(),
                blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
    }

    private record ModelIdentity(String provider, String modelId, String idleAnimation,
                                 String openAnimation, int viewRange) {
        private static ModelIdentity from(Crate crate) {
            return new ModelIdentity(crate.getModelProvider(), crate.getModelEngineId(),
                    crate.getModelEngineIdleAnimation(), crate.getModelEngineOpenAnimation(),
                    crate.getModelEngineViewRange());
        }
    }

    private record ModelPosition(String world, int x, int y, int z) {
        private static ModelPosition of(CrateLocation location) {
            return new ModelPosition(location.getWorld(), location.getX(), location.getY(), location.getZ());
        }

        private boolean isInChunk(String worldName, int chunkX, int chunkZ) {
            return world.equals(worldName) && Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ;
        }

        private Location toLocation(World world) {
            return new Location(world, x, y, z);
        }
    }
}
