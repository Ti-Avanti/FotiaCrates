package gg.fotia.crates.key;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyTest {

    @Test
    void emptyAllowedCratesCannotOpenAnyCrate() {
        Key key = new Key("common", "普通钥匙");
        assertTrue(key.getAllowedCrates().isEmpty());
        assertFalse(key.canOpenCrate("common"));
        assertFalse(key.canOpenCrate("rare"));
    }

    @Test
    void wildcardEntriesOpenAllCrates() {
        assertTrue(newKey(List.of("*")).canOpenCrate("common"));
        assertTrue(newKey(List.of("all")).canOpenCrate("common"));
        assertTrue(newKey(List.of("ALL")).canOpenCrate("rare"));
        assertTrue(newKey(List.of("全部")).canOpenCrate("rare"));
        assertTrue(newKey(List.of(" * ")).canOpenCrate("common"));
    }

    @Test
    void explicitListOnlyOpensListedCrates() {
        Key key = newKey(List.of("common", "rare"));
        assertTrue(key.canOpenCrate("common"));
        assertTrue(key.canOpenCrate("rare"));
        assertFalse(key.canOpenCrate("epic"));
    }

    @Test
    void masterKeyOpensAllCratesRegardlessOfList() {
        Key key = new Key("master", "万能钥匙");
        assertFalse(key.canOpenCrate("common"));

        key.setMasterKey(true);
        assertTrue(key.isMasterKey());
        assertTrue(key.canOpenCrate("common"));
        assertTrue(key.canOpenCrate("any-crate"));
        assertTrue(key.getAllowedCrates().contains("all"));

        Key listedMaster = newKey(List.of("common"));
        listedMaster.setMasterKey(true);
        assertTrue(listedMaster.canOpenCrate("rare"));

        listedMaster.setMasterKey(false);
        assertFalse(listedMaster.isMasterKey());
        assertEquals(List.of("common"), listedMaster.getAllowedCrates());
        assertFalse(listedMaster.canOpenCrate("rare"));
        assertTrue(listedMaster.canOpenCrate("common"));
    }

    @Test
    void removedCrateNoLongerOpenable() {
        Key key = newKey(List.of("common", "rare"));
        key.removeCrate("common");
        assertFalse(key.canOpenCrate("common"));
        assertTrue(key.canOpenCrate("rare"));
    }

    private Key newKey(List<String> allowedCrates) {
        return new Key("test", "Test", Material.TRIPWIRE_HOOK, "Test", List.of(), 0, true, allowedCrates);
    }
}
