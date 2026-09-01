package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardRevealSelectionTest {

    @Test
    void singleDrawAcceptsOnlyOneCardSelection() {
        CardRevealSelection selection = new CardRevealSelection(List.of(10, 11, 12), 1);

        CardRevealSelection.Selection first = selection.select(11).orElseThrow();

        assertEquals(0, first.rewardIndex());
        assertTrue(first.allRewardsRevealed());
        assertTrue(selection.select(10).isEmpty());
    }

    @Test
    void multiDrawRevealsOnePreparedRewardPerDistinctClick() {
        CardRevealSelection selection = new CardRevealSelection(List.of(10, 11, 12), 3);

        assertEquals(0, selection.select(12).orElseThrow().rewardIndex());
        assertTrue(selection.select(12).isEmpty());
        assertEquals(1, selection.select(10).orElseThrow().rewardIndex());
        CardRevealSelection.Selection last = selection.select(11).orElseThrow();

        assertEquals(2, last.rewardIndex());
        assertTrue(last.allRewardsRevealed());
    }

    @Test
    void blocksSelectionUntilNextPageStartsWhenRewardsExceedSlots() {
        CardRevealSelection selection = new CardRevealSelection(List.of(10, 11), 3);

        selection.select(10).orElseThrow();
        CardRevealSelection.Selection pageEnd = selection.select(11).orElseThrow();

        assertTrue(pageEnd.pageComplete());
        assertFalse(pageEnd.allRewardsRevealed());
        assertTrue(selection.select(10).isEmpty());
        assertTrue(selection.startNextPage(List.of(20, 21)));
        assertTrue(selection.select(20).orElseThrow().allRewardsRevealed());
    }

    @Test
    void exposesOnlyUnusedSlotsWhileSelectionIsActive() {
        CardRevealSelection selection = new CardRevealSelection(List.of(10, 11, 12), 3);

        selection.select(11).orElseThrow();

        assertEquals(List.of(10, 12), selection.availableSlots());
    }
}
