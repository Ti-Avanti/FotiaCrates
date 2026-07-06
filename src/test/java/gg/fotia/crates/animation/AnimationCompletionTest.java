package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationCompletionTest {

    @Test
    void completesOnceWhenAnimationTargetIsUnavailable() {
        AnimationCompletion completion = new AnimationCompletion();
        AtomicInteger completed = new AtomicInteger();

        assertTrue(completion.completeIfUnavailable(false, completed::incrementAndGet));
        assertFalse(completion.completeIfUnavailable(false, completed::incrementAndGet));

        assertEquals(1, completed.get());
        assertTrue(completion.isCompleted());
    }

    @Test
    void keepsRunningWhenAnimationTargetIsAvailable() {
        AnimationCompletion completion = new AnimationCompletion();
        AtomicInteger completed = new AtomicInteger();

        assertFalse(completion.completeIfUnavailable(true, completed::incrementAndGet));

        assertEquals(0, completed.get());
        assertFalse(completion.isCompleted());
    }
}
