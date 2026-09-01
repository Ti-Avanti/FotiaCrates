package gg.fotia.crates.crate;

/**
 * Determines whether a prepared multi-open batch should enter the animation pipeline.
 */
public final class MultiOpenAnimationPolicy {

    private MultiOpenAnimationPolicy() {
    }

    public static boolean shouldPlayAnimation(boolean enabled, int preparedRewardCount) {
        return enabled && preparedRewardCount > 1;
    }
}
