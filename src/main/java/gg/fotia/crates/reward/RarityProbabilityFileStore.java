package gg.fotia.crates.reward;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RarityProbabilityFileStore {
    private RarityProbabilityFileStore() {
    }

    public static boolean save(Path path, String expected, String updated) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".rarity-probability-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            if (!Files.readString(path, StandardCharsets.UTF_8).equals(expected)) return false;
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
