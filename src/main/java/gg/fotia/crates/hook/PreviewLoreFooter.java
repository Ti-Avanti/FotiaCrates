package gg.fotia.crates.hook;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTByteArray;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 仅在只读预览图标上暂存抽奖说明，让附魔插件先处理物品本身的 lore。 */
final class PreviewLoreFooter {
    private static final String BUKKIT_VALUES = "PublicBukkitValues";
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();
    private final NamespacedKey key;
    private final String tagName;

    PreviewLoreFooter(Plugin plugin) {
        key = new NamespacedKey(plugin, "preview_lore_footer");
        tagName = key.toString();
    }

    boolean attach(org.bukkit.inventory.ItemStack icon, List<Component> lines) {
        String jsonLines = lines.stream().map(JSON::serialize).collect(Collectors.joining("\n"));
        return icon.editMeta(meta -> meta.getPersistentDataContainer().set(key,
                PersistentDataType.BYTE_ARRAY, jsonLines.getBytes(StandardCharsets.UTF_8)));
    }

    ItemStack append(ItemStack item) {
        if (item == null || item.isEmpty()) return item;
        boolean modern = item.getVersion().isNewerThanOrEquals(ClientVersion.V_1_20_5);
        NBTCompound data = modern ? item.getComponentOr(ComponentTypes.CUSTOM_DATA, null) : item.getNBT();
        NBTCompound values = data == null ? null : data.getCompoundTagOrNull(BUKKIT_VALUES);
        if (values == null || !(values.getTagOrNull(tagName) instanceof NBTByteArray payload)) return item;

        ItemStack result = item.copy();
        NBTCompound clean = data.copy();
        NBTCompound cleanValues = clean.getCompoundTagOrNull(BUKKIT_VALUES);
        cleanValues.removeTag(tagName);
        if (cleanValues.isEmpty()) clean.removeTag(BUKKIT_VALUES);
        String footer = new String(payload.getValue(), StandardCharsets.UTF_8);
        if (modern) {
            if (clean.isEmpty()) result.unsetComponent(ComponentTypes.CUSTOM_DATA);
            else result.setComponent(ComponentTypes.CUSTOM_DATA, clean);
            List<Component> lines = new ArrayList<>(item.getComponentOr(ComponentTypes.LORE, ItemLore.EMPTY).getLines());
            footer.lines().map(JSON::deserialize).forEach(lines::add);
            result.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        } else {
            appendLegacy(clean, footer);
            result.setNBT(clean);
        }
        return result;
    }

    private static void appendLegacy(NBTCompound root, String footer) {
        NBTCompound display = root.getCompoundTagOrNull("display");
        if (display == null) {
            display = new NBTCompound();
            root.setTag("display", display);
        }
        NBTList<NBTString> lore = display.getStringListTagOrNull("Lore");
        if (lore == null) {
            lore = new NBTList<>(NBTType.STRING);
            display.setTag("Lore", lore);
        }
        footer.lines().map(NBTString::new).forEach(lore::addTag);
    }
}
