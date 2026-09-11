package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.gui.CrateGuiHolder;
import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.List;

/** Routes only key-editor actions; general navigation stays in the inventory listener. */
public final class KeyEditorController {
    @FunctionalInterface
    public interface ActionHandler {
        void handle(Player player, String action, String value, CrateGuiHolder holder);
    }

    private final FotiaCrates plugin;
    private final ActionHandler actions;

    public KeyEditorController(FotiaCrates plugin, ActionHandler actions) {
        this.plugin = plugin;
        this.actions = actions;
    }

    public void handleKeysClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("admin_keys");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                actions.handle(player, guiItem.getAction(), guiItem.getActionValue(), holder);
                return;
            }
        }

        // 检查是否点击了钥匙
        List<Integer> contentSlots = config != null ? config.getContentSlots() : List.of();
        int slotIndex = contentSlots.indexOf(slot);
        if (slotIndex >= 0) {
            List<String> keyIds = plugin.getKeyManager().getKeyIds().stream().toList();
            if (slotIndex < keyIds.size()) {
                String keyId = keyIds.get(slotIndex);
                gg.fotia.crates.key.Key key = plugin.getKeyManager().getKey(keyId);
                if (key != null) {
                    if (event.isLeftClick()) {
                        plugin.getGuiManager().openKeyEditGui(player, key);
                    } else if (event.isShiftClick() && event.isRightClick()) {
                        // 删除钥匙
                        plugin.getKeyManager().deleteKey(keyId);
                        plugin.getLanguageManager().send(player, "admin-key-deleted",
                                LanguageManager.placeholders("key", key.getName()));
                        plugin.getGuiManager().openKeysGui(player);
                    }
                }
            }
        }
    }

    public void handleKeyEditClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();
        String keyId = holder.getData("key_id");
        gg.fotia.crates.key.Key key = plugin.getKeyManager().getKey(keyId);

        if (key == null) {
            plugin.getGuiManager().openKeysGui(player);
            return;
        }

        // 检查是否是宝箱选择模式
        Boolean selectMode = holder.getData("select_mode");
        if (selectMode != null && selectMode) {
            handleCrateSelectClick(event, player, holder, key);
            return;
        }

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("admin_key_edit");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleKeyEditAction(player, guiItem.getAction(), key, holder);
                return;
            }
        }

        // 检查是否点击了宝箱（移除宝箱）
        List<Integer> contentSlots = config != null ? config.getContentSlots() : List.of();
        int slotIndex = contentSlots.indexOf(slot);
        if (slotIndex >= 0 && event.isShiftClick() && event.isRightClick()) {
            List<String> crateIds = key.getCrateIds().stream().toList();
            if (slotIndex < crateIds.size()) {
                String crateId = crateIds.get(slotIndex);
                key.removeCrate(crateId);
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-crate-removed",
                        LanguageManager.placeholders("crate", crateId));
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
        }
    }

    private void handleCrateSelectClick(InventoryClickEvent event, Player player, CrateGuiHolder holder, gg.fotia.crates.key.Key key) {
        int slot = event.getSlot();
        String action = plugin.getGuiManager().getConfiguredAction("admin_crate_select", slot);
        if (action == null && slot == 45) action = "back";

        // 返回按钮
        if ("back".equals(action)) {
            plugin.getGuiManager().openKeyEditGui(player, key);
            return;
        }

        // 检查是否点击了宝箱
        List<Integer> contentSlots = plugin.getGuiManager().getConfiguredContentSlots("admin_crate_select",
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34));
        if (contentSlots.contains(slot) && event.isLeftClick()) {
            // 获取点击的宝箱
            int slotIndex = contentSlots.indexOf(slot);
            List<Crate> availableCrates = plugin.getCrateManager().getAllCrates().stream()
                    .filter(c -> !key.getCrateIds().contains(c.getId()))
                    .toList();

            if (slotIndex < availableCrates.size()) {
                Crate crate = availableCrates.get(slotIndex);
                key.addCrate(crate.getId());
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-crate-added",
                        LanguageManager.placeholders("crate", crate.getName()));
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
        }
    }

    private void handleKeyEditAction(Player player, String action, gg.fotia.crates.key.Key key, CrateGuiHolder holder) {
        switch (action.toLowerCase()) {
            case "edit_key_name" -> {
                plugin.getLanguageManager().send(player, "admin-input-name");
                plugin.getGuiManager().startInputSession(player, "key_name", key.getId(), (p, input, data) -> {
                    String keyId = (String) data;
                    gg.fotia.crates.key.Key k = plugin.getKeyManager().getKey(keyId);
                    if (k != null) {
                        k.setName(input);
                        plugin.getKeyManager().saveKey(k);
                        plugin.getLanguageManager().send(p, "admin-key-name-updated",
                                LanguageManager.placeholders("name", input));
                        plugin.getGuiManager().openKeyEditGui(p, k);
                    }
                });
            }
            case "edit_key_item" -> {
                org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    plugin.getLanguageManager().send(player, "reward-hold-item");
                    return;
                }
                key.setItem(item.clone());
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-item-updated");
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
            case "toggle_key_glow" -> {
                key.setGlow(!key.isGlow());
                plugin.getKeyManager().saveKey(key);
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
            case "add_key_crate" -> {
                // 打开宝箱选择界面
                plugin.getGuiManager().openCrateSelectGui(player, key);
            }
            case "save_key" -> {
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-saved");
            }
            case "delete_key" -> {
                plugin.getKeyManager().deleteKey(key.getId());
                plugin.getLanguageManager().send(player, "admin-key-deleted",
                        LanguageManager.placeholders("key", key.getName()));
                plugin.getGuiManager().openKeysGui(player);
            }
            case "open_gui" -> {
                plugin.getGuiManager().openKeysGui(player);
            }
            case "close" -> player.closeInventory();
        }
    }
}
