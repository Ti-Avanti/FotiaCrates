package gg.fotia.crates.crate;

import java.util.Locale;

/**
 * 奖励预览排序方式，仅影响预览展示顺序。
 */
public enum PreviewSortMode {
    CONFIG_ORDER("配置顺序"),
    WEIGHT_DESC("权重从高到低"),
    WEIGHT_ASC("权重从低到高");

    private final String displayName;

    PreviewSortMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PreviewSortMode next() {
        PreviewSortMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public static PreviewSortMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return CONFIG_ORDER;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return CONFIG_ORDER;
        }
    }
}
