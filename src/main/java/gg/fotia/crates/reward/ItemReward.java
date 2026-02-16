package gg.fotia.crates.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ItemReward extends AbstractReward {

    private final ItemStack item;
    private final List<ItemStack> extraItems;

    public ItemReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                      ItemStack displayItem, ItemStack item, List<ItemStack> extraItems) {
        super(id, displayName, rarity, chance, broadcast, displayItem);
        this.item = item;
        this.extraItems = extraItems != null ? extraItems : new ArrayList<>();
    }

    @Override
    public RewardType getType() { return RewardType.ITEM; }

    @Override
    public void give(Player player) {
        giveItem(player, item.clone());
        for (ItemStack extra : extraItems) {
            giveItem(player, extra.clone());
        }
    }

    private void giveItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    public ItemStack getItem() { return item.clone(); }
    public List<ItemStack> getExtraItems() { return new ArrayList<>(extraItems); }
}
