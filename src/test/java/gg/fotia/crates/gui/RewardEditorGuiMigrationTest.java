package gg.fotia.crates.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewardEditorGuiMigrationTest {
    @Test
    void upgradesLegacySingleRewardPercentageInputLabels() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.chance.action", "adjust_chance");
        config.set("items.chance.name", "<!i><yellow>概率: <!i><white>{chance}%");
        config.set("items.chance.lore", List.of("<!i><yellow>左键 +1% / 右键 -1%", "<!i><yellow>Shift = 5%"));
        assertTrue(RewardEditorGuiMigration.applyInput(config));
        assertEquals("<!i><yellow>权重: <!i><white>{chance}", config.getString("items.chance.name"));
        assertEquals("<!i><gray>玩家展示概率: <!i><white>{probability}", config.getStringList("items.chance.lore").get(0));
        assertFalse(RewardEditorGuiMigration.applyInput(config));
    }
    @Test
    void upgradesOldLabelsAndIsIdempotent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.info.lore", List.of("<!i><gray>总概率: {total_chance}%"));
        config.set("items.balance.action", "balance_chances");
        config.set("items.balance.name", "<!i><gold>概率平衡");
        config.set("items.balance.lore", List.of("<!i><gray>自动调整所有奖励概率到 100%"));
        assertTrue(RewardEditorGuiMigration.apply(config, true));
        assertEquals(List.of("<!i><gray>总权重: {total_weight}"), config.getStringList("items.info.lore"));
        assertTrue(config.getStringList("reward-display.lore").stream().anyMatch(line -> line.contains("{probability}")));
        assertFalse(RewardEditorGuiMigration.apply(config, true));
    }

    @Test
    void keepsCustomDisplayAndButtonLayout() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.balance.action", "balance_chances");
        config.set("items.balance.slot", 2);
        config.set("items.balance.name", "&aCustom");
        config.set("items.balance.lore", List.of("&eCustom instructions"));
        config.set("reward-display.lore", List.of());
        assertFalse(RewardEditorGuiMigration.apply(config, true));
        assertEquals(2, config.getInt("items.balance.slot"));
        assertEquals("&aCustom", config.getString("items.balance.name"));
        assertEquals(List.of(), config.getStringList("reward-display.lore"));
    }
}
