package gg.fotia.crates.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        state.changedKeys.add(keyId);
        state.revision++;
    }

    public int getPityCount(UUID playerId, String crateId) {
        State state = states.get(playerId);
        return state == null ? 0 : state.pityCounts.getOrDefault(crateId, 0);
    }

    public boolean hasPityCount(UUID playerId, String crateId) {
        State state = states.get(playerId);
        return state != null && state.pityCounts.containsKey(crateId);
    }

    public Map<String, Integer> getPityCountsByPrefix(UUID playerId, String prefix) {
        State state = states.get(playerId);
        if (state == null) {
            return Map.of();
        }
        Map<String, Integer> matches = new HashMap<>();
        for (Map.Entry<String, Integer> entry : state.pityCounts.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                matches.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(matches);
    }

    public void setPityCount(UUID playerId, String crateId, int count) {
        State state = requireState(playerId);
        state.pityCounts.put(crateId, Math.max(0, count));
        state.changedCrates.add(crateId);
        state.revision++;
    }

    public Snapshot snapshot(UUID playerId) {
        State state = requireState(playerId);
        return new Snapshot(state.virtualKeys, state.pityCounts, state.changedKeys, state.changedCrates,
                state.revision);
    }

    public void markPersisted(UUID playerId, Snapshot persistedSnapshot) {
        State state = states.get(playerId);
        if (state == null || state.revision != persistedSnapshot.revision()) {
            return;
        }
        state.changedKeys.removeAll(persistedSnapshot.changedKeys());
        state.changedCrates.removeAll(persistedSnapshot.changedCrates());
    }

    public void restore(UUID playerId, Snapshot snapshot) {
        // Snapshot 内的 Map/Set 均为不可变拷贝，必须重新做可变拷贝，否则后续 put 会抛异常
        State state = new State(copyNonNegative(snapshot.virtualKeys()), copyNonNegative(snapshot.pityCounts()));
        state.changedKeys.addAll(snapshot.changedKeys());
        state.changedCrates.addAll(snapshot.changedCrates());
        State previous = states.put(playerId, state);
        if (previous != null) {
            // 快照之后、回滚之前发生的变更也保持"待持久化"标记，仅写变更项时才不会漏写
            state.changedKeys.addAll(previous.changedKeys);
            state.changedCrates.addAll(previous.changedCrates);
        }
        state.revision = Math.max(snapshot.revision(), previous == null ? 0 : previous.revision) + 1;
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
        private final Set<String> changedKeys = new HashSet<>();
        private final Set<String> changedCrates = new HashSet<>();
        private long revision;

        private State(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts) {
            this.virtualKeys = virtualKeys;
            this.pityCounts = pityCounts;
        }
    }

    public record Snapshot(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts,
                           Set<String> changedKeys, Set<String> changedCrates, long revision) {

        public Snapshot {
            virtualKeys = Map.copyOf(virtualKeys);
            pityCounts = Map.copyOf(pityCounts);
            changedKeys = Set.copyOf(changedKeys);
            changedCrates = Set.copyOf(changedCrates);
        }

        /**
         * 兼容构造：未提供变更集时视全部条目为已变更（保守全量写入）。
         */
        public Snapshot(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts) {
            this(virtualKeys, pityCounts, virtualKeys.keySet(), pityCounts.keySet(), 0);
        }

        public Snapshot(Map<String, Integer> virtualKeys, Map<String, Integer> pityCounts,
                        Set<String> changedKeys, Set<String> changedCrates) {
            this(virtualKeys, pityCounts, changedKeys, changedCrates, 0);
        }
    }
}
