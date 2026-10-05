package gg.fotia.crates.reward.config;

import org.bukkit.inventory.ItemStack;

/** 奖励解析不直接加载可选插件的类。 */
public interface ExternalRewardItems {
    final class Pending extends IllegalStateException {
        public Pending(String message) { super(message); }
    }

    ExternalRewardItems NONE = new ExternalRewardItems() {};
    default ItemStack create(String id) { throw new IllegalArgumentException("CraftEngine 插件未启用"); }
    default boolean exists(String id) { return false; }
}
