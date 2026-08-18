package gg.fotia.crates.animation;

import gg.fotia.crates.gui.GuiConfig;
import org.bukkit.Material;

import java.util.List;

public record AnimationTemplate(
        String id,
        String displayName,
        Material selectorMaterial,
        String selectorName,
        List<String> selectorLore,
        int selectorCustomModelData,
        String selectorItemModel,
        boolean selectorGlow,
        GuiConfig guiConfig
) {

    public AnimationTemplate {
        selectorLore = selectorLore == null ? List.of() : List.copyOf(selectorLore);
        selectorItemModel = selectorItemModel == null ? "" : selectorItemModel;
    }
}
