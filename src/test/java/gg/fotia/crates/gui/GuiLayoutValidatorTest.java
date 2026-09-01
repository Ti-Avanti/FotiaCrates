package gg.fotia.crates.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiLayoutValidatorTest {

    @Test
    void reportsRowsThatAreNotExactlyNineCharacters() {
        assertEquals(List.of(1, 2), GuiLayoutValidator.invalidRows(List.of(
                "#########",
                "##########",
                "#A#",
                "#########"
        )));
    }
}
