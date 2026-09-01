package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardRevealScheduleTest {

    @Test
    void fitsRevealSequenceInsideConfiguredDuration() {
        CardRevealSchedule schedule = CardRevealSchedule.create(60, 9, 5, 20);

        assertEquals(4, schedule.revealIntervalTicks());
        assertTrue(schedule.completionTick() <= 65);
    }

    @Test
    void usesConfiguredIntervalAndDelaysRevealForLongAnimations() {
        CardRevealSchedule schedule = CardRevealSchedule.create(100, 9, 5, 20);

        assertEquals(5, schedule.revealIntervalTicks());
        assertEquals(35, schedule.startDelayTicks());
        assertEquals(100, schedule.completionTick());
    }
}
