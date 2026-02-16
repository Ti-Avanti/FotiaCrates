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

    /**
     * 获取实际给予玩家的物品
     * @return 奖励物品，如果没有单独设置则返回null
     */
    default ItemStack getItem() {
        return null;
    }

    /**
     * 获取奖励关联的命令列表
     * @return 命令列表，如果没有则返回空列表
     */
    default List<String> getCommands() {
        return List.of();
    }
}
