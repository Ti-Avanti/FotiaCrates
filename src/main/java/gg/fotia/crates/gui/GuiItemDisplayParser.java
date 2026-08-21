package gg.fotia.crates.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * 将YAML节点解析为完整显示数据，并支持从正常状态继承缺省字段。
 */
final class GuiItemDisplayParser {

    private GuiItemDisplayParser() {
    }

    static GuiItemDisplay parse(ConfigurationSection section, GuiItemDisplay fallback) {
        Material fallbackMaterial = fallback == null ? Material.STONE : fallback.material();
        Material material = parseMaterial(section.getString("material"), fallbackMaterial);
        String name = section.contains("name")
                ? section.getString("name", "")
                : fallback == null ? "" : fallback.name();
        List<String> lore = section.contains("lore")
                ? section.getStringList("lore")
                : fallback == null ? List.of() : fallback.lore();
        int customModelData = section.contains("custom-model-data")
                ? Math.max(0, section.getInt("custom-model-data"))
                : fallback == null ? 0 : fallback.customModelData();
        boolean glow = section.contains("glow")
                ? section.getBoolean("glow")
                : fallback != null && fallback.glow();
        String itemModel = resolveItemModel(section, fallback);
        return new GuiItemDisplay(material, name, lore, customModelData, glow, itemModel);
    }

    private static Material parseMaterial(String configured, Material fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(configured);
        return material != null ? material : fallback;
    }

    private static String resolveItemModel(ConfigurationSection section, GuiItemDisplay fallback) {
        if (section.contains("item-model")) {
            return section.getString("item-model", "");
        }
        if (section.contains("item_model")) {
            return section.getString("item_model", "");
        }
        return fallback == null ? "" : fallback.itemModel();
    }
}
