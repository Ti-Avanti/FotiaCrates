package gg.fotia.crates.modelengine;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ModelEngine集成管理器
 * 使用反射调用ModelEngine API，避免硬依赖
 * 支持 ModelEngine R4.x 版本
 */
public class ModelEngineManager {

    private final FotiaCrates plugin;
    private final Map<Location, UUID> crateModels = new HashMap<>();
    private boolean modelEngineAvailable = false;

    // 缓存的反射类和方法
    private Object modelEngineAPI;
    private Method createModeledEntityMethod;
    private Method getBlueprintMethod;
    private Method getModeledEntityMethod;

    public ModelEngineManager(FotiaCrates plugin) {
        this.plugin = plugin;
        initializeReflection();
    }

    /**
     * 初始化反射
     */
    private void initializeReflection() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            plugin.getLogger().info("ModelEngine not found, model features disabled.");
            return;
        }

        try {
            // 获取 ModelEngineAPI 实例
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Method apiMethod = modelEngineAPIClass.getMethod("api");
            modelEngineAPI = apiMethod.invoke(null);

            // 获取方法
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

    /**
     * 尝试替代API（旧版本兼容）
     */
    private void tryAlternativeAPI() {
        try {
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");

            // 尝试获取静态方法
            getBlueprintMethod = modelEngineAPIClass.getMethod("getBlueprint", String.class);

            // 尝试不同的方法名
            try {
                createModeledEntityMethod = modelEngineAPIClass.getMethod("createModeledEntity", Entity.class);
            } catch (NoSuchMethodException e) {
                // 尝试旧版方法名
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

    /**
     * 检查ModelEngine是否可用
     */
    public boolean isAvailable() {
        return modelEngineAvailable;
    }

    /**
     * 在指定位置生成宝箱模型
     * @param crate 宝箱
     * @param location 位置
     * @param placer 放置者，模型将面向放置者
     */
    public void spawnCrateModel(Crate crate, Location location, Player placer) {
        if (!modelEngineAvailable) {
            return;
        }
        if (!crate.isModelEngineEnabled()) {
            return;
        }

        try {
            // 获取Blueprint
            Object blueprintOptional = getBlueprintMethod.invoke(null, crate.getModelEngineId());

            Object blueprint = null;
            if (blueprintOptional instanceof java.util.Optional<?> opt) {
                if (opt.isEmpty()) {
                    plugin.getLogger().warning("ModelEngine blueprint not found: " + crate.getModelEngineId());
                    return;
                }
                blueprint = opt.get();
            } else if (blueprintOptional != null) {
                // 旧版API可能直接返回Blueprint
                blueprint = blueprintOptional;
            } else {
                plugin.getLogger().warning("ModelEngine blueprint not found: " + crate.getModelEngineId());
                return;
            }

            // 将原始方块设为BARRIER，隐藏木箱但保留碰撞
            location.getBlock().setType(Material.BARRIER);

            // 生成ArmorStand作为基础实体，面向放置者
            Location spawnLoc = location.clone().add(0.5, 0, 0.5);

            // 计算面向放置者的yaw角度
            if (placer != null) {
                Location playerLoc = placer.getLocation();
                double dx = playerLoc.getX() - spawnLoc.getX();
                double dz = playerLoc.getZ() - spawnLoc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                spawnLoc.setYaw(yaw);
            }

            Entity baseEntity = location.getWorld().spawn(spawnLoc, org.bukkit.entity.ArmorStand.class, armorStand -> {
                armorStand.setVisible(false);
                armorStand.setGravity(false);
                armorStand.setInvulnerable(true);
                armorStand.setMarker(true);
                armorStand.setPersistent(true);
            });

            // 创建ModeledEntity
            Object modeledEntity = createModeledEntityMethod.invoke(null, baseEntity);

            // 创建ActiveModel - 兼容不同版本的API
            Object activeModel = createActiveModel(blueprint, crate.getModelEngineId());
            if (activeModel == null) {
                plugin.getLogger().warning("Failed to create ActiveModel for: " + crate.getModelEngineId());
                baseEntity.remove();
                return;
            }

            // 添加模型到实体
            Class<?> activeModelClass = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Method addModelMethod = modeledEntity.getClass().getMethod("addModel", activeModelClass, boolean.class);
            addModelMethod.invoke(modeledEntity, activeModel, true);

            // 设置可视距离
            setModelViewRange(modeledEntity, crate.getModelEngineViewRange());

            // 播放待机动画
            playAnimation(activeModel, crate.getModelEngineIdleAnimation(), true);

            // 保存模型引用
            crateModels.put(location.getBlock().getLocation(), baseEntity.getUniqueId());

            plugin.getLogger().info("Spawned ModelEngine model for crate: " + crate.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn ModelEngine model: " + e.getMessage());
        }
    }

    /**
     * 移除指定位置的宝箱模型
     */
    public void removeCrateModel(Location location) {
        Location blockLoc = location.getBlock().getLocation();
        UUID entityUuid = crateModels.remove(blockLoc);

        // 尝试通过UUID移除
        if (entityUuid != null) {
            Entity entity = plugin.getServer().getEntity(entityUuid);
            if (entity != null) {
                removeModeledEntity(entity);
                entity.remove();
            }
            // 移除BARRIER方块
            if (blockLoc.getBlock().getType() == Material.BARRIER) {
                blockLoc.getBlock().setType(Material.AIR);
            }
        }

        // 同时搜索并移除该位置附近的隐形ArmorStand
        Location searchLoc = blockLoc.clone().add(0.5, 0, 0.5);
        for (Entity nearby : searchLoc.getWorld().getNearbyEntities(searchLoc, 0.5, 1, 0.5)) {
            if (nearby instanceof org.bukkit.entity.ArmorStand armorStand) {
                if (!armorStand.isVisible() && armorStand.isMarker()) {
                    removeModeledEntity(armorStand);
                    armorStand.remove();
                }
            }
        }
    }

    /**
     * 移除实体的ModeledEntity
     */
    private void removeModeledEntity(Entity entity) {
        if (!modelEngineAvailable || modelEngineAPI == null) {
            return;
        }

        try {
            Method removeMethod = modelEngineAPI.getClass().getMethod("removeModeledEntity", Entity.class);
            removeMethod.invoke(modelEngineAPI, entity);
        } catch (Exception e) {
            // 忽略错误
        }
    }

    /**
     * 播放宝箱开启动画（只播放开启动画，不自动恢复idle）
     * @param crate 宝箱
     * @param location 位置
     * @param openingPlayer 开箱玩家，其他玩家将看不到开箱动画
     */
    public void playOpenAnimation(Crate crate, Location location, Player openingPlayer) {
        if (!modelEngineAvailable || !crate.isModelEngineEnabled()) {
            return;
        }

        Location blockLoc = location.getBlock().getLocation();
        UUID entityUuid = crateModels.get(blockLoc);
        if (entityUuid == null) {
            return;
        }

        Entity entity = plugin.getServer().getEntity(entityUuid);
        if (entity == null) {
            return;
        }

        // 对其他玩家隐藏模型实体
        if (openingPlayer != null) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(openingPlayer)) {
                    other.hideEntity(plugin, entity);
                }
            }
        }

        try {
            // 获取ModeledEntity
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) {
                return;
            }

            // 获取ActiveModel
            Method getModels = modeledEntity.getClass().getMethod("getModels");
            Object models = getModels.invoke(modeledEntity);

            if (models instanceof Map<?, ?> modelMap && !modelMap.isEmpty()) {
                Object activeModel = modelMap.values().iterator().next();
                String idleAnim = crate.getModelEngineIdleAnimation();
                String openAnim = crate.getModelEngineOpenAnimation();

                // 停止idle动画并播放开启动画（循环播放直到手动停止）
                stopAnimation(activeModel, idleAnim);
                playAnimation(activeModel, openAnim, true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play open animation: " + e.getMessage());
        }
    }

    /**
     * 播放宝箱开启动画（无玩家参数版本，兼容旧代码）
     */
    public void playOpenAnimation(Crate crate, Location location) {
        playOpenAnimation(crate, location, null);
    }

    /**
     * 播放宝箱待机动画（用于开箱结束后恢复）
     * 同时恢复对所有玩家的可见性
     */
    public void playIdleAnimation(Crate crate, Location location) {
        if (!modelEngineAvailable || !crate.isModelEngineEnabled()) {
            return;
        }

        Location blockLoc = location.getBlock().getLocation();
        UUID entityUuid = crateModels.get(blockLoc);
        if (entityUuid == null) {
            return;
        }

        Entity entity = plugin.getServer().getEntity(entityUuid);
        if (entity == null) {
            return;
        }

        // 恢复对所有玩家的可见性
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showEntity(plugin, entity);
        }

        try {
            // 获取ModeledEntity
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) {
                return;
            }

            // 获取ActiveModel
            Method getModels = modeledEntity.getClass().getMethod("getModels");
            Object models = getModels.invoke(modeledEntity);

            if (models instanceof Map<?, ?> modelMap && !modelMap.isEmpty()) {
                Object activeModel = modelMap.values().iterator().next();
                String idleAnim = crate.getModelEngineIdleAnimation();
                String openAnim = crate.getModelEngineOpenAnimation();

                // 停止开启动画并播放idle动画
                stopAnimation(activeModel, openAnim);
                playAnimation(activeModel, idleAnim, true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play idle animation: " + e.getMessage());
        }
    }

    /**
     * 设置模型可视距离
     */
    private void setModelViewRange(Object modeledEntity, int viewRange) {
        try {
            // ModelEngine R4.x: 通过 BaseEntity.setRenderRadius(int) 设置
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

    /**
     * 停止动画
     */
    private void stopAnimation(Object activeModel, String animationName) {
        try {
            Method getAnimationHandler = activeModel.getClass().getMethod("getAnimationHandler");
            Object animationHandler = getAnimationHandler.invoke(activeModel);

            // 尝试停止动画
            try {
                Method stopAnimation = animationHandler.getClass().getMethod("stopAnimation", String.class);
                stopAnimation.invoke(animationHandler, animationName);
            } catch (NoSuchMethodException e) {
                // 尝试forceStopAnimation
                try {
                    Method forceStop = animationHandler.getClass().getMethod("forceStopAnimation", String.class);
                    forceStop.invoke(animationHandler, animationName);
                } catch (NoSuchMethodException e2) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            // 静默失败
        }
    }

    /**
     * 播放动画
     */
    private void playAnimation(Object activeModel, String animationName, boolean loop) {
        try {
            // 获取AnimationHandler
            Method getAnimationHandler = activeModel.getClass().getMethod("getAnimationHandler");
            Object animationHandler = getAnimationHandler.invoke(activeModel);

            // 尝试不同的播放方法签名
            try {
                Method playAnimation = animationHandler.getClass().getMethod("playAnimation",
                        String.class, double.class, double.class, double.class, boolean.class);
                playAnimation.invoke(animationHandler, animationName, 0.0, 0.0, 1.0, loop);
            } catch (NoSuchMethodException e) {
                // 尝试简化版方法
                Method playAnimation = animationHandler.getClass().getMethod("playAnimation", String.class, boolean.class);
                playAnimation.invoke(animationHandler, animationName, loop);
            }
        } catch (Exception e) {
            // 静默失败，动画可能不存在
        }
    }

    /**
     * 创建ActiveModel - 兼容不同版本的ModelEngine API
     */
    private Object createActiveModel(Object blueprint, String modelId) {
        // 方法1: 新版API - blueprint.createActiveModel()
        try {
            Method createActiveModelMethod = blueprint.getClass().getMethod("createActiveModel");
            return createActiveModelMethod.invoke(blueprint);
        } catch (NoSuchMethodException e) {
            // 继续尝试其他方法
        } catch (Exception e) {
            plugin.getLogger().warning("createActiveModel() failed: " + e.getMessage());
        }

        // 方法2: R4.x API - ModelEngineAPI.createActiveModel(modelId)
        try {
            Class<?> modelEngineAPIClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Method createMethod = modelEngineAPIClass.getMethod("createActiveModel", String.class);
            return createMethod.invoke(null, modelId);
        } catch (NoSuchMethodException e) {
            // 继续尝试其他方法
        } catch (Exception e) {
            plugin.getLogger().warning("ModelEngineAPI.createActiveModel() failed: " + e.getMessage());
        }

        // 方法3: 通过BlueprintRegistry获取
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

        // 方法4: 通过api()实例方法
        try {
            if (modelEngineAPI != null) {
                Method createMethod = modelEngineAPI.getClass().getMethod("createActiveModel", String.class);
                return createMethod.invoke(modelEngineAPI, modelId);
            }
        } catch (Exception e) {
            // 忽略
        }

        return null;
    }

    /**
     * 检查位置是否有模型
     */
    public boolean hasModel(Location location) {
        return crateModels.containsKey(location.getBlock().getLocation());
    }

    /**
     * 清理所有模型
     */
    public void cleanup() {
        for (UUID entityUuid : crateModels.values()) {
            Entity entity = plugin.getServer().getEntity(entityUuid);
            if (entity != null) {
                entity.remove();
            }
        }
        crateModels.clear();
    }
}
