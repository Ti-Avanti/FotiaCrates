package gg.fotia.crates.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

final class ItemNameComponentResolver {

    private ItemNameComponentResolver() {
    }

    static Component resolve(ItemMeta itemMeta) {
        return itemMeta.hasItemName() ? itemMeta.itemName() : null;
    }
}
