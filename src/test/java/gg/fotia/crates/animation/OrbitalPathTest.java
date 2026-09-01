package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitalPathTest {

    @Test
    void contractsOrbitIntoFinalRewardPosition() {
        OrbitalPath.Point start = OrbitalPath.sample(0, 8, 0.0, 1.4, 1.5, 2.5);
        OrbitalPath.Point middle = OrbitalPath.sample(0, 8, 0.6, 1.4, 1.5, 2.5);
        OrbitalPath.Point end = OrbitalPath.sample(0, 8, 1.0, 1.4, 1.5, 2.5);

        assertTrue(start.radius() > middle.radius());
        assertTrue(middle.radius() > end.radius());
        assertEquals(0.0, end.radius(), 0.0001);
        assertEquals(0.0, end.x(), 0.0001);
        assertEquals(0.0, end.z(), 0.0001);
        assertTrue(end.y() > start.y());
    }

    @Test
    void distributesItemsEvenlyAroundInitialRing() {
        OrbitalPath.Point first = OrbitalPath.sample(0, 4, 0.0, 1.0, 1.5, 2.0);
        OrbitalPath.Point opposite = OrbitalPath.sample(2, 4, 0.0, 1.0, 1.5, 2.0);

        assertEquals(-first.x(), opposite.x(), 0.0001);
        assertEquals(-first.z(), opposite.z(), 0.0001);
    }
}
