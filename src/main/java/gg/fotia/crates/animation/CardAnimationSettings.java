package gg.fotia.crates.animation;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record CardAnimationSettings(
        Material backMaterial,
        String backName,
        int revealIntervalTicks
) {

    private static final Material DEFAULT_MATERIAL = Material.PURPLE_STAINED_GLASS_PANE;
    private static final String DEFAULT_NAME = "<!i><light_purple>神秘奖励";
    private static final int DEFAULT_INTERVAL = 5;

    public static CardAnimationSettings from(ConfigurationSection section) {
        if (section == null) {
            return new CardAnimationSettings(DEFAULT_MATERIAL, DEFAULT_NAME, DEFAULT_INTERVAL);
        }
        Material material = Material.matchMaterial(
                section.getString("back-material", DEFAULT_MATERIAL.name()));
        if (material == null || material == Material.AIR) {
            material = DEFAULT_MATERIAL;
        }
        return new CardAnimationSettings(
                material,
                section.getString("back-name", DEFAULT_NAME),
                Math.max(1, Math.min(20,
                        section.getInt("reveal-interval-ticks", DEFAULT_INTERVAL)))
        );
    }
}
