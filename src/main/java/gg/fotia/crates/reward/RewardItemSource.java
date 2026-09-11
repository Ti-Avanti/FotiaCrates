package gg.fotia.crates.reward;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 区分旧配置的图标奖励回退与显式空奖励，并统一首个物品的选择。 */
public final class RewardItemSource {
    private RewardItemSource() {
    }

    public static boolean usesLegacyDisplay(ConfigurationSection section) {
        return "ITEM".equalsIgnoreCase(section.getString("type", "item"))
                && !section.contains("item");
    }

    public static ItemStack first(ItemStack main, List<ItemStack> extras) {
        if (isPresent(main)) return main;
        for (ItemStack extra : extras) {
            if (isPresent(extra)) return extra;
        }
        return null;
    }

    public static boolean isPresent(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }
}
