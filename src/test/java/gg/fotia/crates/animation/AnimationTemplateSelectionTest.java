package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
