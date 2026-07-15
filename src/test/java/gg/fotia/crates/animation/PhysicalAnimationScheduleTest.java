package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalAnimationScheduleTest {

    @Test
    void precomputesRemainingScrollsForConstantTimeTickLookups() {
        PhysicalAnimationSchedule schedule = new PhysicalAnimationSchedule(200);
        int observedScrolls = 0;

        for (int tick = 0; tick < 200; tick++) {
            if (schedule.shouldScroll(tick)) {
                observedScrolls++;
            }
        }

        assertTrue(observedScrolls > 0);
        assertEquals(observedScrolls, schedule.remainingScrolls(0));
        assertEquals(0, schedule.remainingScrolls(200));

        for (int tick = 0; tick < 200; tick++) {
            int expectedNext = schedule.remainingScrolls(tick)
                    - (schedule.shouldScroll(tick) ? 1 : 0);
            assertEquals(expectedNext, schedule.remainingScrolls(tick + 1));
        }
    }

    @Test
    void clampsConfiguredAnimationDuration() {
        assertEquals(1, PhysicalAnimationSchedule.clampDurationSeconds(0));
        assertEquals(15, PhysicalAnimationSchedule.clampDurationSeconds(15));
        assertEquals(30, PhysicalAnimationSchedule.clampDurationSeconds(120));
    }
}
