package gg.fotia.crates.animation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class AnimationSlotResolver {

    private AnimationSlotResolver() {
    }

    public static Resolution forCards(List<Integer> configured, int inventorySize,
                                      List<Integer> defaults) {
        List<Integer> valid = validSlots(configured, inventorySize);
        if (!valid.isEmpty()) {
            return new Resolution(valid, false, "");
        }
        return new Resolution(validSlots(defaults, inventorySize), true,
                "requires at least 1 valid slot");
    }

    private static List<Integer> validSlots(List<Integer> slots, int inventorySize) {
        if (slots == null || inventorySize <= 0) {
            return List.of();
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer slot : slots) {
            if (slot != null && slot >= 0 && slot < inventorySize) {
                unique.add(slot);
            }
        }
        return List.copyOf(new ArrayList<>(unique));
    }

    public record Resolution(List<Integer> slots, boolean usedFallback, String problem) {

        public Resolution {
            slots = slots == null ? List.of() : List.copyOf(slots);
            problem = problem == null ? "" : problem;
        }
    }
}
