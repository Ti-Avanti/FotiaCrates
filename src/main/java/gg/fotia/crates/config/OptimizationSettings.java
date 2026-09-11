package gg.fotia.crates.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Adds only new controls, preserving all existing administrator choices. */
public final class OptimizationSettings {
    private OptimizationSettings() {
    }

    public static boolean installDefaults(FileConfiguration config) {
        if (config.getDefaults() == null) return false;
        boolean changed = false;
        for (String path : List.of("hologram.update-interval-ticks", "hologram.max-updates-per-tick",
                "persistence.player-load.retry-delay-ticks", "persistence.player-load.max-retry-delay-ticks",
                "persistence.configuration.write-delay-ticks", "persistence.configuration.retry-delay-ticks",
                "performance.multi-open.delivery-batch-size")) {
            if (!config.isSet(path) && config.getDefaults().contains(path)) {
                config.set(path, config.getDefaults().get(path));
                config.setComments(path, config.getDefaults().getComments(path));
                changed = true;
            }
        }
        return changed;
    }
}
