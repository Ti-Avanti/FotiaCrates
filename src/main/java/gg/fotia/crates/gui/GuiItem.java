package gg.fotia.crates.gui;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * GUI物品配置
 */
public class GuiItem {

    private final int slot;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int customModelData;
    private final boolean glow;
    private final String action; // 点击动作
    private final String actionValue; // 动作参数
    private String itemModel; // 物品模型 (1.21.4+)

    public GuiItem(int slot, Material material, String name, List<String> lore,
                   int customModelData, boolean glow, String action, String actionValue) {
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.customModelData = customModelData;
        this.glow = glow;
        this.action = action;
        this.actionValue = actionValue;
        this.itemModel = "";
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public boolean isGlow() {
        return glow;
    }

    public String getAction() {
        return action;
    }

    public String getActionValue() {
        return actionValue;
    }

    public String getItemModel() {
        return itemModel;
    }

    public void setItemModel(String itemModel) {
        this.itemModel = itemModel != null ? itemModel : "";
    }
}
