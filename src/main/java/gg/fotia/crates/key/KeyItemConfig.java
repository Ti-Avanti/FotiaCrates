package gg.fotia.crates.key;

import org.bukkit.configuration.ConfigurationSection;

final class KeyItemConfig {

    private KeyItemConfig() {
    }

    static String readCraftEngineId(ConfigurationSection itemSection) {
        String itemId = normalized(itemSection.getString("craftengine-id"));
        if (itemId != null) {
            return itemId;
        }
        return normalized(itemSection.getString("craft-engine-id"));
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
