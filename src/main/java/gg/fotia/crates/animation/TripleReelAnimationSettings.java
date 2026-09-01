package gg.fotia.crates.animation;

import org.bukkit.configuration.ConfigurationSection;

public record TripleReelAnimationSettings(int stopGapTicks, int spinIntervalTicks) {

    private static final TripleReelAnimationSettings DEFAULTS =
            new TripleReelAnimationSettings(10, 2);

    public static TripleReelAnimationSettings from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        return new TripleReelAnimationSettings(
                Math.max(2, Math.min(40,
                        section.getInt("stop-gap-ticks", DEFAULTS.stopGapTicks))),
                Math.max(1, Math.min(10,
                        section.getInt("spin-interval-ticks", DEFAULTS.spinIntervalTicks)))
        );
    }
}
