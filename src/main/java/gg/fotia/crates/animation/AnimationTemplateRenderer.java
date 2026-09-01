package gg.fotia.crates.animation;

import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;
import gg.fotia.crates.util.ItemBuilder;
import org.bukkit.inventory.Inventory;

public final class AnimationTemplateRenderer {

    private AnimationTemplateRenderer() {
    }

    public static void render(Inventory inventory, GuiConfig config) {
        if (inventory == null || config == null) {
            return;
        }
        for (AnimationTemplatePlacements.Placement placement
                : AnimationTemplatePlacements.collect(config, inventory.getSize())) {
            GuiItem item = placement.item();
            inventory.setItem(placement.slot(), new ItemBuilder(item.getMaterial())
                    .name(item.getName())
                    .lore(item.getLore())
                    .customModelData(item.getCustomModelData())
                    .itemModel(item.getItemModel())
                    .glow(item.isGlow())
                    .build());
        }
    }
}
