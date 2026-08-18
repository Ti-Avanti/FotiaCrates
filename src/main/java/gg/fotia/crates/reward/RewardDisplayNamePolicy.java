package gg.fotia.crates.reward;

public final class RewardDisplayNamePolicy {

    private RewardDisplayNamePolicy() {
    }

    public static String manualOverride(String displayName, boolean autoDisplayName) {
        return autoDisplayName ? null : displayName;
    }
}
