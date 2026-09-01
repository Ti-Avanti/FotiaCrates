package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationTypeTest {

    @Test
    void exposesThreeNewAnimationTypes() {
        assertEquals("TRIPLE_REEL", AnimationType.valueOf("TRIPLE_REEL").name());
        assertEquals("CARD_REVEAL", AnimationType.valueOf("CARD_REVEAL").name());
        assertEquals("ORBITAL_CONVERGENCE", AnimationType.valueOf("ORBITAL_CONVERGENCE").name());
    }

    @Test
    void keepsLegacyRouletteAliasesInRouletteTemplateFamily() {
        assertEquals(AnimationType.ROULETTE, AnimationType.CSGO.templateFamily());
        assertEquals(AnimationType.ROULETTE, AnimationType.PHYSICAL.templateFamily());
        assertEquals(AnimationType.CARD_REVEAL, AnimationType.CARD_REVEAL.templateFamily());
    }
}
