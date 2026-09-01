package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationEditorActionTest {

    @Test
    void mapsEveryAnimationButtonToItsType() {
        assertEquals(AnimationType.ROULETTE,
                AnimationEditorAction.selectedType("select_roulette").orElseThrow());
        assertEquals(AnimationType.CARD_REVEAL,
                AnimationEditorAction.selectedType("select_card_reveal").orElseThrow());
        assertEquals(AnimationType.ORBITAL_CONVERGENCE,
                AnimationEditorAction.selectedType("select_orbital_convergence").orElseThrow());
        assertEquals(AnimationType.VOID_RIFT,
                AnimationEditorAction.selectedType("select_void_rift").orElseThrow());
        assertEquals(AnimationType.METEOR_JUDGMENT,
                AnimationEditorAction.selectedType("select_meteor_judgment").orElseThrow());
        assertEquals(AnimationType.INSTANT,
                AnimationEditorAction.selectedType("select_instant").orElseThrow());
        assertTrue(AnimationEditorAction.selectedType("preview_animation").isEmpty());
        assertTrue(AnimationEditorAction.selectedType("select_triple_reel").isEmpty());
    }
}
