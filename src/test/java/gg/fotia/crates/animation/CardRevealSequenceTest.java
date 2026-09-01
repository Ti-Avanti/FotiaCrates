package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardRevealSequenceTest {

    @Test
    void revealsEveryCardOnceAndKeepsWinnerLast() {
        List<Integer> slots = List.of(10, 11, 12, 19, 20, 21, 28, 29, 30);

        List<Integer> order = CardRevealSequence.create(slots, 20, new Random(7));

        assertEquals(slots.size(), order.size());
        assertEquals(slots.stream().sorted().toList(), order.stream().sorted().toList());
        assertEquals(20, order.get(order.size() - 1));
    }

    @Test
    void usesMiddleSlotWhenConfiguredWinnerIsMissing() {
        List<Integer> order = CardRevealSequence.create(List.of(1, 2, 3), 99, new Random(1));

        assertEquals(2, order.get(order.size() - 1));
    }
}
