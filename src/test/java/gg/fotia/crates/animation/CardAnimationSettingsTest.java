package gg.fotia.crates.animation;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardAnimationSettingsTest {

    @Test
    void readsConfiguredCardBackAndClampsRevealInterval() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.back-material", "BLACK_STAINED_GLASS_PANE");
        config.set("card-reveal.back-name", "<!i><gold>未知奖励");
        config.set("card-reveal.back-lore", List.of(
                "<!i><gray>点击翻开",
                "&e支持旧颜色码"
        ));
        config.set("card-reveal.back-custom-model-data", 321);
        config.set("card-reveal.back-item-model", "fotia:mystery_card");
        config.set("card-reveal.back-glow", true);
        config.set("card-reveal.reveal-interval-ticks", 99);

        CardAnimationSettings settings = CardAnimationSettings.from(
                config.getConfigurationSection("card-reveal"));

        CardBackDisplay back = settings.backDisplay();
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, back.material());
        assertEquals("<!i><gold>未知奖励", back.name());
        assertEquals(List.of("<!i><gray>点击翻开", "&e支持旧颜色码"), back.lore());
        assertEquals(321, back.customModelData());
        assertEquals("fotia:mystery_card", back.itemModel());
        assertTrue(back.glow());
        assertEquals(20, settings.revealIntervalTicks());
    }

    @Test
    void fallsBackForInvalidMaterial() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.back-material", "NOT_A_MATERIAL");

        assertEquals(Material.PURPLE_STAINED_GLASS_PANE,
                CardAnimationSettings.from(config.getConfigurationSection("card-reveal"))
                        .backDisplay().material());
    }

    @Test
    void supportsUnderscoreItemModelAliasAndClampsNegativeCustomModelData() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.back-item_model", "fotia:legacy_card");
        config.set("card-reveal.back-custom-model-data", -5);

        CardBackDisplay back = CardAnimationSettings.from(
                config.getConfigurationSection("card-reveal")).backDisplay();

        assertEquals("fotia:legacy_card", back.itemModel());
        assertEquals(0, back.customModelData());
    }

    @Test
    void bundledTemplateProvidesAVisibleCardBackHint() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/guis/animations/card-reveal.yml")) {
            assertNotNull(stream);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            CardBackDisplay back = CardAnimationSettings.from(
                    config.getConfigurationSection("card-reveal")).backDisplay();

            assertEquals(List.of("<!i><gray>点击选择这张卡牌"), back.lore());
        }
    }

    @Test
    void readsAndClampsInteractiveAnimationSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.card-count", 99);
        config.set("card-reveal.flicker-interval-ticks", 0);
        config.set("card-reveal.flicker-min-cycles", 99);
        config.set("card-reveal.flicker-min-ticks", 1);
        config.set("card-reveal.cover-delay-ticks", 999);
        config.set("card-reveal.selection-timeout-ticks", 20);
        config.set("card-reveal.page-transition-ticks", 0);
        config.set("card-reveal.result-hold-ticks", 999);
        config.set("card-reveal.status-slot", 99);
        config.set("card-reveal.status.select-name", "<!i><green>剩余 {remaining}/{total}");

        CardAnimationSettings settings = CardAnimationSettings.from(
                config.getConfigurationSection("card-reveal"));

        assertEquals(54, settings.cardCount());
        assertEquals(1, settings.flickerIntervalTicks());
        assertEquals(10, settings.flickerMinCycles());
        assertEquals(10, settings.flickerMinTicks());
        assertEquals(100, settings.coverDelayTicks());
        assertEquals(100, settings.selectionTimeoutTicks());
        assertEquals(5, settings.pageTransitionTicks());
        assertEquals(200, settings.resultHoldTicks());
        assertEquals(53, settings.statusSlot());
        assertEquals("<!i><green>剩余 {remaining}/{total}", settings.selectStatusName());
    }

    @Test
    void mapsLegacyShowcaseSettingsToFlickerSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("card-reveal.showcase-page-ticks", 25);
        config.set("card-reveal.shuffle-ticks", 35);
        config.set("card-reveal.status.showcase-name", "<!i><gold>旧展示");
        config.set("card-reveal.status.shuffle-name", "<!i><aqua>旧洗牌");

        CardAnimationSettings settings = CardAnimationSettings.from(
                config.getConfigurationSection("card-reveal"));

        assertEquals(60, settings.flickerMinTicks());
        assertEquals(35, settings.coverDelayTicks());
        assertEquals("<!i><gold>旧展示", settings.flickerStatusName());
        assertEquals("<!i><aqua>旧洗牌", settings.coverStatusName());
    }
}
