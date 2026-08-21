package gg.fotia.crates.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GuiItemDisplayParserTest {

    @Test
    void unavailableDisplayInheritsFieldsThatAreNotConfigured() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection display = config.createSection("display");
        display.set("material", "ARROW");
        display.set("name", "next");
        display.set("lore", List.of("line"));
        display.set("custom-model-data", 12);
        display.set("item-model", "fotia:next");
        display.set("glow", true);

        GuiItemDisplay available = GuiItemDisplayParser.parse(display, null);
        ConfigurationSection unavailableSection = display.createSection("unavailable");
        unavailableSection.set("material", "YELLOW_STAINED_GLASS_PANE");
        unavailableSection.set("name", "no next page");
        unavailableSection.set("glow", false);

        GuiItemDisplay unavailable = GuiItemDisplayParser.parse(unavailableSection, available);

        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, unavailable.material());
        assertEquals("no next page", unavailable.name());
        assertEquals(List.of("line"), unavailable.lore());
        assertEquals(12, unavailable.customModelData());
        assertEquals("fotia:next", unavailable.itemModel());
        assertFalse(unavailable.glow(), "Explicit false must override the available glow state");
    }
}
