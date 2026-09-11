package gg.fotia.crates.reward;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardItemSourceTest {
    @Test
    void onlyLegacyItemRewardsMayUseTheDisplayAsTheirItem() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "ITEM");
        assertTrue(RewardItemSource.usesLegacyDisplay(config));
        config.set("item.material", "AIR");
        assertFalse(RewardItemSource.usesLegacyDisplay(config));
        config.set("item", null);
        config.set("type", "COMMAND");
        assertFalse(RewardItemSource.usesLegacyDisplay(config));
    }
}
