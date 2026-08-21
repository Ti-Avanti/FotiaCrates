package gg.fotia.crates.gui;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * GUI物品配置
 */
public class GuiItem {

    private final int slot;
    private GuiItemDisplay display;
    private final GuiItemDisplay unavailableDisplay;
    private final String action; // 点击动作
    private final String actionValue; // 动作参数

    public GuiItem(int slot, Material material, String name, List<String> lore,
                   int customModelData, boolean glow, String action, String actionValue) {
        this(slot, new GuiItemDisplay(material, name, lore, customModelData, glow, ""),
                null, action, actionValue);
    }

    public GuiItem(int slot, GuiItemDisplay display, GuiItemDisplay unavailableDisplay,
                   String action, String actionValue) {
        this.slot = slot;
        this.display = display;
        this.unavailableDisplay = unavailableDisplay;
        this.action = action;
        this.actionValue = actionValue;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return display.material();
    }

    public String getName() {
        return display.name();
    }

    public List<String> getLore() {
        return display.lore();
    }

    public int getCustomModelData() {
        return display.customModelData();
    }

    public boolean isGlow() {
        return display.glow();
    }

    public String getAction() {
        return action;
    }

    public String getActionValue() {
        return actionValue;
    }

    public String getItemModel() {
        return display.itemModel();
    }

    public void setItemModel(String itemModel) {
        this.display = new GuiItemDisplay(display.material(), display.name(), display.lore(),
                display.customModelData(), display.glow(), itemModel);
    }

    public GuiItemDisplay getDisplay() {
        return display;
    }

    public GuiItemDisplay getUnavailableDisplay() {
        return unavailableDisplay;
    }
}
