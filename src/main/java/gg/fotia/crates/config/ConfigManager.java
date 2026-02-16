package gg.fotia.crates.config;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final FotiaCrates plugin;
    private FileConfiguration config;

    // 稀有度缓存
    private final Map<String, RarityConfig> rarities = new LinkedHashMap<>();

    public ConfigManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        saveDefaultCrate("common.yml");

        plugin.reloadConfig();
        config = plugin.getConfig();

        loadRarities();
    }

    /**
     * 加载稀有度配置
     */
    private void loadRarities() {
        rarities.clear();
        ConfigurationSection raritiesSection = config.getConfigurationSection("rarities");
        if (raritiesSection != null) {
            for (String id : raritiesSection.getKeys(false)) {
                ConfigurationSection raritySection = raritiesSection.getConfigurationSection(id);
                if (raritySection != null) {
                    String displayName = raritySection.getString("display-name", id);
                    String color = raritySection.getString("color", "white");
                    rarities.put(id, new RarityConfig(id, displayName, color));
                }
            }
        }

        // 如果没有配置，添加默认稀有度
        if (rarities.isEmpty()) {
            rarities.put("common", new RarityConfig("common", "<!i><gray>普通", "gray"));
            rarities.put("uncommon", new RarityConfig("uncommon", "<!i><green>稀有", "green"));
            rarities.put("rare", new RarityConfig("rare", "<!i><blue>精良", "blue"));
            rarities.put("epic", new RarityConfig("epic", "<!i><dark_purple>史诗", "dark_purple"));
            rarities.put("legendary", new RarityConfig("legendary", "<!i><gold>传说", "gold"));
        }
    }

    /**
     * 获取所有稀有度ID
     */
    public List<String> getRarityIds() {
        return new ArrayList<>(rarities.keySet());
    }

    /**
     * 获取稀有度配置
     */
    public RarityConfig getRarity(String id) {
        return rarities.get(id.toLowerCase());
    }

    /**
     * 获取稀有度显示名称
     */
    public String getRarityDisplayName(String id) {
        RarityConfig rarity = rarities.get(id.toLowerCase());
        return rarity != null ? rarity.getDisplayName() : id;
    }

    /**
     * 获取稀有度颜色
     */
    public String getRarityColor(String id) {
        RarityConfig rarity = rarities.get(id.toLowerCase());
        return rarity != null ? rarity.getColor() : "white";
    }

    /**
     * 获取所有稀有度配置
     */
    public Collection<RarityConfig> getAllRarities() {
        return rarities.values();
    }

    /**
     * 添加或更新稀有度
     */
    public void saveRarity(String id, String displayName, String color) {
        rarities.put(id.toLowerCase(), new RarityConfig(id.toLowerCase(), displayName, color));
        saveRaritiesToConfig();
    }

    /**
     * 删除稀有度
     */
    public void deleteRarity(String id) {
        rarities.remove(id.toLowerCase());
        saveRaritiesToConfig();
    }

    /**
     * 保存稀有度到配置文件
     */
    private void saveRaritiesToConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            // 清除旧的稀有度配置
            config.set("rarities", null);

            // 写入新的稀有度配置
            for (RarityConfig rarity : rarities.values()) {
                String path = "rarities." + rarity.getId();
                config.set(path + ".display-name", rarity.getDisplayName());
                config.set(path + ".color", rarity.getColor());
            }

            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save rarities: " + e.getMessage());
        }
    }

    /**
     * 获取下一个稀有度（用于循环切换）
     */
    public String getNextRarity(String currentRarity) {
        List<String> ids = getRarityIds();
        if (ids.isEmpty()) return currentRarity;

        int index = ids.indexOf(currentRarity.toLowerCase());
        if (index < 0) return ids.get(0);
        return ids.get((index + 1) % ids.size());
    }

    private void saveDefaultConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
    }

    private void saveDefaultCrate(String fileName) {
        File cratesFolder = new File(plugin.getDataFolder(), "crates");
        if (!cratesFolder.exists()) {
            cratesFolder.mkdirs();
        }
        File file = new File(cratesFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource("crates/" + fileName, false);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "fotiacrates");
    }

    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }

    public String getSQLiteFile() {
        return config.getString("database.sqlite.file", "data.db");
    }

    public boolean isDefaultAnimationEnabled() {
        return config.getBoolean("settings.default-animation", true);
    }

    public boolean isBroadcastRareRewards() {
        return config.getBoolean("settings.broadcast-rare-rewards", true);
    }

    public boolean isSaveHistory() {
        return config.getBoolean("settings.save-history", true);
    }

    public int getMaxHistoryEntries() {
        return config.getInt("settings.max-history-entries", 100);
    }

    /**
     * 稀有度配置类
     */
    public static class RarityConfig {
        private final String id;
        private final String displayName;
        private final String color;

        public RarityConfig(String id, String displayName, String color) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
}
