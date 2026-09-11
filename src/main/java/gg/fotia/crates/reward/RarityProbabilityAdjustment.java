package gg.fotia.crates.reward;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces a preview without changing the reward pool. All output weights total 100. */
public final class RarityProbabilityAdjustment {
    private RarityProbabilityAdjustment() {
    }

    public record Entry(String id, String rarity, double weight) {
        public boolean matches(String target) {
            return rarity.equalsIgnoreCase(target);
        }
    }

    public record Plan(Map<String, Double> weights, int selectedCount, int excludedCount,
                       double previousTotal, double targetTotal) {
        public Plan {
            weights = Map.copyOf(weights);
        }
    }

    public static double parse(String text) {
        try {
            double value = Double.parseDouble(text.trim().replaceFirst("[%％]$", "").trim());
            validate(value);
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid-number");
        }
    }

    public static List<Entry> snapshot(List<Reward> rewards) {
        return rewards.stream().map(r -> new Entry(r.getId(), r.getRarity(), r.getChance())).toList();
    }

    public static Plan plan(List<Entry> entries, String rarity, RarityProbabilityMode mode, double percentage) {
        validate(percentage);
        if (entries.stream().anyMatch(e -> !Double.isFinite(e.weight()) || e.weight() < 0)) {
            throw new IllegalArgumentException("invalid-weights");
        }
        List<Entry> selected = entries.stream().filter(e -> e.matches(rarity) && e.weight() > 0).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("no-matches");
        BigDecimal total = BigDecimal.valueOf(percentage);
        if (mode == RarityProbabilityMode.PER_REWARD) total = total.multiply(BigDecimal.valueOf(selected.size()));
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException("total-exceeded");
        double target = total.doubleValue();
        double remaining = BigDecimal.valueOf(100).subtract(total).doubleValue();
        double otherMax = entries.stream().filter(e -> !e.matches(rarity))
                .mapToDouble(Entry::weight).max().orElse(0);
        if (remaining > 0 && otherMax == 0) throw new IllegalArgumentException("no-remainder");
        double otherScaledSum = otherMax == 0 ? 0 : entries.stream().filter(e -> !e.matches(rarity))
                .mapToDouble(e -> e.weight() / otherMax).sum();
        double max = entries.stream().mapToDouble(Entry::weight).max().orElse(1);
        double scaledSum = entries.stream().mapToDouble(e -> e.weight() / max).sum();
        double previousTotal = selected.stream().mapToDouble(e -> (e.weight() / max) / scaledSum * 100).sum();
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Entry entry : entries) {
            double weight = entry.weight() == 0 ? 0 : entry.matches(rarity) ? target / selected.size()
                    : remaining == 0 ? 0 : (entry.weight() / otherMax) / otherScaledSum * remaining;
            if (!Double.isFinite(weight) || (entry.weight() > 0 && weight == 0
                    && (entry.matches(rarity) ? target > 0 : remaining > 0))) {
                throw new IllegalArgumentException("precision");
            }
            weights.put(entry.id(), weight);
        }
        int excluded = (int) entries.stream().filter(e -> e.matches(rarity) && e.weight() == 0).count();
        return new Plan(weights, selected.size(), excluded, previousTotal, target);
    }

    private static void validate(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("invalid-number");
    }
}
