package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOpenAnimationPolicyTest {

    @Test
    void playsOnlyTheFirstDrawAnimationForARealMultiOpenWhenEnabled() {
        assertTrue(MultiOpenAnimationPolicy.shouldPlayFirstDrawAnimation(true, 10));
    }

    @Test
    void skipsTheAnimationWhenDisabledOrOnlyOneRewardWasPrepared() {
        assertFalse(MultiOpenAnimationPolicy.shouldPlayFirstDrawAnimation(false, 10));
        assertFalse(MultiOpenAnimationPolicy.shouldPlayFirstDrawAnimation(true, 1));
    }
}
