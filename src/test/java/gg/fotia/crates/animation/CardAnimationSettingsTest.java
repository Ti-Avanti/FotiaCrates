package gg.fotia.crates.animation;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardAnimationSettingsTest {

    @Test
    void readsConfiguredCardBackAndClampsRevealInterval() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.back-material", "BLACK_STAINED_GLASS_PANE");
        config.set("card-reveal.back-name", "<!i><gold>未知奖励");
        config.set("card-reveal.reveal-interval-ticks", 99);

        CardAnimationSettings settings = CardAnimationSettings.from(
                config.getConfigurationSection("card-reveal"));

        assertEquals(Material.BLACK_STAINED_GLASS_PANE, settings.backMaterial());
        assertEquals("<!i><gold>未知奖励", settings.backName());
        assertEquals(20, settings.revealIntervalTicks());
    }

    @Test
    void fallsBackForInvalidMaterial() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.back-material", "NOT_A_MATERIAL");

        assertEquals(Material.PURPLE_STAINED_GLASS_PANE,
                CardAnimationSettings.from(config.getConfigurationSection("card-reveal")).backMaterial());
    }
}
