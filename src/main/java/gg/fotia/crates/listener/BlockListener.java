package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationManager;
import gg.fotia.crates.config.MessageConfig;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import gg.fotia.crates.key.KeyType;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BlockListener implements Listener {

    private final FotiaCrates plugin;
    // 防止重复开箱的冷却集合
    private final Set<UUID> openingPlayers = new HashSet<>();

    public BlockListener(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // 检查是否为钥匙物品，阻止放置
        if (plugin.getKeyManager().isPhysicalKey(item)) {
            event.setCancelled(true);
            return;
        }

        // 检查是否为宝箱方块物品
        String crateId = plugin.getCrateManager().getCrateIdFromItem(item);
        if (crateId == null) return;

        // 检查权限
        if (!player.hasPermission("fotiacrates.admin.place")) {
            event.setCancelled(true);
            plugin.getMessageConfig().send(player, "no-permission");
            return;
        }

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            event.setCancelled(true);
            plugin.getMessageConfig().send(player, "invalid-crate");
            return;
        }

        Location location = event.getBlock().getLocation();

        // 检查位置是否已有宝箱
        if (plugin.getCrateManager().isLocationSet(location)) {
            event.setCancelled(true);
            plugin.getMessageConfig().send(player, "already-crate-location");
            return;
        }

        // 注册宝箱位置
        plugin.getCrateManager().setCrateLocation(crateId, location);

        // 发送消息
        plugin.getMessageConfig().send(player, "crate-block-placed",
                MessageConfig.placeholders("crate", crate.getName()));

        // 尝试生成ModelEngine模型
        if (crate.isModelEngineEnabled()) {
            plugin.getModelEngineManager().spawnCrateModel(crate, location, player);
        }

        // 创建全息显示
        plugin.getHologramManager().createHologram(location, crateId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        Block block = event.getClickedBlock();
        if (block == null) return;

        Location location = block.getLocation();
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) return;

        event.setCancelled(true);

        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getMessageConfig().send(player, "invalid-crate");
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 蹲下+右键 = 管理员移除宝箱
            if (player.isSneaking()) {
                if (!player.hasPermission("fotiacrates.admin.remove")) {
                    plugin.getMessageConfig().send(player, "no-permission");
                    return;
                }

                // 移除ModelEngine模型
                plugin.getModelEngineManager().removeCrateModel(location);
                // 移除全息显示
                plugin.getHologramManager().removeHologram(location);
                plugin.getCrateManager().removeLocation(location);
                plugin.getMessageConfig().send(player, "crate-removed",
                        MessageConfig.placeholders("crate", crate.getName()));
                return;
            }

            // 普通右键 = 开箱
            if (!hasOpenPermission(player, crate)) {
                plugin.getMessageConfig().send(player, "no-permission");
                return;
            }

            if (!plugin.getKeyManager().hasKeyForCrate(player, crate.getId())) {
                plugin.getMessageConfig().send(player, "no-key");
                return;
            }

            openCrate(player, crate, location);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // 左键 = 预览
            if (!player.hasPermission("fotiacrates.preview")) {
                plugin.getMessageConfig().send(player, "no-permission");
                return;
            }

            plugin.getGuiManager().openPreview(player, crate);
        }
    }

    private void openCrate(Player player, Crate crate, Location crateLocation) {
        // 防止重复开箱
        if (openingPlayers.contains(player.getUniqueId())) {
            return;
        }
        openingPlayers.add(player.getUniqueId());

        if (!plugin.getKeyManager().consumeKeyForCrate(player, crate.getId(), KeyType.ALL)) {
            openingPlayers.remove(player.getUniqueId());
            plugin.getMessageConfig().send(player, "no-key");
            return;
        }

        // 隐藏全息显示
        plugin.getHologramManager().hideHologram(crateLocation);

        // 多级保底检查
        Reward reward;
        Crate.PityTier triggeredTier = null;
        if (crate.isPityEnabled() && !crate.getPityTiers().isEmpty()) {
            int currentCount = plugin.getPityManager().getPityCount(player.getUniqueId(), crate.getId()) + 1;
            triggeredTier = crate.getTriggeredPityTier(currentCount);
            if (triggeredTier != null) {
                reward = crate.rollPityReward(triggeredTier.getRarity());
            } else {
                reward = crate.rollReward();
            }
        } else {
            reward = crate.rollReward();
        }

        if (reward == null) {
            plugin.getLogger().warning("No reward found for crate: " + crate.getId());
            return;
        }

        // 更新保底计数
        if (crate.isPityEnabled() && !crate.getPityTiers().isEmpty()) {
            int currentCount = plugin.getPityManager().getPityCount(player.getUniqueId(), crate.getId()) + 1;
            // 检查是否达到最高级保底或获得了高稀有度奖励
            int maxPityCount = crate.getMaxPityCount();
            if (currentCount >= maxPityCount) {
                // 达到最高级保底，重置计数
                plugin.getPityManager().resetPityCount(player.getUniqueId(), crate.getId());
            } else {
                // 增加计数
                plugin.getPityManager().incrementPityCount(player.getUniqueId(), crate.getId());
            }
        }

        // 播放ModelEngine开启动画
        boolean hasModelEngine = crate.isModelEngineEnabled() && plugin.getModelEngineManager().hasModel(crateLocation);
        if (hasModelEngine) {
            plugin.getModelEngineManager().playOpenAnimation(crate, crateLocation, player);
            // 延迟后开始滚动抽奖
            int delay = crate.getModelEngineOpenDelay();
            final Reward finalReward = reward;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                playGuiAnimationAndGiveReward(player, crate, finalReward, crateLocation);
            }, delay);
        } else {
            playGuiAnimationAndGiveReward(player, crate, reward, crateLocation);
        }
    }

    private void playGuiAnimationAndGiveReward(Player player, Crate crate, Reward reward, Location crateLocation) {
        // 保存玩家UUID，以便动画结束后玩家离线时使用
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        String crateId = crate.getId();

        // 完成回调：恢复idle动画、显示全息、移除冷却、给奖励
        Runnable onComplete = () -> {
            // 检查宝箱是否还存在（可能在抽奖过程中被移除）
            if (!plugin.getCrateManager().isLocationSet(crateLocation)) {
                openingPlayers.remove(playerUuid);
                giveRewardSafe(playerUuid, playerName, crate, reward);
                return;
            }
            // 恢复ModelEngine idle动画
            if (crate.isModelEngineEnabled() && plugin.getModelEngineManager().hasModel(crateLocation)) {
                plugin.getModelEngineManager().playIdleAnimation(crate, crateLocation);
            }
            plugin.getHologramManager().showHologram(crateLocation, crateId);
            openingPlayers.remove(playerUuid);
            giveRewardSafe(playerUuid, playerName, crate, reward);
        };

        boolean guiAnimEnabled = crate.isAnimationEnabled();
        boolean physicalAnimEnabled = crate.isPhysicalAnimationEnabled();

        // 如果有任何动画启用，使用AnimationManager统一管理
        if (guiAnimEnabled || physicalAnimEnabled) {
            AnimationManager animationManager = new AnimationManager(plugin);
            animationManager.playAnimation(player, crate, reward, crateLocation, onComplete);
        } else {
            // 没有任何动画，直接完成
            onComplete.run();
        }
    }

    /**
     * 安全地给予奖励，检查玩家是否在线
     * 如果玩家离线，奖励存入待领取列表
     */
    private void giveRewardSafe(UUID playerUuid, String playerName, Crate crate, Reward reward) {
        Player player = plugin.getServer().getPlayer(playerUuid);

        // 玩家离线，存入待领取列表
        if (player == null || !player.isOnline()) {
            plugin.getPendingRewardManager().addPendingReward(playerUuid, crate.getId(), reward);
            plugin.getLogger().info("Player " + playerName + " is offline, reward stored for later claim.");

            // 仍然记录历史和广播
            plugin.getHistoryManager().addHistory(playerUuid, playerName, crate.getId(), reward.getId(), reward.getDisplayName());

            if (reward.shouldBroadcast() && plugin.getConfigManager().isBroadcastRareRewards()) {
                var message = plugin.getMessageConfig().getMessage("broadcast-rare",
                        MessageConfig.placeholders(
                                "player", playerName,
                                "crate", crate.getName(),
                                "reward", reward.getDisplayName()
                        ));
                plugin.getServer().broadcast(message);
            }
            return;
        }

        // 玩家在线，正常给予奖励
        giveReward(player, crate, reward);
    }

    private void giveReward(Player player, Crate crate, Reward reward) {
        reward.give(player);

        plugin.getMessageConfig().send(player, "reward-received",
                MessageConfig.placeholders("reward", reward.getDisplayName()));

        if (reward.shouldBroadcast() && plugin.getConfigManager().isBroadcastRareRewards()) {
            var message = plugin.getMessageConfig().getMessage("broadcast-rare",
                    MessageConfig.placeholders(
                            "player", player.getName(),
                            "crate", crate.getName(),
                            "reward", reward.getDisplayName()
                    ));
            plugin.getServer().broadcast(message);
        }

        plugin.getHistoryManager().addHistory(
                player.getUniqueId(),
                player.getName(),
                crate.getId(),
                reward.getId(),
                reward.getDisplayName()
        );

        if (crate.isParticlesEnabled()) {
            player.getWorld().spawnParticle(crate.getParticleType(),
                    player.getLocation().add(0, 1, 0),
                    crate.getParticleCount(), 0.5, 0.5, 0.5, 0.1);
        }

        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        // 如果是宝箱位置，阻止破坏（只能通过蹲下+左键移除）
        if (plugin.getCrateManager().isLocationSet(location)) {
            event.setCancelled(true);
            plugin.getMessageConfig().send(event.getPlayer(), "crate-break-denied");
        }
    }

    /**
     * 处理点击ModelEngine模型（ArmorStand）的情况
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        // 只处理ArmorStand（ModelEngine模型的基础实体）
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) return;

        // 检查是否是隐形的marker ArmorStand（ModelEngine模型）
        if (armorStand.isVisible() || !armorStand.isMarker()) return;

        // 获取ArmorStand所在方块位置
        Location location = armorStand.getLocation().getBlock().getLocation();
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getMessageConfig().send(player, "invalid-crate");
            return;
        }

        // 蹲下 = 管理员移除宝箱
        if (player.isSneaking()) {
            if (!player.hasPermission("fotiacrates.admin.remove")) {
                plugin.getMessageConfig().send(player, "no-permission");
                return;
            }

            // 移除ModelEngine模型
            plugin.getModelEngineManager().removeCrateModel(location);
            // 移除全息显示
            plugin.getHologramManager().removeHologram(location);
            plugin.getCrateManager().removeLocation(location);
            plugin.getMessageConfig().send(player, "crate-removed",
                    MessageConfig.placeholders("crate", crate.getName()));
        } else {
            // 普通右键 = 打开宝箱
            if (!hasOpenPermission(player, crate)) {
                plugin.getMessageConfig().send(player, "no-permission");
                return;
            }

            if (!plugin.getKeyManager().hasKeyForCrate(player, crate.getId())) {
                plugin.getMessageConfig().send(player, "no-key");
                return;
            }

            openCrate(player, crate, location);
        }
    }

    /**
     * 检查玩家是否有开箱权限
     * 如果宝箱没有配置权限（留空），则所有玩家都可以开箱
     * 如果配置了权限，则检查玩家是否有该权限
     */
    private boolean hasOpenPermission(Player player, Crate crate) {
        String customPermission = crate.getPermission();
        if (customPermission == null || customPermission.isEmpty()) {
            // 留空表示不需要权限，所有玩家都可以开箱
            return true;
        }
        // 使用自定义权限
        return player.hasPermission(customPermission);
    }

    /**
     * 处理左键点击ModelEngine模型（ArmorStand）的情况 - 用于预览
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 只处理玩家攻击ArmorStand的情况
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand armorStand)) return;

        // 检查是否是隐形的marker ArmorStand（ModelEngine模型）
        if (armorStand.isVisible() || !armorStand.isMarker()) return;

        // 获取ArmorStand所在方块位置
        Location location = armorStand.getLocation().getBlock().getLocation();
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) return;

        event.setCancelled(true);

        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getMessageConfig().send(player, "invalid-crate");
            return;
        }

        // 左键 = 预览
        if (!player.hasPermission("fotiacrates.preview")) {
            plugin.getMessageConfig().send(player, "no-permission");
            return;
        }

        plugin.getGuiManager().openPreview(player, crate);
    }
}
