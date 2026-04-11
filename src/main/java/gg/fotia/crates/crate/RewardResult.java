package gg.fotia.crates.crate;

import gg.fotia.crates.reward.Reward;

/**
 * 抽奖结果，包含显示奖励和实际奖励
 */
public class RewardResult {
    private final Reward displayReward;  // 用于动画显示的奖励
    private final Reward actualReward;   // 实际给予玩家的奖励
    private final boolean wasReplaced;   // 是否被替代

    public RewardResult(Reward displayReward, Reward actualReward, boolean wasReplaced) {
        this.displayReward = displayReward;
        this.actualReward = actualReward;
        this.wasReplaced = wasReplaced;
    }

    /**
     * 创建未替代的结果
     */
    public static RewardResult normal(Reward reward) {
        return new RewardResult(reward, reward, false);
    }

    /**
     * 创建被替代的结果
     */
    public static RewardResult replaced(Reward displayReward, Reward actualReward) {
        return new RewardResult(displayReward, actualReward, true);
    }

    public Reward getDisplayReward() { return displayReward; }
    public Reward getActualReward() { return actualReward; }
    public boolean wasReplaced() { return wasReplaced; }
}
