package gg.fotia.crates.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 使用PacketEvents监听玩家左键点击实体的数据包
 * 用于处理ModelEngine模型的左键预览
 */
public class EntityInteractPacketListener {

    private final FotiaCrates plugin;
    private PacketListenerCommon packetListener;

    public EntityInteractPacketListener(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册数据包监听器
     */
    public void register() {
        if (!isPacketEventsAvailable()) {
            plugin.getLogger().warning("PacketEvents not available, ModelEngine left-click preview may not work properly.");
            return;
        }

        packetListener = new SimplePacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
                if (event.isCancelled()) return;

                Player player = event.<Player>getPlayer();
                if (player == null) return;

                try {
                    Integer entityId = readAttackEntityId(event);
                    if (entityId == null) return;

                    Bukkit.getScheduler().runTask(plugin, () -> handleLeftClickEntity(player, entityId));
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        plugin.getLogger().info("EntityInteractPacketListener registered.");
    }

    /**
     * 读取左键攻击的实体ID
     */
    private Integer readAttackEntityId(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            return new WrapperPlayClientAttack(event).getEntityId();
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interaction = new WrapperPlayClientInteractEntity(event);
            if (interaction.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                return interaction.getEntityId();
            }
        }

        return null;
    }

    /**
     * 处理左键点击实体
     */
    private void handleLeftClickEntity(Player player, int entityId) {
        // 通过实体ID找到实体
        Entity entity = null;
        for (Entity e : player.getWorld().getEntities()) {
            if (e.getEntityId() == entityId) {
                entity = e;
                break;
            }
        }

        // 如果没找到实体（ModelEngine虚拟实体），通过玩家视线找宝箱
        if (entity == null) {
            CrateLocation crateLocation = findCrateByPlayerLook(player);
            if (crateLocation != null) {
                Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
                if (crate != null) {
                    if (!player.hasPermission("fotiacrates.preview")) {
                        plugin.getLanguageManager().send(player, "no-permission");
                        return;
                    }
                    plugin.getGuiManager().openPreview(player, crate);
                }
            }
            return;
        }

        // 获取实体位置对应的宝箱
        Location entityLoc = entity.getLocation().getBlock().getLocation();
        CrateLocation crateLocation = findNearbyModelEngineCrate(entityLoc);
        if (crateLocation == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) return;

        if (!player.hasPermission("fotiacrates.preview")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        plugin.getGuiManager().openPreview(player, crate);
    }

    /**
     * 通过玩家视线方向找到看向的宝箱
     */
    private CrateLocation findCrateByPlayerLook(Player player) {
        Location eyeLoc = player.getEyeLocation();
        org.bukkit.util.Vector direction = eyeLoc.getDirection().normalize();

        CrateLocation bestMatch = null;
        double bestScore = -1;

        for (var crateLocation : plugin.getCrateManager().getCrateLocations()) {
            if (!crateLocation.getWorld().equals(player.getWorld().getName())) continue;

            Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
            if (crate == null || !crate.isModelEngineEnabled()) continue;

            Location crateLoc = crateLocation.toLocation(player.getWorld());
            if (crateLoc == null) continue;

            // 宝箱中心位置
            Location crateCenter = crateLoc.clone().add(0.5, 1.0, 0.5);
            double distance = eyeLoc.distance(crateCenter);

            // 只检查6格内
            if (distance > 6.0) continue;

            // 计算玩家视线到宝箱的方向
            org.bukkit.util.Vector toCrate = crateCenter.toVector().subtract(eyeLoc.toVector()).normalize();
            double dot = direction.dot(toCrate);

            // dot > 0.7 表示大约45度内
            if (dot > 0.7) {
                // 得分 = 角度准确度 / 距离
                double score = dot / distance;
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = crateLocation;
                }
            }
        }

        return bestMatch;
    }

    /**
     * 查找实体位置附近的ModelEngine宝箱
     */
    private CrateLocation findNearbyModelEngineCrate(Location entityLoc) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    Location testLoc = entityLoc.clone().add(x, y, z);
                    CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(testLoc);
                    if (crateLocation != null) {
                        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
                        if (crate != null && crate.isModelEngineEnabled()) {
                            return crateLocation;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 注销数据包监听器
     */
    public void unregister() {
        if (packetListener != null && isPacketEventsAvailable()) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
    }

    /**
     * 检查PacketEvents是否可用
     */
    private boolean isPacketEventsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("packetevents");
    }
}
