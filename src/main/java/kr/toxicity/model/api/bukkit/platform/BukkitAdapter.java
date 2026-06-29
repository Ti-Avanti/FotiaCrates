package kr.toxicity.model.api.bukkit.platform;

import kr.toxicity.model.api.platform.PlatformEntity;
import org.bukkit.entity.Entity;

/**
 * Minimal compile-time stub for BetterModel 3.x. Excluded from the plugin jar.
 */
public final class BukkitAdapter {

    private BukkitAdapter() {
    }

    public static PlatformEntity adapt(Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
