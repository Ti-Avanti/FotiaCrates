package gg.fotia.crates.animation;

import java.util.List;

public final class AnimationRewardBatchPolicy {

    private AnimationRewardBatchPolicy() {
    }

    public static <T> List<T> forType(AnimationType type, List<T> preparedRewards) {
        if (preparedRewards == null || preparedRewards.isEmpty()) {
            return List.of();
        }
        List<T> rewards = List.copyOf(preparedRewards);
        return type != null && type.templateFamily() == AnimationType.CARD_REVEAL
                ? rewards
                : List.of(rewards.get(0));
    }
}
