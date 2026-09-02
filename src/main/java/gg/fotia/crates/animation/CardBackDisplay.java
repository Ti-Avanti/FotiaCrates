package gg.fotia.crates.animation;

import gg.fotia.crates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 秘匣翻牌动画的牌背显示配置。
 */
public record CardBackDisplay(
        Material material,
        String name,
        List<String> lore,
        int customModelData,
        String itemModel,
        boolean glow
) {

    private static final Material DEFAULT_MATERIAL = Material.PURPLE_STAINED_GLASS_PANE;
    private static final String DEFAULT_NAME = "<!i><light_purple>神秘奖励";

    public CardBackDisplay {
        if (!isUsableItem(material)) {
            material = DEFAULT_MATERIAL;
        }
        name = name == null ? DEFAULT_NAME : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
        customModelData = Math.max(0, customModelData);
        itemModel = itemModel == null ? "" : itemModel;
    }

    static CardBackDisplay from(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        Material material = Material.matchMaterial(
                section.getString("back-material", DEFAULT_MATERIAL.name()));
        String itemModel = section.contains("back-item-model")
                ? section.getString("back-item-model", "")
                : section.getString("back-item_model", "");
        return new CardBackDisplay(
                material,
                section.getString("back-name", DEFAULT_NAME),
                section.getStringList("back-lore"),
                section.getInt("back-custom-model-data", 0),
                itemModel,
                section.getBoolean("back-glow", false)
        );
    }

    ItemStack createItem(boolean animatedGlow) {
        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .customModelData(customModelData)
                .itemModel(itemModel)
                .glow(glow || animatedGlow)
                .build();
    }

    private static CardBackDisplay defaults() {
        return new CardBackDisplay(DEFAULT_MATERIAL, DEFAULT_NAME, List.of(), 0, "", false);
    }

    private static boolean isUsableItem(Material material) {
        return material != null && material != Material.AIR;
    }
}
