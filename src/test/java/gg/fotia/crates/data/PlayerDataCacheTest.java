package gg.fotia.crates.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataCacheTest {

    @Test
    void restoresThePreviousStateWhenPersistenceFails() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();

        cache.load(playerId, Map.of("common_key", 5), Map.of("common", 3));
        PlayerDataCache.Snapshot beforeOpen = cache.snapshot(playerId);

        cache.setVirtualKeys(playerId, "common_key", 4);
        cache.setPityCount(playerId, "common", 4);

        assertEquals(4, cache.getVirtualKeys(playerId, "common_key"));
        assertEquals(4, cache.getPityCount(playerId, "common"));

        cache.restore(playerId, beforeOpen);

        assertTrue(cache.isLoaded(playerId));
        assertEquals(5, cache.getVirtualKeys(playerId, "common_key"));
        assertEquals(3, cache.getPityCount(playerId, "common"));
    }

    @Test
    void unloadRemovesCachedState() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();

        cache.load(playerId, Map.of("common_key", 1), Map.of());
        cache.unload(playerId);

        assertFalse(cache.isLoaded(playerId));
        assertEquals(0, cache.getVirtualKeys(playerId, "common_key"));
    }
}
