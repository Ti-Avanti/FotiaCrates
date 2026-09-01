package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationRewardBatchPolicyTest {

    @Test
    void cardRevealReceivesAllPreparedRewards() {
        assertEquals(List.of("a", "b", "c"), AnimationRewardBatchPolicy.forType(
                AnimationType.CARD_REVEAL, List.of("a", "b", "c")));
    }

    @Test
    void nonInteractiveAnimationsKeepTheFirstPreparedReward() {
        assertEquals(List.of("a"), AnimationRewardBatchPolicy.forType(
                AnimationType.VOID_RIFT, List.of("a", "b", "c")));
    }

    @Test
    void removedTripleReelAliasUsesCardBatchBehavior() {
        assertEquals(List.of("a", "b", "c"), AnimationRewardBatchPolicy.forType(
                AnimationType.TRIPLE_REEL, List.of("a", "b", "c")));
    }
}
