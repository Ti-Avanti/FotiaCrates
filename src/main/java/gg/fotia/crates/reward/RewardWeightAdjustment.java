package gg.fotia.crates.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** 按比例归一化与平均分配共用的权重写入策略，不截断低概率奖励。 */
public final class RewardWeightAdjustment {
    private RewardWeightAdjustment() {
    }

    public static double parse(String input) {
        double weight = Double.parseDouble(input);
        if (!Double.isFinite(weight) || weight < 0) {
            throw new NumberFormatException("Weight must be finite and non-negative");
        }
        return weight;
    }

    public static boolean apply(ConfigurationSection rewards, boolean equalize) {
        if (rewards == null) return false;
        List<String> ids = rewards.getKeys(false).stream()
                .filter(rewards::isConfigurationSection).toList();
        if (ids.isEmpty()) return false;
        double[] weights = new double[ids.size()];
        double max = 0.0;
        for (int i = 0; i < ids.size(); i++) {
            double weight = rewards.getDouble(ids.get(i) + ".chance", 10.0);
            weights[i] = Double.isFinite(weight) && weight > 0 ? weight : 0;
            max = Math.max(max, weights[i]);
        }
        double scaledTotal = 0.0;
        if (!equalize && max > 0) {
            for (double weight : weights) scaledTotal += weight / max;
        }
        for (int i = 0; i < ids.size(); i++) {
            double adjusted = equalize || max == 0 ? 100.0 / ids.size()
                    : (weights[i] / max) / scaledTotal * 100.0;
            rewards.set(ids.get(i) + ".chance", adjusted);
        }
        return true;
    }
}
