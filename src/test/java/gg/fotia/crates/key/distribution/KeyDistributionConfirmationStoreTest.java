package gg.fotia.crates.key.distribution;

import gg.fotia.crates.key.KeyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyDistributionConfirmationStoreTest {

    @Test
    void confirmationCanOnlyBeConsumedOnceByItsCreator() {
        KeyDistributionConfirmationStore store = new KeyDistributionConfirmationStore(() -> "ABC123");
        KeyDistributionRequest request = new KeyDistributionRequest(
                KeyDistributionScope.ALL, "common_key", 3, KeyType.VIRTUAL,
                "player:admin", "Admin", List.of());

        KeyDistributionConfirmation confirmation = store.create(request, 1_000L, 30_000L);

        assertEquals("ABC123", confirmation.token());
        assertTrue(store.consume("player:other", "ABC123", 2_000L).isEmpty());
        assertEquals(Optional.of(request), store.consume("player:admin", "abc123", 2_000L));
        assertTrue(store.consume("player:admin", "ABC123", 2_000L).isEmpty());
    }

    @Test
    void expiredConfirmationCannotBeConsumed() {
        KeyDistributionConfirmationStore store = new KeyDistributionConfirmationStore(() -> "ABC123");
        KeyDistributionRequest request = new KeyDistributionRequest(
                KeyDistributionScope.ALL, "common_key", 1, KeyType.PHYSICAL,
                "console", "CONSOLE", List.of());
        store.create(request, 1_000L, 30_000L);

        assertTrue(store.consume("console", "ABC123", 31_001L).isEmpty());
    }
}
