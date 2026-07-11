package gg.fotia.crates.pity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PityResetPolicyTest {

    private static final List<String> RARITY_ORDER = List.of("common", "rare", "epic", "legendary");

    @Test
    void resetsOnAnEarlyQualifyingRewardWhenEnabled() {
        assertTrue(PityResetPolicy.shouldResetEarly(true, false, "rare", "rare", RARITY_ORDER));
        assertTrue(PityResetPolicy.shouldResetEarly(true, false, "legendary", "rare", RARITY_ORDER));
    }

    @Test
    void keepsTheCounterForNonQualifyingOrForcedRewards() {
        assertFalse(PityResetPolicy.shouldResetEarly(true, false, "common", "rare", RARITY_ORDER));
        assertFalse(PityResetPolicy.shouldResetEarly(true, true, "rare", "rare", RARITY_ORDER));
        assertFalse(PityResetPolicy.shouldResetEarly(false, false, "rare", "rare", RARITY_ORDER));
    }
}
