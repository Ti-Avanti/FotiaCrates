package gg.fotia.crates.lang;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 多语言管理器
 * 支持根据玩家客户端语言自动切换
 */
public class LanguageManager {

    private final FotiaCrates plugin;
    private final Map<String, FileConfiguration> languages = new HashMap<>();
    private String defaultLanguage = "zh_CN";
    private String prefix = "";

    public LanguageManager(FotiaCrates plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    /**
     * 加载所有语言文件
     */
    public void loadLanguages() {
        languages.clear();

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // 保存默认语言文件
        saveDefaultLanguage("zh_CN.yml");
        saveDefaultLanguage("en_US.yml");

        // 加载所有语言文件
        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String langCode = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                applyBundledDefaults(config, file.getName());
                languages.put(langCode, config);
                plugin.getLogger().info("Loaded language: " + langCode);
            }
        }

        // 从主配置读取默认语言
        defaultLanguage = plugin.getConfig().getString("settings.default-language", "zh_CN");

        // 加载前缀
        FileConfiguration defaultConfig = languages.get(defaultLanguage);
        if (defaultConfig != null) {
            prefix = defaultConfig.getString("prefix", "<!i><gradient:#FFD700:#FFA500>[FotiaCrates]</gradient> ");
        }

        plugin.getLogger().info("Loaded " + languages.size() + " languages. Default: " + defaultLanguage);
    }

    /**
     * 保存默认语言文件
     */
    private void saveDefaultLanguage(String fileName) {
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }

    /**
     * Preserve administrator-owned language files while allowing new bundled messages to fall back at runtime.
     */
    private void applyBundledDefaults(FileConfiguration config, String fileName) {
        try (InputStream stream = plugin.getResource("lang/" + fileName)) {
            if (stream == null) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to load bundled language defaults for " + fileName + ": " + exception.getMessage());
        }
    }

    /**
     * 获取玩家的语言代码
     */
    public String getPlayerLanguage(Player player) {
        if (player == null) {
            return defaultLanguage;
        }

        String locale = player.locale().toString();
        // 转换格式: en_us -> en_US, zh_cn -> zh_CN
        if (locale.contains("_")) {
            String[] parts = locale.split("_");
            if (parts.length >= 2) {
                locale = parts[0].toLowerCase() + "_" + parts[1].toUpperCase();
            }
        }

        // 如果有对应语言文件则使用，否则使用默认语言
        if (languages.containsKey(locale)) {
            return locale;
        }

        return defaultLanguage;
    }

    /**
     * 获取语言配置
     */
    public FileConfiguration getLanguageConfig(String langCode) {
        return languages.getOrDefault(langCode, languages.get(defaultLanguage));
    }

    /**
     * 获取玩家对应的语言配置
     */
    public FileConfiguration getPlayerConfig(Player player) {
        return getLanguageConfig(getPlayerLanguage(player));
    }

    /**
     * 获取消息（带前缀）
     */
    public Component getMessage(Player player, String key) {
        FileConfiguration config = getPlayerConfig(player);
        String message = config.getString("messages." + key, key);
        String langPrefix = config.getString("prefix", prefix);
        return MessageUtil.parse(langPrefix + message);
    }

    /**
     * 获取消息（带前缀，无玩家参数，使用默认语言）
     */
    public Component getMessage(String key) {
        FileConfiguration config = getLanguageConfig(defaultLanguage);
        String message = config.getString("messages." + key, key);
        String langPrefix = config.getString("prefix", prefix);
        return MessageUtil.parse(langPrefix + message);
    }

    /**
     * 获取消息（带前缀和占位符）
     */
    public Component getMessage(Player player, String key, Map<String, String> placeholders) {
        FileConfiguration config = getPlayerConfig(player);
        String message = config.getString("messages." + key, key);
        String langPrefix = config.getString("prefix", prefix);
        return MessageUtil.parse(langPrefix + message, placeholders);
    }

    /**
     * 获取消息（带前缀和占位符，无玩家参数，使用默认语言）
     */
    public Component getMessage(String key, Map<String, String> placeholders) {
        FileConfiguration config = getLanguageConfig(defaultLanguage);
        String message = config.getString("messages." + key, key);
        String langPrefix = config.getString("prefix", prefix);
        return MessageUtil.parse(langPrefix + message, placeholders);
    }

    /**
     * 获取消息（无前缀）
     */
    public Component getMessageNoPrefix(Player player, String key) {
        FileConfiguration config = getPlayerConfig(player);
        String message = config.getString("messages." + key, key);
        return MessageUtil.parse(message);
    }

    /**
     * 获取消息（无前缀，带占位符）
     */
    public Component getMessageNoPrefix(Player player, String key, Map<String, String> placeholders) {
        FileConfiguration config = getPlayerConfig(player);
        String message = config.getString("messages." + key, key);
        return MessageUtil.parse(message, placeholders);
    }

    /**
     * 获取原始消息字符串
     */
    public String getRawMessage(Player player, String key) {
        FileConfiguration config = getPlayerConfig(player);
        return config.getString("messages." + key, key);
    }

    /**
     * 发送消息给玩家
     */
    public void send(Player player, String key) {
        if (player != null) {
            player.sendMessage(getMessage(player, key));
        }
    }

    /**
     * 发送消息给玩家（带占位符）
     */
    public void send(Player player, String key, Map<String, String> placeholders) {
        if (player != null) {
            player.sendMessage(getMessage(player, key, placeholders));
        }
    }

    /**
     * 创建占位符Map的便捷方法
     */
    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /**
     * 重新加载语言文件
     */
    public void reload() {
        loadLanguages();
    }

    /**
     * 获取默认语言
     */
    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    /**
     * 获取前缀
     */
    public String getPrefix() {
        return prefix;
    }
}
