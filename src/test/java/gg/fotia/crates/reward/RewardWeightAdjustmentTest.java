package gg.fotia.crates.reward;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardWeightAdjustmentTest {
    @Test
    void editorInputAcceptsWeightsNotOnlyTwoDecimalPercentages() {
        assertEquals(0.0001, RewardWeightAdjustment.parse("0.0001"));
        assertEquals(1000.0, RewardWeightAdjustment.parse("1000"));
        assertEquals(0.0, RewardWeightAdjustment.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> RewardWeightAdjustment.parse("NaN"));
        assertThrows(IllegalArgumentException.class, () -> RewardWeightAdjustment.parse("Infinity"));
        assertThrows(IllegalArgumentException.class, () -> RewardWeightAdjustment.parse("-1"));
    }
    @Test
    void allZeroWeightsAreEqualAndSurviveYamlRoundTrip() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        for (String id : new String[]{"a", "b", "c"}) config.set(id + ".chance", 0.0);
        RewardWeightAdjustment.apply(config, false);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        assertEquals(100.0 / 3, reloaded.getDouble("a.chance"));
        assertEquals(100.0, reloaded.getKeys(false).stream()
                .mapToDouble(id -> reloaded.getDouble(id + ".chance")).sum(), 1e-12);
    }
    @Test
    void normalizationPreservesTinyPositiveWeightsAndRatios() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rare.chance", 1.0);
        config.set("common.chance", 99999.0);
        config.set("disabled.chance", 0.0);
        RewardWeightAdjustment.apply(config, false);
        assertEquals(0.001, config.getDouble("rare.chance"), 1e-12);
        assertEquals(99.999, config.getDouble("common.chance"), 1e-12);
        assertEquals(0.0, config.getDouble("disabled.chance"));
    }

    @Test
    void equalDistributionIsExplicitAndNormalizationUsesMissingWeightDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.createSection("a");
        config.set("b.chance", 30.0);
        RewardWeightAdjustment.apply(config, false);
        assertEquals(25.0, config.getDouble("a.chance"));
        assertEquals(75.0, config.getDouble("b.chance"));
        RewardWeightAdjustment.apply(config, true);
        assertEquals(50.0, config.getDouble("a.chance"));
        assertEquals(50.0, config.getDouble("b.chance"));
    }

    @Test
    void invalidWeightsCannotPoisonTheTotal() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("a.chance", Double.MAX_VALUE);
        config.set("b.chance", Double.MAX_VALUE);
        config.set("invalid.chance", Double.NaN);
        RewardWeightAdjustment.apply(config, false);
        assertEquals(50.0, config.getDouble("a.chance"));
        assertEquals(50.0, config.getDouble("b.chance"));
        assertEquals(0.0, config.getDouble("invalid.chance"));
    }
}
