package gg.fotia.crates.particle;

import java.util.Locale;

public enum ParticleEffectMode {
    POINT("定点"),
    RANDOM_AREA("随机区域"),
    CIRCLE("圆环"),
    DOUBLE_CIRCLE("双层圆环"),
    ORBIT("环绕旋转"),
    HALO("光环"),
    HELIX("螺旋"),
    DOUBLE_HELIX("双螺旋"),
    BURST("爆发"),
    RING_EXPAND("扩散圆环"),
    SPHERE_EXPAND("扩散球体"),
    FOUNTAIN("喷泉"),
    FALLING("落下"),
    BEAM("光束"),
    STAR("星形");

    private final String displayName;

    ParticleEffectMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ParticleEffectMode fromString(String input, ParticleEffectMode fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return ParticleEffectMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public ParticleEffectMode next() {
        ParticleEffectMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public ParticleEffectMode previous() {
        ParticleEffectMode[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
