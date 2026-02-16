package gg.fotia.crates.config;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageConfig {

    private final FotiaCrates plugin;
    private FileConfiguration config;
    private String prefix;

    public MessageConfig(FotiaCrates plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        prefix = config.getString("prefix", "<!i><gradient:#FFD700:#FFA500>[FotiaCrates]</gradient> ");
    }

    public Component getMessage(String key) {
        String message = config.getString("messages." + key, key);
        return MessageUtil.parse(prefix + message);
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String message = config.getString("messages." + key, key);
        return MessageUtil.parse(prefix + message, placeholders);
    }

    public Component getMessageNoPrefix(String key) {
        String message = config.getString("messages." + key, key);
        return MessageUtil.parse(message);
    }

    public Component getMessageNoPrefix(String key, Map<String, String> placeholders) {
        String message = config.getString("messages." + key, key);
        return MessageUtil.parse(message, placeholders);
    }

    public void send(Player player, String key) {
        if (player != null) {
            player.sendMessage(getMessage(key));
        }
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        if (player != null) {
            player.sendMessage(getMessage(key, placeholders));
        }
    }

    public String getPrefix() {
        return prefix;
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
