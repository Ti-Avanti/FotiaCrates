package gg.fotia.crates.animation;

import org.bukkit.configuration.ConfigurationSection;

public record OrbitalAnimationSettings(
        double radius,
        double height,
        int itemCount,
        double rotations
) {

    private static final OrbitalAnimationSettings DEFAULTS =
            new OrbitalAnimationSettings(1.4, 1.5, 8, 2.5);

    public static OrbitalAnimationSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        return new OrbitalAnimationSettings(
                clamp(section.getDouble("radius", DEFAULTS.radius), 0.5, 3.0),
                clamp(section.getDouble("height", DEFAULTS.height), 0.5, 4.0),
                Math.max(4, Math.min(12, section.getInt("item-count", DEFAULTS.itemCount))),
                clamp(section.getDouble("rotations", DEFAULTS.rotations), 0.5, 6.0)
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
