package gg.fotia.crates.animation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CardRevealSelection {

    private List<Integer> slots;
    private final int rewardCount;
    private final Set<Integer> usedSlots = new LinkedHashSet<>();
    private int nextRewardIndex;
    private boolean waitingForNextPage;

    public CardRevealSelection(List<Integer> slots, int rewardCount) {
        this.slots = normalizedSlots(slots);
        if (this.slots.isEmpty()) {
            throw new IllegalArgumentException("Card slots cannot be empty");
        }
        if (rewardCount <= 0) {
            throw new IllegalArgumentException("Reward count must be positive");
        }
        this.rewardCount = rewardCount;
    }

    public Optional<Selection> select(int slot) {
        if (waitingForNextPage || nextRewardIndex >= rewardCount
                || !slots.contains(slot) || !usedSlots.add(slot)) {
            return Optional.empty();
        }

        int rewardIndex = nextRewardIndex++;
        boolean allRewardsRevealed = nextRewardIndex >= rewardCount;
        boolean pageComplete = allRewardsRevealed || usedSlots.size() >= slots.size();
        waitingForNextPage = pageComplete && !allRewardsRevealed;
        return Optional.of(new Selection(slot, rewardIndex, pageComplete, allRewardsRevealed));
    }

    public boolean startNextPage(List<Integer> nextSlots) {
        if (!waitingForNextPage) {
            return false;
        }
        List<Integer> normalized = normalizedSlots(nextSlots);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Card slots cannot be empty");
        }
        slots = normalized;
        usedSlots.clear();
        waitingForNextPage = false;
        return true;
    }

    public int revealedCount() {
        return nextRewardIndex;
    }

    public int rewardCount() {
        return rewardCount;
    }

    public boolean waitingForNextPage() {
        return waitingForNextPage;
    }

    public List<Integer> availableSlots() {
        if (waitingForNextPage || nextRewardIndex >= rewardCount) {
            return List.of();
        }
        return slots.stream().filter(slot -> !usedSlots.contains(slot)).toList();
    }

    private static List<Integer> normalizedSlots(List<Integer> slots) {
        return slots == null ? List.of() : List.copyOf(new LinkedHashSet<>(slots));
    }

    public record Selection(int slot, int rewardIndex, boolean pageComplete,
                            boolean allRewardsRevealed) {
    }
}
