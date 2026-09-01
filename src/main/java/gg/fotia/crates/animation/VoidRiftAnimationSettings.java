package gg.fotia.crates.animation;

import org.bukkit.configuration.ConfigurationSection;

public record VoidRiftAnimationSettings(
        double radius,
        double riftHeight,
        double resultHeight,
        int itemCount,
        double rotations,
        int particleCount
) {

    private static final VoidRiftAnimationSettings DEFAULTS =
            new VoidRiftAnimationSettings(1.9, 2.6, 1.45, 7, 1.8, 18);

    public static VoidRiftAnimationSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        return new VoidRiftAnimationSettings(
                clamp(section.getDouble("radius", DEFAULTS.radius), 0.8, 4.0),
                clamp(section.getDouble("rift-height", DEFAULTS.riftHeight), 1.2, 6.0),
                clamp(section.getDouble("result-height", DEFAULTS.resultHeight), 0.5, 4.0),
                clamp(section.getInt("item-count", DEFAULTS.itemCount), 4, 12),
                clamp(section.getDouble("rotations", DEFAULTS.rotations), 0.5, 5.0),
                clamp(section.getInt("particle-count", DEFAULTS.particleCount), 8, 48)
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
