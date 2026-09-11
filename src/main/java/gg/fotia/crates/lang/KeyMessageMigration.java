package gg.fotia.crates.lang;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

final class KeyMessageMigration {
    private KeyMessageMigration() {
    }

    static boolean apply(ConfigurationSection config, ConfigurationSection bundled) {
        boolean changed = false;
        for (String key : List.of("no-key", "no-key-detail", "no-key-separator", "no-key-configured")) {
            String path = "messages." + key;
            String current = config.getString(path);
            String replacement = bundled.getString(path);
            boolean oldDefault = key.equals("no-key") && (
                    "<!i><red>你没有足够的钥匙！".equals(current)
                    || "<!i><red>You don't have enough keys!".equals(current));
            if (replacement != null && (current == null || oldDefault) && !replacement.equals(current)) {
                config.set(path, replacement);
                changed = true;
            }
        }
        return changed;
    }
}
