package gg.fotia.crates.reward;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RarityProbabilityFileStoreTest {
    @TempDir Path directory;

    @Test void writesUtf8AndRejectsStaleSnapshots() throws Exception {
        Path file = directory.resolve("crate.yml");
        Files.writeString(file, "name: 宝箱\nchance: 10\n");
        assertFalse(RarityProbabilityFileStore.save(file, "old", "wrong"));
        assertEquals("name: 宝箱\nchance: 10\n", Files.readString(file));
        assertTrue(RarityProbabilityFileStore.save(file, Files.readString(file), "name: 宝箱\nchance: 1.0\n"));
        assertEquals("name: 宝箱\nchance: 1.0\n", Files.readString(file));
        assertNotEquals((byte) 0xef, Files.readAllBytes(file)[0]);
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
