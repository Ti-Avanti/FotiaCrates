package gg.fotia.crates.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyMessageMigrationTest {
    @Test
    void upgradesOldDefaultsAndAddsConfigurableMessages() {
        for (String old : new String[]{"<!i><red>你没有足够的钥匙！", "<!i><red>You don't have enough keys!"}) {
            var config = new YamlConfiguration();
            config.set("messages.no-key", old);
            var bundled = defaults();
            assertTrue(KeyMessageMigration.apply(config, bundled));
            assertEquals("Missing {keys}", config.getString("messages.no-key"));
            assertEquals(" or ", config.getString("messages.no-key-separator"));
            assertFalse(KeyMessageMigration.apply(config, bundled));
        }
    }

    @Test
    void preservesCustomAndDisabledMessages() {
        for (String custom : new String[]{"&cCustom", ""}) {
            var config = new YamlConfiguration();
            config.set("messages.no-key", custom);
            config.set("messages.no-key-detail", "");
            KeyMessageMigration.apply(config, defaults());
            assertEquals(custom, config.getString("messages.no-key"));
            assertEquals("", config.getString("messages.no-key-detail"));
        }
    }

    private static YamlConfiguration defaults() {
        var config = new YamlConfiguration();
        config.set("messages.no-key", "Missing {keys}");
        config.set("messages.no-key-detail", "Need {keys}");
        config.set("messages.no-key-separator", " or ");
        config.set("messages.no-key-configured", "No keys configured for {crate}");
        return config;
    }
}
