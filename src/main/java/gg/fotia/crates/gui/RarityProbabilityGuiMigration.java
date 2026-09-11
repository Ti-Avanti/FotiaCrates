package gg.fotia.crates.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class RarityProbabilityGuiMigration {
    private RarityProbabilityGuiMigration() {
    }

    public static boolean install(YamlConfiguration config, ConfigurationSection button) {
        if (button == null || config.getValues(true).values().stream()
                .anyMatch(value -> value instanceof String text && text.contains("rarity_probability"))) return false;
        List<String> layout = new ArrayList<>(config.getStringList("layout"));
        if (!layout.isEmpty()) {
            String symbols = String.join("", layout);
            String symbol = "QWERTYUIOPASDFGHJKLZXCVBNM".chars().mapToObj(c -> String.valueOf((char)c))
                    .filter(c -> !symbols.contains(c) && !config.contains("icons." + c)).findFirst().orElse(null);
            if (symbol == null) return false;
            int row = layout.size() - 1;
            for (int col : List.of(1, 2, 6, 0, 3, 4, 5, 7, 8)) {
                if (layout.get(row).length() <= col) continue;
                String old = String.valueOf(layout.get(row).charAt(col));
                ConfigurationSection icon = config.getConfigurationSection("icons." + old);
                if (icon != null && (icon.getBoolean("content") || icon.contains("action") || icon.contains("actions")
                        || !icon.getString("display.name", " ").isBlank())) continue;
                String line = layout.get(row);
                layout.set(row, line.substring(0, col) + symbol + line.substring(col + 1));
                config.set("layout", layout);
                var display = new java.util.HashMap<>(button.getValues(false));
                display.remove("slot");
                display.remove("action");
                config.createSection("icons." + symbol + ".display", display);
                config.set("icons." + symbol + ".action", "rarity_probability");
                return true;
            }
            return false;
        }
        var occupied = new HashSet<>(config.getIntegerList("content-slots"));
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) occupied.add(items.getInt(key + ".slot", -1));
        int size = config.getInt("size", 54);
        for (int offset : List.of(1, 2, 6, 0, 3, 4, 5, 7, 8)) {
            int slot = size - 9 + offset;
            if (slot < 0 || occupied.contains(slot)) continue;
            config.createSection("items.rarity_probability", button.getValues(false));
            config.set("items.rarity_probability.slot", slot);
            return true;
        }
        return false;
    }
}
