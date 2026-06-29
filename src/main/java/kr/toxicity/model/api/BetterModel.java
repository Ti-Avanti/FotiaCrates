package kr.toxicity.model.api;

import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;

import java.util.Optional;
import java.util.UUID;

/**
 * Minimal compile-time stub for BetterModel 3.x. Excluded from the plugin jar.
 */
public final class BetterModel {

    private BetterModel() {
    }

    public static BetterModelPlatform platform() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public static Optional<ModelRenderer> model(String name) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public static Optional<EntityTrackerRegistry> registry(UUID uuid) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
