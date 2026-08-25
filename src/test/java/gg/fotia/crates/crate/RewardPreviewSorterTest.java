package gg.fotia.crates.crate;

import gg.fotia.crates.reward.PermissionAction;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RewardPreviewSorterTest {

    private final Reward low = reward("low", 1.0);
    private final Reward highFirst = reward("high-first", 10.0);
    private final Reward highSecond = reward("high-second", 10.0);

    @Test
    void configOrderReturnsAnIndependentCopyWithoutReordering() {
        List<Reward> source = List.of(low, highFirst, highSecond);

        List<Reward> sorted = RewardPreviewSorter.sort(source, PreviewSortMode.CONFIG_ORDER);

        assertNotSame(source, sorted);
        assertEquals(List.of("low", "high-first", "high-second"), ids(sorted));
    }

    @Test
    void weightDescendingKeepsEqualWeightsStable() {
        List<Reward> sorted = RewardPreviewSorter.sort(
                List.of(low, highFirst, highSecond), PreviewSortMode.WEIGHT_DESC);

        assertEquals(List.of("high-first", "high-second", "low"), ids(sorted));
    }

    @Test
    void weightAscendingKeepsEqualWeightsStable() {
        List<Reward> sorted = RewardPreviewSorter.sort(
                List.of(highFirst, highSecond, low), PreviewSortMode.WEIGHT_ASC);

        assertEquals(List.of("low", "high-first", "high-second"), ids(sorted));
    }

    private static List<String> ids(List<Reward> rewards) {
        return rewards.stream().map(Reward::getId).toList();
    }

    private static Reward reward(String id, double weight) {
        return new Reward() {
            @Override public String getId() { return id; }
            @Override public String getDisplayName() { return id; }
            @Override public String getRarity() { return "common"; }
            @Override public double getChance() { return weight; }
            @Override public boolean shouldBroadcast() { return false; }
            @Override public RewardType getType() { return RewardType.ITEM; }
            @Override public ItemStack getDisplayItem() { return null; }
            @Override public void give(Player player) { }
            @Override public PermissionAction getPermissionAction() { return PermissionAction.SKIP; }
        };
    }
}
