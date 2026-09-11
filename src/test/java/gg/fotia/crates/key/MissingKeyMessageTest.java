package gg.fotia.crates.key;

import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissingKeyMessageTest {
    @Test
    void usesItemDisplayNamesAndAccurateMultiOpenShortfall() {
        Key key = new Key("common", "Internal name");
        key.setDisplayName("&6普通钥匙");
        var values = MissingKeyMessage.placeholders("宝箱", List.of(key), " 或 ", 10, 3);
        assertEquals("7", values.get("missing"));
        assertEquals("10", values.get("required"));
        assertEquals("3", values.get("owned"));
        assertEquals("普通钥匙", plain(values.get("keys")));
    }

    @Test
    void listsAlternativesInStableOrderWithLegacyAndMiniMessageColors() {
        Key second = new Key("b", "§bB");
        Key first = new Key("a", "<!i><gold>A");
        var values = MissingKeyMessage.placeholders("Crate", List.of(second, first), "<!i><gray> or ", 1, 0);
        assertEquals("A or B", plain(values.get("keys")));
        assertEquals("A or B", plain(values.get("key")));
    }

    @Test
    void fallsBackToConfiguredNameThenId() {
        Key named = new Key("a", "Name");
        named.setDisplayName(" ");
        Key unnamed = new Key("b", " ");
        unnamed.setDisplayName(null);
        assertEquals("Name or b", plain(MissingKeyMessage.placeholders("Crate",
                List.of(unnamed, named), " or ", 1, 0).get("keys")));
    }

    @Test
    void oldCustomMessagesGetDetailsWithoutDuplicatingNewPlaceholders() {
        assertTrue(MissingKeyMessage.needsDetails("Not enough keys"));
        assertFalse(MissingKeyMessage.needsDetails("Missing {keys}"));
        assertFalse(MissingKeyMessage.needsDetails("Missing {key}"));
        assertFalse(MissingKeyMessage.needsDetails(""));
    }

    private static String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(MessageUtil.parse(text));
    }
}
