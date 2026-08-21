package gg.fotia.crates.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GuiItemDisplayResolverTest {

    @Test
    void selectsUnavailableDisplayOnlyWhenPaginationActionCannotRun() {
        GuiItemDisplay available = new GuiItemDisplay(
                Material.ARROW, "next", List.of("available"), 10, true, "fotia:next");
        GuiItemDisplay unavailable = new GuiItemDisplay(
                Material.YELLOW_STAINED_GLASS_PANE, "end", List.of("unavailable"), 20, false, "fotia:end");
        GuiItem item = new GuiItem(50, available, unavailable, "next_page", "");

        assertSame(available, GuiItemDisplayResolver.resolve(item, new GuiPaginationState(0, 2)));
        assertSame(unavailable, GuiItemDisplayResolver.resolve(item, new GuiPaginationState(1, 2)));
    }

    @Test
    void keepsAvailableDisplayWhenNoUnavailableDisplayIsConfigured() {
        GuiItemDisplay available = new GuiItemDisplay(
                Material.ARROW, "previous", List.of(), 0, false, "");
        GuiItem item = new GuiItem(48, available, null, "previous_page", "");

        assertSame(available, GuiItemDisplayResolver.resolve(item, new GuiPaginationState(0, 1)));
        assertEquals(Material.ARROW,
                GuiItemDisplayResolver.resolve(item, new GuiPaginationState(0, 1)).material());
    }
}
