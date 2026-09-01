package gg.fotia.crates.animation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrbitalAnimationSettingsTest {

    @Test
    void readsAndClampsTemplateSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("orbit.radius", 9.0);
        config.set("orbit.height", 0.1);
        config.set("orbit.item-count", 99);
        config.set("orbit.rotations", 0.2);

        OrbitalAnimationSettings settings = OrbitalAnimationSettings.from(
                config.getConfigurationSection("orbit"));

        assertEquals(3.0, settings.radius());
        assertEquals(0.5, settings.height());
        assertEquals(12, settings.itemCount());
        assertEquals(0.5, settings.rotations());
    }

    @Test
    void usesStableDefaultsWhenSectionIsMissing() {
        OrbitalAnimationSettings settings = OrbitalAnimationSettings.from(null);

        assertEquals(1.4, settings.radius());
        assertEquals(1.5, settings.height());
        assertEquals(8, settings.itemCount());
        assertEquals(2.5, settings.rotations());
    }
}
