package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripleReelScheduleTest {

    @Test
    void stopsReelsFromLeftToRightWithConfiguredGap() {
        TripleReelSchedule schedule = new TripleReelSchedule(100, 10);

        assertEquals(80, schedule.stopTick(0));
        assertEquals(90, schedule.stopTick(1));
        assertEquals(100, schedule.stopTick(2));
        assertTrue(schedule.isSpinning(0, 79));
        assertFalse(schedule.isSpinning(0, 80));
        assertTrue(schedule.isSpinning(2, 99));
        assertFalse(schedule.isSpinning(2, 100));
    }

    @Test
    void keepsShortAnimationsLongEnoughForThreeStops() {
        TripleReelSchedule schedule = new TripleReelSchedule(10, 10);

        assertTrue(schedule.totalTicks() >= 40);
        assertTrue(schedule.stopTick(0) > 0);
    }
}
