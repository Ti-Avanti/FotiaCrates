package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnimationSlotResolverTest {

    @Test
    void cardSlotsAcceptAnyNonEmptyValidConfiguration() {
        AnimationSlotResolver.Resolution resolution = AnimationSlotResolver.forCards(
                List.of(3, 7, 11), 18, List.of(10, 12, 14));

        assertEquals(List.of(3, 7, 11), resolution.slots());
        assertFalse(resolution.usedFallback());
    }
}
