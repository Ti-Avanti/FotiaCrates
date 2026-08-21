package gg.fotia.crates.gui;

import org.bukkit.Material;

import java.util.List;

/**
 * GUI按钮单个状态的显示数据。
 */
public record GuiItemDisplay(Material material, String name, List<String> lore,
                             int customModelData, boolean glow, String itemModel) {

    public GuiItemDisplay {
        material = material == null ? Material.STONE : material;
        name = name == null ? "" : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
        customModelData = Math.max(0, customModelData);
        itemModel = itemModel == null ? "" : itemModel;
    }
}
