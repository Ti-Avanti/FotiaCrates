package gg.fotia.crates.crate;

/**
 * Multi-open animations intentionally show only the first confirmed result.
 */
public final class MultiOpenAnimationPolicy {

    private MultiOpenAnimationPolicy() {
    }

    public static boolean shouldPlayFirstDrawAnimation(boolean enabled, int preparedRewardCount) {
        return enabled && preparedRewardCount > 1;
    }
}
