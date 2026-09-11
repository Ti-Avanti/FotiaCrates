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

import gg.fotia.crates.key.Key;

/** Key list, key properties and allowed-crate selection views. */
public final class KeyEditorGui {
    private static final List<Integer> DEFAULT_CONTENT_SLOTS_21 = List.of(
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
    private final FotiaCrates plugin;
    private final GuiConfigManager configManager;
    private final GuiRenderSupport renderer;

    public KeyEditorGui(FotiaCrates plugin, GuiConfigManager configManager, GuiRenderSupport renderer) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.renderer = renderer;
    }

    public void openKeysGui(Player player) {
        GuiConfig config = configManager.getGuiConfig("admin_keys");
        if (config == null) {
            plugin.getLogger().warning("Admin keys GUI config not found!");
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_KEYS, null),
                config.getSize(),
                MessageUtil.parse(config.getTitle())
        );

        // 填充背景
        renderer.fillBackground(inventory, config);

        // 放置固定物品（带占位符替换）
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{key_count}", String.valueOf(plugin.getKeyManager().getKeyIds().size()));
        renderer.placeFixedItemsWithPlaceholders(inventory, config, player, null, placeholders);

        // 放置钥匙列表
        List<Integer> contentSlots = config.getContentSlots();
        int slotIndex = 0;
        for (String keyId : plugin.getKeyManager().getKeyIds()) {
            if (slotIndex >= contentSlots.size()) break;

            Key key = plugin.getKeyManager().getKey(keyId);
            if (key == null) continue;

            int slot = contentSlots.get(slotIndex);
            ItemStack keyItem = createAdminKeyItem(key);
            inventory.setItem(slot, keyItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    public void openKeyEditGui(Player player, Key key) {
        GuiConfig config = configManager.getGuiConfig("admin_key_edit");
        if (config == null) {
            plugin.getLogger().warning("Admin key edit GUI config not found!");
            return;
        }

        String title = config.getTitle()
                .replace("{key}", MessageUtil.stripColor(key.getName()));
        CrateGuiHolder holder = new CrateGuiHolder(GuiType.ADMIN_KEY_EDIT, null);
        holder.setData("key_id", key.getId());

        Inventory inventory = Bukkit.createInventory(
                holder,
                config.getSize(),
                MessageUtil.parse(title)
        );

        // 填充背景
        renderer.fillBackground(inventory, config);

        // 准备占位符
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{key}", key.getName());
        placeholders.put("{key_id}", key.getId());
        placeholders.put("{crate_count}", String.valueOf(key.getCrateIds().size()));
        placeholders.put("{glow}", key.isGlow() ? "是" : "否");

        renderer.placeFixedItemsWithPlaceholders(inventory, config, player, null, placeholders);

        // 放置可开启的宝箱列表
        List<Integer> contentSlots = config.getContentSlots();
        int slotIndex = 0;
        for (String crateId : key.getCrateIds()) {
            if (slotIndex >= contentSlots.size()) break;

            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate == null) continue;

            int slot = contentSlots.get(slotIndex);
            ItemStack crateItem = createKeyEditCrateItem(crate);
            inventory.setItem(slot, crateItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    private ItemStack createAdminKeyItem(Key key) {
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>ID: <!i><white>" + key.getId());
        lore.add("<!i><gray>可开启宝箱: <!i><white>" + key.getCrateIds().size() + "个");
        lore.add("");
        lore.add("<!i><yellow>左键 <!i><gray>- 编辑钥匙");
        lore.add("<!i><red>Shift+右键 <!i><gray>- 删除钥匙");

        ItemBuilder builder = new ItemBuilder(key.getItem())
                .name(key.getName())
                .lore(lore);

        if (key.isGlow()) {
            builder.glow(true);
        }

        return builder.build();
    }

    private ItemStack createKeyEditCrateItem(Crate crate) {
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>ID: <!i><white>" + crate.getId());
        lore.add("");
        lore.add("<!i><red>Shift+右键 <!i><gray>- 移除此宝箱");

        return new ItemBuilder(crate.getBlockMaterial())
                .name(crate.getName())
                .lore(lore)
                .build();
    }

    public void openCrateSelectGui(Player player, Key key) {
        if (openConfiguredCrateSelectGui(player, key)) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_KEY_EDIT, null),
                54,
                MessageUtil.parse("<!i><dark_gray>选择宝箱")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("key_id", key.getId());
        holder.setData("select_mode", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回钥匙编辑界面"))
                .build();
        inventory.setItem(45, back);

        // 放置宝箱列表
        List<Integer> contentSlots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        int slotIndex = 0;
        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            if (slotIndex >= contentSlots.size()) break;

            // 跳过已添加的宝箱
            if (key.getCrateIds().contains(crate.getId())) continue;

            int slot = contentSlots.get(slotIndex);
            ItemStack crateItem = new ItemBuilder(crate.getBlockMaterial())
                    .name(crate.getName())
                    .lore(List.of(
                            "<!i><gray>ID: <!i><white>" + crate.getId(),
                            "",
                            "<!i><yellow>左键点击添加"
                    ))
                    .build();
            inventory.setItem(slot, crateItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    private boolean openConfiguredCrateSelectGui(Player player, Key key) {
        String guiId = "admin_crate_select";
        GuiConfig config = configManager.getGuiConfig(guiId);
        if (config == null) return false;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{key}", key.getName());
        placeholders.put("{key_id}", key.getId());

        CrateGuiHolder holder = new CrateGuiHolder(GuiType.ADMIN_KEY_EDIT, null);
        holder.setData("key_id", key.getId());
        holder.setData("select_mode", true);
        Inventory inventory = renderer.createConfiguredInventory(guiId, holder, "<!i><dark_gray>选择宝箱", 54, placeholders);
        renderer.placeFixedItemsWithPlaceholders(inventory, config, player, null, placeholders);

        List<Integer> contentSlots = renderer.getConfiguredContentSlots(guiId, DEFAULT_CONTENT_SLOTS_21);
        int slotIndex = 0;
        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            if (key.getCrateIds().contains(crate.getId())) continue;
            if (slotIndex >= contentSlots.size()) break;
            inventory.setItem(contentSlots.get(slotIndex++), new ItemBuilder(crate.getBlockMaterial())
                    .name(crate.getName())
                    .lore(List.of(
                            "<!i><gray>ID: <!i><white>" + crate.getId(),
                            "",
                            "<!i><yellow>左键点击添加"
                    ))
                    .build());
        }
        player.openInventory(inventory);
        return true;
    }
}
