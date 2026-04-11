package gg.fotia.crates.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface Reward {
    String getId();
    String getDisplayName();
    String getRarity();
    double getChance();
    boolean shouldBroadcast();
    RewardType getType();
    ItemStack getDisplayItem();
    void give(Player player);

    default ItemStack getItem() {
        return null;
    }

    default List<ItemStack> getExtraItems() {
        return List.of();
    }

    default List<String> getCommands() {
        return List.of();
    }

    default boolean isPermissionCheckEnabled() {
        return false;
    }

    default String getCheckPermission() {
        return null;
    }

    default PermissionAction getPermissionAction() {
        return PermissionAction.SKIP;
    }

    default String getAlternativeRewardId() {
        return null;
    }
}
