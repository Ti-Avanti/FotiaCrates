package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.hook.SweetMailHook;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class RewardItemDeliveryService {

    private final FotiaCrates plugin;
    private final SweetMailHook sweetMailHook;

    public RewardItemDeliveryService(FotiaCrates plugin) {
        this.plugin = plugin;
        this.sweetMailHook = new SweetMailHook(plugin);
    }

    public void deliver(Player player, Collection<ItemStack> items) {
        List<ItemStack> overflowItems = addToInventory(player, items);
        if (overflowItems.isEmpty()) {
            return;
        }

        int mailedCount = 0;
        List<ItemStack> droppedItems = new ArrayList<>();

        if (plugin.getConfigManager().isOverflowMailEnabled() && sweetMailHook.isAvailable()) {
            List<ItemStack> mailItems = new ArrayList<>();
            for (ItemStack overflowItem : overflowItems) {
                if (sweetMailHook.isMailable(overflowItem)) {
                    mailItems.add(overflowItem);
                } else {
                    droppedItems.add(overflowItem);
                }
            }

            if (!mailItems.isEmpty()) {
                if (sweetMailHook.sendOverflowMail(player, mailItems)) {
                    mailedCount = mailItems.size();
                } else {
                    droppedItems.addAll(mailItems);
                }
            }
        } else {
            droppedItems.addAll(overflowItems);
        }

        if (!droppedItems.isEmpty()) {
            dropItems(player, droppedItems);
        }

        if (mailedCount > 0 && droppedItems.isEmpty()) {
            plugin.getLanguageManager().send(player, "overflow-mail-sent",
                    LanguageManager.placeholders("count", String.valueOf(mailedCount)));
            return;
        }

        if (mailedCount > 0) {
            plugin.getLanguageManager().send(player, "overflow-mail-partial",
                    LanguageManager.placeholders(
                            "mailed", String.valueOf(mailedCount),
                            "dropped", String.valueOf(droppedItems.size())
                    ));
            return;
        }

        plugin.getLanguageManager().send(player, "inventory-full");
    }

    private List<ItemStack> addToInventory(Player player, Collection<ItemStack> items) {
        List<ItemStack> overflowItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            overflowItems.addAll(leftover.values());
        }
        return overflowItems;
    }

    private void dropItems(Player player, Collection<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
