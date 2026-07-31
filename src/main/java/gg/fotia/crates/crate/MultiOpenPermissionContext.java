package gg.fotia.crates.crate;

import gg.fotia.crates.reward.PermissionAction;
import gg.fotia.crates.reward.Reward;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

final class MultiOpenPermissionContext {

    private final Predicate<String> livePermissionCheck;
    private final Set<String> pendingPermissions = new HashSet<>();
    private final boolean uniqueDrawEnabled;
    private final Set<String> excludedRewardIds = new HashSet<>();

    MultiOpenPermissionContext(Predicate<String> livePermissionCheck) {
        this(livePermissionCheck, false, Set.of());
    }

    MultiOpenPermissionContext(Predicate<String> livePermissionCheck, boolean uniqueDrawEnabled,
                               Set<String> collectedRewardIds) {
        this.livePermissionCheck = livePermissionCheck != null ? livePermissionCheck : permission -> false;
        this.uniqueDrawEnabled = uniqueDrawEnabled;
        if (uniqueDrawEnabled && collectedRewardIds != null) {
            excludedRewardIds.addAll(collectedRewardIds);
        }
    }

    boolean hasPermission(String permission) {
        return permission != null
                && !permission.isBlank()
                && (pendingPermissions.contains(permission) || livePermissionCheck.test(permission));
    }

    boolean shouldSkip(Reward reward) {
        return reward != null && (isUniqueRewardExcluded(reward)
                || (reward.isPermissionCheckEnabled()
                && reward.getPermissionAction() == PermissionAction.SKIP
                && hasPermission(reward.getCheckPermission())));
    }

    private boolean isUniqueRewardExcluded(Reward reward) {
        return uniqueDrawEnabled && excludedRewardIds.contains(reward.getId());
    }

    void recordAward(RewardResult rewardResult) {
        if (rewardResult == null) {
            return;
        }

        Reward displayReward = rewardResult.getDisplayReward();
        if (uniqueDrawEnabled && displayReward != null) {
            excludedRewardIds.add(displayReward.getId());
        }

        Reward reward = rewardResult.getActualReward();
        if (reward == null
                || !reward.isPermissionCheckEnabled()
                || reward.getPermissionAction() != PermissionAction.SKIP) {
            return;
        }

        String permission = reward.getCheckPermission();
        if (permission != null && !permission.isBlank()) {
            pendingPermissions.add(permission);
        }
    }
}
