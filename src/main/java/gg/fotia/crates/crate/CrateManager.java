package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationType;
import gg.fotia.crates.particle.CrateParticleEffect;
import gg.fotia.crates.particle.ParticleCompat;
import gg.fotia.crates.particle.ParticleEffectMode;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.particle.ParticleTarget;
import gg.fotia.crates.reward.*;
import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

public class CrateManager {

    private final FotiaCrates plugin;
    private final Map<String, Crate> crates = new HashMap<>();
    private final CrateLocationIndex crateLocations = new CrateLocationIndex();

    public CrateManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void loadCrates() {
        crates.clear();
        File cratesFolder = new File(plugin.getDataFolder(), "crates");
        if (!cratesFolder.exists()) {
            cratesFolder.mkdirs();
            return;
        }

        File[] files = cratesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                Crate crate = loadCrate(id, file);
                if (crate != null) {
                    crates.put(id, crate);
                    plugin.getLogger().info("Loaded crate: " + id);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load crate " + id + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + crates.size() + " crates.");
    }

    private Crate loadCrate(String id, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name", id);
        Material blockMaterial = Material.valueOf(config.getString("block.material", "CHEST"));

        // 宝箱方块物品设置
        String blockItemName = config.getString("block.item.name", name);
        List<String> blockItemLore = config.getStringList("block.item.lore");

        // 模型设置，优先读取新版 block.model，兼容旧版 block.modelengine 和 block.bettermodel
        boolean betterModelLegacyEnabled = config.getBoolean("block.bettermodel.enabled", false);
        String modelProvider = config.getString("block.model.provider",
                betterModelLegacyEnabled ? "bettermodel" : "modelengine");
        boolean modelEngineEnabled = config.getBoolean("block.model.enabled",
                betterModelLegacyEnabled || config.getBoolean("block.modelengine.enabled", false));
        String modelBasePath = "block.model";
        String legacyBasePath = betterModelLegacyEnabled ? "block.bettermodel" : "block.modelengine";
        String modelEngineId = getModelString(config, modelBasePath, legacyBasePath, "model-id", "");
        String modelEngineIdleAnimation = getModelString(config, modelBasePath, legacyBasePath, "idle-animation", "idle");
        String modelEngineOpenAnimation = getModelString(config, modelBasePath, legacyBasePath, "open-animation", "open");
        int modelEngineOpenDelay = getModelInt(config, modelBasePath, legacyBasePath, "open-delay", 20);
        int modelEngineViewRange = getModelInt(config, modelBasePath, legacyBasePath, "view-range", 48);

        boolean previewEnabled = config.getBoolean("preview.enabled", true);
        boolean showChance = config.getBoolean("preview.show-chance", true);
        String previewTitle = config.getString("preview.title", name + " Preview");

        boolean animationEnabled = config.getBoolean("animation.enabled", true);
        AnimationType animationType = AnimationType.valueOf(config.getString("animation.type", "ROULETTE"));
        int animationDuration = config.getInt("animation.duration", 3);
        String animationTitle = config.getString("animation.title", "<!i><dark_gray>" + name);

        boolean physicalAnimationEnabled = config.getBoolean("physical-animation.enabled", false);
        double physicalAnimationHeight = config.getDouble("physical-animation.height", 1.5);

        double hologramHeight = config.getDouble("hologram.height", 1.5);
        List<String> hologramLines = config.getStringList("hologram.lines");

        boolean particlesEnabled = config.getBoolean("particles.enabled", true);
        String legacyParticleName = config.getString("particles.type", "FLAME");
        Particle particleType = ParticleCompat.resolveParticle(legacyParticleName, Particle.FLAME);
        int particleCount = config.getInt("particles.count", 10);
        Map<ParticleStage, CrateParticleEffect> particleEffects = CrateParticleEffect.loadAll(
                config.getConfigurationSection("particles"), legacyParticleName, particleCount);

        Sound spinSound = Sound.valueOf(config.getString("sounds.spin.sound", "BLOCK_NOTE_BLOCK_PLING"));
        float spinVolume = (float) config.getDouble("sounds.spin.volume", 1.0);
        float spinPitch = (float) config.getDouble("sounds.spin.pitch", 1.0);
        Sound winSound = Sound.valueOf(config.getString("sounds.win.sound", "ENTITY_PLAYER_LEVELUP"));
        float winVolume = (float) config.getDouble("sounds.win.volume", 1.0);
        float winPitch = (float) config.getDouble("sounds.win.pitch", 1.0);

        boolean pityEnabled = config.getBoolean("pity.enabled", false);

        // 加载多级保底配置
        List<Crate.PityTier> pityTiers = new ArrayList<>();
        boolean hasPityTiersNode = config.contains("pity.tiers");
        ConfigurationSection pityTiersSection = config.getConfigurationSection("pity.tiers");
        if (pityTiersSection != null) {
            for (String tierKey : pityTiersSection.getKeys(false)) {
                ConfigurationSection tierSection = pityTiersSection.getConfigurationSection(tierKey);
                if (tierSection != null) {
                    int count = tierSection.getInt("count", 50);
                    String rarity = tierSection.getString("rarity", plugin.getConfigManager().getDefaultPityRarityId());
                    pityTiers.add(new Crate.PityTier(count, rarity));
                }
            }
        }
        // 兼容旧版单级保底配置
        if (!hasPityTiersNode && pityTiers.isEmpty() && pityEnabled) {
            int pityCount = config.getInt("pity.count", 50);
            String pityRarity = config.getString("pity.rarity", plugin.getConfigManager().getDefaultPityRarityId());
            pityTiers.add(new Crate.PityTier(pityCount, pityRarity));
        }
        boolean resetPityOnEarlyQualifyingReward = config.getBoolean("pity.reset-on-early-qualifying-reward", false);

        boolean multiOpenEnabled = config.getBoolean("multi-open.enabled", true);
        int multiOpenMax = config.getInt("multi-open.max", 10);
        boolean multiOpenAnimationEnabled = config.getBoolean("multi-open.animation.enabled", false);

        // 权限节点，默认为空（留空则不检测开箱权限）
        String permission = config.getString("permission", "");

        List<Reward> rewards = loadRewards(config.getConfigurationSection("rewards"));

        return new Crate(id, name, blockMaterial,
                blockItemName, blockItemLore,
                modelProvider, modelEngineEnabled, modelEngineId,
                modelEngineIdleAnimation, modelEngineOpenAnimation,
                modelEngineOpenDelay, modelEngineViewRange, physicalAnimationHeight,
                hologramHeight, hologramLines,
                rewards,
                previewEnabled, showChance, previewTitle,
                animationEnabled, animationType, animationDuration,
                animationTitle, physicalAnimationEnabled,
                particlesEnabled, particleType, particleCount, particleEffects,
                spinSound, spinVolume, spinPitch,
                winSound, winVolume, winPitch,
                pityEnabled, pityTiers, resetPityOnEarlyQualifyingReward,
                multiOpenEnabled, multiOpenMax, multiOpenAnimationEnabled, permission,
                plugin.getConfigManager().getRarityIds());
    }

    private String getModelString(YamlConfiguration config, String newBasePath, String legacyBasePath, String key, String def) {
        String newPath = newBasePath + "." + key;
        if (config.contains(newPath)) {
            return config.getString(newPath, def);
        }
        return config.getString(legacyBasePath + "." + key, def);
    }

    private int getModelInt(YamlConfiguration config, String newBasePath, String legacyBasePath, String key, int def) {
        String newPath = newBasePath + "." + key;
        if (config.contains(newPath)) {
            return config.getInt(newPath, def);
        }
        return config.getInt(legacyBasePath + "." + key, def);
    }

    private List<Reward> loadRewards(ConfigurationSection section) {
        List<Reward> rewards = new ArrayList<>();
        if (section == null) return rewards;

        for (String rewardId : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(rewardId);
            if (rewardSection == null) continue;

            Reward reward = loadReward(rewardId, rewardSection);
            if (reward != null) {
                rewards.add(reward);
            }
        }

        return rewards;
    }

    private Reward loadReward(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        String rarity = section.getString("rarity", plugin.getConfigManager().getDefaultRarityId());
        double chance = section.getDouble("chance", 10.0);
        boolean broadcast = section.getBoolean("broadcast", false);
        String type = section.getString("type", "item");
        boolean autoDisplayIcon = section.getBoolean("display-auto.icon", true);
        boolean autoDisplayName = section.getBoolean("display-auto.name", true);

        ItemStack displayItem = loadDisplayItem(section, displayName);

        // 加载权限检测配置
        boolean permCheckEnabled = section.getBoolean("permission-check.enabled", false);
        String checkPermission = section.getString("permission-check.permission", null);
        String actionStr = section.getString("permission-check.action", "skip");
        PermissionAction permAction = actionStr.equalsIgnoreCase("alternative")
                ? PermissionAction.ALTERNATIVE : PermissionAction.SKIP;
        String alternativeRewardId = section.getString("permission-check.alternative-reward", null);

        return switch (type.toLowerCase()) {
            case "item" -> loadItemReward(id, displayName, rarity, chance, broadcast, displayItem, section,
                    permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
            case "command" -> loadCommandReward(id, displayName, rarity, chance, broadcast, displayItem, section,
                    permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
            case "money" -> loadMoneyReward(id, displayName, rarity, chance, broadcast, displayItem, section,
                    permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
            case "experience" -> loadExperienceReward(id, displayName, rarity, chance, broadcast, displayItem, section,
                    permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
            default -> null;
        };
    }

    private ItemStack loadDisplayItem(ConfigurationSection section, String defaultName) {
        // 首先尝试直接获取序列化的ItemStack
        Object displayObj = section.get("display");
        if (displayObj instanceof ItemStack) {
            return (ItemStack) displayObj;
        }

        ConfigurationSection displaySection = section.getConfigurationSection("display");
        if (displaySection != null) {
            // 检查是否是序列化的ItemStack格式
            if (displaySection.contains("==") || displaySection.contains("type") || displaySection.contains("v")) {
                ItemStack item = section.getItemStack("display");
                if (item != null) return item;
            }
            return loadItemFromSection(displaySection, defaultName);
        }

        // 尝试从item配置加载
        Object itemObj = section.get("item");
        if (itemObj instanceof ItemStack) {
            return (ItemStack) itemObj;
        }

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection != null) {
            if (itemSection.contains("==") || itemSection.contains("type") || itemSection.contains("v")) {
                ItemStack item = section.getItemStack("item");
                if (item != null) return item;
            }
            return loadItemFromSection(itemSection, defaultName);
        }

        return new ItemBuilder(Material.PAPER).name(defaultName).build();
    }

    private ItemStack loadItemFromSection(ConfigurationSection section, String defaultName) {
        Material material = Material.valueOf(section.getString("material", "PAPER"));
        String name = section.getString("name", defaultName);
        List<String> lore = section.getStringList("lore");
        int amount = section.getInt("amount", 1);
        int customModelData = section.getInt("custom-model-data", 0);
        boolean glow = section.getBoolean("glow", false);

        ItemBuilder builder = new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .amount(amount)
                .customModelData(customModelData)
                .glow(glow);

        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            Map<String, Integer> enchants = new HashMap<>();
            for (String enchantName : enchantSection.getKeys(false)) {
                enchants.put(enchantName, enchantSection.getInt(enchantName));
            }
            builder.enchantments(enchants);
        }

        return builder.build();
    }

    private ItemStack loadOptionalRewardItem(ConfigurationSection section, String defaultName) {
        Object itemObj = section.get("item");
        if (itemObj instanceof ItemStack itemStack) {
            return itemStack;
        }

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection == null) {
            return null;
        }

        if (itemSection.contains("==") || itemSection.contains("type") || itemSection.contains("v")) {
            return section.getItemStack("item");
        }

        return loadItemFromSection(itemSection, defaultName);
    }

    private List<ItemStack> loadExtraItems(ConfigurationSection section) {
        List<ItemStack> extraItems = new ArrayList<>();
        List<?> extraList = section.getList("extra-items");
        if (extraList == null) {
            return extraItems;
        }

        for (Object obj : extraList) {
            if (obj instanceof ItemStack itemStack) {
                extraItems.add(itemStack);
            } else if (obj instanceof Map<?, ?> extraMap && extraMap.containsKey("material")) {
                try {
                    Material material = Material.valueOf((String) extraMap.get("material"));
                    extraItems.add(new ItemStack(material));
                } catch (Exception ignored) {
                }
            }
        }

        return extraItems;
    }

    private ItemReward loadItemReward(String id, String displayName, String rarity, double chance,
                                      boolean broadcast, ItemStack displayItem, ConfigurationSection section,
                                      boolean permCheckEnabled, String checkPermission,
                                      PermissionAction permAction, String alternativeRewardId,
                                      boolean autoDisplayIcon, boolean autoDisplayName) {
        ItemStack item = loadOptionalRewardItem(section, displayName);

        // 首先尝试直接获取序列化的ItemStack
        Object itemObj = section.get("item");
        if (itemObj instanceof ItemStack) {
            item = (ItemStack) itemObj;
        } else {
            ConfigurationSection itemSection = section.getConfigurationSection("item");
            if (itemSection != null) {
                // 检查是否是序列化的ItemStack格式
                if (itemSection.contains("==") || itemSection.contains("type") || itemSection.contains("v")) {
                    ItemStack serializedItem = section.getItemStack("item");
                    item = serializedItem != null ? serializedItem : displayItem.clone();
                } else {
                    item = loadItemFromSection(itemSection, displayName);
                }
            } else {
                item = displayItem.clone();
            }
        }

        List<ItemStack> extraItems = new ArrayList<>();
        // 尝试加载序列化的extra-items列表
        List<?> extraList = section.getList("extra-items");
        if (extraList != null) {
            for (Object obj : extraList) {
                if (obj instanceof ItemStack) {
                    extraItems.add((ItemStack) obj);
                } else if (obj instanceof Map) {
                    // 旧格式兼容
                    Map<?, ?> extraMap = (Map<?, ?>) obj;
                    if (extraMap.containsKey("material")) {
                        try {
                            Material material = Material.valueOf((String) extraMap.get("material"));
                            ItemStack extraItem = new ItemStack(material);
                            extraItems.add(extraItem);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        item = loadOptionalRewardItem(section, displayName);
        extraItems = loadExtraItems(section);
        List<String> commands = section.getStringList("commands");

        return new ItemReward(id, displayName, rarity, chance, broadcast, displayItem, item, extraItems, commands,
                permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
    }

    private CommandReward loadCommandReward(String id, String displayName, String rarity, double chance,
                                            boolean broadcast, ItemStack displayItem, ConfigurationSection section,
                                            boolean permCheckEnabled, String checkPermission,
                                            PermissionAction permAction, String alternativeRewardId,
                                            boolean autoDisplayIcon, boolean autoDisplayName) {
        List<String> commands = section.getStringList("commands");
        ItemStack item = loadOptionalRewardItem(section, displayName);
        List<ItemStack> extraItems = loadExtraItems(section);
        return new CommandReward(id, displayName, rarity, chance, broadcast, displayItem, commands, item, extraItems,
                permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
    }

    private MoneyReward loadMoneyReward(String id, String displayName, String rarity, double chance,
                                        boolean broadcast, ItemStack displayItem, ConfigurationSection section,
                                        boolean permCheckEnabled, String checkPermission,
                                        PermissionAction permAction, String alternativeRewardId,
                                        boolean autoDisplayIcon, boolean autoDisplayName) {
        double amount = section.getDouble("amount", 100);
        return new MoneyReward(id, displayName, rarity, chance, broadcast, displayItem, amount,
                permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
    }

    private ExperienceReward loadExperienceReward(String id, String displayName, String rarity, double chance,
                                                  boolean broadcast, ItemStack displayItem, ConfigurationSection section,
                                                  boolean permCheckEnabled, String checkPermission,
                                                  PermissionAction permAction, String alternativeRewardId,
                                                  boolean autoDisplayIcon, boolean autoDisplayName) {
        ConfigurationSection expSection = section.getConfigurationSection("experience");
        int amount = expSection != null ? expSection.getInt("amount", 100) : 100;
        boolean levels = expSection != null && expSection.getString("type", "points").equalsIgnoreCase("levels");
        return new ExperienceReward(id, displayName, rarity, chance, broadcast, displayItem, amount, levels,
                permCheckEnabled, checkPermission, permAction, alternativeRewardId, autoDisplayIcon, autoDisplayName);
    }

    public void loadLocations() {
        crateLocations.clear();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM crate_locations");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String crateId = rs.getString("crate_id");
                float yaw = rs.getFloat("yaw");
                crateLocations.put(new CrateLocation(world, x, y, z, crateId, yaw));
            }

            plugin.getLogger().info("Loaded " + crateLocations.size() + " crate locations.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load crate locations: " + e.getMessage());
        }
    }

    public void addLocation(CrateLocation location) {
        crateLocations.put(location);
        String sql = plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql")
                ? "INSERT INTO crate_locations (world, x, y, z, crate_id, yaw) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE crate_id = VALUES(crate_id), yaw = VALUES(yaw)"
                : "INSERT OR REPLACE INTO crate_locations (world, x, y, z, crate_id, yaw) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, location.getWorld());
            stmt.setInt(2, location.getX());
            stmt.setInt(3, location.getY());
            stmt.setInt(4, location.getZ());
            stmt.setString(5, location.getCrateId());
            stmt.setFloat(6, location.getYaw());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save crate location: " + e.getMessage());
        }
    }

    public void removeLocation(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        crateLocations.remove(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM crate_locations WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            stmt.setString(1, location.getWorld().getName());
            stmt.setInt(2, location.getBlockX());
            stmt.setInt(3, location.getBlockY());
            stmt.setInt(4, location.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove crate location: " + e.getMessage());
        }
    }

    public CrateLocation getLocationAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return crateLocations.get(location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }

    public boolean isLocationSet(Location location) {
        return getLocationAt(location) != null;
    }

    public Crate getCrate(String id) { return crates.get(id); }
    public Collection<Crate> getAllCrates() { return crates.values(); }
    public Set<String> getCrateIds() { return crates.keySet(); }
    public List<CrateLocation> getCrateLocations() { return crateLocations.values(); }

    public Collection<CrateLocation> getNearbyCrateLocations(String world, int blockX, int blockZ, double radius) {
        return crateLocations.nearby(world, blockX, blockZ, radius);
    }

    public void setCrateLocation(String crateId, Location location) {
        setCrateLocation(crateId, location, 0f);
    }

    public void setCrateLocation(String crateId, Location location, float yaw) {
        CrateLocation crateLocation = new CrateLocation(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                crateId,
                yaw
        );
        addLocation(crateLocation);
    }

    public Crate getCrateAtLocation(Location location) {
        CrateLocation crateLocation = getLocationAt(location);
        if (crateLocation == null) return null;
        return getCrate(crateLocation.getCrateId());
    }

    public void removeCrateLocation(Location location) {
        removeLocation(location);
    }

    /**
     * 创建宝箱方块物品
     * @param crate 宝箱
     * @param amount 数量
     * @return 带有PDC标记的宝箱方块物品
     */
    public ItemStack createCrateBlockItem(Crate crate, int amount) {
        ItemStack item = new ItemBuilder(crate.getBlockMaterial())
                .name(crate.getBlockItemName())
                .lore(crate.getBlockItemLore())
                .amount(amount)
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "crate_block"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    crate.getId()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 检查物品是否为宝箱方块
     * @param item 物品
     * @return 宝箱ID，如果不是宝箱方块则返回null
     */
    public String getCrateIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "crate_block"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    // ==================== 编辑功能 ====================

    /**
     * 更新宝箱名称
     */
    public void updateCrateName(String crateId, String newName) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("name", newName);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update crate name: " + e.getMessage());
        }
    }

    /**
     * 更新宝箱方块类型
     */
    public void updateCrateBlock(String crateId, Material material) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("block.material", material.name());
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update crate block: " + e.getMessage());
        }
    }

    /**
     * 更新动画时长
     */
    public void updateAnimationDuration(String crateId, int duration) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("animation.duration", duration);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update animation duration: " + e.getMessage());
        }
    }

    /**
     * 更新多连抽最大数量
     */
    public void updateMultiOpenMax(String crateId, int max) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("multi-open.max", max);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update multi-open max: " + e.getMessage());
        }
    }

    /**
     * 切换粒子效果
     */
    public void toggleParticles(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("particles.enabled", true);
            config.set("particles.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle particles: " + e.getMessage());
        }
    }

    public void toggleParticleStage(String crateId, ParticleStage stage) {
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            boolean current = section.getBoolean("enabled", true);
            section.set("enabled", !current);
        }, "toggle particle stage");
    }

    public void updateParticleType(String crateId, ParticleStage stage, String particleName) {
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set("type", particleName.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        }, "update particle type");
    }

    public void updateParticleMode(String crateId, ParticleStage stage, ParticleEffectMode mode) {
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set("mode", mode.name());
        }, "update particle mode");
    }

    public void updateParticleTarget(String crateId, ParticleStage stage, ParticleTarget target) {
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set("target", target.name());
        }, "update particle target");
    }

    public void updateParticleInt(String crateId, ParticleStage stage, String key, int value, int min, int max) {
        int clamped = Math.max(min, Math.min(max, value));
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set(key, clamped);
        }, "update particle " + key);
    }

    public void updateParticleDouble(String crateId, ParticleStage stage, String key, double value, double min, double max) {
        double clamped = Math.max(min, Math.min(max, value));
        clamped = Math.round(clamped * 100.0) / 100.0;
        double finalClamped = clamped;
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set(key, finalClamped);
        }, "update particle " + key);
    }

    public void updateParticleColor(String crateId, ParticleStage stage, String key, String color) {
        String normalized = color == null ? "" : color.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        String finalColor = normalized.toUpperCase(Locale.ROOT);
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set(key, finalColor);
        }, "update particle color");
    }

    public void updateParticleMaterial(String crateId, ParticleStage stage, String key, Material material) {
        updateParticleConfig(crateId, config -> {
            ConfigurationSection section = ensureParticleStageSection(config, crateId, stage);
            section.set(key, material.name());
        }, "update particle material");
    }

    private void updateParticleConfig(String crateId, Consumer<YamlConfiguration> updater, String actionName) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            updater.accept(config);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to " + actionName + ": " + e.getMessage());
        }
    }

    private ConfigurationSection ensureParticleStageSection(YamlConfiguration config, String crateId, ParticleStage stage) {
        ConfigurationSection root = config.getConfigurationSection("particles");
        if (root == null) {
            root = config.createSection("particles");
        }

        ConfigurationSection section = root.getConfigurationSection(stage.path());
        if (section == null) {
            section = root.createSection(stage.path());
            Crate crate = getCrate(crateId);
            CrateParticleEffect effect = crate != null ? crate.getParticleEffect(stage) : CrateParticleEffect.defaultFor(stage);
            effect.writeTo(section);
        }
        return section;
    }

    /**
     * 切换预览功能
     */
    public void togglePreview(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("preview.enabled", true);
            config.set("preview.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle preview: " + e.getMessage());
        }
    }

    /**
     * 切换多连抽功能
     */
    public void toggleMultiOpen(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("multi-open.enabled", true);
            config.set("multi-open.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle multi-open: " + e.getMessage());
        }
    }

    public void toggleMultiOpenAnimation(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("multi-open.animation.enabled", false);
            config.set("multi-open.animation.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle multi-open animation: " + e.getMessage());
        }
    }

    /**
     * 添加奖励
     */
    public void addReward(String crateId, ItemStack item, double chance, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String rewardId = "reward_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String path = "rewards." + rewardId;

            config.set(path + ".type", "ITEM");
            config.set(path + ".item", item);
            config.set(path + ".chance", chance);
            config.set(path + ".rarity", rarity);
            config.set(path + ".broadcast", rarity.equalsIgnoreCase("legendary") || rarity.equalsIgnoreCase("epic"));
            config.set(path + ".display-auto.icon", true);
            config.set(path + ".display-auto.name", true);
            applyAutoDisplayFromFirstItem(config, path, item);

            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to add reward: " + e.getMessage());
        }
    }

    /**
     * 移除奖励
     */
    public void removeReward(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId, null);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to remove reward: " + e.getMessage());
        }
    }

    /**
     * 更新奖励概率
     */
    public void updateRewardChance(String crateId, String rewardId, double chance) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".chance", chance);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward chance: " + e.getMessage());
        }
    }

    /**
     * 更新奖励稀有度
     */
    public void updateRewardRarity(String crateId, String rewardId, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".rarity", rarity);
            config.set("rewards." + rewardId + ".broadcast", rarity.equalsIgnoreCase("legendary") || rarity.equalsIgnoreCase("epic"));
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward rarity: " + e.getMessage());
        }
    }

    /**
     * 更新动画类型
     */
    public void updateAnimationType(String crateId, AnimationType type) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("animation.type", type.name());
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update animation type: " + e.getMessage());
        }
    }

    /**
     * 切换GUI动画开关
     */
    public void toggleGuiAnimation(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("animation.enabled", true);
            config.set("animation.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle GUI animation: " + e.getMessage());
        }
    }

    /**
     * 切换物理动画开关
     */
    public void togglePhysicalAnimation(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("physical-animation.enabled", false);
            config.set("physical-animation.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle physical animation: " + e.getMessage());
        }
    }

    /**
     * 更新物理动画高度
     */
    public void updatePhysicalAnimationHeight(String crateId, double height) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("physical-animation.height", height);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update physical animation height: " + e.getMessage());
        }
    }

    /**
     * 切换保底启用状态
     */
    public void togglePity(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("pity.enabled", false);
            config.set("pity.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle pity: " + e.getMessage());
        }
    }

    public void togglePityEarlyReset(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("pity.reset-on-early-qualifying-reward", false);
            config.set("pity.reset-on-early-qualifying-reward", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle early pity reset: " + e.getMessage());
        }
    }

    /**
     * 添加保底等级
     */
    public void addPityTier(String crateId, int count, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String tierId = "tier_" + System.currentTimeMillis();
            config.set("pity.tiers." + tierId + ".count", count);
            config.set("pity.tiers." + tierId + ".rarity", rarity);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to add pity tier: " + e.getMessage());
        }
    }

    /**
     * 更新保底等级
     */
    public void updatePityTier(String crateId, int tierIndex, int count, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection tiersSection = config.getConfigurationSection("pity.tiers");
            if (tiersSection == null) return;

            List<String> tierKeys = new ArrayList<>(tiersSection.getKeys(false));
            if (tierIndex < 0 || tierIndex >= tierKeys.size()) return;

            String tierKey = tierKeys.get(tierIndex);
            config.set("pity.tiers." + tierKey + ".count", count);
            config.set("pity.tiers." + tierKey + ".rarity", rarity);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update pity tier: " + e.getMessage());
        }
    }

    /**
     * 删除保底等级
     */
    public void removePityTier(String crateId, int tierIndex) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection tiersSection = config.getConfigurationSection("pity.tiers");
            if (tiersSection == null) return;

            List<String> tierKeys = new ArrayList<>(tiersSection.getKeys(false));
            if (tierIndex < 0 || tierIndex >= tierKeys.size()) return;

            String tierKey = tierKeys.get(tierIndex);
            config.set("pity.tiers." + tierKey, null);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to remove pity tier: " + e.getMessage());
        }
    }

    /**
     * 更新保底设置（旧版兼容）
     */
    @Deprecated
    public void updatePity(String crateId, boolean enabled, int count, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("pity.enabled", enabled);
            config.set("pity.count", count);
            config.set("pity.rarity", rarity);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update pity: " + e.getMessage());
        }
    }

    /**
     * 保存宝箱配置
     */
    public void saveCrate(String crateId) {
        // 配置已经在每次修改时保存，这里只是重新加载确保同步
        loadCrates();
    }

    /**
     * 删除宝箱
     */
    public void deleteCrate(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (file.exists()) {
            file.delete();
        }
        crates.remove(crateId);

        // 删除相关的位置
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM crate_locations WHERE crate_id = ?")) {
            stmt.setString(1, crateId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete crate locations: " + e.getMessage());
        }

        crateLocations.removeByCrateId(crateId);
    }

    /**
     * 创建新宝箱
     */
    public void createCrate(String crateId, String name) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (file.exists()) return;

        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("name", name);
            config.set("block.material", "CHEST");
            config.set("block.item.name", "<!i><gold>" + name);
            config.set("block.item.lore", List.of("<!i><gray>放置此方块创建宝箱"));
            config.set("preview.enabled", true);
            config.set("preview.show-chance", true);
            config.set("animation.enabled", true);
            config.set("animation.type", "ROULETTE");
            config.set("animation.duration", 3);
            config.set("particles.enabled", true);
            config.set("particles.type", "FLAME");
            config.set("particles.count", 10);
            writeDefaultParticleEffect(config, ParticleStage.IDLE);
            writeDefaultParticleEffect(config, ParticleStage.OPEN);
            writeDefaultParticleEffect(config, ParticleStage.REWARD);
            config.set("sounds.spin.sound", "BLOCK_NOTE_BLOCK_PLING");
            config.set("sounds.spin.volume", 1.0);
            config.set("sounds.spin.pitch", 1.0);
            config.set("sounds.win.sound", "ENTITY_PLAYER_LEVELUP");
            config.set("sounds.win.volume", 1.0);
            config.set("sounds.win.pitch", 1.0);
            config.set("pity.enabled", false);
            config.set("pity.count", 50);
            config.set("pity.rarity", plugin.getConfigManager().getDefaultPityRarityId());
            config.set("pity.reset-on-early-qualifying-reward", false);
            config.set("multi-open.enabled", true);
            config.set("multi-open.max", 10);
            config.set("multi-open.animation.enabled", false);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to create crate: " + e.getMessage());
        }
    }

    private void writeDefaultParticleEffect(YamlConfiguration config, ParticleStage stage) {
        ConfigurationSection section = config.createSection("particles." + stage.path());
        CrateParticleEffect.defaultFor(stage).writeTo(section);
    }

    /**
     * 切换奖励广播
     */
    public void toggleRewardBroadcast(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("rewards." + rewardId + ".broadcast", false);
            config.set("rewards." + rewardId + ".broadcast", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle reward broadcast: " + e.getMessage());
        }
    }

    /**
     * 更新奖励物品（实际给予玩家的物品）
     */
    public void updateRewardItem(String crateId, String rewardId, ItemStack item) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String path = "rewards." + rewardId + ".item";

            // 只更新item配置（实际给予的物品）
            config.set(path + ".material", item.getType().name());
            config.set(path + ".amount", item.getAmount());

            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    config.set(path + ".name", meta.getDisplayName());
                } else {
                    config.set(path + ".name", null);
                }
                if (meta.hasLore()) {
                    config.set(path + ".lore", meta.getLore());
                } else {
                    config.set(path + ".lore", null);
                }
            } else {
                config.set(path + ".name", null);
                config.set(path + ".lore", null);
            }

            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward item: " + e.getMessage());
        }
    }

    /**
     * 更新奖励类型
     */
    public void updateRewardType(String crateId, String rewardId, String type) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".type", type);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward type: " + e.getMessage());
        }
    }

    /**
     * 更新奖励显示图标（完整保存NBT，使用序列化）
     */
    public void updateRewardDisplayIconFull(String crateId, String rewardId, ItemStack item) {
        updateRewardDisplayIconFull(crateId, rewardId, item, true);
    }

    public void updateRewardDisplayIconFull(String crateId, String rewardId, ItemStack item, boolean manual) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            // 使用Bukkit序列化完整保存物品（包含所有NBT）
            String basePath = "rewards." + rewardId;
            config.set(basePath + ".display", item);
            if (manual) {
                config.set(basePath + ".display-auto.icon", false);
            }
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward display icon: " + e.getMessage());
        }
    }

    /**
     * 更新奖励显示图标（仅用于预览显示，不影响实际给予的物品）
     */
    public void updateRewardDisplayIcon(String crateId, String rewardId, ItemStack item) {
        updateRewardDisplayIcon(crateId, rewardId, item, true);
    }

    public void updateRewardDisplayIcon(String crateId, String rewardId, ItemStack item, boolean manual) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String basePath = "rewards." + rewardId;
            String path = basePath + ".display";

            // 只更新display配置
            config.set(path + ".material", item.getType().name());
            config.set(path + ".amount", item.getAmount());

            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    config.set(path + ".name", meta.getDisplayName());
                } else {
                    config.set(path + ".name", null);
                }
                if (meta.hasLore()) {
                    config.set(path + ".lore", meta.getLore());
                } else {
                    config.set(path + ".lore", null);
                }
            } else {
                config.set(path + ".name", null);
                config.set(path + ".lore", null);
            }
            if (manual) {
                config.set(basePath + ".display-auto.icon", false);
            }

            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward display icon: " + e.getMessage());
        }
    }

    /**
     * 更新奖励物品（完整保存NBT，使用序列化）
     */
    public void updateRewardItemFull(String crateId, String rewardId, ItemStack item) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String path = "rewards." + rewardId + ".item";

            // 使用Bukkit序列化完整保存物品（包含所有NBT）
            config.set(path, item);
            applyAutoDisplayFromFirstItem(config, "rewards." + rewardId, item);

            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward item: " + e.getMessage());
        }
    }

    /**
     * 清除奖励物品（使用显示图标作为奖励）
     */
    public void clearRewardItem(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".item", null);
            config.set("rewards." + rewardId + ".extra-items", null);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to clear reward item: " + e.getMessage());
        }
    }

    /**
     * 清除所有奖励物品
     */
    public void clearRewardItems(String crateId, String rewardId) {
        clearRewardItem(crateId, rewardId);
    }

    /**
     * 更新奖励物品（支持多个物品）
     */
    public void updateRewardItems(String crateId, String rewardId, List<ItemStack> items) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String basePath = "rewards." + rewardId;

            if (items.isEmpty()) {
                config.set(basePath + ".item", null);
                config.set(basePath + ".extra-items", null);
            } else {
                // 第一个物品作为主物品
                config.set(basePath + ".item", items.get(0));

                // 其余物品作为额外物品
                if (items.size() > 1) {
                    List<ItemStack> extraItems = items.subList(1, items.size());
                    config.set(basePath + ".extra-items", extraItems);
                } else {
                    config.set(basePath + ".extra-items", null);
                }
                applyAutoDisplayFromFirstItem(config, basePath, items.get(0));
            }

            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward items: " + e.getMessage());
        }
    }

    /**
     * 清除奖励命令
     */
    public void clearRewardCommands(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".commands", new ArrayList<String>());
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to clear reward commands: " + e.getMessage());
        }
    }

    /**
     * 添加奖励命令
     */
    public void addRewardCommand(String crateId, String rewardId, String command) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> commands = config.getStringList("rewards." + rewardId + ".commands");
            commands.add(command);
            config.set("rewards." + rewardId + ".commands", commands);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to add reward command: " + e.getMessage());
        }
    }

    /**
     * 更新奖励显示名称
     */
    public void updateRewardDisplayName(String crateId, String rewardId, String displayName) {
        updateRewardDisplayName(crateId, rewardId, displayName, true);
    }

    public void updateRewardDisplayName(String crateId, String rewardId, String displayName, boolean manual) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String basePath = "rewards." + rewardId;
            config.set(basePath + ".display-name", displayName);
            if (manual) {
                config.set(basePath + ".display-auto.name", false);
            }
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward display name: " + e.getMessage());
        }
    }

    /**
     * 恢复奖励显示自动同步状态
     */
    public void resetRewardDisplayIconAuto(String crateId, String rewardId) {
        resetRewardAutoDisplay(crateId, rewardId, true, false);
    }

    public void resetRewardDisplayNameAuto(String crateId, String rewardId) {
        resetRewardAutoDisplay(crateId, rewardId, false, true);
    }

    private void resetRewardAutoDisplay(String crateId, String rewardId, boolean icon, boolean name) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String basePath = "rewards." + rewardId;
            if (icon) {
                config.set(basePath + ".display-auto.icon", true);
            }
            if (name) {
                config.set(basePath + ".display-auto.name", true);
            }
            ItemStack firstItem = loadFirstRewardItem(config, basePath);
            if (hasRewardItem(firstItem) && plugin.getConfigManager().isRewardAutoDisplayFromFirstItemEnabled()) {
                if (icon && plugin.getConfigManager().isRewardAutoDisplayIconFromFirstItem()) {
                    applyAutoDisplayIcon(config, basePath, firstItem);
                }
                if (name && plugin.getConfigManager().isRewardAutoDisplayNameFromFirstItem()) {
                    applyAutoDisplayName(config, basePath, firstItem);
                }
            }
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to reset reward auto display: " + e.getMessage());
        }
    }

    /**
     * 获取奖励的广播状态
     */
    public boolean getRewardBroadcast(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return false;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getBoolean("rewards." + rewardId + ".broadcast", false);
    }

    /**
     * 获取奖励的命令列表
     */
    public List<String> getRewardCommands(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return new ArrayList<>();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getStringList("rewards." + rewardId + ".commands");
    }

    /**
     * 复制奖励
     */
    public String copyReward(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return null;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sourceSection = config.getConfigurationSection("rewards." + rewardId);
            if (sourceSection == null) return null;

            String newRewardId = "reward_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String newPath = "rewards." + newRewardId;

            // 复制所有配置
            for (String key : sourceSection.getKeys(true)) {
                Object value = sourceSection.get(key);
                if (!(value instanceof ConfigurationSection)) {
                    config.set(newPath + "." + key, value);
                }
            }

            config.save(file);
            loadCrates();
            return newRewardId;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to copy reward: " + e.getMessage());
            return null;
        }
    }

    /**
     * 平衡所有奖励概率使总和为100%
     */
    public void balanceRewardChances(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
            if (rewardsSection == null) return;

            Set<String> rewardIds = rewardsSection.getKeys(false);
            if (rewardIds.isEmpty()) return;

            // 计算当前总概率
            double totalChance = 0;
            for (String id : rewardIds) {
                totalChance += config.getDouble("rewards." + id + ".chance", 0);
            }

            if (totalChance <= 0) {
                // 如果总概率为0，平均分配
                double equalChance = 100.0 / rewardIds.size();
                for (String id : rewardIds) {
                    config.set("rewards." + id + ".chance", Math.round(equalChance * 100.0) / 100.0);
                }
            } else {
                // 按比例调整
                double ratio = 100.0 / totalChance;
                for (String id : rewardIds) {
                    double currentChance = config.getDouble("rewards." + id + ".chance", 0);
                    double newChance = currentChance * ratio;
                    config.set("rewards." + id + ".chance", Math.round(newChance * 100.0) / 100.0);
                }
            }

            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to balance reward chances: " + e.getMessage());
        }
    }

    private void applyAutoDisplayFromFirstItem(YamlConfiguration config, String basePath, ItemStack firstItem) {
        if (!hasRewardItem(firstItem)) {
            return;
        }

        boolean enabled = plugin.getConfigManager().isRewardAutoDisplayFromFirstItemEnabled();
        boolean onlyWhenNotCustomized = plugin.getConfigManager().isRewardAutoDisplayOnlyWhenNotCustomized();
        if (RewardAutoDisplayPolicy.shouldApply(enabled,
                plugin.getConfigManager().isRewardAutoDisplayIconFromFirstItem(),
                onlyWhenNotCustomized,
                config.getBoolean(basePath + ".display-auto.icon", true))) {
            applyAutoDisplayIcon(config, basePath, firstItem);
        }
        if (RewardAutoDisplayPolicy.shouldApply(enabled,
                plugin.getConfigManager().isRewardAutoDisplayNameFromFirstItem(),
                onlyWhenNotCustomized,
                config.getBoolean(basePath + ".display-auto.name", true))) {
            applyAutoDisplayName(config, basePath, firstItem);
        }
    }

    private void applyAutoDisplayIcon(YamlConfiguration config, String basePath, ItemStack firstItem) {
        ItemStack displayIcon = firstItem.clone();
        displayIcon.setAmount(1);
        config.set(basePath + ".display", displayIcon);
        config.set(basePath + ".display-auto.icon", true);
    }

    private void applyAutoDisplayName(YamlConfiguration config, String basePath, ItemStack firstItem) {
        config.set(basePath + ".display-name", resolveAutoDisplayName(firstItem));
        config.set(basePath + ".display-auto.name", true);
    }

    private ItemStack loadFirstRewardItem(YamlConfiguration config, String basePath) {
        ConfigurationSection rewardSection = config.getConfigurationSection(basePath);
        if (rewardSection == null) {
            return null;
        }
        String defaultName = rewardSection.getString("display-name", "reward");
        return loadOptionalRewardItem(rewardSection, defaultName);
    }

    private boolean hasRewardItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private String resolveAutoDisplayName(ItemStack item) {
        return RewardAutoDisplayNameResolver.resolve(item);
    }

    /**
     * 创建空奖励（用户先创建，再配置）
     * @return 新奖励的ID
     */
    public String createEmptyReward(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return null;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String rewardId = "reward_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String path = "rewards." + rewardId;

            config.set(path + ".type", "ITEM");
            config.set(path + ".chance", 10.0);
            config.set(path + ".rarity", plugin.getConfigManager().getDefaultRarityId());
            config.set(path + ".broadcast", false);
            config.set(path + ".display-name", "新奖励");

            // 设置默认显示物品为钻石
            config.set(path + ".display-auto.icon", true);
            config.set(path + ".display-auto.name", true);
            config.set(path + ".display.material", "DIAMOND");
            config.set(path + ".display.amount", 1);

            config.save(file);
            loadCrates();
            return rewardId;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to create empty reward: " + e.getMessage());
            return null;
        }
    }

    /**
     * 添加奖励（增强版，支持更多参数）
     */
    public String addRewardEnhanced(String crateId, ItemStack item, double chance, String rarity, String displayName) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return null;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String rewardId = "reward_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String path = "rewards." + rewardId;

            config.set(path + ".type", "item");
            config.set(path + ".chance", chance);
            config.set(path + ".rarity", rarity);
            config.set(path + ".broadcast", rarity.equalsIgnoreCase("legendary") || rarity.equalsIgnoreCase("epic"));
            boolean customDisplayName = displayName != null && !displayName.isEmpty();
            config.set(path + ".display-auto.icon", true);
            config.set(path + ".display-auto.name", !customDisplayName);

            // 设置显示名称
            String name = displayName;
            if (name == null || name.isEmpty()) {
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    name = item.getItemMeta().getDisplayName();
                } else {
                    name = formatMaterialName(item.getType().name());
                }
            }
            config.set(path + ".display-name", name);

            // 设置物品
            config.set(path + ".item.material", item.getType().name());
            config.set(path + ".item.amount", item.getAmount());
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    config.set(path + ".item.name", meta.getDisplayName());
                }
                if (meta.hasLore()) {
                    config.set(path + ".item.lore", meta.getLore());
                }
            }

            // 设置显示物品
            config.set(path + ".display.material", item.getType().name());
            config.set(path + ".display.amount", item.getAmount());
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    config.set(path + ".display.name", meta.getDisplayName());
                }
                if (meta.hasLore()) {
                    config.set(path + ".display.lore", meta.getLore());
                }
            }
            applyAutoDisplayFromFirstItem(config, path, item);

            config.save(file);
            loadCrates();
            return rewardId;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to add reward: " + e.getMessage());
            return null;
        }
    }

    /**
     * 格式化材质名称
     */
    private String formatMaterialName(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null ? RewardAutoDisplayPolicy.fallbackName(material) : materialName;
    }

    // ==================== 权限检测配置管理 ====================

    /**
     * 切换奖励权限检测开关
     */
    public void toggleRewardPermissionCheck(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean current = config.getBoolean("rewards." + rewardId + ".permission-check.enabled", false);
            config.set("rewards." + rewardId + ".permission-check.enabled", !current);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle reward permission check: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测开关状态
     */
    public boolean getRewardPermissionCheckEnabled(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return false;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getBoolean("rewards." + rewardId + ".permission-check.enabled", false);
    }

    /**
     * 更新奖励权限检测的权限节点
     */
    public void updateRewardCheckPermission(String crateId, String rewardId, String permission) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".permission-check.permission", permission);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward check permission: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测的权限节点
     */
    public String getRewardCheckPermission(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getString("rewards." + rewardId + ".permission-check.permission", null);
    }

    /**
     * 更新奖励权限检测的行为
     */
    public void updateRewardPermissionAction(String crateId, String rewardId, PermissionAction action) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".permission-check.action", action.name().toLowerCase());
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward permission action: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测的行为
     */
    public PermissionAction getRewardPermissionAction(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return PermissionAction.SKIP;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String action = config.getString("rewards." + rewardId + ".permission-check.action", "skip");
        return action.equalsIgnoreCase("alternative") ? PermissionAction.ALTERNATIVE : PermissionAction.SKIP;
    }

    /**
     * 更新奖励权限检测的替代奖励ID
     */
    public void updateRewardAlternativeReward(String crateId, String rewardId, String alternativeRewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards." + rewardId + ".permission-check.alternative-reward", alternativeRewardId);
            config.save(file);
            loadCrates();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward alternative reward: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测的替代奖励ID
     */
    public String getRewardAlternativeReward(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getString("rewards." + rewardId + ".permission-check.alternative-reward", null);
    }
}
