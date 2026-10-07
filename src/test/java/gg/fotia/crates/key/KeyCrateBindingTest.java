package gg.fotia.crates.key;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyCrateBindingTest {
    @Test
    void newAndRecreatedKeysHaveNoCrateAccess() {
        Key original = new Key("six", "Six");
        original.addCrate("wine");
        assertTrue(original.canOpenCrate("wine"));

        Key recreated = new Key(original.getId(), original.getName());
        assertFalse(recreated.canOpenCrate("wine"));
        assertFalse(recreated.canOpenCrate("another"));
    }

    @Test
    void emptyAndMissingConfigurationDoNotAuthorizeCrates() throws Exception {
        for (String yaml : List.of("allowed-crates: []", "name: Six")) {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yaml);
            assertFalse(loaded(config.getStringList("allowed-crates")).canOpenCrate("wine"));
        }
        assertFalse(loaded(null).canOpenCrate("wine"));
    }

    @Test
    void removingLastBindingStaysUnboundAfterSaveAndReload() throws Exception {
        Key key = loaded(List.of("wine"));
        key.removeCrate("wine");
        assertFalse(key.canOpenCrate("wine"));
        assertFalse(key.canOpenCrate("another"));

        YamlConfiguration saved = new YamlConfiguration();
        saved.set("allowed-crates", key.getAllowedCrates());
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(saved.saveToString());
        assertFalse(loaded(reloaded.getStringList("allowed-crates")).canOpenCrate("wine"));
    }

    @Test
    void explicitBindingsAuthorizeOnlyTheirCrates() {
        Key key = loaded(List.of("wine", "hats"));
        assertTrue(key.canOpenCrate("wine"));
        assertTrue(key.canOpenCrate("hats"));
        assertFalse(key.canOpenCrate("other"));
        key.removeCrate("wine");
        assertFalse(key.canOpenCrate("wine"));
        assertTrue(key.canOpenCrate("hats"));
    }

    @Test
    void onlyExplicitWildcardGrantsUniversalAccess() {
        Key key = loaded(List.of("*", "wine"));
        assertTrue(key.canOpenCrate("wine"));
        assertTrue(key.canOpenCrate("future_crate"));
        key.removeCrate("*");
        assertTrue(key.canOpenCrate("wine"));
        assertFalse(key.canOpenCrate("future_crate"));
        key.removeCrate("wine");
        assertFalse(key.canOpenCrate("future_crate"));
    }

    private static Key loaded(List<String> bindings) {
        return new Key("six", "Six", Material.TRIPWIRE_HOOK, "Six", List.of(), 0, false, bindings);
    }
}
