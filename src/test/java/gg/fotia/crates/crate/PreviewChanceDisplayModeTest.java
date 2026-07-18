package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewChanceDisplayModeTest {

    @Test
    void loadsExplicitDisplayModeCaseInsensitively() {
        assertEquals(PreviewChanceDisplayMode.WEIGHT,
                PreviewChanceDisplayMode.fromConfig("weight", true));
    }

    @Test
    void fallsBackToLegacyShowChanceSetting() {
        assertEquals(PreviewChanceDisplayMode.PERCENTAGE,
                PreviewChanceDisplayMode.fromConfig(null, true));
        assertEquals(PreviewChanceDisplayMode.HIDDEN,
                PreviewChanceDisplayMode.fromConfig("", false));
    }

    @Test
    void cyclesThroughAllDisplayModes() {
        assertEquals(PreviewChanceDisplayMode.WEIGHT, PreviewChanceDisplayMode.PERCENTAGE.next());
        assertEquals(PreviewChanceDisplayMode.HIDDEN, PreviewChanceDisplayMode.WEIGHT.next());
        assertEquals(PreviewChanceDisplayMode.PERCENTAGE, PreviewChanceDisplayMode.HIDDEN.next());
    }
}
