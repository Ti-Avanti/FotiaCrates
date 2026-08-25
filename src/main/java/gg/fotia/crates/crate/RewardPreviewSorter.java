package gg.fotia.crates.crate;

import gg.fotia.crates.reward.Reward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 创建用于预览的奖励副本，不修改抽奖池原始顺序。
 */
public final class RewardPreviewSorter {

    private RewardPreviewSorter() {
    }

    public static List<Reward> sort(List<? extends Reward> rewards, PreviewSortMode mode) {
        List<Reward> result = rewards == null ? new ArrayList<>() : new ArrayList<>(rewards);
        PreviewSortMode resolvedMode = mode == null ? PreviewSortMode.CONFIG_ORDER : mode;
        switch (resolvedMode) {
            case WEIGHT_DESC -> result.sort(Comparator.comparingDouble(Reward::getChance).reversed());
            case WEIGHT_ASC -> result.sort(Comparator.comparingDouble(Reward::getChance));
            case CONFIG_ORDER -> {
                // 保持配置文件中的顺序。
            }
        }
        return result;
    }
}
