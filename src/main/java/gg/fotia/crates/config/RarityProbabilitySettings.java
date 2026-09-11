package gg.fotia.crates.config;

import gg.fotia.crates.reward.RarityProbabilityMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

public record RarityProbabilitySettings(boolean enabled, RarityProbabilityMode mode, ConfigurationSection presets) {
    public static final String PATH = "editor.rewards.rarity-probability";

    public static RarityProbabilitySettings read(ConfigurationSection config) {
        RarityProbabilityMode mode;
        try {
            mode = RarityProbabilityMode.valueOf(config.getString(PATH + ".default-mode", "PER_REWARD")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            mode = RarityProbabilityMode.PER_REWARD;
        }
        return new RarityProbabilitySettings(config.getBoolean(PATH + ".enabled", true), mode,
                config.getConfigurationSection(PATH + ".presets"));
    }

    public Double preset(String rarity) {
        if (presets == null) return null;
        Object value = presets.get(rarity.toLowerCase(Locale.ROOT));
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        return Double.isFinite(result) && result >= 0 && result <= 100 ? result : null;
    }

    public static boolean installDefaults(FileConfiguration config) {
        if (config.getDefaults() == null) return false;
        boolean changed = false;
        for (String field : List.of("enabled", "default-mode", "presets")) {
            String path = PATH + "." + field;
            if (config.isSet(path)) continue;
            Object value = config.getDefaults().get(path);
            if (value == null) continue;
            if (value instanceof ConfigurationSection section) config.createSection(path, section.getValues(false));
            else config.set(path, value);
            config.setComments(path, config.getDefaults().getComments(path));
            changed = true;
        }
        return changed;
    }
}
