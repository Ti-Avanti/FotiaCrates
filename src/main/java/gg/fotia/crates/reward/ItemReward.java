package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemReward extends AbstractReward {

    private final ItemStack item;
    private final List<ItemStack> extraItems;
    private final List<String> commands;

    public ItemReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                      ItemStack displayItem, ItemStack item, List<ItemStack> extraItems) {
        this(id, displayName, rarity, chance, broadcast, displayItem, item, extraItems,
                List.of(), false, null, PermissionAction.SKIP, null);
    }

    public ItemReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                      ItemStack displayItem, ItemStack item, List<ItemStack> extraItems, List<String> commands) {
        this(id, displayName, rarity, chance, broadcast, displayItem, item, extraItems,
                commands, false, null, PermissionAction.SKIP, null);
    }

    public ItemReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                      ItemStack displayItem, ItemStack item, List<ItemStack> extraItems,
                      boolean permissionCheckEnabled, String checkPermission,
                      PermissionAction permissionAction, String alternativeRewardId) {
        this(id, displayName, rarity, chance, broadcast, displayItem, item, extraItems,
                List.of(), permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId);
    }

    public ItemReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                      ItemStack displayItem, ItemStack item, List<ItemStack> extraItems, List<String> commands,
                      boolean permissionCheckEnabled, String checkPermission,
                      PermissionAction permissionAction, String alternativeRewardId) {
        super(id, displayName, rarity, chance, broadcast, displayItem,
                permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId);
        this.item = item != null ? item.clone() : null;
        this.extraItems = extraItems != null ? new ArrayList<>(extraItems) : new ArrayList<>();
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
    }

    @Override
    public RewardType getType() {
        return RewardType.ITEM;
    }

    @Override
    public void give(Player player) {
        FotiaCrates.getInstance().getRewardItemDeliveryService().deliver(player, getRewardItems());

        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            String parsed = command.replace("%player%", player.getName())
                    .replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private List<ItemStack> getRewardItems() {
        List<ItemStack> items = new ArrayList<>();
        if (item != null && !item.getType().isAir()) {
            items.add(item.clone());
        }
        for (ItemStack extra : extraItems) {
            if (extra != null && !extra.getType().isAir()) {
                items.add(extra.clone());
            }
        }
        return items;
    }

    @Override
    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    @Override
    public List<ItemStack> getExtraItems() {
        return new ArrayList<>(extraItems);
    }

    @Override
    public List<String> getCommands() {
        return new ArrayList<>(commands);
    }
}
