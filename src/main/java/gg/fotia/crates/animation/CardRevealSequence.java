package gg.fotia.crates.animation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

final class CardRevealSequence {

    private CardRevealSequence() {
    }

    static List<Integer> create(List<Integer> configuredSlots, int configuredWinnerSlot, Random random) {
        List<Integer> slots = new ArrayList<>(new LinkedHashSet<>(configuredSlots));
        if (slots.isEmpty()) {
            return List.of();
        }

        int winnerSlot = slots.contains(configuredWinnerSlot)
                ? configuredWinnerSlot
                : slots.get(slots.size() / 2);
        slots.remove(Integer.valueOf(winnerSlot));
        java.util.Collections.shuffle(slots, random);
        slots.add(winnerSlot);
        return List.copyOf(slots);
    }
}
