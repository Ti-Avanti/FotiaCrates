package gg.fotia.crates.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import gg.fotia.crates.reward.RarityProbabilityMode;

import static org.junit.jupiter.api.Assertions.*;

class RarityProbabilitySettingsTest {
    @Test void installsDefaultsWithoutOverwritingCustomSettings() {
        var defaults = new YamlConfiguration();
        String path = RarityProbabilitySettings.PATH;
        defaults.set(path + ".enabled", true);
        defaults.set(path + ".default-mode", "PER_REWARD");
        defaults.set(path + ".presets.epic", 1);
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        assertTrue(RarityProbabilitySettings.installDefaults(config));
        assertEquals(1, RarityProbabilitySettings.read(config).preset("epic"));
        config.set(path + ".enabled", false);
        config.set(path + ".presets.epic", .25);
        config.set(path + ".default-mode", "RARITY_TOTAL");
        assertFalse(RarityProbabilitySettings.installDefaults(config));
        var settings = RarityProbabilitySettings.read(config);
        assertFalse(settings.enabled());
        assertEquals(.25, settings.preset("epic"));
        assertEquals(RarityProbabilityMode.RARITY_TOTAL, settings.mode());
        assertNull(settings.preset("custom"));
    }
}
