package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationTemplate;
import gg.fotia.crates.animation.AnimationType;
import gg.fotia.crates.config.CrateConfigurationStore;
import gg.fotia.crates.particle.CrateParticleEffect;
import gg.fotia.crates.particle.ParticleCompat;
import gg.fotia.crates.particle.ParticleEffectMode;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.particle.ParticleTarget;
import gg.fotia.crates.reward.*;
import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

public class CrateManager {

    private final FotiaCrates plugin;
    private final CrateConfigurationStore configurationStore;
    private final Map<String, Crate> crates = new HashMap<>();
    private final CrateLocationIndex crateLocations = new CrateLocationIndex();
    private final NamespacedKey crateBlockKey;

    public CrateManager(FotiaCrates plugin) {
        this.plugin = plugin;
        this.configurationStore = new CrateConfigurationStore(plugin);
        this.crateBlockKey = new NamespacedKey(plugin, "crate_block");
    }

    public void loadCrates() {
        configurationStore.reset();
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

    public Crate reloadCrate(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) {
            crates.remove(crateId);
            return null;
        }

        try {
            Crate crate = loadCrate(crateId, file);
            if (crate == null) {
                crates.remove(crateId);
                return null;
            }
            crates.put(crateId, crate);
            return crate;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Failed to reload crate " + crateId + ": " + exception.getMessage());
            return null;
        }
    }

    private Crate loadCrate(String id, File file) {
        YamlConfiguration config = configurationStore.read(file);
        return loadCrate(id, file, config);
    }

    public Crate reloadCrate(String crateId, YamlConfiguration config) {
        configurationStore.remember(new File(plugin.getDataFolder(), "crates/" + crateId + ".yml"), config);
        Crate crate = loadCrate(crateId, new File(plugin.getDataFolder(), "crates/" + crateId + ".yml"), config);
        crates.put(crateId, crate);
        return crate;
    }

    private Crate loadCrate(String id, File file, YamlConfiguration config) {

        String name = config.getString("name", id);
        Material blockMaterial = Material.valueOf(config.getString("block.material", "CHEST"));
        ItemStack blockItemTemplate = config.getItemStack("block.item.template");

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
        boolean legacyShowChance = config.getBoolean("preview.show-chance", true);
        PreviewChanceDisplayMode previewChanceDisplayMode = PreviewChanceDisplayMode.fromConfig(
                config.getString("preview.chance-display"), legacyShowChance);
        PreviewSortMode previewSortMode = PreviewSortMode.fromConfig(
                config.getString("preview.sort-mode"));
        String previewTitle = config.getString("preview.title", name + " Preview");

        boolean animationEnabled = config.getBoolean("animation.enabled",
                plugin.getConfigManager().isDefaultAnimationEnabled());
        AnimationType animationType = loadAnimationType(config, file);
        String animationTemplate = config.getString("animation.template", "default");
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
                    // count 最小为 1，防止 0/负数触发保底取余除零
                    int count = Math.max(1, tierSection.getInt("count", 50));
                    String rarity = tierSection.getString("rarity", plugin.getConfigManager().getDefaultPityRarityId());
                    pityTiers.add(new Crate.PityTier(tierKey, count, rarity));
                }
            }
        }
        // 兼容旧版单级保底配置
        if (!hasPityTiersNode && pityTiers.isEmpty() && pityEnabled) {
            int pityCount = Math.max(1, config.getInt("pity.count", 50));
            String pityRarity = config.getString("pity.rarity", plugin.getConfigManager().getDefaultPityRarityId());
            pityTiers.add(new Crate.PityTier("legacy", pityCount, pityRarity));
        }
        boolean resetPityOnEarlyQualifyingReward = config.getBoolean("pity.reset-on-early-qualifying-reward", false);

        boolean multiOpenEnabled = config.getBoolean("multi-open.enabled", true);
        int multiOpenMax = Math.max(1, Math.min(config.getInt("multi-open.max", 10),
                plugin.getConfigManager().getMultiOpenHardLimit()));
        boolean multiOpenAnimationEnabled = config.getBoolean("multi-open.animation.enabled", false);

        boolean uniqueDrawEnabled = config.getBoolean("unique-draw.enabled", false);
        boolean replaceObtainedInPreview = config.getBoolean(
                "unique-draw.preview.replace-obtained", true);
        Material obtainedIconMaterial = Material.matchMaterial(config.getString(
                "unique-draw.preview.obtained-icon.material", "BARRIER"));
        if (obtainedIconMaterial == null || !obtainedIconMaterial.isItem()) {
            obtainedIconMaterial = Material.BARRIER;
        }
        int obtainedIconCustomModelData = Math.max(0, config.getInt(
                "unique-draw.preview.obtained-icon.custom-model-data", 0));
        String obtainedIconItemModel = config.getString(
                "unique-draw.preview.obtained-icon.item-model", "");
        UniqueDrawSettings uniqueDrawSettings = new UniqueDrawSettings(
                uniqueDrawEnabled,
                replaceObtainedInPreview,
                new UniqueDrawSettings.ObtainedIcon(
                        obtainedIconMaterial,
                        obtainedIconCustomModelData,
                        obtainedIconItemModel
                )
        );

        // 权限节点，默认为空（留空则不检测开箱权限）
        String permission = config.getString("permission", "");

        List<Reward> rewards = loadRewards(config.getConfigurationSection("rewards"));

        return new Crate(id, name, blockMaterial, blockItemTemplate,
                blockItemName, blockItemLore,
                modelProvider, modelEngineEnabled, modelEngineId,
                modelEngineIdleAnimation, modelEngineOpenAnimation,
                modelEngineOpenDelay, modelEngineViewRange, physicalAnimationHeight,
                hologramHeight, hologramLines,
                rewards,
                previewEnabled, previewChanceDisplayMode, previewSortMode, previewTitle,
                animationEnabled, animationType, animationTemplate, animationDuration,
                animationTitle, physicalAnimationEnabled,
                particlesEnabled, particleType, particleCount, particleEffects,
                spinSound, spinVolume, spinPitch,
                winSound, winVolume, winPitch,
                pityEnabled, pityTiers, resetPityOnEarlyQualifyingReward,
                multiOpenEnabled, multiOpenMax, multiOpenAnimationEnabled,
                uniqueDrawSettings, permission,
                plugin.getConfigManager().getRarityIds());
    }

    private AnimationType loadAnimationType(YamlConfiguration config, File file) {
        String configured = config.getString("animation.type", "ROULETTE");
        AnimationType type;
        try {
            type = AnimationType.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown animation type " + configured
                    + " in " + file.getName() + "; using ROULETTE.");
            return AnimationType.ROULETTE;
        }
        if (type != AnimationType.TRIPLE_REEL) {
            return type;
        }

        config.set("animation.type", AnimationType.CARD_REVEAL.name());
        if ("triple-reel".equalsIgnoreCase(config.getString("animation.template", ""))) {
            config.set("animation.template", "card-reveal");
        }
        try {
            configurationStore.save(config, file);
            plugin.getLogger().info("Migrated removed TRIPLE_REEL animation to CARD_REVEAL in "
                    + file.getName() + ".");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not persist animation migration for "
                    + file.getName() + ": " + exception.getMessage());
        }
        return AnimationType.CARD_REVEAL;
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

        // 实际奖励物品不能继承奖励标题，否则自动同步会再次读回旧标题。
        return loadItemFromSection(itemSection, null);
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
        if (item == null && RewardItemSource.usesLegacyDisplay(section)) {
            // 未配置 item 时回退到显示图标，保证抽中后仍有物品可发
            item = loadDisplayItem(section, null);
        }
        List<ItemStack> extraItems = loadExtraItems(section);
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
            crateLocations.put(location);
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to save crate location: " + exception.getMessage());
        }
    }

    public void removeLocation(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM crate_locations WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.executeUpdate();
            crateLocations.remove(world, x, y, z);
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to remove crate location: " + exception.getMessage());
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

    public List<CrateLocation> getCrateLocationsInChunk(String world, int chunkX, int chunkZ) {
        return crateLocations.inChunk(world, chunkX, chunkZ);
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
        ItemStack baseItem = CrateBlockItemTemplate.createBase(
                crate.getBlockItemTemplate(), crate.getBlockMaterial());
        ItemStack item = new ItemBuilder(baseItem)
                .name(crate.getBlockItemName())
                .lore(crate.getBlockItemLore())
                .amount(amount)
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    crateBlockKey,
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
                crateBlockKey,
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    // ==================== 编辑功能 ====================

    /**
     * 更新宝箱名称
     */
    public void updateCrateName(String crateId, String newName) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("name", newName);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update crate name: " + e.getMessage());
        }
    }

    /**
     * 更新宝箱方块类型
     */
    public void updateCrateBlock(String crateId, Material material) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("block.material", material.name());
            config.set("block.item.template", null);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update crate block: " + e.getMessage());
        }
    }

    /**
     * 更新宝箱本体物品，保留 CraftEngine 等插件写入的自定义组件。
     */
    public void updateCrateBlockItem(String crateId, ItemStack item) {
        ItemStack storedItem = CrateBlockItemTemplate.copyForStorage(item);
        if (storedItem == null) return;

        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("block.material", storedItem.getType().name());
            config.set("block.item.template", storedItem);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update crate block item: " + e.getMessage());
        }
    }

    /**
     * 更新动画时长
     */
    public void updateAnimationDuration(String crateId, int duration) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("animation.duration", duration);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update animation duration: " + e.getMessage());
        }
    }

    public void updateAnimationTemplate(String crateId, String templateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("animation.template", templateId);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update animation template: " + e.getMessage());
        }
    }

    /**
     * 更新多连抽最大数量
     */
    public void updateMultiOpenMax(String crateId, int max) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("multi-open.max", Math.max(1,
                    Math.min(max, plugin.getConfigManager().getMultiOpenHardLimit())));
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update multi-open max: " + e.getMessage());
        }
    }

    /**
     * 切换粒子效果
     */
    public void toggleParticles(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("particles.enabled", true);
            config.set("particles.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            updater.accept(config);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("preview.enabled", true);
            config.set("preview.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle preview: " + e.getMessage());
        }
    }

    /**
     * 切换多连抽功能
     */
    public void toggleMultiOpen(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("multi-open.enabled", true);
            config.set("multi-open.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle multi-open: " + e.getMessage());
        }
    }

    public void toggleUniqueDraw(String crateId) {
        updateUniqueDrawConfig(crateId, config -> {
            boolean current = config.getBoolean("unique-draw.enabled", false);
            config.set("unique-draw.enabled", !current);
        }, "toggle unique draw");
    }

    public void toggleUniquePreviewReplacement(String crateId) {
        updateUniqueDrawConfig(crateId, config -> {
            boolean current = config.getBoolean("unique-draw.preview.replace-obtained", true);
            config.set("unique-draw.preview.replace-obtained", !current);
        }, "toggle unique preview replacement");
    }

    public void updateUniqueObtainedIconMaterial(String crateId, Material material) {
        if (material == null || !material.isItem()) {
            return;
        }
        updateUniqueDrawConfig(crateId,
                config -> config.set("unique-draw.preview.obtained-icon.material", material.name()),
                "update unique obtained icon material");
    }

    public void updateUniqueObtainedIconCustomModelData(String crateId, int customModelData) {
        updateUniqueDrawConfig(crateId,
                config -> config.set("unique-draw.preview.obtained-icon.custom-model-data",
                        Math.max(0, customModelData)),
                "update unique obtained icon custom model data");
    }

    public void updateUniqueObtainedIconItemModel(String crateId, String itemModel) {
        updateUniqueDrawConfig(crateId,
                config -> config.set("unique-draw.preview.obtained-icon.item-model",
                        itemModel == null ? "" : itemModel.trim().toLowerCase(Locale.ROOT)),
                "update unique obtained icon item model");
    }

    public void resetUniqueObtainedIcon(String crateId) {
        updateUniqueDrawConfig(crateId, config -> {
            config.set("unique-draw.preview.obtained-icon.material", "BARRIER");
            config.set("unique-draw.preview.obtained-icon.custom-model-data", 0);
            config.set("unique-draw.preview.obtained-icon.item-model", "");
        }, "reset unique obtained icon");
    }

    private void updateUniqueDrawConfig(String crateId, Consumer<YamlConfiguration> updater,
                                        String actionName) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) {
            return;
        }
        try {
            YamlConfiguration config = configurationStore.read(file);
            updater.accept(config);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to " + actionName + ": " + e.getMessage());
        }
    }

    public void cyclePreviewChanceDisplayMode(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            PreviewChanceDisplayMode current = PreviewChanceDisplayMode.fromConfig(
                    config.getString("preview.chance-display"),
                    config.getBoolean("preview.show-chance", true));
            PreviewChanceDisplayMode next = current.next();
            config.set("preview.chance-display", next.name());
            config.set("preview.show-chance", next != PreviewChanceDisplayMode.HIDDEN);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update preview chance display mode: " + e.getMessage());
        }
    }

    public void cyclePreviewSortMode(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            PreviewSortMode current = PreviewSortMode.fromConfig(
                    config.getString("preview.sort-mode"));
            config.set("preview.sort-mode", current.next().name());
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update preview sort mode: " + e.getMessage());
        }
    }

    public void toggleMultiOpenAnimation(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("multi-open.animation.enabled", false);
            config.set("multi-open.animation.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle multi-open animation: " + e.getMessage());
        }
    }

    /**
     * 添加奖励
     */
    public void addReward(String crateId, ItemStack item, double chance, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
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

            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to add reward: " + e.getMessage());
        }
    }

    /**
     * 移除奖励
     */
    public void removeReward(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId, null);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to remove reward: " + e.getMessage());
        }
    }

    /**
     * 更新奖励概率
     */
    public void updateRewardChance(String crateId, String rewardId, double chance) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".chance", chance);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward chance: " + e.getMessage());
        }
    }

    /**
     * 更新奖励稀有度
     */
    public void updateRewardRarity(String crateId, String rewardId, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".rarity", rarity);
            config.set("rewards." + rewardId + ".broadcast", rarity.equalsIgnoreCase("legendary") || rarity.equalsIgnoreCase("epic"));
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward rarity: " + e.getMessage());
        }
    }

    /**
     * 更新动画类型
     */
    public void updateAnimationType(String crateId, AnimationType type) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("animation.type", type.name());
            AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                    .getAnimationTemplate(config.getString("animation.template"), type);
            if (template != null) {
                config.set("animation.template", template.id());
            }
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update animation type: " + e.getMessage());
        }
    }

    /**
     * 切换GUI动画开关
     */
    public void toggleGuiAnimation(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("animation.enabled", true);
            config.set("animation.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle GUI animation: " + e.getMessage());
        }
    }

    /**
     * 切换物理动画开关
     */
    public void togglePhysicalAnimation(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("physical-animation.enabled", false);
            config.set("physical-animation.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle physical animation: " + e.getMessage());
        }
    }

    /**
     * 更新物理动画高度
     */
    public void updatePhysicalAnimationHeight(String crateId, double height) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("physical-animation.height", height);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update physical animation height: " + e.getMessage());
        }
    }

    /**
     * 切换保底启用状态
     */
    public void togglePity(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("pity.enabled", false);
            config.set("pity.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle pity: " + e.getMessage());
        }
    }

    public void togglePityEarlyReset(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("pity.reset-on-early-qualifying-reward", false);
            config.set("pity.reset-on-early-qualifying-reward", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle early pity reset: " + e.getMessage());
        }
    }

    /**
     * 添加保底等级
     */
    public void addPityTier(String crateId, int count, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            String tierId = "tier_" + System.currentTimeMillis();
            config.set("pity.tiers." + tierId + ".count", count);
            config.set("pity.tiers." + tierId + ".rarity", rarity);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to add pity tier: " + e.getMessage());
        }
    }

    /**
     * 更新保底等级
     */
    public void updatePityTier(String crateId, int tierIndex, int count, String rarity) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            Crate crate = crates.get(crateId);
            if (crate == null || tierIndex < 0 || tierIndex >= crate.getPityTiers().size()) return;

            YamlConfiguration config = configurationStore.read(file);
            ConfigurationSection tiersSection = config.getConfigurationSection("pity.tiers");
            if (tiersSection == null) return;

            String tierKey = crate.getPityTiers().get(tierIndex).getId();
            if (!tiersSection.contains(tierKey)) return;
            config.set("pity.tiers." + tierKey + ".count", count);
            config.set("pity.tiers." + tierKey + ".rarity", rarity);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update pity tier: " + e.getMessage());
        }
    }

    /**
     * 删除保底等级
     */
    public void removePityTier(String crateId, int tierIndex) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            Crate crate = crates.get(crateId);
            if (crate == null || tierIndex < 0 || tierIndex >= crate.getPityTiers().size()) return;

            YamlConfiguration config = configurationStore.read(file);
            ConfigurationSection tiersSection = config.getConfigurationSection("pity.tiers");
            if (tiersSection == null) return;

            String tierKey = crate.getPityTiers().get(tierIndex).getId();
            if (!tiersSection.contains(tierKey)) return;
            config.set("pity.tiers." + tierKey, null);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("pity.enabled", enabled);
            config.set("pity.count", count);
            config.set("pity.rarity", rarity);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update pity: " + e.getMessage());
        }
    }

    /**
     * 保存宝箱配置
     */
    public boolean isConfigSavePending() {
        return configurationStore.isSaving();
    }

    public void shutdownConfigurationStore() {
        configurationStore.shutdown();
    }

    public void saveCrate(String crateId) {
        // 配置已经在每次修改时保存，这里只是重新加载确保同步
        reloadCrate(crateId);
    }

    /**
     * 删除宝箱
     */
    public void deleteCrate(String crateId) {
        List<CrateLocation> removedLocations = crateLocations.values().stream()
                .filter(location -> Objects.equals(crateId, location.getCrateId()))
                .toList();
        plugin.getModelEngineManager().removeCrateModels(removedLocations);
        for (CrateLocation crateLocation : removedLocations) {
            org.bukkit.World world = plugin.getServer().getWorld(crateLocation.getWorld());
            if (world != null && world.isChunkLoaded(Math.floorDiv(crateLocation.getX(), 16),
                    Math.floorDiv(crateLocation.getZ(), 16))) {
                plugin.getHologramManager().removeHologram(crateLocation.toLocation(world));
            }
        }

        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        try {
            configurationStore.delete(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to queue crate deletion: " + exception.getMessage());
            return;
        }
        crates.remove(crateId);

        plugin.getAsyncPlayerDataManager().executeDatabaseOperation(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM crate_locations WHERE crate_id = ?")) {
                statement.setString(1, crateId);
                statement.executeUpdate();
            }
            return null;
        }, null, exception -> plugin.getLogger().severe("Failed to delete crate locations: " + exception.getMessage()));

        crateLocations.removeByCrateId(crateId);
    }

    /**
     * 创建新宝箱
     */
    public void createCrate(String crateId, String name) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("name", name);
            config.set("block.material", "CHEST");
            config.set("block.item.name", "<!i><gold>" + name);
            config.set("block.item.lore", List.of("<!i><gray>放置此方块创建宝箱"));
            config.set("preview.enabled", true);
            config.set("preview.show-chance", true);
            config.set("preview.chance-display", PreviewChanceDisplayMode.PERCENTAGE.name());
            config.set("preview.sort-mode", PreviewSortMode.CONFIG_ORDER.name());
            config.set("animation.enabled", true);
            config.set("animation.type", "ROULETTE");
            config.set("animation.template", "default");
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
            config.set("unique-draw.enabled", false);
            config.set("unique-draw.preview.replace-obtained", true);
            config.set("unique-draw.preview.obtained-icon.material", "BARRIER");
            config.set("unique-draw.preview.obtained-icon.custom-model-data", 0);
            config.set("unique-draw.preview.obtained-icon.item-model", "");
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("rewards." + rewardId + ".broadcast", false);
            config.set("rewards." + rewardId + ".broadcast", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle reward broadcast: " + e.getMessage());
        }
    }

    /**
     * 更新奖励物品（实际给予玩家的物品）
     */
    public void updateRewardItem(String crateId, String rewardId, ItemStack item) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
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

            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward item: " + e.getMessage());
        }
    }

    /**
     * 更新奖励类型
     */
    public void updateRewardType(String crateId, String rewardId, String type) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".type", type);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            // 使用Bukkit序列化完整保存物品（包含所有NBT）
            String basePath = "rewards." + rewardId;
            config.set(basePath + ".display", item);
            if (manual) {
                config.set(basePath + ".display-auto.icon", false);
            }
            syncDisplayNameFromFirstItem(config, basePath);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
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

            syncDisplayNameFromFirstItem(config, basePath);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward display icon: " + e.getMessage());
        }
    }

    /**
     * 更新奖励物品（完整保存NBT，使用序列化）
     */
    public void updateRewardItemFull(String crateId, String rewardId, ItemStack item) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            String path = "rewards." + rewardId + ".item";

            // 使用Bukkit序列化完整保存物品（包含所有NBT）
            config.set(path, item);
            applyAutoDisplayFromFirstItem(config, "rewards." + rewardId, item);

            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward item: " + e.getMessage());
        }
    }

    /**
     * 清除奖励物品（使用显示图标作为奖励）
     */
    public void clearRewardItem(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".item", new ItemStack(Material.AIR));
            config.set("rewards." + rewardId + ".extra-items", null);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            String basePath = "rewards." + rewardId;

            if (items.isEmpty()) {
                config.set(basePath + ".item", new ItemStack(Material.AIR));
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

            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward items: " + e.getMessage());
        }
    }

    /**
     * 清除奖励命令
     */
    public void clearRewardCommands(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".commands", new ArrayList<String>());
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to clear reward commands: " + e.getMessage());
        }
    }

    /**
     * 添加奖励命令
     */
    public void addRewardCommand(String crateId, String rewardId, String command) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            List<String> commands = config.getStringList("rewards." + rewardId + ".commands");
            commands.add(command);
            config.set("rewards." + rewardId + ".commands", commands);
            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            String basePath = "rewards." + rewardId;
            config.set(basePath + ".display-name", displayName);
            if (manual) {
                config.set(basePath + ".display-auto.name", false);
            }
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward display name: " + e.getMessage());
        }
    }

    /**
     * 恢复奖励显示自动同步状态
     */
    public boolean resetRewardDisplayIconAuto(String crateId, String rewardId) {
        return resetRewardAutoDisplay(crateId, rewardId, true, false);
    }

    public boolean resetRewardDisplayNameAuto(String crateId, String rewardId) {
        return resetRewardAutoDisplay(crateId, rewardId, false, true);
    }

    private boolean resetRewardAutoDisplay(String crateId, String rewardId, boolean icon, boolean name) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return false;

        try {
            YamlConfiguration config = configurationStore.read(file);
            String basePath = "rewards." + rewardId;
            if (icon) {
                config.set(basePath + ".display-auto.icon", true);
            }
            if (name) {
                config.set(basePath + ".display-auto.name", true);
            }
            ItemStack firstItem = loadFirstRewardItem(config, basePath);
            boolean synced = false;
            if (hasRewardItem(firstItem) && plugin.getConfigManager().isRewardAutoDisplayFromFirstItemEnabled()) {
                if (icon && plugin.getConfigManager().isRewardAutoDisplayIconFromFirstItem()) {
                    applyAutoDisplayIcon(config, basePath, firstItem);
                    synced = true;
                }
                if (name && plugin.getConfigManager().isRewardAutoDisplayNameFromFirstItem()) {
                    applyAutoDisplayName(config, basePath, firstItem);
                    synced = true;
                }
            }
            configurationStore.save(config, file);
            reloadCrate(crateId);
            return synced;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to reset reward auto display: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取奖励的广播状态（从内存缓存读取，配置修改会经 reloadCrate 同步）
     */
    public boolean getRewardBroadcast(String crateId, String rewardId) {
        Reward reward = findReward(crateId, rewardId);
        return reward != null && reward.shouldBroadcast();
    }

    /**
     * 获取奖励的命令列表
     */
    public List<String> getRewardCommands(String crateId, String rewardId) {
        Reward reward = findReward(crateId, rewardId);
        return reward == null ? new ArrayList<>() : new ArrayList<>(reward.getCommands());
    }

    private Reward findReward(String crateId, String rewardId) {
        Crate crate = getCrate(crateId);
        if (crate == null || rewardId == null) {
            return null;
        }
        for (Reward reward : crate.getRewards()) {
            if (rewardId.equals(reward.getId())) {
                return reward;
            }
        }
        return null;
    }

    /**
     * 复制奖励
     */
    public String copyReward(String crateId, String rewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return null;

        try {
            YamlConfiguration config = configurationStore.read(file);
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

            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        adjustRewardWeights(crateId, false);
    }

    public boolean adjustRewardWeights(String crateId, boolean equalize) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return false;

        try {
            YamlConfiguration config = configurationStore.read(file);
            if (!RewardWeightAdjustment.apply(config.getConfigurationSection("rewards"), equalize)) return false;

            configurationStore.save(config, file);
            reloadCrate(crateId);
            return true;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to balance reward chances: " + e.getMessage());
            return false;
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
        String name = resolveAutoDisplayName(firstItem);
        config.set(basePath + ".display-name", name);
        config.set(basePath + ".display-auto.name", true);
        ConfigurationSection section = config.getConfigurationSection(basePath);
        ItemStack icon = loadDisplayItem(section, null);
        config.set(basePath + ".display", new ItemBuilder(icon).name(name).build());
    }

    private void syncDisplayNameFromFirstItem(YamlConfiguration config, String basePath) {
        ConfigurationSection section = config.getConfigurationSection(basePath);
        if (section != null && RewardAutoDisplayPolicy.shouldApply(
                plugin.getConfigManager().isRewardAutoDisplayFromFirstItemEnabled(),
                plugin.getConfigManager().isRewardAutoDisplayNameFromFirstItem(),
                plugin.getConfigManager().isRewardAutoDisplayOnlyWhenNotCustomized(),
                section.getBoolean("display-auto.name", true))) {
            ItemStack first = loadFirstRewardItem(config, basePath);
            if (hasRewardItem(first)) applyAutoDisplayName(config, basePath, first);
        }
    }

    private ItemStack loadFirstRewardItem(YamlConfiguration config, String basePath) {
        ConfigurationSection rewardSection = config.getConfigurationSection(basePath);
        if (rewardSection == null) {
            return null;
        }
        ItemStack main = loadOptionalRewardItem(rewardSection, null);
        if (main == null && RewardItemSource.usesLegacyDisplay(rewardSection)) {
            main = loadDisplayItem(rewardSection, null);
        }
        return RewardItemSource.first(main, loadExtraItems(rewardSection));
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
        if (!configurationStore.exists(file)) return null;

        try {
            YamlConfiguration config = configurationStore.read(file);
            String rewardId = "reward_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String path = "rewards." + rewardId;

            config.set(path + ".type", "ITEM");
            config.set(path + ".chance", 10.0);
            config.set(path + ".rarity", plugin.getConfigManager().getDefaultRarityId());
            config.set(path + ".broadcast", false);
            config.set(path + ".display-name", "新奖励");
            // 新建奖励显式为空；缺少 item 的旧配置仍保留图标奖励回退。
            config.set(path + ".item", new ItemStack(Material.AIR));

            // 设置默认显示物品为钻石
            config.set(path + ".display-auto.icon", true);
            config.set(path + ".display-auto.name", true);
            config.set(path + ".display.material", "DIAMOND");
            config.set(path + ".display.amount", 1);

            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return null;

        try {
            YamlConfiguration config = configurationStore.read(file);
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

            configurationStore.save(config, file);
            reloadCrate(crateId);
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
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            boolean current = config.getBoolean("rewards." + rewardId + ".permission-check.enabled", false);
            config.set("rewards." + rewardId + ".permission-check.enabled", !current);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to toggle reward permission check: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测开关状态（内存读取）
     */
    public boolean getRewardPermissionCheckEnabled(String crateId, String rewardId) {
        Reward reward = findReward(crateId, rewardId);
        return reward != null && reward.isPermissionCheckEnabled();
    }

    /**
     * 更新奖励权限检测的权限节点
     */
    public void updateRewardCheckPermission(String crateId, String rewardId, String permission) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".permission-check.permission", permission);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward check permission: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测的权限节点（内存读取）
     */
    public String getRewardCheckPermission(String crateId, String rewardId) {
        Reward reward = findReward(crateId, rewardId);
        return reward == null ? null : reward.getCheckPermission();
    }

    /**
     * 更新奖励权限检测的行为
     */
    public void updateRewardPermissionAction(String crateId, String rewardId, PermissionAction action) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".permission-check.action", action.name().toLowerCase());
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward permission action: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测的行为（内存读取）
     */
    public PermissionAction getRewardPermissionAction(String crateId, String rewardId) {
        Reward reward = findReward(crateId, rewardId);
        return reward == null ? PermissionAction.SKIP : reward.getPermissionAction();
    }

    /**
     * 更新奖励权限检测的替代奖励ID
     */
    public void updateRewardAlternativeReward(String crateId, String rewardId, String alternativeRewardId) {
        File file = new File(plugin.getDataFolder(), "crates/" + crateId + ".yml");
        if (!configurationStore.exists(file)) return;

        try {
            YamlConfiguration config = configurationStore.read(file);
            config.set("rewards." + rewardId + ".permission-check.alternative-reward", alternativeRewardId);
            configurationStore.save(config, file);
            reloadCrate(crateId);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to update reward alternative reward: " + e.getMessage());
        }
    }

    /**
     * 获取奖励权限检测的替代奖励ID（内存读取）
     */
    public String getRewardAlternativeReward(String crateId, String rewardId) {
        Reward reward = findReward(crateId, rewardId);
        return reward == null ? null : reward.getAlternativeRewardId();
    }
}
