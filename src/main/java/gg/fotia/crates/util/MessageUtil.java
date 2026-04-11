package gg.fotia.crates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Map;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    // CraftEngine MiniMessage 解析方法缓存
    private static Object craftEngineMiniMessage = null;
    private static Method craftEngineDeserializeMethod = null;
    private static Object[] craftEngineTagResolvers = null;
    private static boolean craftEngineChecked = false;

    /**
     * 初始化 CraftEngine MiniMessage 解析器
     */
    private static void initCraftEngine() {
        if (craftEngineChecked) return;
        craftEngineChecked = true;

        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            return;
        }

        try {
            // 获取 AdventureHelper 类 (core包)
            Class<?> adventureHelperClass = Class.forName("net.momirealms.craftengine.core.util.AdventureHelper");

            // 获取 MiniMessage 实例
            Method miniMessageMethod = adventureHelperClass.getMethod("miniMessage");
            craftEngineMiniMessage = miniMessageMethod.invoke(null);

            if (craftEngineMiniMessage == null) {
                Bukkit.getLogger().warning("[FotiaCrates] CraftEngine AdventureHelper.miniMessage() returned null");
                return;
            }

            // 获取 ImageTag.INSTANCE 和 ShiftTag.INSTANCE
            Class<?> imageTagClass = Class.forName("net.momirealms.craftengine.core.plugin.text.minimessage.ImageTag");
            Class<?> shiftTagClass = Class.forName("net.momirealms.craftengine.core.plugin.text.minimessage.ShiftTag");

            Object imageTag = imageTagClass.getField("INSTANCE").get(null);
            Object shiftTag = shiftTagClass.getField("INSTANCE").get(null);

            // 获取 TagResolver 类
            Class<?> tagResolverClass = Class.forName("net.kyori.adventure.text.minimessage.tag.resolver.TagResolver");

            // 创建 TagResolver 数组
            craftEngineTagResolvers = (Object[]) java.lang.reflect.Array.newInstance(tagResolverClass, 2);
            craftEngineTagResolvers[0] = shiftTag;
            craftEngineTagResolvers[1] = imageTag;

            // 获取 deserialize 方法 (varargs)
            craftEngineDeserializeMethod = craftEngineMiniMessage.getClass().getMethod("deserialize", String.class, tagResolverClass.arrayType());

            Bukkit.getLogger().info("[FotiaCrates] CraftEngine MiniMessage integration enabled (ImageTag + ShiftTag)");
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().warning("[FotiaCrates] CraftEngine class not found: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            Bukkit.getLogger().warning("[FotiaCrates] CraftEngine method not found: " + e.getMessage());
        } catch (NoSuchFieldException e) {
            Bukkit.getLogger().warning("[FotiaCrates] CraftEngine field not found: " + e.getMessage());
        } catch (Exception e) {
            Bukkit.getLogger().warning("[FotiaCrates] Failed to init CraftEngine: " + e.getClass().getName() + ": " + e.getMessage());
        }

        if (craftEngineMiniMessage == null || craftEngineDeserializeMethod == null || craftEngineTagResolvers == null) {
            Bukkit.getLogger().warning("[FotiaCrates] CraftEngine found but MiniMessage API not available. Custom tags like <image:...> won't work.");
        }
    }

    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        // 如果包含&或§符号，先转换为MiniMessage格式
        if (message.contains("§") || message.contains("&")) {
            message = LegacyColorConverter.convertToMiniMessage(message);
        }
        if (message.contains("<") && message.contains(">")) {
            // 尝试使用 CraftEngine 解析器
            initCraftEngine();
            if (craftEngineMiniMessage != null && craftEngineDeserializeMethod != null && craftEngineTagResolvers != null) {
                try {
                    Object result = craftEngineDeserializeMethod.invoke(craftEngineMiniMessage, message, craftEngineTagResolvers);
                    if (result instanceof Component) {
                        return (Component) result;
                    }
                } catch (Exception e) {
                    // 回退到默认解析器
                    Bukkit.getLogger().warning("[FotiaCrates] CraftEngine parse failed: " + e.getMessage());
                }
            }
            return MINI_MESSAGE.deserialize(message);
        }
        return MINI_MESSAGE.deserialize(message);
    }

    public static Component parse(String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        String processed = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return parse(processed);
    }

    public static String stripColor(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("(?i)[&§][0-9a-fk-or]", "")
                .replaceAll("<[^>]+>", "");
    }

    /**
     * 将Component转换为Legacy格式字符串
     */
    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SECTION.serialize(component);
    }

    /**
     * 将Legacy格式字符串转换为普通字符串
     */
    public static String toLegacy(String message) {
        if (message == null) {
            return "";
        }
        // 如果已经是legacy格式，直接返回
        if (message.contains("§")) {
            return message;
        }
        // 如果是MiniMessage格式，先解析再转换
        if (message.contains("<") && message.contains(">")) {
            return LEGACY_SECTION.serialize(parse(message));
        }
        return message;
    }

    /**
     * 检查 CraftEngine 是否可用
     */
    public static boolean isCraftEngineAvailable() {
        initCraftEngine();
        return craftEngineMiniMessage != null && craftEngineDeserializeMethod != null && craftEngineTagResolvers != null;
    }
}
