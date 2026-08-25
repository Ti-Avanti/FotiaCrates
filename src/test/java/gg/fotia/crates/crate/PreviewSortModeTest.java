package gg.fotia.crates.crate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewSortModeTest {

    @Test
    void invalidOrMissingConfigKeepsOriginalOrder() {
        assertEquals(PreviewSortMode.CONFIG_ORDER, PreviewSortMode.fromConfig(null));
        assertEquals(PreviewSortMode.CONFIG_ORDER, PreviewSortMode.fromConfig(""));
        assertEquals(PreviewSortMode.CONFIG_ORDER, PreviewSortMode.fromConfig("unknown"));
    }

    @Test
    void editorCyclesThroughAllModes() {
        assertEquals(PreviewSortMode.WEIGHT_DESC, PreviewSortMode.CONFIG_ORDER.next());
        assertEquals(PreviewSortMode.WEIGHT_ASC, PreviewSortMode.WEIGHT_DESC.next());
        assertEquals(PreviewSortMode.CONFIG_ORDER, PreviewSortMode.WEIGHT_ASC.next());
    }
}
