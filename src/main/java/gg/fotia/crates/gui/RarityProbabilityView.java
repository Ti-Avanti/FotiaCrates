package gg.fotia.crates.gui;

import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Config-driven rendering shared by the rarity picker and probability preview. */
final class RarityProbabilityView {
    private RarityProbabilityView() {
    }

    static Inventory create(GuiConfig config, CrateGuiHolder holder, Map<String, String> values) {
        Inventory inventory = Bukkit.createInventory(holder, config.getSize(), MessageUtil.parse(replace(config.getTitle(), values)));
        holder.setInventory(inventory);
        if (config.isFillEnabled()) {
            ItemStack fill = new ItemBuilder(config.getFillMaterial()).name(config.getFillName()).build();
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, fill);
        }
        for (var entry : config.getItems().entrySet()) {
            String action = entry.getValue().getAction();
            if ("select_rarity".equals(action) || "probability_reward".equals(action)) continue;
            inventory.setItem(entry.getKey(), item(entry.getValue(), values));
        }
        return inventory;
    }

    static ItemStack item(GuiItem item, Map<String, String> values) {
        return new ItemBuilder(item.getMaterial()).name(replace(item.getName(), values))
                .lore(item.getLore().stream().map(line -> replace(line, values)).toList())
                .customModelData(item.getCustomModelData()).itemModel(item.getItemModel()).glow(item.isGlow()).build();
    }

    static List<Integer> content(GuiConfig config) {
        return config.getContentSlots().stream().filter(slot -> slot >= 0 && slot < config.getSize())
                .filter(slot -> {
                    GuiItem item = config.getItem(slot);
                    return item == null || "select_rarity".equals(item.getAction()) || "probability_reward".equals(item.getAction());
                }).distinct().toList();
    }

    private static String replace(String text, Map<String, String> values) {
        String result = text == null ? "" : text;
        for (var entry : values.entrySet()) result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        return result;
    }
}
