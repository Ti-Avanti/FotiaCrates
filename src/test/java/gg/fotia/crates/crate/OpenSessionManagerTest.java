package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSessionManagerTest {

    @Test
    void allowsOnlyOneOpenSessionPerPlayer() {
        OpenSessionManager sessions = new OpenSessionManager();
        UUID playerId = UUID.randomUUID();

        assertTrue(sessions.tryBegin(playerId));
        assertFalse(sessions.tryBegin(playerId));
        assertTrue(sessions.isActive(playerId));

        sessions.finish(playerId);

        assertFalse(sessions.isActive(playerId));
        assertTrue(sessions.tryBegin(playerId));
    }

    @Test
    void centralizesInteractionCooldownAndPlayerCleanup() {
        OpenSessionManager sessions = new OpenSessionManager();
        UUID playerId = UUID.randomUUID();

        assertTrue(sessions.tryInteract(playerId, 1_000L, 500L));
        assertFalse(sessions.tryInteract(playerId, 1_499L, 500L));
        assertTrue(sessions.tryInteract(playerId, 1_500L, 500L));

        sessions.clear(playerId);

        assertTrue(sessions.tryInteract(playerId, 1_501L, 500L));
    }
}
