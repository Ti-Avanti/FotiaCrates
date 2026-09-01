package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldAnimationPathTest {

    @Test
    void voidRiftCandidatesConvergeAtTheRiftCenter() {
        VoidRiftAnimationSettings settings = VoidRiftAnimationSettings.from(null);
        VoidRiftPath.Point point = VoidRiftPath.sample(3, 7, 0.72, settings);

        assertEquals(0.0, point.x(), 0.0001);
        assertEquals(settings.riftHeight(), point.y(), 0.0001);
        assertEquals(0.0, point.z(), 0.0001);
    }

    @Test
    void meteorPathStartsAndEndsAtConfiguredPoints() {
        MeteorPath.Point start = new MeteorPath.Point(-4.0, 6.0, -2.0);
        MeteorPath.Point end = new MeteorPath.Point(0.0, 1.5, 0.0);

        assertEquals(start, MeteorPath.sample(start, end, 0.0));
        assertEquals(end, MeteorPath.sample(start, end, 1.0));
    }
}
