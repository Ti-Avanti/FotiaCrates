package gg.fotia.crates.animation;

import org.bukkit.configuration.ConfigurationSection;

public record MeteorAnimationSettings(
        double skyHeight,
        double spreadRadius,
        double impactRadius,
        double resultHeight,
        int decoyCount,
        int staggerTicks,
        int fallTicks,
        int trailParticleCount
) {

    private static final MeteorAnimationSettings DEFAULTS =
            new MeteorAnimationSettings(6.0, 4.0, 2.2, 1.45, 4, 7, 18, 3);

    public static MeteorAnimationSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        return new MeteorAnimationSettings(
                clamp(section.getDouble("sky-height", DEFAULTS.skyHeight), 3.0, 12.0),
                clamp(section.getDouble("spread-radius", DEFAULTS.spreadRadius), 1.5, 8.0),
                clamp(section.getDouble("impact-radius", DEFAULTS.impactRadius), 0.8, 5.0),
                clamp(section.getDouble("result-height", DEFAULTS.resultHeight), 0.5, 4.0),
                clamp(section.getInt("decoy-count", DEFAULTS.decoyCount), 2, 8),
                clamp(section.getInt("stagger-ticks", DEFAULTS.staggerTicks), 2, 20),
                clamp(section.getInt("fall-ticks", DEFAULTS.fallTicks), 8, 50),
                clamp(section.getInt("trail-particle-count", DEFAULTS.trailParticleCount), 1, 8)
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
