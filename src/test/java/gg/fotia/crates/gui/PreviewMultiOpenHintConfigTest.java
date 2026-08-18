package gg.fotia.crates.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewMultiOpenHintConfigTest {

    @Test
    void rendersConfiguredAvailableAndUnavailableHints() {
        PreviewMultiOpenHintConfig config = new PreviewMultiOpenHintConfig(
                "<!i><yellow>右键抽取 {amount} 次",
                "<!i><red>当前无法多连抽"
        );

        assertEquals("<!i><yellow>右键抽取 10 次", config.render(10));
        assertEquals("<!i><red>当前无法多连抽", config.render(1));
    }
}
