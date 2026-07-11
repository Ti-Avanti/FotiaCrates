package gg.fotia.crates.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardProbabilityTest {

    @Test
    void convertsConfiguredWeightsIntoNormalizedPercentages() {
        TestReward rare = new TestReward("rare", 1.0);
        TestReward common = new TestReward("common", 3.0);
        TestReward filler = new TestReward("filler", 6.0);

        assertEquals(10.0, RewardProbability.percentage(rare, List.of(rare, common, filler)));
        assertEquals(30.0, RewardProbability.percentage(common, List.of(rare, common, filler)));
        assertEquals("30%", RewardProbability.format(30.0));
        assertEquals("12.35%", RewardProbability.format(12.345));
    }

    @Test
    void samplesAnimationRewardsUsingConfiguredWeights() {
        TestReward rare = new TestReward("rare", 1.0);
        TestReward common = new TestReward("common", 9.0);
        List<Reward> rewards = List.of(rare, common);

        assertEquals(rare, RewardProbability.select(rewards, new FixedRandom(0.09)));
        assertEquals(common, RewardProbability.select(rewards, new FixedRandom(0.10)));
        assertEquals(common, RewardProbability.select(rewards, new FixedRandom(0.99)));
    }

    private static final class FixedRandom extends Random {
        private final double value;

        private FixedRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }

    private record TestReward(String id, double chance) implements Reward {
        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return id;
        }

        @Override
        public String getRarity() {
            return "common";
        }

        @Override
        public double getChance() {
            return chance;
        }

        @Override
        public boolean shouldBroadcast() {
            return false;
        }

        @Override
        public RewardType getType() {
            return RewardType.ITEM;
        }

        @Override
        public ItemStack getDisplayItem() {
            return null;
        }

        @Override
        public void give(Player player) {
        }
    }
}
