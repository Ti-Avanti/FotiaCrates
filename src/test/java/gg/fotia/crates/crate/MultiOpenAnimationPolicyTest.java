package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOpenAnimationPolicyTest {

    @Test
    void playsOnlyTheFirstDrawAnimationForARealMultiOpenWhenEnabled() {
        assertTrue(MultiOpenAnimationPolicy.shouldPlayAnimation(true, 10));
    }

    @Test
    void skipsTheAnimationWhenDisabledOrOnlyOneRewardWasPrepared() {
        assertFalse(MultiOpenAnimationPolicy.shouldPlayAnimation(false, 10));
        assertFalse(MultiOpenAnimationPolicy.shouldPlayAnimation(true, 1));
    }
}
