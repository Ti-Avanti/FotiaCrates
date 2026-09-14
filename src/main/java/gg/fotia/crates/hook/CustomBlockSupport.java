package gg.fotia.crates.hook;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** 不依赖第三方 API 的可选自定义方块边界。 */
public interface CustomBlockSupport {
    CustomBlockSupport NONE = new CustomBlockSupport() {};

    default boolean isReady() { return true; }
    default boolean isCustomBlockItem(ItemStack item) { return false; }
    default boolean isCustomBlock(Block block) { return false; }
}
