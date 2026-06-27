package gg.fotia.crates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Map;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final char SECTION_CHAR = '\u00A7';

    private static Object craftEngineMiniMessage = null;
    private static Method craftEngineDeserializeMethod = null;
    private static Object[] craftEngineTagResolvers = null;
    private static boolean craftEngineChecked = false;

    private static void initCraftEngine() {
        if (craftEngineChecked) return;
        craftEngineChecked = true;

        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            return;
        }

        try {
            Class<?> adventureHelperClass = Class.forName("net.momirealms.craftengine.core.util.AdventureHelper");
            Method miniMessageMethod = adventureHelperClass.getMethod("miniMessage");
            craftEngineMiniMessage = miniMessageMethod.invoke(null);
            if (craftEngineMiniMessage == null) {
                Bukkit.getLogger().warning("[FotiaCrates] CraftEngine AdventureHelper.miniMessage() returned null");
                return;
            }

            Class<?> imageTagClass = Class.forName("net.momirealms.craftengine.core.plugin.text.minimessage.ImageTag");
            Class<?> shiftTagClass = Class.forName("net.momirealms.craftengine.core.plugin.text.minimessage.ShiftTag");

            Object imageTag = imageTagClass.getField("INSTANCE").get(null);
            Object shiftTag = shiftTagClass.getField("INSTANCE").get(null);

            Class<?> tagResolverClass = Class.forName("net.kyori.adventure.text.minimessage.tag.resolver.TagResolver");
            craftEngineTagResolvers = (Object[]) java.lang.reflect.Array.newInstance(tagResolverClass, 2);
            craftEngineTagResolvers[0] = shiftTag;
            craftEngineTagResolvers[1] = imageTag;

            craftEngineDeserializeMethod = craftEngineMiniMessage.getClass()
                    .getMethod("deserialize", String.class, tagResolverClass.arrayType());

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

        if (message.indexOf(SECTION_CHAR) >= 0 || message.contains("&")) {
            message = LegacyColorConverter.convertToMiniMessage(message);
        }

        if (message.contains("<") && message.contains(">") && usesCraftEngineTags(message)) {
            initCraftEngine();
            if (craftEngineMiniMessage != null
                    && craftEngineDeserializeMethod != null
                    && craftEngineTagResolvers != null) {
                try {
                    Object result = craftEngineDeserializeMethod.invoke(craftEngineMiniMessage, message, craftEngineTagResolvers);
                    if (result instanceof Component component) {
                        return component;
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Bukkit.getLogger().warning("[FotiaCrates] CraftEngine parse failed: " + e.getMessage());
                }
            }
        }

        return parseMiniMessageSafely(message);
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

    private static boolean usesCraftEngineTags(String message) {
        String lower = message.toLowerCase();
        return lower.contains("<image:") || lower.contains("<shift:");
    }

    private static Component parseMiniMessageSafely(String message) {
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (RuntimeException e) {
            Bukkit.getLogger().warning("[FotiaCrates] MiniMessage parse failed, using plain text fallback: " + e.getMessage());
            return Component.text(message);
        }
    }

    public static String stripColor(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("(?i)[&" + SECTION_CHAR + "][0-9a-fk-or]", "")
                .replaceAll("<[^>]+>", "");
    }

    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SECTION.serialize(component);
    }

    public static String toLegacy(String message) {
        if (message == null) {
            return "";
        }
        if (message.indexOf(SECTION_CHAR) >= 0) {
            return message;
        }
        if (message.contains("<") && message.contains(">")) {
            return LEGACY_SECTION.serialize(parse(message));
        }
        return message;
    }

    public static boolean isCraftEngineAvailable() {
        initCraftEngine();
        return craftEngineMiniMessage != null && craftEngineDeserializeMethod != null && craftEngineTagResolvers != null;
    }
}
