package gg.fotia.crates.crate;

import java.util.Locale;

public enum PreviewChanceDisplayMode {
    PERCENTAGE("百分比"),
    WEIGHT("权重"),
    HIDDEN("隐藏");

    private final String displayName;

    PreviewChanceDisplayMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PreviewChanceDisplayMode next() {
        PreviewChanceDisplayMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static PreviewChanceDisplayMode fromConfig(String value, boolean legacyShowChance) {
        if (value == null || value.isBlank()) {
            return legacyShowChance ? PERCENTAGE : HIDDEN;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return legacyShowChance ? PERCENTAGE : HIDDEN;
        }
    }
}
