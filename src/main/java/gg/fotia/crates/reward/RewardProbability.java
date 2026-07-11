package gg.fotia.crates.reward;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

/**
 * Shared weight calculations for reward previews and animation frames.
 */
public final class RewardProbability {

    private RewardProbability() {
    }

    public static double percentage(Reward reward, List<? extends Reward> rewards) {
        if (reward == null) {
            return 0.0;
        }

        double totalWeight = totalWeight(rewards);
        if (totalWeight <= 0.0) {
            return 0.0;
        }

        return positiveWeight(reward.getChance()) / totalWeight * 100.0;
    }

    public static String format(double percentage) {
        if (!Double.isFinite(percentage) || percentage <= 0.0) {
            return "0%";
        }

        String value = BigDecimal.valueOf(percentage)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return value + "%";
    }

    public static Reward select(List<? extends Reward> rewards, Random random) {
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }

        double totalWeight = totalWeight(rewards);
        if (totalWeight <= 0.0) {
            return rewards.get(rewards.size() - 1);
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (Reward reward : rewards) {
            cumulative += positiveWeight(reward.getChance());
            if (roll < cumulative) {
                return reward;
            }
        }

        return rewards.get(rewards.size() - 1);
    }

    private static double totalWeight(List<? extends Reward> rewards) {
        if (rewards == null) {
            return 0.0;
        }

        return rewards.stream()
                .mapToDouble(reward -> positiveWeight(reward.getChance()))
                .sum();
    }

    private static double positiveWeight(double weight) {
        return Double.isFinite(weight) && weight > 0.0 ? weight : 0.0;
    }
}
