package gg.fotia.crates.pity;

import gg.fotia.crates.crate.Crate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PityProgressKeyTest {

    @Test
    void stableTierKeyDoesNotChangeWhenThresholdChanges() {
        Crate.PityTier before = new Crate.PityTier("tier_1", 50, "rare");
        Crate.PityTier after = new Crate.PityTier("tier_1", 100, "rare");

        assertEquals(
                PityProgressKey.stableKey("common", before),
                PityProgressKey.stableKey("common", after)
        );
    }

    @Test
    void stableTierKeySeparatesCratesAndTierIdsWithinDatabaseLimit() {
        String first = PityProgressKey.stableKey(
                "common", new Crate.PityTier("tier_1", 50, "rare"));
        String second = PityProgressKey.stableKey(
                "common", new Crate.PityTier("tier_2", 50, "rare"));
        String third = PityProgressKey.stableKey(
                "rare", new Crate.PityTier("tier_1", 50, "rare"));

        assertNotEquals(first, second);
        assertNotEquals(first, third);
        assertTrue(first.length() <= 96);
    }

    @Test
    void legacyTierKeyStillMatchesPreviousStorageFormat() {
        assertEquals("common#t50", PityProgressKey.legacyKey("common", 50));
    }
}
