package gg.fotia.crates.key.distribution;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class KeyDistributionConfirmationStore {

    private final Map<String, KeyDistributionConfirmation> confirmations = new HashMap<>();
    private final Supplier<String> tokenSupplier;

    public KeyDistributionConfirmationStore() {
        this(() -> UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
    }

    KeyDistributionConfirmationStore(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    public KeyDistributionConfirmation create(KeyDistributionRequest request, long now, long timeoutMillis) {
        KeyDistributionConfirmation confirmation = new KeyDistributionConfirmation(
                tokenSupplier.get(), request, now + Math.max(1L, timeoutMillis));
        confirmations.put(request.creatorId(), confirmation);
        return confirmation;
    }

    public Optional<KeyDistributionRequest> consume(String creatorId, String token, long now) {
        KeyDistributionConfirmation confirmation = confirmations.get(creatorId);
        if (confirmation == null) {
            return Optional.empty();
        }
        if (confirmation.expiresAt() < now) {
            confirmations.remove(creatorId);
            return Optional.empty();
        }
        if (token == null || !confirmation.token().equalsIgnoreCase(token)) {
            return Optional.empty();
        }
        confirmations.remove(creatorId);
        return Optional.of(confirmation.request());
    }
}
