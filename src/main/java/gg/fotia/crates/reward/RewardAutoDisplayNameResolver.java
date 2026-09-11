package gg.fotia.crates.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RewardAutoDisplayNameResolver {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*$" );

    private RewardAutoDisplayNameResolver() {
    }

    public static String resolve(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return RewardAutoDisplayPolicy.fallbackName(Material.PAPER);
        }

        Component name = resolveNameComponent(item);
        if (name == null) {
            name = Component.translatable(item.translationKey());
        }
        return toConfigName(name, item.getType());
    }

    static String toConfigName(Component itemName, Material fallbackMaterial) {
        if (itemName != null) {
            return MINI_MESSAGE.serialize(itemName);
        }
        return RewardAutoDisplayPolicy.fallbackName(fallbackMaterial);
    }

    static boolean supportsItemName(String minecraftVersion) {
        Matcher matcher = MINECRAFT_VERSION.matcher(minecraftVersion == null ? "" : minecraftVersion);
        if (!matcher.matches()) {
            return false;
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return major > 1 || (major == 1 && (minor > 20 || (minor == 20 && patch >= 5)));
    }

    private static Component resolveNameComponent(ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return null;
        }

        Component customName = itemMeta.displayName();
        if (customName != null) {
            return customName;
        }

        // The component API class is only reached on versions that expose item_name.
        if (supportsItemName(Bukkit.getMinecraftVersion())) {
            return ItemNameComponentResolver.resolve(itemMeta);
        }
        return null;
    }
}
