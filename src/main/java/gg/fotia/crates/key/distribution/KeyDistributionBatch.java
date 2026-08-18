package gg.fotia.crates.key.distribution;

import gg.fotia.crates.key.KeyType;

public record KeyDistributionBatch(
        String id,
        KeyDistributionScope scope,
        String keyId,
        int amount,
        KeyType keyType,
        String createdBy,
        long createdAt,
        int targetCount,
        String status
) {
}
