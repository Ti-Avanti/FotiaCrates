package gg.fotia.crates.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main-thread cache for the persistent key and pity values of online players.
 */
public final class PlayerDataCache {

    private final Map<UUID, State> states = new HashMap<>();

    public boolean isLoaded(UUID playerId) {
        return states.containsKey(playerId);
    }

    public void load(UUID playerId, Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts) {
        states.put(playerId, new State(copyNonNegative(virtualKeys), copyNonNegative(pityCounts)));
    }

    public void unload(UUID playerId) {
        states.remove(playerId);
    }

    public int getVirtualKeys(UUID playerId, String keyId) {
        State state = states.get(playerId);
        return state == null ? 0 : state.virtualKeys.getOrDefault(keyId, 0);
    }

    public void setVirtualKeys(UUID playerId, String keyId, int amount) {
        State state = requireState(playerId);
        state.virtualKeys.put(keyId, Math.max(0, amount));
    }

    public int getPityCount(UUID playerId, String crateId) {
        State state = states.get(playerId);
        return state == null ? 0 : state.pityCounts.getOrDefault(crateId, 0);
    }

    public void setPityCount(UUID playerId, String crateId, int count) {
        State state = requireState(playerId);
        state.pityCounts.put(crateId, Math.max(0, count));
    }

    public Snapshot snapshot(UUID playerId) {
        State state = requireState(playerId);
        return new Snapshot(state.virtualKeys, state.pityCounts);
    }

    public void restore(UUID playerId, Snapshot snapshot) {
        states.put(playerId, new State(snapshot.virtualKeys(), snapshot.pityCounts()));
    }

    private State requireState(UUID playerId) {
        State state = states.get(playerId);
        if (state == null) {
            throw new IllegalStateException("Player data is not loaded: " + playerId);
        }
        return state;
    }

    private Map<String, Integer> copyNonNegative(Map<String, Integer> values) {
        Map<String, Integer> copy = new HashMap<>();
        if (values == null) {
            return copy;
        }
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey(), Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
            }
        }
        return copy;
    }

    private static final class State {
        private final Map<String, Integer> virtualKeys;
        private final Map<String, Integer> pityCounts;

        private State(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts) {
            this.virtualKeys = virtualKeys;
            this.pityCounts = pityCounts;
        }
    }

    public record Snapshot(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts) {

        public Snapshot {
            virtualKeys = Map.copyOf(virtualKeys);
            pityCounts = Map.copyOf(pityCounts);
        }
    }
}
