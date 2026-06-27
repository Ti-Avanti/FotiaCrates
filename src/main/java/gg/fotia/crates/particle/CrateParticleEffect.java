package gg.fotia.crates.particle;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class CrateParticleEffect {

    private final ParticleStage stage;
    private final boolean enabled;
    private final String particle;
    private final ParticleEffectMode mode;
    private final ParticleTarget target;
    private final int count;
    private final int interval;
    private final int duration;
    private final double speed;
    private final double radius;
    private final double height;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final String color;
    private final String toColor;
    private final float size;
    private final Material blockMaterial;
    private final Material itemMaterial;

    public CrateParticleEffect(ParticleStage stage, boolean enabled, String particle,
                               ParticleEffectMode mode, ParticleTarget target,
                               int count, int interval, int duration,
                               double speed, double radius, double height,
                               double offsetX, double offsetY, double offsetZ,
                               String color, String toColor, float size,
                               Material blockMaterial, Material itemMaterial) {
        this.stage = stage;
        this.enabled = enabled;
        this.particle = normalizeParticleName(particle);
        this.mode = mode;
        this.target = target;
        this.count = Math.max(1, count);
        this.interval = Math.max(1, interval);
        this.duration = Math.max(1, duration);
        this.speed = Math.max(0.0, speed);
        this.radius = Math.max(0.0, radius);
        this.height = Math.max(0.0, height);
        this.offsetX = Math.max(0.0, offsetX);
        this.offsetY = Math.max(0.0, offsetY);
        this.offsetZ = Math.max(0.0, offsetZ);
        this.color = normalizeColor(color, "#FFD700");
        this.toColor = normalizeColor(toColor, this.color);
        this.size = Math.max(0.1f, size);
        this.blockMaterial = blockMaterial != null && blockMaterial.isBlock() ? blockMaterial : Material.GOLD_BLOCK;
        this.itemMaterial = itemMaterial != null && !itemMaterial.isAir() ? itemMaterial : Material.GOLD_INGOT;
    }

    public static Map<ParticleStage, CrateParticleEffect> loadAll(ConfigurationSection root,
                                                                  String legacyParticle,
                                                                  int legacyCount) {
        Map<ParticleStage, CrateParticleEffect> effects = new EnumMap<>(ParticleStage.class);
        boolean structured = root != null && (root.isConfigurationSection("idle")
                || root.isConfigurationSection("open")
                || root.isConfigurationSection("reward"));

        for (ParticleStage stage : ParticleStage.values()) {
            CrateParticleEffect fallback = structured
                    ? defaultFor(stage)
                    : legacyFallback(stage, legacyParticle, legacyCount);
            ConfigurationSection section = root != null ? root.getConfigurationSection(stage.path()) : null;
            effects.put(stage, load(stage, section, fallback));
        }
        return effects;
    }

    public static CrateParticleEffect defaultFor(ParticleStage stage) {
        return switch (stage) {
            case IDLE -> new CrateParticleEffect(stage, true, "FLAME", ParticleEffectMode.CIRCLE,
                    ParticleTarget.CRATE_TOP, 16, 10, 1, 0.01, 0.8, 1.0,
                    0.2, 0.2, 0.2, "#FF8A00", "#FFD700", 1.0f,
                    Material.GOLD_BLOCK, Material.GOLD_INGOT);
            case OPEN -> new CrateParticleEffect(stage, true, "END_ROD", ParticleEffectMode.BURST,
                    ParticleTarget.CRATE_TOP, 40, 2, 20, 0.12, 0.7, 1.2,
                    0.1, 0.1, 0.1, "#FFFFFF", "#99CCFF", 1.0f,
                    Material.GOLD_BLOCK, Material.GOLD_INGOT);
            case REWARD -> new CrateParticleEffect(stage, true, "HAPPY_VILLAGER", ParticleEffectMode.HELIX,
                    ParticleTarget.PLAYER, 24, 2, 40, 0.02, 0.6, 1.8,
                    0.2, 0.3, 0.2, "#55FF55", "#FFFF55", 1.0f,
                    Material.GOLD_BLOCK, Material.GOLD_INGOT);
        };
    }

    private static CrateParticleEffect legacyFallback(ParticleStage stage, String legacyParticle, int legacyCount) {
        if (stage == ParticleStage.REWARD) {
            return new CrateParticleEffect(stage, true, legacyParticle, ParticleEffectMode.BURST,
                    ParticleTarget.PLAYER, legacyCount, 1, 1, 0.1, 0.5, 1.0,
                    0.5, 0.5, 0.5, "#FFD700", "#FFFFFF", 1.0f,
                    Material.GOLD_BLOCK, Material.GOLD_INGOT);
        }
        CrateParticleEffect defaults = defaultFor(stage);
        return new CrateParticleEffect(stage, false, defaults.getParticle(), defaults.getMode(),
                defaults.getTarget(), defaults.getCount(), defaults.getInterval(), defaults.getDuration(),
                defaults.getSpeed(), defaults.getRadius(), defaults.getHeight(),
                defaults.getOffsetX(), defaults.getOffsetY(), defaults.getOffsetZ(),
                defaults.getColor(), defaults.getToColor(), defaults.getSize(),
                defaults.getBlockMaterial(), defaults.getItemMaterial());
    }

    private static CrateParticleEffect load(ParticleStage stage, ConfigurationSection section,
                                            CrateParticleEffect fallback) {
        if (section == null) {
            return fallback;
        }
        return new CrateParticleEffect(
                stage,
                section.getBoolean("enabled", fallback.isEnabled()),
                section.getString("type", fallback.getParticle()),
                ParticleEffectMode.fromString(section.getString("mode"), fallback.getMode()),
                ParticleTarget.fromString(section.getString("target"), fallback.getTarget()),
                section.getInt("count", fallback.getCount()),
                section.getInt("interval", fallback.getInterval()),
                section.getInt("duration", fallback.getDuration()),
                section.getDouble("speed", fallback.getSpeed()),
                section.getDouble("radius", fallback.getRadius()),
                section.getDouble("height", fallback.getHeight()),
                section.getDouble("offset-x", fallback.getOffsetX()),
                section.getDouble("offset-y", fallback.getOffsetY()),
                section.getDouble("offset-z", fallback.getOffsetZ()),
                section.getString("color", fallback.getColor()),
                section.getString("to-color", fallback.getToColor()),
                (float) section.getDouble("size", fallback.getSize()),
                parseMaterial(section.getString("block", fallback.getBlockMaterial().name()), fallback.getBlockMaterial()),
                parseMaterial(section.getString("item", fallback.getItemMaterial().name()), fallback.getItemMaterial())
        );
    }

    public void writeTo(ConfigurationSection section) {
        section.set("enabled", enabled);
        section.set("type", particle);
        section.set("mode", mode.name());
        section.set("target", target.name());
        section.set("count", count);
        section.set("interval", interval);
        section.set("duration", duration);
        section.set("speed", speed);
        section.set("radius", radius);
        section.set("height", height);
        section.set("offset-x", offsetX);
        section.set("offset-y", offsetY);
        section.set("offset-z", offsetZ);
        section.set("color", color);
        section.set("to-color", toColor);
        section.set("size", size);
        section.set("block", blockMaterial.name());
        section.set("item", itemMaterial.name());
    }

    private static Material parseMaterial(String input, Material fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String normalizeParticleName(String input) {
        if (input == null || input.isBlank()) {
            return "FLAME";
        }
        return input.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeColor(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String normalized = input.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        return normalized.matches("#[0-9a-fA-F]{6}") ? normalized.toUpperCase(Locale.ROOT) : fallback;
    }

    public ParticleStage getStage() { return stage; }
    public boolean isEnabled() { return enabled; }
    public String getParticle() { return particle; }
    public ParticleEffectMode getMode() { return mode; }
    public ParticleTarget getTarget() { return target; }
    public int getCount() { return count; }
    public int getInterval() { return interval; }
    public int getDuration() { return duration; }
    public double getSpeed() { return speed; }
    public double getRadius() { return radius; }
    public double getHeight() { return height; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public String getColor() { return color; }
    public String getToColor() { return toColor; }
    public float getSize() { return size; }
    public Material getBlockMaterial() { return blockMaterial; }
    public Material getItemMaterial() { return itemMaterial; }
}
