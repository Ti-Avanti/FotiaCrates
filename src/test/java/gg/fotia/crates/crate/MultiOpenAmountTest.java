package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiOpenAmountTest {

    @Test
    void limitsPreviewRightClickAmountByAvailableKeys() {
        assertEquals(6, MultiOpenAmount.forPreviewRightClick(true, 10, 6));
    }

    @Test
    void limitsPreviewRightClickAmountByConfiguredMaximum() {
        assertEquals(10, MultiOpenAmount.forPreviewRightClick(true, 10, 24));
    }

    @Test
    void fallsBackToSingleOpenWhenMultiOpenIsUnavailable() {
        assertEquals(1, MultiOpenAmount.forPreviewRightClick(false, 10, 24));
        assertEquals(1, MultiOpenAmount.forPreviewRightClick(true, 10, 1));
        assertEquals(1, MultiOpenAmount.forPreviewRightClick(true, 1, 24));
    }
}
