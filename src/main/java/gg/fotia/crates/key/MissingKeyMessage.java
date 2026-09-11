package gg.fotia.crates.key;

import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class MissingKeyMessage {
    private MissingKeyMessage() {
    }

    public static Map<String, String> placeholders(String crateName, List<Key> keys,
                                                   String separator, int required, int owned) {
        List<Component> names = keys.stream()
                .sorted(Comparator.comparing(Key::getId))
                .map(MissingKeyMessage::displayName)
                .map(MessageUtil::parse)
                .toList();
        String joined = MiniMessage.miniMessage().serialize(Component.join(
                JoinConfiguration.separator(MessageUtil.parse(separator)), names));
        return Map.of("crate", crateName, "keys", joined, "key", joined,
                "required", String.valueOf(required), "owned", String.valueOf(owned),
                "missing", String.valueOf(Math.max(0, required - owned)));
    }

    private static String displayName(Key key) {
        if (key.getDisplayName() != null && !key.getDisplayName().isBlank()) {
            return key.getDisplayName();
        }
        return key.getName() != null && !key.getName().isBlank() ? key.getName() : key.getId();
    }

    public static boolean needsDetails(String template) {
        return !template.isBlank() && !template.contains("{keys}") && !template.contains("{key}");
    }
}
