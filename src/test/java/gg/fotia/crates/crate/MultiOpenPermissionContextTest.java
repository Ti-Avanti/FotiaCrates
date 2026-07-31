package gg.fotia.crates.crate;

import gg.fotia.crates.reward.PermissionAction;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOpenPermissionContextTest {

    @Test
    void skipsOneTimeRewardAfterItWasSelectedEarlierInSameBatch() {
        TestReward reward = new TestReward("armor", true, "hb.3", PermissionAction.SKIP);
        MultiOpenPermissionContext context = new MultiOpenPermissionContext(permission -> false);

        assertFalse(context.shouldSkip(reward));
        context.recordAward(RewardResult.normal(reward));
        assertTrue(context.shouldSkip(reward));
    }

    @Test
    void respectsPermissionsOwnedBeforeBatchStarts() {
        TestReward reward = new TestReward("armor", true, "hb.3", PermissionAction.SKIP);
        MultiOpenPermissionContext context = new MultiOpenPermissionContext("hb.3"::equals);

        assertTrue(context.shouldSkip(reward));
    }

    @Test
    void doesNotSuppressOrdinaryOrAlternativeRewards() {
        TestReward ordinary = new TestReward("ordinary", false, null, PermissionAction.SKIP);
        TestReward alternative = new TestReward("alternative", true, "hb.4", PermissionAction.ALTERNATIVE);
        MultiOpenPermissionContext context = new MultiOpenPermissionContext(permission -> false);

        context.recordAward(RewardResult.normal(ordinary));
        context.recordAward(RewardResult.normal(alternative));

        assertFalse(context.shouldSkip(ordinary));
        assertFalse(context.shouldSkip(alternative));
    }

    @Test
    void uniqueModeSkipsPersistedAndEarlierBatchRewards() {
        TestReward persisted = new TestReward("persisted", false, null, PermissionAction.SKIP);
        TestReward firstInBatch = new TestReward("first", false, null, PermissionAction.SKIP);
        TestReward stillAvailable = new TestReward("available", false, null, PermissionAction.SKIP);
        MultiOpenPermissionContext context = new MultiOpenPermissionContext(
                permission -> false, true, Set.of("persisted"));

        assertTrue(context.shouldSkip(persisted));
        assertFalse(context.shouldSkip(firstInBatch));
        assertFalse(context.shouldSkip(stillAvailable));

        context.recordAward(RewardResult.normal(firstInBatch));

        assertTrue(context.shouldSkip(firstInBatch));
        assertFalse(context.shouldSkip(stillAvailable));
    }

    @Test
    void disabledUniqueModeDoesNotUseCollectedRewardIds() {
        TestReward reward = new TestReward("persisted", false, null, PermissionAction.SKIP);
        MultiOpenPermissionContext context = new MultiOpenPermissionContext(
                permission -> false, false, Set.of("persisted"));

        assertFalse(context.shouldSkip(reward));
        context.recordAward(RewardResult.normal(reward));
        assertFalse(context.shouldSkip(reward));
    }

    private record TestReward(String id, boolean permissionCheckEnabled, String permission,
                              PermissionAction action) implements Reward {
        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return id; }
        @Override public String getRarity() { return "common"; }
        @Override public double getChance() { return 1.0; }
        @Override public boolean shouldBroadcast() { return false; }
        @Override public RewardType getType() { return RewardType.ITEM; }
        @Override public ItemStack getDisplayItem() { return null; }
        @Override public void give(Player player) { }
        @Override public boolean isPermissionCheckEnabled() { return permissionCheckEnabled; }
        @Override public String getCheckPermission() { return permission; }
        @Override public PermissionAction getPermissionAction() { return action; }
    }
}
