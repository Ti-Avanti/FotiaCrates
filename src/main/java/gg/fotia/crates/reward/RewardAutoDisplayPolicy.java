package gg.fotia.crates.reward;

import org.bukkit.Material;

import java.util.Locale;

public final class RewardAutoDisplayPolicy {

    private RewardAutoDisplayPolicy() {
    }

    public static boolean shouldApply(boolean enabled, boolean fieldEnabled, boolean onlyWhenNotCustomized, boolean rewardAutoFlag) {
        if (!enabled || !fieldEnabled) {
            return false;
        }
        return !onlyWhenNotCustomized || rewardAutoFlag;
    }

    public static String fallbackName(Material material) {
        if (material == null) {
            return "";
        }
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return result.toString();
    }
}
