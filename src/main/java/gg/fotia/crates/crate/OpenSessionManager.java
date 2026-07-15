package gg.fotia.crates.crate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OpenSessionManager {

    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<UUID, Long> lastInteractions = new HashMap<>();

    public boolean tryBegin(UUID playerId) {
        return activePlayers.add(playerId);
    }

    public void finish(UUID playerId) {
        activePlayers.remove(playerId);
    }

    public boolean isActive(UUID playerId) {
        return activePlayers.contains(playerId);
    }

    public boolean tryInteract(UUID playerId, long now, long cooldownMillis) {
        Long previous = lastInteractions.get(playerId);
        if (previous != null && now - previous < cooldownMillis) {
            return false;
        }
        lastInteractions.put(playerId, now);
        return true;
    }

    public void clear(UUID playerId) {
        activePlayers.remove(playerId);
        lastInteractions.remove(playerId);
    }

    public void clearInteraction(UUID playerId) {
        lastInteractions.remove(playerId);
    }

    public void clear() {
        activePlayers.clear();
        lastInteractions.clear();
    }
}
