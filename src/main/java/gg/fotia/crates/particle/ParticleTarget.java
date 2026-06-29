package gg.fotia.crates.particle;

import java.util.Locale;
import java.util.List;

public enum ParticleTarget {
    CRATE("箱子中心"),
    CRATE_TOP("箱子顶部"),
    PLAYER("玩家");

    private final String displayName;

    ParticleTarget(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ParticleTarget fromString(String input, ParticleTarget fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return ParticleTarget.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static List<ParticleTarget> optionsForStage(ParticleStage stage) {
        if (stage == ParticleStage.IDLE) {
            return List.of(CRATE, CRATE_TOP);
        }
        return List.of(values());
    }

    public static ParticleTarget normalizeForStage(ParticleStage stage, ParticleTarget target) {
        List<ParticleTarget> options = optionsForStage(stage);
        return options.contains(target) ? target : CRATE_TOP;
    }

    public ParticleTarget next() {
        ParticleTarget[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public ParticleTarget next(ParticleStage stage) {
        List<ParticleTarget> options = optionsForStage(stage);
        int index = options.indexOf(this);
        if (index < 0) {
            return options.get(0);
        }
        return options.get((index + 1) % options.size());
    }

    public ParticleTarget previous() {
        ParticleTarget[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public ParticleTarget previous(ParticleStage stage) {
        List<ParticleTarget> options = optionsForStage(stage);
        int index = options.indexOf(this);
        if (index < 0) {
            return options.get(options.size() - 1);
        }
        return options.get((index - 1 + options.size()) % options.size());
    }
}
