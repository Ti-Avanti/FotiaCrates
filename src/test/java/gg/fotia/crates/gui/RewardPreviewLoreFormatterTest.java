package gg.fotia.crates.gui;

import gg.fotia.crates.crate.PreviewChanceDisplayMode;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardPreviewLoreFormatterTest {

    private static final RewardPreviewDisplayConfig CONFIG = new RewardPreviewDisplayConfig(
            true,
            List.of("", "稀有度: {rarity}", "{chance_line}", "{broadcast_line}"),
            "概率: {probability}",
            "权重: {weight}",
            "稀有奖励: {reward_name}"
    );

    @Test
    void rendersNormalizedPercentageAndSkipsEmptyDynamicLines() {
        TestReward selected = new TestReward("selected", "史诗", 1.0, false);
        TestReward other = new TestReward("other", "普通", 3.0, false);

        assertEquals(List.of("", "稀有度: 史诗", "概率: 25%"),
                RewardPreviewLoreFormatter.render(
                        CONFIG, PreviewChanceDisplayMode.PERCENTAGE,
                        selected, List.of(selected, other), "史诗"));
    }

    @Test
    void rendersRawWeightAndConfiguredBroadcastLine() {
        TestReward selected = new TestReward("selected", "史诗", 5.0, true);

        assertEquals(List.of("", "稀有度: 史诗", "权重: 5", "稀有奖励: selected"),
                RewardPreviewLoreFormatter.render(
                        CONFIG, PreviewChanceDisplayMode.WEIGHT,
                        selected, List.of(selected), "史诗"));
    }

    @Test
    void hiddenModeKeepsIntentionalBlankLinesButOmitsChanceLine() {
        TestReward selected = new TestReward("selected", "史诗", 5.0, false);

        assertEquals(List.of("", "稀有度: 史诗"),
                RewardPreviewLoreFormatter.render(
                        CONFIG, PreviewChanceDisplayMode.HIDDEN,
                        selected, List.of(selected), "史诗"));
    }

    private record TestReward(String id, String rarity, double chance, boolean broadcast) implements Reward {
        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return id; }
        @Override public String getRarity() { return rarity; }
        @Override public double getChance() { return chance; }
        @Override public boolean shouldBroadcast() { return broadcast; }
        @Override public RewardType getType() { return RewardType.ITEM; }
        @Override public ItemStack getDisplayItem() { return null; }
        @Override public void give(Player player) { }
    }
}
