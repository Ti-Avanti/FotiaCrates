package gg.fotia.crates.particle;

import java.util.Locale;

public enum ParticleStage {
    IDLE("idle", "待机"),
    OPEN("open", "开箱"),
    REWARD("reward", "奖励");

    private final String path;
    private final String displayName;

    ParticleStage(String path, String displayName) {
        this.path = path;
        this.displayName = displayName;
    }

    public String path() {
        return path;
    }

    public String displayName() {
        return displayName;
    }

    public static ParticleStage fromPath(String input) {
        if (input == null || input.isBlank()) {
            return IDLE;
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ParticleStage stage : values()) {
            if (stage.name().equals(normalized) || stage.path.equalsIgnoreCase(input)) {
                return stage;
            }
        }
        return IDLE;
    }
}
