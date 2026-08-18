package gg.fotia.crates.key.distribution;

import gg.fotia.crates.key.KeyType;

import java.util.UUID;

public record KeyDistributionGrant(
        long id,
        String batchId,
        UUID playerId,
        String keyId,
        int amount,
        KeyType keyType,
        long createdAt
) {
}
