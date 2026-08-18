package gg.fotia.crates.key.distribution;

import gg.fotia.crates.key.KeyType;

import java.util.List;
import java.util.UUID;

public record KeyDistributionRequest(
        KeyDistributionScope scope,
        String keyId,
        int amount,
        KeyType keyType,
        String creatorId,
        String creatorName,
        List<UUID> onlineRecipients
) {

    public KeyDistributionRequest {
        onlineRecipients = onlineRecipients == null ? List.of() : List.copyOf(onlineRecipients);
    }
}
