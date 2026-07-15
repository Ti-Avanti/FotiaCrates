package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CrateLocationIndexTest {

    @Test
    void replacesAndRemovesLocationsByBlockPosition() {
        CrateLocationIndex index = new CrateLocationIndex();
        CrateLocation original = new CrateLocation("world", 10, 64, 10, "basic");
        CrateLocation replacement = new CrateLocation("world", 10, 64, 10, "rare");

        index.put(original);
        assertSame(original, index.get("world", 10, 64, 10));

        index.put(replacement);
        assertEquals(1, index.size());
        assertSame(replacement, index.get("world", 10, 64, 10));

        assertSame(replacement, index.remove("world", 10, 64, 10));
        assertNull(index.get("world", 10, 64, 10));
    }

    @Test
    void returnsStableSnapshotsAndNearbyLocationsOnly() {
        CrateLocationIndex index = new CrateLocationIndex();
        CrateLocation center = new CrateLocation("world", 0, 64, 0, "center");
        CrateLocation edge = new CrateLocation("world", 16, 70, 0, "edge");
        CrateLocation far = new CrateLocation("world", 33, 64, 0, "far");
        CrateLocation otherWorld = new CrateLocation("world_nether", 0, 64, 0, "nether");

        index.put(center);
        List<CrateLocation> snapshot = index.values();
        index.put(edge);
        index.put(far);
        index.put(otherWorld);

        assertEquals(List.of(center), snapshot);
        assertEquals(List.of(center, edge), index.nearby("world", 0, 0, 16.0));

        index.removeByCrateId("edge");
        assertEquals(List.of(center), index.nearby("world", 0, 0, 32.0));
    }
}
