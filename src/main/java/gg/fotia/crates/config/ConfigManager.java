package gg.fotia.crates.config;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {

    private final FotiaCrates plugin;
    private FileConfiguration config;
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

    private void loadRarities() {
        rarities.clear();

        ConfigurationSection raritiesSection = config.getConfigurationSection("rarities");
        if (raritiesSection != null) {
            for (String id : raritiesSection.getKeys(false)) {
                ConfigurationSection raritySection = raritiesSection.getConfigurationSection(id);
                if (raritySection == null) {
                    continue;
                }

                String normalizedId = id.toLowerCase(Locale.ROOT);
                String displayName = raritySection.getString("display-name", normalizedId);
                String color = raritySection.getString("color", "white");
                rarities.put(normalizedId, new RarityConfig(normalizedId, displayName, color));
            }
        }

        if (rarities.isEmpty()) {
            addDefaultRarities();
        }
    }

    private void addDefaultRarities() {
        rarities.put("common", new RarityConfig("common", "<!i><gray>普通", "gray"));
        rarities.put("uncommon", new RarityConfig("uncommon", "<!i><green>稀有", "green"));
        rarities.put("rare", new RarityConfig("rare", "<!i><blue>精良", "blue"));
        rarities.put("epic", new RarityConfig("epic", "<!i><dark_purple>史诗", "dark_purple"));
        rarities.put("legendary", new RarityConfig("legendary", "<!i><gold>传说", "gold"));
        rarities.put("mythic", new RarityConfig("mythic", "<!i><light_purple>神话", "light_purple"));
    }

    public List<String> getRarityIds() {
        return new ArrayList<>(rarities.keySet());
    }

    public String getDefaultRarityId() {
        return rarities.isEmpty() ? "common" : getRarityIds().get(0);
    }

    public String getDefaultPityRarityId() {
        List<String> rarityIds = getRarityIds();
        if (rarityIds.isEmpty()) {
            return "rare";
        }

        return rarityIds.get(Math.min(2, rarityIds.size() - 1));
    }

    public String getHighestRarityId() {
        List<String> rarityIds = getRarityIds();
        if (rarityIds.isEmpty()) {
            return getDefaultPityRarityId();
        }

        return rarityIds.get(rarityIds.size() - 1);
    }

    public int getRarityIndex(String id) {
        if (id == null) {
            return -1;
        }

        int index = 0;
        for (String rarityId : rarities.keySet()) {
            if (rarityId.equalsIgnoreCase(id)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public RarityConfig getRarity(String id) {
        if (id == null) {
            return null;
        }
        return rarities.get(id.toLowerCase(Locale.ROOT));
    }

    public String getRarityDisplayName(String id) {
        RarityConfig rarity = getRarity(id);
        return rarity != null ? rarity.getDisplayName() : id;
    }

    public String getRarityColor(String id) {
        RarityConfig rarity = getRarity(id);
        return rarity != null ? rarity.getColor() : "white";
    }

    public Collection<RarityConfig> getAllRarities() {
        return rarities.values();
    }

    public void saveRarity(String id, String displayName, String color) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        rarities.put(normalizedId, new RarityConfig(normalizedId, displayName, color));
        saveRaritiesToConfig();
    }

    public void deleteRarity(String id) {
        if (id == null) {
            return;
        }

        rarities.remove(id.toLowerCase(Locale.ROOT));
        saveRaritiesToConfig();
    }

    private void saveRaritiesToConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            config.set("rarities", null);

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

    public String getNextRarity(String currentRarity) {
        List<String> ids = getRarityIds();
        if (ids.isEmpty()) {
            return currentRarity;
        }

        int index = getRarityIndex(currentRarity);
        if (index < 0) {
            return ids.get(0);
        }

        return ids.get((index + 1) % ids.size());
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

    public boolean isOverflowMailEnabled() {
        return config.getBoolean("overflow-mail.enabled", true);
    }

    public String getOverflowMailIcon() {
        return config.getString("overflow-mail.icon", "CHEST");
    }

    public String getOverflowMailSenderDisplay() {
        return config.getString("overflow-mail.sender-display", "<!i><gold>FotiaCrates");
    }

    public String getOverflowMailTitle() {
        return config.getString("overflow-mail.title", "<!i><yellow>抽奖奖励补发");
    }

    public List<String> getOverflowMailContent() {
        return config.getStringList("overflow-mail.content");
    }

    public static class RarityConfig {
        private final String id;
        private final String displayName;
        private final String color;

        public RarityConfig(String id, String displayName, String color) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }
    }
}
