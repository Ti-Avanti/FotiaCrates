package gg.fotia.crates.crate;

import gg.fotia.crates.reward.PermissionAction;
import gg.fotia.crates.reward.Reward;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

final class MultiOpenPermissionContext {

    private final Predicate<String> livePermissionCheck;
    private final Set<String> pendingPermissions = new HashSet<>();

    MultiOpenPermissionContext(Predicate<String> livePermissionCheck) {
        this.livePermissionCheck = livePermissionCheck != null ? livePermissionCheck : permission -> false;
    }

    boolean hasPermission(String permission) {
        return permission != null
                && !permission.isBlank()
                && (pendingPermissions.contains(permission) || livePermissionCheck.test(permission));
    }

    boolean shouldSkip(Reward reward) {
        return reward != null
                && reward.isPermissionCheckEnabled()
                && reward.getPermissionAction() == PermissionAction.SKIP
                && hasPermission(reward.getCheckPermission());
    }

    void recordAward(RewardResult rewardResult) {
        if (rewardResult == null) {
            return;
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
