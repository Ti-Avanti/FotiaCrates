package gg.fotia.crates.gui;

public record RewardEditContext(ReturnTarget returnTarget, int returnPage) {

    public static final String HOLDER_KEY = "reward_edit_context";

    public RewardEditContext {
        returnTarget = returnTarget != null ? returnTarget : ReturnTarget.CRATE_EDIT;
        returnPage = Math.max(0, returnPage);
    }

    public static RewardEditContext crateEditor() {
        return new RewardEditContext(ReturnTarget.CRATE_EDIT, 0);
    }

    public static RewardEditContext rewardManager(int page) {
        return new RewardEditContext(ReturnTarget.REWARD_MANAGER, page);
    }

    public int clampReturnPage(int rewardCount, int itemsPerPage) {
        int pageSize = Math.max(1, itemsPerPage);
        int maxPage = Math.max(0, (Math.max(0, rewardCount) - 1) / pageSize);
        return Math.min(returnPage, maxPage);
    }

    public boolean returnChildToSource() {
        return returnTarget == ReturnTarget.REWARD_MANAGER;
    }

    public enum ReturnTarget {
        CRATE_EDIT,
        REWARD_MANAGER
    }
}
