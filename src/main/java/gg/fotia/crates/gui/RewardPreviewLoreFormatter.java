package gg.fotia.crates.gui;

import gg.fotia.crates.crate.PreviewChanceDisplayMode;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RewardPreviewLoreFormatter {

    private RewardPreviewLoreFormatter() {
    }

    public static List<String> render(RewardPreviewDisplayConfig config,
                                      PreviewChanceDisplayMode displayMode,
                                      Reward reward,
                                      List<? extends Reward> rewards,
                                      String rarityDisplayName) {
        RewardPreviewDisplayConfig resolvedConfig = config != null
                ? config
                : RewardPreviewDisplayConfig.defaults();
        PreviewChanceDisplayMode resolvedMode = displayMode != null
                ? displayMode
                : PreviewChanceDisplayMode.PERCENTAGE;

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{reward_name}", reward.getDisplayName());
        placeholders.put("{reward_id}", reward.getId());
        placeholders.put("{reward_type}", reward.getType().name());
        placeholders.put("{rarity}", rarityDisplayName != null ? rarityDisplayName : reward.getRarity());
        placeholders.put("{rarity_id}", reward.getRarity());
        placeholders.put("{probability}", RewardProbability.format(RewardProbability.percentage(reward, rewards)));
        placeholders.put("{weight}", RewardProbability.formatWeight(reward.getChance()));

        String chanceTemplate = switch (resolvedMode) {
            case PERCENTAGE -> resolvedConfig.getPercentageLine();
            case WEIGHT -> resolvedConfig.getWeightLine();
            case HIDDEN -> "";
        };
        placeholders.put("{chance_line}", replace(chanceTemplate, placeholders));
        placeholders.put("{broadcast_line}", reward.shouldBroadcast()
                ? replace(resolvedConfig.getBroadcastLine(), placeholders)
                : "");

        List<String> rendered = new ArrayList<>();
        for (String template : resolvedConfig.getLore()) {
            if (template == null) {
                continue;
            }
            String line = replace(template, placeholders);
            if (isEmptyDynamicLine(template, line)) {
                continue;
            }
            rendered.add(line);
        }
        return rendered;
    }

    private static String replace(String input, Map<String, String> placeholders) {
        String result = input != null ? input : "";
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static boolean isEmptyDynamicLine(String template, String rendered) {
        String trimmed = template.trim();
        return rendered.isBlank() && ("{chance_line}".equals(trimmed) || "{broadcast_line}".equals(trimmed));
    }

}
