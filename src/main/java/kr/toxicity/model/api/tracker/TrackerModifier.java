package kr.toxicity.model.api.tracker;

/**
 * Minimal compile-time stub for BetterModel 3.x. Excluded from the plugin jar.
 */
public final class TrackerModifier {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        public Builder sightTrace(boolean sightTrace) {
            return this;
        }

        public Builder damageAnimation(boolean damageAnimation) {
            return this;
        }

        public Builder damageTint(boolean damageTint) {
            return this;
        }

        public TrackerModifier build() {
            return new TrackerModifier();
        }
    }
}
