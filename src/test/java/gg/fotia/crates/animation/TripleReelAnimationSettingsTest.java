package gg.fotia.crates.animation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripleReelAnimationSettingsTest {

    @Test
    void readsAndClampsTimingSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("triple-reel.stop-gap-ticks", 100);
        config.set("triple-reel.spin-interval-ticks", 0);

        TripleReelAnimationSettings settings = TripleReelAnimationSettings.from(
                config.getConfigurationSection("triple-reel"));

        assertEquals(40, settings.stopGapTicks());
        assertEquals(1, settings.spinIntervalTicks());
    }
}
