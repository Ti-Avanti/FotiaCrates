package gg.fotia.crates.key;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyItemConfigTest {

    @Test
    void readsDocumentedCraftEngineId() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("item:\n  craftengine-id: payitem:alipay\n");

        assertEquals("payitem:alipay",
                KeyItemConfig.readCraftEngineId(config.getConfigurationSection("item")));
    }

    @Test
    void readsHyphenatedCraftEngineIdAlias() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("item:\n  craft-engine-id: payitem:alipay\n");

        assertEquals("payitem:alipay",
                KeyItemConfig.readCraftEngineId(config.getConfigurationSection("item")));
    }
}
