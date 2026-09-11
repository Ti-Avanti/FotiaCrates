package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared inventory layout and configurable item rendering, independent of editor actions. */
public final class GuiRenderSupport {
    private final FotiaCrates plugin;
    private final GuiConfigManager configManager;

    public GuiRenderSupport(FotiaCrates plugin, GuiConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void fillBackground(Inventory inventory, GuiConfig config) {
        if (!config.isFillEnabled()) return;

        ItemStack fillItem = new ItemBuilder(config.getFillMaterial())
                .name(config.getFillName())
                .build();

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }
    }

    public Inventory createConfiguredInventory(String guiId, CrateGuiHolder holder,
                                                String fallbackTitle, int fallbackSize,
                                                Map<String, String> placeholders) {
        GuiConfig config = configManager.getGuiConfig(guiId);
        String title = config != null ? config.getTitle() : fallbackTitle;
        int size = config != null ? config.getSize() : fallbackSize;
        Inventory inventory = Bukkit.createInventory(holder, size, MessageUtil.parse(applyPlaceholders(title, placeholders)));
        holder.setInventory(inventory);

        if (config != null) {
            fillBackground(inventory, config);
        } else {
            ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, fill);
            }
        }
        return inventory;
    }

    public ItemStack configuredItem(GuiConfig config, String action, ItemStack fallback,
                                     Map<String, String> placeholders) {
        GuiItem item = config != null ? config.getItemByAction(action) : null;
        if (item == null) {
            return fallback;
        }
        return buildConfiguredItem(item, placeholders);
    }

    public ItemStack buildConfiguredItem(GuiItem guiItem, Map<String, String> placeholders) {
        String name = applyPlaceholders(guiItem.getName(), placeholders);
        List<String> lore = applyPlaceholders(guiItem.getLore(), placeholders);
        ItemBuilder builder = new ItemBuilder(guiItem.getMaterial())
                .name(name)
                .lore(lore);
        if (guiItem.getCustomModelData() > 0) {
            builder.customModelData(guiItem.getCustomModelData());
        }
        if (guiItem.isGlow()) {
            builder.glow(true);
        }
        return builder.build();
    }

    public void setConfiguredItem(Inventory inventory, GuiConfig config, String action,
                                   int fallbackSlot, ItemStack fallback,
                                   Map<String, String> placeholders) {
        int slot = config != null ? config.getSlotByAction(action, fallbackSlot) : fallbackSlot;
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, configuredItem(config, action, fallback, placeholders));
    }

    public void setConfiguredItems(Inventory inventory, GuiConfig config, String action,
                                    List<Integer> fallbackSlots, ItemStack fallback,
                                    Map<String, String> placeholders) {
        List<Integer> slots = config != null ? config.getSlotsByAction(action) : List.of();
        if (slots.isEmpty()) {
            slots = fallbackSlots;
        }
        ItemStack item = configuredItem(config, action, fallback, placeholders);
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }
    }

    public String getConfiguredAction(String guiId, int slot) {
        GuiConfig config = configManager.getGuiConfig(guiId);
        GuiItem item = config != null ? config.getItem(slot) : null;
        return item != null ? item.getAction() : null;
    }

    public int getConfiguredSlot(String guiId, String action, int fallback) {
        GuiConfig config = configManager.getGuiConfig(guiId);
        return config != null ? config.getSlotByAction(action, fallback) : fallback;
    }

    public List<Integer> getConfiguredContentSlots(String guiId, List<Integer> fallback) {
        GuiConfig config = configManager.getGuiConfig(guiId);
        if (config == null || config.getContentSlots().isEmpty()) {
            return new ArrayList<>(fallback);
        }
        return config.getContentSlots();
    }

    public String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    public List<String> applyPlaceholders(List<String> input, Map<String, String> placeholders) {
        if (input == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(input);
        if (placeholders == null || placeholders.isEmpty()) {
            return result;
        }
        result.replaceAll(line -> applyPlaceholders(line, placeholders));
        return result;
    }

    public void placeFixedItems(Inventory inventory, GuiConfig config, Player player, Crate crate) {
        placeFixedItemsWithPlaceholders(inventory, config, player, crate, new HashMap<>());
    }

    public void placeFixedItemsWithPlaceholders(Inventory inventory, GuiConfig config,
                                                  Player player, Crate crate,
                                                  Map<String, String> extraPlaceholders) {
        placeFixedItemsWithPlaceholders(inventory, config, player, crate, extraPlaceholders, null);
    }

    public void placeFixedItemsWithPlaceholders(Inventory inventory, GuiConfig config,
                                                  Player player, Crate crate,
                                                  Map<String, String> extraPlaceholders,
                                                  GuiPaginationState paginationState) {
        // 公共占位符只算一次：{keys} 触发全背包扫描，不能放进每个物品的循环里；
        // 调用方已算好的值（extraPlaceholders）优先，不再重复计算
        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        if (player != null) {
            placeholders.put("{player}", player.getName());
        }
        if (crate != null) {
            placeholders.put("{crate}", crate.getName());
            if (player != null && !placeholders.containsKey("{keys}")) {
                placeholders.put("{keys}", String.valueOf(plugin.getKeyManager().getTotalKeysForCrate(player, crate.getId())));
            }
            placeholders.put("{reward_count}", String.valueOf(crate.getRewards().size()));
        }

        for (Map.Entry<Integer, GuiItem> entry : config.getItems().entrySet()) {
            int slot = entry.getKey();
            GuiItem guiItem = entry.getValue();
            GuiItemDisplay display = GuiItemDisplayResolver.resolve(guiItem, paginationState);

            String name = display.name();
            List<String> lore = new ArrayList<>(display.lore());

            for (Map.Entry<String, String> ph : placeholders.entrySet()) {
                name = name.replace(ph.getKey(), ph.getValue());
                lore.replaceAll(line -> line.replace(ph.getKey(), ph.getValue()));
            }

            ItemBuilder builder = new ItemBuilder(display.material())
                    .name(name)
                    .lore(lore);

            if (display.customModelData() > 0) {
                builder.customModelData(display.customModelData());
            }
            if (display.glow()) {
                builder.glow(true);
            }
            builder.itemModel(display.itemModel());

            inventory.setItem(slot, builder.build());
        }
    }
}
