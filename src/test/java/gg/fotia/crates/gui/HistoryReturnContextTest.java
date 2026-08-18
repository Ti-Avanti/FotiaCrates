package gg.fotia.crates.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReturnContextTest {

    @Test
    void remembersThePreviewCrateAndPage() {
        HistoryReturnContext context = HistoryReturnContext.preview("weapon", 3);

        assertTrue(context.returnsToPreview());
        assertEquals("weapon", context.crateId());
        assertEquals(3, context.page());
    }

    @Test
    void commandHistoryUsesCloseAsItsReturnTarget() {
        HistoryReturnContext context = HistoryReturnContext.close();

        assertFalse(context.returnsToPreview());
        assertEquals(0, context.page());
    }
}
