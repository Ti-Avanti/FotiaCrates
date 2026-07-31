package gg.fotia.crates.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
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

    @Test
    void successfulPersistenceClearsAcknowledgedChanges() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();

        cache.load(playerId, Map.of("common_key", 1), Map.of("common", 0));
        cache.setVirtualKeys(playerId, "common_key", 2);
        cache.setPityCount(playerId, "common", 1);
        PlayerDataCache.Snapshot persisted = cache.snapshot(playerId);

        cache.markPersisted(playerId, persisted);

        PlayerDataCache.Snapshot current = cache.snapshot(playerId);
        assertTrue(current.changedKeys().isEmpty());
        assertTrue(current.changedCrates().isEmpty());
    }

    @Test
    void acknowledgingOldSnapshotKeepsNewerChangesDirty() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();

        cache.load(playerId, Map.of("common_key", 1), Map.of("common", 0));
        cache.setVirtualKeys(playerId, "common_key", 2);
        PlayerDataCache.Snapshot persisted = cache.snapshot(playerId);

        cache.setPityCount(playerId, "common", 1);
        cache.markPersisted(playerId, persisted);

        PlayerDataCache.Snapshot current = cache.snapshot(playerId);
        assertTrue(current.changedKeys().contains("common_key"));
        assertTrue(current.changedCrates().contains("common"));
    }

    @Test
    void distinguishesMissingPityEntryFromStoredZero() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();

        cache.load(playerId, Map.of(), Map.of("existing", 0));

        assertTrue(cache.hasPityCount(playerId, "existing"));
        assertFalse(cache.hasPityCount(playerId, "missing"));
    }

    @Test
    void collectedRewardsAreIdempotentAndRestoredWithTheOpenSnapshot() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();
        PlayerDataCache.RewardKey existing = new PlayerDataCache.RewardKey("common", "reward_a");

        cache.load(playerId, Map.of(), Map.of(), Set.of(existing));
        PlayerDataCache.Snapshot beforeOpen = cache.snapshot(playerId);

        assertTrue(cache.hasCollectedReward(playerId, "common", "reward_a"));
        assertFalse(cache.collectReward(playerId, "common", "reward_a"));
        assertTrue(cache.collectReward(playerId, "common", "reward_b"));
        assertEquals(Set.of("reward_a", "reward_b"), cache.getCollectedRewardIds(playerId, "common"));

        cache.restore(playerId, beforeOpen);

        assertEquals(Set.of("reward_a"), cache.getCollectedRewardIds(playerId, "common"));
        assertFalse(cache.hasCollectedReward(playerId, "common", "reward_b"));
        assertFalse(cache.snapshot(playerId).changedCollectedRewards().contains(
                new PlayerDataCache.RewardKey("common", "reward_b")));
    }

    @Test
    void successfulPersistenceClearsCollectedRewardChanges() {
        PlayerDataCache cache = new PlayerDataCache();
        UUID playerId = UUID.randomUUID();

        cache.load(playerId, Map.of(), Map.of(), Set.of());
        cache.collectReward(playerId, "common", "reward_a");
        PlayerDataCache.Snapshot persisted = cache.snapshot(playerId);

        assertEquals(Set.of(new PlayerDataCache.RewardKey("common", "reward_a")),
                persisted.changedCollectedRewards());

        cache.markPersisted(playerId, persisted);

        assertTrue(cache.snapshot(playerId).changedCollectedRewards().isEmpty());
    }
}
