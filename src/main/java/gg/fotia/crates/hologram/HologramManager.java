package gg.fotia.crates.hologram;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * 全息显示管理器 - 使用 TextDisplay 实体在宝箱上方显示信息
 * 每个玩家看到自己的钥匙数量
 */
public class HologramManager {

    private final FotiaCrates plugin;
    // 每个位置对应每个玩家的全息显示
    private final Map<String, Map<UUID, TextDisplay>> playerHolograms = new HashMap<>();
    // 共享全息显示（用于没有玩家在附近时）
    private final Map<String, TextDisplay> sharedHolograms = new HashMap<>();
    // 定时更新任务
    private BukkitTask updateTask;

    // 配置
    private boolean enabled;
    private double heightOffset;
    private float scale;
    private List<String> lines;
    private double updateRadius = 32.0; // 更新半径

    public HologramManager(FotiaCrates plugin) {
        this.plugin = plugin;
        loadConfig();
        startUpdateTask();
    }

    public void loadConfig() {
        var config = plugin.getConfigManager().getConfig();
        this.enabled = config.getBoolean("hologram.enabled", true);
        this.heightOffset = config.getDouble("hologram.height-offset", 1.5);
        this.scale = (float) config.getDouble("hologram.scale", 1.0);
        this.lines = config.getStringList("hologram.lines");
        this.updateRadius = config.getDouble("hologram.update-radius", 32.0);
        if (this.lines.isEmpty()) {
            this.lines = List.of(
                    "<!i><gold>{crate_name}",
                    "<!i><gray>钥匙: <yellow>{keys}",
                    "<!i><gray>左键预览 | 右键开启"
            );
        }
    }

    /**
     * 启动定时更新任务
     */
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        // 每秒更新一次
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllHolograms, 20L, 20L);
    }

    /**
     * 更新所有全息显示
     */
    private void updateAllHolograms() {
        if (!enabled) return;

        for (CrateLocation crateLocation : plugin.getCrateManager().getCrateLocations()) {
            World world = Bukkit.getWorld(crateLocation.getWorld());
            if (world == null) continue;

            Location loc = crateLocation.toLocation(world);
            if (loc == null) continue;

            String key = getLocationKey(loc);
            Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
            if (crate == null) continue;

            // 使用宝箱配置的可视距离，如果启用了模型则使用模型的可视距离
            double viewRadius = updateRadius;
            if (crate.isModelEnabled() && crate.getModelEngineViewRange() > 0) {
                viewRadius = crate.getModelEngineViewRange();
            }

            // 获取附近的玩家
            Collection<Player> nearbyPlayers = loc.getNearbyPlayers(viewRadius);

            if (nearbyPlayers.isEmpty()) {
                // 没有玩家在可视距离内，移除所有全息显示
                removePlayerHologramsAt(loc);
                removeSharedHologram(loc);
            } else {
                // 有玩家在附近，移除共享全息，为每个玩家创建/更新专属全息
                removeSharedHologram(loc);
                for (Player player : nearbyPlayers) {
                    updateOrCreatePlayerHologram(loc, crate, player);
                }
                // 移除不在附近的玩家的全息
                cleanupDistantPlayerHolograms(loc, nearbyPlayers);
            }
        }
    }

    /**
     * 为所有已放置的宝箱创建全息显示
     */
    public void createAllHolograms() {
        if (!enabled) return;

        for (CrateLocation crateLocation : plugin.getCrateManager().getCrateLocations()) {
            World world = Bukkit.getWorld(crateLocation.getWorld());
            if (world == null) continue;

            Location loc = crateLocation.toLocation(world);
            if (loc == null) continue;

            createHologram(loc, crateLocation.getCrateId());
        }
    }

    /**
     * 创建单个宝箱的全息显示（共享版本）
     */
    public void createHologram(Location location, String crateId) {
        if (!enabled) return;
        if (location == null || location.getWorld() == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) return;

        // 检查附近是否有玩家
        Collection<Player> nearbyPlayers = location.getNearbyPlayers(updateRadius);
        if (nearbyPlayers.isEmpty()) {
            ensureSharedHologram(location, crate);
        } else {
            for (Player player : nearbyPlayers) {
                updateOrCreatePlayerHologram(location, crate, player);
            }
        }
    }

    /**
     * 确保共享全息存在
     */
    private void ensureSharedHologram(Location location, Crate crate) {
        String key = getLocationKey(location);
        TextDisplay existing = sharedHolograms.get(key);
        if (existing != null && !existing.isDead()) {
            return; // 已存在
        }

        // 创建共享全息
        double crateHologramHeight = crate.getHologramHeight();
        double finalHeight = crateHologramHeight > 0 ? crateHologramHeight : heightOffset;
        Location holoLoc = location.clone().add(0.5, finalHeight, 0.5);

        // 使用宝箱配置的可视距离
        float viewRange = 32;
        if (crate.isModelEnabled() && crate.getModelEngineViewRange() > 0) {
            viewRange = crate.getModelEngineViewRange();
        }
        final float finalViewRange = viewRange;

        Component text = buildHologramText(crate, null);

        TextDisplay display = location.getWorld().spawn(holoLoc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            d.setViewRange(finalViewRange);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            d.setPersistent(false);
        });

        sharedHolograms.put(key, display);
    }

    /**
     * 为玩家创建或更新专属全息
     */
    private void updateOrCreatePlayerHologram(Location location, Crate crate, Player player) {
        String key = getLocationKey(location);
        Map<UUID, TextDisplay> playerMap = playerHolograms.computeIfAbsent(key, k -> new HashMap<>());

        TextDisplay existing = playerMap.get(player.getUniqueId());

        double crateHologramHeight = crate.getHologramHeight();
        double finalHeight = crateHologramHeight > 0 ? crateHologramHeight : heightOffset;
        Location holoLoc = location.clone().add(0.5, finalHeight, 0.5);

        // 使用宝箱配置的可视距离
        float viewRange = 32;
        if (crate.isModelEnabled() && crate.getModelEngineViewRange() > 0) {
            viewRange = crate.getModelEngineViewRange();
        }
        final float finalViewRange = viewRange;

        Component text = buildHologramText(crate, player);

        if (existing != null && !existing.isDead()) {
            // 更新现有全息
            existing.text(text);
            existing.teleport(holoLoc);
            existing.setViewRange(finalViewRange);
        } else {
            // 创建新全息
            TextDisplay display = location.getWorld().spawn(holoLoc, TextDisplay.class, d -> {
                d.text(text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                d.setViewRange(finalViewRange);
                d.setShadowRadius(0);
                d.setShadowStrength(0);
                d.setPersistent(false);
            });

            // 只对该玩家显示
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.hideEntity(plugin, display);
                }
            }

            playerMap.put(player.getUniqueId(), display);
        }
    }

    /**
     * 移除共享全息
     */
    private void removeSharedHologram(Location location) {
        String key = getLocationKey(location);
        TextDisplay display = sharedHolograms.remove(key);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    /**
     * 移除某位置的所有玩家专属全息
     */
    private void removePlayerHologramsAt(Location location) {
        String key = getLocationKey(location);
        Map<UUID, TextDisplay> playerMap = playerHolograms.remove(key);
        if (playerMap != null) {
            for (TextDisplay display : playerMap.values()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
    }

    /**
     * 清理不在附近的玩家的全息
     */
    private void cleanupDistantPlayerHolograms(Location location, Collection<Player> nearbyPlayers) {
        String key = getLocationKey(location);
        Map<UUID, TextDisplay> playerMap = playerHolograms.get(key);
        if (playerMap == null) return;

        Set<UUID> nearbyUuids = new HashSet<>();
        for (Player p : nearbyPlayers) {
            nearbyUuids.add(p.getUniqueId());
        }

        Iterator<Map.Entry<UUID, TextDisplay>> it = playerMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TextDisplay> entry = it.next();
            if (!nearbyUuids.contains(entry.getKey())) {
                TextDisplay display = entry.getValue();
                if (display != null && !display.isDead()) {
                    display.remove();
                }
                it.remove();
            }
        }
    }

    /**
     * 移除单个宝箱的全息显示
     */
    public void removeHologram(Location location) {
        if (location == null) return;

        removeSharedHologram(location);
        removePlayerHologramsAt(location);
    }

    /**
     * 临时隐藏全息显示（开箱时）
     */
    public void hideHologram(Location location) {
        removeHologram(location);
    }

    /**
     * 恢复全息显示（开箱结束后）
     */
    public void showHologram(Location location, String crateId) {
        createHologram(location, crateId);
    }

    /**
     * 更新玩家看到的全息显示（显示该玩家的钥匙数量）
     */
    public void updateHologramForPlayer(Player player, Location location, String crateId) {
        if (!enabled) return;
        if (location == null || location.getWorld() == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) return;

        updateOrCreatePlayerHologram(location, crate, player);
    }

    /**
     * 构建全息显示文本
     */
    private Component buildHologramText(Crate crate, Player player) {
        List<String> displayLines = crate.getHologramLines();
        if (displayLines == null || displayLines.isEmpty()) {
            displayLines = lines;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < displayLines.size(); i++) {
            String line = displayLines.get(i);

            line = line.replace("{crate_name}", crate.getName());
            line = line.replace("{crate_id}", crate.getId());

            if (player != null) {
                int keys = plugin.getKeyManager().getKeyCountForCrate(player, crate.getId());
                line = line.replace("{keys}", String.valueOf(keys));
            } else {
                line = line.replace("{keys}", "?");
            }

            sb.append(line);
            if (i < displayLines.size() - 1) {
                sb.append("\n");
            }
        }

        return MessageUtil.parse(sb.toString());
    }

    /**
     * 移除所有全息显示
     */
    public void removeAllHolograms() {
        for (TextDisplay display : sharedHolograms.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        sharedHolograms.clear();

        for (Map<UUID, TextDisplay> playerMap : playerHolograms.values()) {
            for (TextDisplay display : playerMap.values()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
        playerHolograms.clear();
    }

    /**
     * 重新加载配置并重建全息显示
     */
    public void reload() {
        removeAllHolograms();
        loadConfig();
        createAllHolograms();
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        removeAllHolograms();
    }

    /**
     * 获取位置的唯一键
     */
    private String getLocationKey(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
