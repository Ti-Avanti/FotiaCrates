package gg.fotia.crates.particle;

import java.util.Locale;

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

    public ParticleTarget next() {
        ParticleTarget[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public ParticleTarget previous() {
        ParticleTarget[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
