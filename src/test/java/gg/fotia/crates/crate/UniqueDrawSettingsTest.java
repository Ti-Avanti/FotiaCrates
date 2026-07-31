package gg.fotia.crates.crate;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueDrawSettingsTest {

    @Test
    void defaultsToDisabledUniqueDrawAndBarrierPreviewIcon() {
        UniqueDrawSettings settings = UniqueDrawSettings.defaults();

        assertFalse(settings.enabled());
        assertTrue(settings.replaceObtainedInPreview());
        assertEquals(Material.BARRIER, settings.obtainedIcon().material());
        assertEquals(0, settings.obtainedIcon().customModelData());
        assertEquals("", settings.obtainedIcon().itemModel());
    }

    @Test
    void invalidIconValuesFallBackSafely() {
        UniqueDrawSettings.ObtainedIcon icon = new UniqueDrawSettings.ObtainedIcon(
                Material.AIR, -5, "  Example:Collected_Reward  ");

        assertEquals(Material.BARRIER, icon.material());
        assertEquals(0, icon.customModelData());
        assertEquals("example:collected_reward", icon.itemModel());
    }
}
