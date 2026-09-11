package gg.fotia.crates.reward;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RarityProbabilityAdjustmentTest {
    private final List<RarityProbabilityAdjustment.Entry> pool = List.of(
            entry("a", "epic", 10), entry("b", "epic", 20),
            entry("c", "common", 30), entry("d", "common", 60), entry("off", "epic", 0));

    @Test void perRewardSetsActualPercentagesAndPreservesOtherRatios() {
        var plan = RarityProbabilityAdjustment.plan(pool, "epic", RarityProbabilityMode.PER_REWARD, 1);
        assertEquals(1, plan.weights().get("a"));
        assertEquals(1, plan.weights().get("b"));
        assertEquals(0, plan.weights().get("off"));
        assertEquals(2, plan.targetTotal());
        assertEquals(25, plan.previousTotal(), 1e-10);
        assertEquals(2, plan.selectedCount());
        assertEquals(1, plan.excludedCount());
        assertEquals(2, plan.weights().get("d") / plan.weights().get("c"), 1e-10);
        assertEquals(100, plan.weights().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
        assertEquals(10, pool.get(0).weight());
    }

    @Test void rarityTotalIsSharedEqually() {
        var plan = RarityProbabilityAdjustment.plan(pool, "EPIC", RarityProbabilityMode.RARITY_TOTAL, 1);
        assertEquals(.5, plan.weights().get("a"));
        assertEquals(.5, plan.weights().get("b"));
    }

    @Test void rejectsOverflowNoMatchesAndInvalidNumbers() {
        fails(pool, "epic", RarityProbabilityMode.PER_REWARD, 60, "total-exceeded");
        fails(pool, "absent", RarityProbabilityMode.PER_REWARD, 1, "no-matches");
        for (double value : new double[]{-1, 101, Double.NaN, Double.POSITIVE_INFINITY}) {
            fails(pool, "epic", RarityProbabilityMode.PER_REWARD, value, "invalid-number");
        }
    }

    @Test void wholePoolRequiresExactlyOneHundredPercent() {
        var all = List.of(entry("a", "epic", 10), entry("b", "epic", 20));
        fails(all, "epic", RarityProbabilityMode.RARITY_TOTAL, 20, "no-remainder");
        assertEquals(50, RarityProbabilityAdjustment.plan(all, "epic",
                RarityProbabilityMode.PER_REWARD, 50).weights().get("a"));
        fails(all, "epic", RarityProbabilityMode.PER_REWARD, 0, "no-remainder");
    }

    @Test void supportsZeroAndHundredWithoutReactivatingDisabledEntries() {
        assertEquals(0, RarityProbabilityAdjustment.plan(pool, "epic",
                RarityProbabilityMode.RARITY_TOTAL, 0).weights().get("a"));
        var full = RarityProbabilityAdjustment.plan(pool, "epic", RarityProbabilityMode.RARITY_TOTAL, 100);
        assertEquals(0, full.weights().get("c"));
        assertEquals(50, full.weights().get("a"));
    }

    @Test void avoidsOverflowAndRetainsTinyProbabilities() {
        var large = List.of(entry("a", "epic", 1e308), entry("b", "common", 1e308), entry("c", "common", 1e308));
        var plan = RarityProbabilityAdjustment.plan(large, "epic", RarityProbabilityMode.PER_REWARD, .000001);
        assertEquals(.000001, plan.weights().get("a"));
        assertTrue(Double.isFinite(plan.previousTotal()));
        fails(List.of(entry("a", "epic", 1), entry("b", "common", Double.NaN)),
                "epic", RarityProbabilityMode.PER_REWARD, 1, "invalid-weights");
    }

    @Test void parsesPercentSuffixButRejectsNonFiniteInput() {
        assertEquals(.5, RarityProbabilityAdjustment.parse(" 0.5% "));
        assertThrows(IllegalArgumentException.class, () -> RarityProbabilityAdjustment.parse("NaN"));
    }

    private static RarityProbabilityAdjustment.Entry entry(String id, String rarity, double weight) {
        return new RarityProbabilityAdjustment.Entry(id, rarity, weight);
    }

    private static void fails(List<RarityProbabilityAdjustment.Entry> entries, String rarity,
                              RarityProbabilityMode mode, double target, String reason) {
        assertEquals(reason, assertThrows(IllegalArgumentException.class,
                () -> RarityProbabilityAdjustment.plan(entries, rarity, mode, target)).getMessage());
    }
}
