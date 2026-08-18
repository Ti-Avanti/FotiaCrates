package gg.fotia.crates.crate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class CrateBlockItemTemplate {

    private CrateBlockItemTemplate() {
    }

    public static boolean isUsable(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    public static ItemStack createBase(ItemStack template, Material fallbackMaterial) {
        if (isUsable(template)) {
            return template.clone();
        }
        Material material = fallbackMaterial != null ? fallbackMaterial : Material.CHEST;
        return new ItemStack(material);
    }

    public static ItemStack copyForStorage(ItemStack item) {
        if (!isUsable(item)) {
            return null;
        }
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }
}
