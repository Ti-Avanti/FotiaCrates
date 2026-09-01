package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationEditorActionTest {

    @Test
    void mapsEveryAnimationButtonToItsType() {
        assertEquals(AnimationType.ROULETTE,
                AnimationEditorAction.selectedType("select_roulette").orElseThrow());
        assertEquals(AnimationType.TRIPLE_REEL,
                AnimationEditorAction.selectedType("select_triple_reel").orElseThrow());
        assertEquals(AnimationType.CARD_REVEAL,
                AnimationEditorAction.selectedType("select_card_reveal").orElseThrow());
        assertEquals(AnimationType.ORBITAL_CONVERGENCE,
                AnimationEditorAction.selectedType("select_orbital_convergence").orElseThrow());
        assertEquals(AnimationType.INSTANT,
                AnimationEditorAction.selectedType("select_instant").orElseThrow());
        assertTrue(AnimationEditorAction.selectedType("preview_animation").isEmpty());
    }
}
