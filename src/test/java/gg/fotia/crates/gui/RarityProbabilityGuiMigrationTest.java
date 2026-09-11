package gg.fotia.crates.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RarityProbabilityGuiMigrationTest {
    @Test void usesAvailableSlotAndKeepsExistingButtons() throws Exception {
        var config = new YamlConfiguration();
        config.set("size", 54);
        config.set("items.custom.slot", 46);
        config.set("items.custom.name", "Custom");
        assertTrue(RarityProbabilityGuiMigration.install(config, button()));
        assertEquals(47, config.getInt("items.rarity_probability.slot"));
        assertEquals("Custom", config.getString("items.custom.name"));
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        assertFalse(RarityProbabilityGuiMigration.install(reloaded, button()));
        assertEquals("rarity_probability", reloaded.getString("items.rarity_probability.action"));
    }

    @Test void addsLayoutShortcutWithoutOverwritingContentOrActions() {
        var config = new YamlConfiguration();
        config.set("layout", List.of("#########", "#R#B#####"));
        config.set("icons.R.content", true);
        config.set("icons.B.action", "back");
        config.set("icons.#.display.name", " ");
        assertTrue(RarityProbabilityGuiMigration.install(config, button()));
        String row = config.getStringList("layout").get(1);
        assertEquals('R', row.charAt(1));
        assertEquals('B', row.charAt(3));
        assertEquals('Q', row.charAt(2));
        assertEquals("rarity_probability", config.getString("icons.Q.action"));
    }

    @Test void keepsFullyOccupiedMenus() {
        var config = new YamlConfiguration();
        config.set("size", 9);
        config.set("content-slots", List.of(0,1,2,3,4,5,6,7,8));
        assertFalse(RarityProbabilityGuiMigration.install(config, button()));
    }

    private static ConfigurationSection button() {
        var button = new YamlConfiguration();
        button.set("slot", 46);
        button.set("material", "DIAMOND");
        button.set("name", "Quick probability");
        button.set("action", "rarity_probability");
        return button;
    }
}
