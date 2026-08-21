package gg.fotia.crates.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiPaginationStateTest {

    @Test
    void singlePageDisablesBothDirections() {
        GuiPaginationState state = new GuiPaginationState(0, 1);

        assertFalse(state.isActionAvailable("prev_page"));
        assertFalse(state.isActionAvailable("previous_page"));
        assertFalse(state.isActionAvailable("next_page"));
    }

    @Test
    void firstMiddleAndLastPagesExposeCorrectDirections() {
        GuiPaginationState first = new GuiPaginationState(0, 3);
        GuiPaginationState middle = new GuiPaginationState(1, 3);
        GuiPaginationState last = new GuiPaginationState(2, 3);

        assertFalse(first.isActionAvailable("prev_page"));
        assertTrue(first.isActionAvailable("next_page"));
        assertTrue(middle.isActionAvailable("previous_page"));
        assertTrue(middle.isActionAvailable("next_page"));
        assertTrue(last.isActionAvailable("prev_page"));
        assertFalse(last.isActionAvailable("next_page"));
        assertTrue(last.isActionAvailable("open_crate"));
    }
}
