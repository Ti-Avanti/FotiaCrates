package gg.fotia.crates.pity;

import gg.fotia.crates.crate.Crate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class PityProgressKey {

    private static final String LEGACY_SEPARATOR = "#t";

    private PityProgressKey() {
    }

    static String stableKey(String crateId, Crate.PityTier tier) {
        String identity = crateId + '\0' + tier.getId();
        return "t:" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    static String migrationMarkerKey(String crateId) {
        return "m2:" + UUID.nameUUIDFromBytes(crateId.getBytes(StandardCharsets.UTF_8));
    }

    static String legacyKey(String crateId, int count) {
        return crateId + LEGACY_SEPARATOR + count;
    }

    static String legacyPrefix(String crateId) {
        return crateId + LEGACY_SEPARATOR;
    }
}
