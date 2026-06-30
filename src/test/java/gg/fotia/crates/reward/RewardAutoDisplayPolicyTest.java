package gg.fotia.crates.reward;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardAutoDisplayPolicyTest {

    @Test
    void respectsGlobalAndManualOverrideFlags() {
        assertTrue(RewardAutoDisplayPolicy.shouldApply(true, true, true, true));
        assertFalse(RewardAutoDisplayPolicy.shouldApply(false, true, true, true));
        assertFalse(RewardAutoDisplayPolicy.shouldApply(true, false, true, true));
        assertFalse(RewardAutoDisplayPolicy.shouldApply(true, true, true, false));
        assertTrue(RewardAutoDisplayPolicy.shouldApply(true, true, false, false));
    }

    @Test
    void formatsMaterialFallbackName() {
        assertEquals("Diamond Sword", RewardAutoDisplayPolicy.fallbackName(Material.DIAMOND_SWORD));
        assertEquals("Glowstone Dust", RewardAutoDisplayPolicy.fallbackName(Material.GLOWSTONE_DUST));
    }
}
