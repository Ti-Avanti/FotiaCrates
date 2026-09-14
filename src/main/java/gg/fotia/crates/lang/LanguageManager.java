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
    private final Map<String, FileConfiguration> bundledLanguages = new HashMap<>();
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
        bundledLanguages.clear();

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
                FileConfiguration bundledConfig = loadBundledLanguage(file.getName());
                if (bundledConfig != null) {
                    bundledLanguages.put(langCode, bundledConfig);
                    boolean changed = KeyMessageMigration.apply(config, bundledConfig);
                    var messages = bundledConfig.getConfigurationSection("messages");
                    if (messages != null) {
                        for (String key : messages.getKeys(false)) {
                            String path = "messages." + key;
                            if ((key.startsWith("rarity-probability-") || key.startsWith("reward-delivery-")
                                    || key.equals("configuration-save-busy") || key.equals("crate-location-save-failed")) && !config.isSet(path)) {
                                config.set(path, messages.get(key));
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        try {
                            config.save(file);
                        } catch (IOException exception) {
                            plugin.getLogger().warning("Failed to save key message defaults for "
                                    + file.getName() + ": " + exception.getMessage());
                        }
                    }
                }
                languages.put(langCode, config);
                plugin.getLogger().info("Loaded language: " + langCode);
            }
        }

        // 从主配置读取默认语言
        defaultLanguage = plugin.getConfig().getString("settings.default-language", "zh_CN");

        // 加载前缀
        prefix = resolveLanguageValue(defaultLanguage, "prefix",
                "<!i><gradient:#FFD700:#FFA500>[FotiaCrates]</gradient> ");

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

    private FileConfiguration loadBundledLanguage(String fileName) {
        try (InputStream stream = plugin.getResource("lang/" + fileName)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to load bundled language defaults for " + fileName + ": " + exception.getMessage());
            return null;
        }
    }

    private String resolveLanguageValue(String langCode, String path, String fallback) {
        var translated = gg.fotia.translator.bridge.PaperTranslatorBridge.find("fotiacrates", langCode, path);
        if (translated.isPresent() && !(translated.get() instanceof java.util.List<?>)) return String.valueOf(translated.get());

        String value = getString(languages.get(langCode), path);
        if (value != null) {
            return value;
        }

        value = getString(bundledLanguages.get(langCode), path);
        if (value != null) {
            return value;
        }

        if (!defaultLanguage.equals(langCode)) {
            value = getString(languages.get(defaultLanguage), path);
            if (value != null) {
                return value;
            }

            value = getString(bundledLanguages.get(defaultLanguage), path);
            if (value != null) {
                return value;
            }
        }

        return fallback;
    }

    private String getString(FileConfiguration config, String path) {
        return config != null ? config.getString(path) : null;
    }

    /**
     * 获取玩家的语言代码
     */
    public String getPlayerLanguage(Player player) {
        return gg.fotia.translator.bridge.PaperTranslatorBridge.locale(player, defaultLanguage);
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
        String langCode = getPlayerLanguage(player);
        String message = resolveLanguageValue(langCode, "messages." + key, key);
        String langPrefix = resolveLanguageValue(langCode, "prefix", prefix);
        return MessageUtil.parse(langPrefix + message);
    }

    /**
     * 获取消息（带前缀，无玩家参数，使用默认语言）
     */
    public Component getMessage(String key) {
        String message = resolveLanguageValue(defaultLanguage, "messages." + key, key);
        String langPrefix = resolveLanguageValue(defaultLanguage, "prefix", prefix);
        return MessageUtil.parse(langPrefix + message);
    }

    /**
     * 获取消息（带前缀和占位符）
     */
    public Component getMessage(Player player, String key, Map<String, String> placeholders) {
        String langCode = getPlayerLanguage(player);
        String message = resolveLanguageValue(langCode, "messages." + key, key);
        String langPrefix = resolveLanguageValue(langCode, "prefix", prefix);
        return MessageUtil.parse(langPrefix + message, placeholders);
    }

    /**
     * 获取消息（带前缀和占位符，无玩家参数，使用默认语言）
     */
    public Component getMessage(String key, Map<String, String> placeholders) {
        String message = resolveLanguageValue(defaultLanguage, "messages." + key, key);
        String langPrefix = resolveLanguageValue(defaultLanguage, "prefix", prefix);
        return MessageUtil.parse(langPrefix + message, placeholders);
    }

    /**
     * 获取消息（无前缀）
     */
    public Component getMessageNoPrefix(Player player, String key) {
        String message = resolveLanguageValue(getPlayerLanguage(player), "messages." + key, key);
        return MessageUtil.parse(message);
    }

    /**
     * 获取消息（无前缀，带占位符）
     */
    public Component getMessageNoPrefix(Player player, String key, Map<String, String> placeholders) {
        String message = resolveLanguageValue(getPlayerLanguage(player), "messages." + key, key);
        return MessageUtil.parse(message, placeholders);
    }

    /**
     * 获取原始消息字符串
     */
    public String getRawMessage(Player player, String key) {
        return resolveLanguageValue(getPlayerLanguage(player), "messages." + key, key);
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
