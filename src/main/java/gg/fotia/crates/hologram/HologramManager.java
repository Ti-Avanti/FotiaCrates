package gg.fotia.crates.hologram;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import gg.fotia.crates.key.PlayerKeyCountSnapshot;
import gg.fotia.crates.util.MessageUtil;
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
    private final Map<String, Map<UUID, PlayerHologram>> playerHolograms = new HashMap<>();
    // 定时更新任务
    private BukkitTask updateTask;
    private final Map<UUID, Set<String>> playerLocations = new HashMap<>();
    private final Map<String, Integer> hiddenLocations = new HashMap<>();
    private final HologramUpdateQueue updateQueue;
    private int updateInterval;
    private int updateBudget;

    // 配置
    private boolean enabled;
    private double heightOffset;
    private float scale;
    private List<String> lines;
    private double updateRadius = 32.0; // 更新半径
    private double maxSearchRadius = 32.0;
    private int searchRadiusRefreshCounter;

    /**
     * 已发送状态缓存：文本/位置/视距未变化时跳过 text()/teleport()/setViewRange()，
     * 避免每秒对每个（玩家×宝箱）重发 metadata 与 teleport 包
     */
    private static final class PlayerHologram {
        final TextDisplay display;
        String lastText;
        double lastX;
        double lastY;
        double lastZ;
        float lastViewRange;

        PlayerHologram(TextDisplay display) {
            this.display = display;
        }
    }

    public HologramManager(FotiaCrates plugin) {
        this.plugin = plugin;
        this.updateQueue = new HologramUpdateQueue(plugin, new HologramUpdateQueue.Target() {
            public Set<UUID> knownPlayers() { return playerLocations.keySet(); }
            public double searchRadius() { return maxSearchRadius; }
            public String update(Player player, CrateLocation location, PlayerKeyCountSnapshot keys) {
                return updateNearbyHologram(player, location, keys);
            }
            public void finish(UUID playerId, Set<String> visible) { cleanupPlayerHolograms(playerId, visible); }
        });
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
        if (!Double.isFinite(updateRadius) || updateRadius <= 0) updateRadius = 32.0;
        this.updateInterval = Math.max(1, config.getInt("hologram.update-interval-ticks", 20));
        this.updateBudget = Math.max(1, config.getInt("hologram.max-updates-per-tick", 128));
        refreshMaxSearchRadius();
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
        // 每 tick 分摊更新工作；一轮刷新周期由配置控制
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllHolograms, 1L, 1L);
    }

    /**
     * 更新所有全息显示
     */
    private void updateAllHolograms() {
        if (!enabled) return;

        if (++searchRadiusRefreshCounter >= 400) {
            searchRadiusRefreshCounter = 0;
            refreshMaxSearchRadius();
        }
        updateQueue.tick(updateInterval, updateBudget);
    }

    private String updateNearbyHologram(Player player, CrateLocation crateLocation,
                                        PlayerKeyCountSnapshot keys) {
        World world = player.getWorld();
        if (!world.getName().equals(crateLocation.getWorld())
                || !world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16), Math.floorDiv(crateLocation.getZ(), 16))) {
            return null;
        }
        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) return null;
        Location location = crateLocation.toLocation(world);
        double radius = getViewRadius(crate);
        String key = getLocationKey(location);
        if (!plugin.getCrateManager().isLocationSet(location) || hiddenLocations.containsKey(key) || location.distanceSquared(player.getLocation()) > radius * radius) return null;
        updateOrCreatePlayerHologram(location, crate, player, keys::count);
        return key;
    }

    private void cleanupPlayerHolograms(UUID playerId, Set<String> visible) {
        Set<String> previous = playerLocations.get(playerId);
        if (previous == null) return;
        for (Iterator<String> iterator = previous.iterator(); iterator.hasNext();) {
            String key = iterator.next();
            if (visible.contains(key)) continue;
            Map<UUID, PlayerHologram> holograms = playerHolograms.get(key);
            if (holograms != null) {
                removeDisplay(holograms.remove(playerId));
                if (holograms.isEmpty()) playerHolograms.remove(key);
            }
            iterator.remove();
        }
        if (previous.isEmpty()) playerLocations.remove(playerId);
    }

    private void refreshMaxSearchRadius() {
        double maximum = Math.max(1.0, updateRadius);
        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            maximum = Math.max(maximum, getViewRadius(crate));
        }
        maxSearchRadius = maximum;
    }

    private double getViewRadius(Crate crate) {
        if (crate.isModelEnabled() && crate.getModelEngineViewRange() > 0) {
            return Math.max(updateRadius, crate.getModelEngineViewRange());
        }
        return updateRadius;
    }

    /**
     * 为所有已放置的宝箱创建全息显示
     */
    public void createAllHolograms() {
        if (!enabled) return;
        refreshMaxSearchRadius();
        updateQueue.reset();
    }

    /**
     * 创建单个宝箱的全息显示
     */
    public void createHologram(Location location, String crateId) {
        if (!enabled) return;
        if (location == null || location.getWorld() == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) return;

        // 检查附近是否有玩家
        Collection<Player> nearbyPlayers = location.getNearbyPlayers(getViewRadius(crate));
        for (Player player : nearbyPlayers) {
            updateOrCreatePlayerHologram(location, crate, player);
        }
    }

    /**
     * 为玩家创建或更新专属全息
     */
    private void updateOrCreatePlayerHologram(Location location, Crate crate, Player player) {
        updateOrCreatePlayerHologram(location, crate, player, id -> plugin.getKeyManager().getKeyCountForCrate(player, id));
    }

    private void updateOrCreatePlayerHologram(Location location, Crate crate, Player player,
                                             java.util.function.ToIntFunction<String> keyCounts) {
        String key = getLocationKey(location);
        if (hiddenLocations.containsKey(key)) return;
        playerLocations.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(key);
        Map<UUID, PlayerHologram> playerMap = playerHolograms.computeIfAbsent(key, k -> new HashMap<>());

        PlayerHologram existing = playerMap.get(player.getUniqueId());

        double crateHologramHeight = crate.getHologramHeight();
        double finalHeight = crateHologramHeight > 0 ? crateHologramHeight : heightOffset;
        Location holoLoc = location.clone().add(0.5, finalHeight, 0.5);

        // 使用宝箱配置的可视距离
        float viewRange = 32;
        if (crate.isModelEnabled() && crate.getModelEngineViewRange() > 0) {
            viewRange = crate.getModelEngineViewRange();
        }
        final float finalViewRange = viewRange;

        String rawText = buildHologramString(crate, keyCounts);

        if (existing != null && !existing.display.isDead()) {
            // 增量更新：内容、位置、视距未变化时不重发包，也不重新解析 MiniMessage
            if (!rawText.equals(existing.lastText)) {
                existing.display.text(MessageUtil.parse(rawText));
                existing.lastText = rawText;
            }
            if (existing.lastX != holoLoc.getX() || existing.lastY != holoLoc.getY()
                    || existing.lastZ != holoLoc.getZ()) {
                existing.display.teleport(holoLoc);
                rememberPosition(existing, holoLoc);
            }
            if (existing.lastViewRange != finalViewRange) {
                existing.display.setViewRange(finalViewRange);
                existing.lastViewRange = finalViewRange;
            }
            player.showEntity(plugin, existing.display);
        } else {
            // 创建新全息
            var text = MessageUtil.parse(rawText);
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
                d.setVisibleByDefault(false);
            });
            player.showEntity(plugin, display);

            PlayerHologram hologram = new PlayerHologram(display);
            hologram.lastText = rawText;
            hologram.lastViewRange = finalViewRange;
            rememberPosition(hologram, holoLoc);
            playerMap.put(player.getUniqueId(), hologram);
        }
    }

    private void rememberPosition(PlayerHologram hologram, Location location) {
        hologram.lastX = location.getX();
        hologram.lastY = location.getY();
        hologram.lastZ = location.getZ();
    }

    private void removeDisplay(PlayerHologram hologram) {
        if (hologram != null && !hologram.display.isDead()) {
            hologram.display.remove();
        }
    }

    /**
     * 移除某位置的所有玩家专属全息
     */
    private void removePlayerHologramsAt(Location location) {
        String key = getLocationKey(location);
        Map<UUID, PlayerHologram> playerMap = playerHolograms.remove(key);
        if (playerMap != null) {
            for (UUID playerId : playerMap.keySet()) {
                Set<String> locations = playerLocations.get(playerId);
                if (locations != null) {
                    locations.remove(key);
                    if (locations.isEmpty()) playerLocations.remove(playerId);
                }
            }
            for (PlayerHologram hologram : playerMap.values()) {
                removeDisplay(hologram);
            }
        }
    }

    /**
     * 移除单个宝箱的全息显示
     */
    public void removeHologram(Location location) {
        if (location == null) return;

        removePlayerHologramsAt(location);
    }

    /**
     * 临时隐藏全息显示（开箱时）
     */
    public void hideHologram(Location location) {
        if (location == null || location.getWorld() == null) return;
        hiddenLocations.merge(getLocationKey(location), 1, Integer::sum);
        removeHologram(location);
    }

    /**
     * 恢复全息显示（开箱结束后）
     */
    public void showHologram(Location location, String crateId) {
        if (location == null || location.getWorld() == null) return;
        hiddenLocations.computeIfPresent(getLocationKey(location), (key, count) -> count <= 1 ? null : count - 1);
        if (plugin.isEnabled() && plugin.getCrateManager().isLocationSet(location)) createHologram(location, crateId);
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
     * 构建全息显示原始文本；{keys} 的背包扫描只在模板确实包含该占位符时计算一次
     */
    private String buildHologramString(Crate crate, java.util.function.ToIntFunction<String> keyCounts) {
        List<String> displayLines = crate.getHologramLines();
        if (displayLines == null || displayLines.isEmpty()) {
            displayLines = lines;
        }

        String keysValue = null;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < displayLines.size(); i++) {
            String line = displayLines.get(i);

            line = line.replace("{crate_name}", crate.getName());
            line = line.replace("{crate_id}", crate.getId());

            if (line.contains("{keys}")) {
                if (keysValue == null) {
                    keysValue = String.valueOf(keyCounts.applyAsInt(crate.getId()));
                }
                line = line.replace("{keys}", keysValue);
            }

            sb.append(line);
            if (i < displayLines.size() - 1) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * 移除所有全息显示
     */
    public void removeAllHolograms() {
        for (Map<UUID, PlayerHologram> playerMap : playerHolograms.values()) {
            for (PlayerHologram hologram : playerMap.values()) {
                removeDisplay(hologram);
            }
        }
        playerHolograms.clear();
        playerLocations.clear();
        updateQueue.reset();
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
        hiddenLocations.clear();
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
