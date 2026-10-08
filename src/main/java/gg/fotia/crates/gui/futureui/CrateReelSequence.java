package gg.fotia.crates.gui.futureui;

import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 预排展示带及落点，避免结束时把中奖物品突然替换到中心。 */
final class CrateReelSequence {
    private final List<Reward> tape;
    private final int capacity, winningIndex;
    private final double slowdownPower, landingPosition;

    CrateReelSequence(List<Reward> pool, Reward winner, int travel, int capacity, double slowdownPower,
                      double landingSpread, boolean avoidDuplicates) {
        this.capacity = capacity;
        this.slowdownPower = slowdownPower;
        var random = new Random();
        // 每次演出只采样一次落点；概率与中奖物品已经由原抽奖服务确定。
        landingPosition = travel + (random.nextDouble() * 2 - 1) * landingSpread;
        winningIndex = travel + capacity / 2 + 1;
        var values = new ArrayList<Reward>(travel + capacity + 2);
        for (int i = 0; i < travel + capacity + 2; i++) {
            Reward previous = values.isEmpty() ? null : values.get(values.size() - 1);
            Reward next = i + 1 == winningIndex ? winner : null;
            List<Reward> candidates = avoidDuplicates ? pool.stream()
                    .filter(r -> r != previous && r != next).toList() : pool;
            if (candidates.isEmpty()) candidates = pool;
            Reward sampled = i == winningIndex ? winner : RewardProbability.select(candidates, random);
            values.add(sampled == null ? winner : sampled);
        }
        tape = List.copyOf(values);
    }

    List<Reward> frame(int visible, double progress) {
        int position = (int)Math.floor(position(progress));
        int first = position + (capacity - visible) / 2;
        return List.copyOf(tape.subList(first, first + visible + 2));
    }
    double offset(double progress) {
        double value = position(progress);
        return 1 + value - Math.floor(value);
    }
    int selectedIndex(int visible) {
        // 负偏移会使最终窗口少推进一格，不能再固定高亮列表的中间项。
        return winningIndex - ((int)Math.floor(landingPosition) + (capacity - visible) / 2);
    }
    private double position(double progress) {
        double fraction = Math.max(0, Math.min(1, progress));
        return fraction == 1 ? landingPosition : landingPosition * (1 - Math.pow(1 - fraction, slowdownPower));
    }
}
