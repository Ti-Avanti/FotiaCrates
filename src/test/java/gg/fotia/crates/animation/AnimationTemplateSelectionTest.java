package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationTemplateSelectionTest {

    @Test
    void normalizesConfiguredIdsAndKeepsExistingTemplate() {
        assertEquals("golden", AnimationTemplateSelection.resolve(" Golden ",
                List.of("default", "golden")));
    }

    @Test
    void fallsBackDeterministicallyWhenConfiguredTemplateIsMissing() {
        assertEquals("default", AnimationTemplateSelection.resolve("removed",
                List.of("golden", "default")));
        assertEquals("golden", AnimationTemplateSelection.resolve("removed",
                List.of("roulette", "golden")));
        assertEquals("default", AnimationTemplateSelection.resolve("removed", List.of()));
    }

    @Test
    void resolvesOnlyTemplatesCompatibleWithSelectedAnimation() {
        Map<String, AnimationType> templates = Map.of(
                "default", AnimationType.ROULETTE,
                "golden", AnimationType.ROULETTE,
                "card-reveal", AnimationType.CARD_REVEAL,
                "void-rift", AnimationType.VOID_RIFT
        );

        assertEquals("card-reveal", AnimationTemplateSelection.resolve(
                "golden", templates, AnimationType.TRIPLE_REEL));
        assertEquals("golden", AnimationTemplateSelection.resolve(
                "golden", templates, AnimationType.CSGO));
        assertEquals("default", AnimationTemplateSelection.resolve(
                "missing", templates, AnimationType.ORBITAL_CONVERGENCE));
        assertEquals("void-rift", AnimationTemplateSelection.resolve(
                "missing", templates, AnimationType.VOID_RIFT));
    }
}
