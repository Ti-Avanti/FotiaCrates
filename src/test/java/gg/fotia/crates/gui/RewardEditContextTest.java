package gg.fotia.crates.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardEditContextTest {

    @Test
    void preservesRewardManagerSourcePage() {
        RewardEditContext context = RewardEditContext.rewardManager(1);

        assertEquals(RewardEditContext.ReturnTarget.REWARD_MANAGER, context.returnTarget());
        assertEquals(1, context.returnPage());
    }

    @Test
    void clampsRemovedLastPageToLastAvailablePage() {
        RewardEditContext context = RewardEditContext.rewardManager(2);

        assertEquals(1, context.clampReturnPage(30, 28));
        assertEquals(0, context.clampReturnPage(0, 28));
    }

    @Test
    void defaultsDirectRewardEditingToCrateEditor() {
        assertEquals(RewardEditContext.ReturnTarget.CRATE_EDIT,
                RewardEditContext.crateEditor().returnTarget());
    }

    @Test
    void childMenusReturnDirectlyToRewardManagerOnlyWhenOpenedFromManager() {
        assertEquals(true, RewardEditContext.rewardManager(1).returnChildToSource());
        assertEquals(false, RewardEditContext.crateEditor().returnChildToSource());
    }
}
