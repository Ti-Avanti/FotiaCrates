package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CommandReward extends AbstractReward {

    private final List<String> commands;
    private final ItemStack item;
    private final List<ItemStack> extraItems;

    public CommandReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                         ItemStack displayItem, List<String> commands) {
        this(id, displayName, rarity, chance, broadcast, displayItem, commands,
                null, List.of(), false, null, PermissionAction.SKIP, null);
    }

    public CommandReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                         ItemStack displayItem, List<String> commands,
                         ItemStack item, List<ItemStack> extraItems) {
        this(id, displayName, rarity, chance, broadcast, displayItem, commands,
                item, extraItems, false, null, PermissionAction.SKIP, null);
    }

    public CommandReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                         ItemStack displayItem, List<String> commands,
                         boolean permissionCheckEnabled, String checkPermission,
                         PermissionAction permissionAction, String alternativeRewardId) {
        this(id, displayName, rarity, chance, broadcast, displayItem, commands,
                null, List.of(), permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId);
    }

    public CommandReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                         ItemStack displayItem, List<String> commands,
                         ItemStack item, List<ItemStack> extraItems,
                         boolean permissionCheckEnabled, String checkPermission,
                         PermissionAction permissionAction, String alternativeRewardId) {
        this(id, displayName, rarity, chance, broadcast, displayItem, commands, item, extraItems,
                permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId, true, true);
    }

    public CommandReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                         ItemStack displayItem, List<String> commands,
                         ItemStack item, List<ItemStack> extraItems,
                         boolean permissionCheckEnabled, String checkPermission,
                         PermissionAction permissionAction, String alternativeRewardId,
                         boolean autoDisplayIcon, boolean autoDisplayName) {
        super(id, displayName, rarity, chance, broadcast, displayItem,
                permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId,
                autoDisplayIcon, autoDisplayName);
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.item = item != null ? item.clone() : null;
        this.extraItems = extraItems != null ? new ArrayList<>(extraItems) : new ArrayList<>();
    }

    @Override
    public RewardType getType() {
        return RewardType.COMMAND;
    }

    @Override
    public void give(Player player) {
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            String parsed = command.replace("%player%", player.getName())
                    .replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        FotiaCrates.getInstance().getRewardItemDeliveryService().deliver(player, getRewardItems());
    }

    @Override
    public List<String> getCommands() {
        return new ArrayList<>(commands);
    }

    @Override
    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    @Override
    public List<ItemStack> getExtraItems() {
        return new ArrayList<>(extraItems);
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
}
