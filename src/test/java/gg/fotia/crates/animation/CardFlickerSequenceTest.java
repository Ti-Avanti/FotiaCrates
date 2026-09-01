package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardFlickerSequenceTest {

    @Test
    void coversEveryRewardAndKeepsEveryFrameFull() {
        List<List<Integer>> frames = CardFlickerSequence.create(
                23, 7, 1, 8, new Random(42));

        Set<Integer> seen = new HashSet<>();
        for (List<Integer> frame : frames) {
            assertEquals(7, frame.size());
            seen.addAll(frame);
        }
        assertEquals(23, seen.size());
        assertTrue(frames.size() >= 8);
    }
}
