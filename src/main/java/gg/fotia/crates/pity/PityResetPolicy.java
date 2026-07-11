package gg.fotia.crates.pity;

import java.util.List;

/**
 * Decides whether a naturally rolled reward restarts a shared pity counter.
 */
public final class PityResetPolicy {

    private PityResetPolicy() {
    }

    public static boolean shouldResetEarly(boolean enabled, boolean pityTriggered,
                                           String actualRewardRarity, String minimumPityRarity,
                                           List<String> rarityOrder) {
        if (!enabled || pityTriggered || actualRewardRarity == null || minimumPityRarity == null) {
            return false;
        }

        if (actualRewardRarity.equalsIgnoreCase(minimumPityRarity)) {
            return true;
        }

        int rewardLevel = rarityLevel(actualRewardRarity, rarityOrder);
        int minimumLevel = rarityLevel(minimumPityRarity, rarityOrder);
        return rewardLevel >= 0 && minimumLevel >= 0 && rewardLevel >= minimumLevel;
    }

    private static int rarityLevel(String rarity, List<String> rarityOrder) {
        if (rarityOrder == null) {
            return -1;
        }

        for (int index = 0; index < rarityOrder.size(); index++) {
            if (rarityOrder.get(index).equalsIgnoreCase(rarity)) {
                return index;
            }
        }
        return -1;
    }
}
