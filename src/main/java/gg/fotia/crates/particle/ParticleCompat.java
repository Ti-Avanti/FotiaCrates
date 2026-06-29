package gg.fotia.crates.particle;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ParticleCompat {

    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("DUST", List.of("DUST", "REDSTONE")),
            Map.entry("REDSTONE", List.of("REDSTONE", "DUST")),
            Map.entry("HAPPY_VILLAGER", List.of("HAPPY_VILLAGER", "VILLAGER_HAPPY")),
            Map.entry("VILLAGER_HAPPY", List.of("VILLAGER_HAPPY", "HAPPY_VILLAGER")),
            Map.entry("ANGRY_VILLAGER", List.of("ANGRY_VILLAGER", "VILLAGER_ANGRY")),
            Map.entry("VILLAGER_ANGRY", List.of("VILLAGER_ANGRY", "ANGRY_VILLAGER")),
            Map.entry("ENCHANT", List.of("ENCHANT", "ENCHANTMENT_TABLE")),
            Map.entry("ENCHANTMENT_TABLE", List.of("ENCHANTMENT_TABLE", "ENCHANT")),
            Map.entry("ENCHANTED_HIT", List.of("ENCHANTED_HIT", "CRIT_MAGIC")),
            Map.entry("CRIT_MAGIC", List.of("CRIT_MAGIC", "ENCHANTED_HIT")),
            Map.entry("WITCH", List.of("WITCH", "SPELL_WITCH")),
            Map.entry("SPELL_WITCH", List.of("SPELL_WITCH", "WITCH")),
            Map.entry("SMOKE", List.of("SMOKE", "SMOKE_NORMAL")),
            Map.entry("SMOKE_NORMAL", List.of("SMOKE_NORMAL", "SMOKE")),
            Map.entry("LARGE_SMOKE", List.of("LARGE_SMOKE", "SMOKE_LARGE")),
            Map.entry("SMOKE_LARGE", List.of("SMOKE_LARGE", "LARGE_SMOKE")),
            Map.entry("FIREWORK", List.of("FIREWORK", "FIREWORKS_SPARK")),
            Map.entry("FIREWORKS_SPARK", List.of("FIREWORKS_SPARK", "FIREWORK")),
            Map.entry("ITEM", List.of("ITEM", "ITEM_CRACK")),
            Map.entry("ITEM_CRACK", List.of("ITEM_CRACK", "ITEM")),
            Map.entry("BLOCK", List.of("BLOCK", "BLOCK_CRACK")),
            Map.entry("BLOCK_CRACK", List.of("BLOCK_CRACK", "BLOCK")),
            Map.entry("TOTEM_OF_UNDYING", List.of("TOTEM_OF_UNDYING", "TOTEM")),
            Map.entry("TOTEM", List.of("TOTEM", "TOTEM_OF_UNDYING"))
    );

    private static final List<String> SELECTABLE_PARTICLES = List.of(
            "FLAME",
            "SOUL_FIRE_FLAME",
            "END_ROD",
            "FIREWORK",
            "HAPPY_VILLAGER",
            "ANGRY_VILLAGER",
            "WITCH",
            "ENCHANT",
            "ENCHANTED_HIT",
            "CRIT",
            "TOTEM_OF_UNDYING",
            "PORTAL",
            "REVERSE_PORTAL",
            "CLOUD",
            "SMOKE",
            "LARGE_SMOKE",
            "ELECTRIC_SPARK",
            "GLOW",
            "DUST",
            "DUST_COLOR_TRANSITION",
            "BLOCK",
            "ITEM"
    );

    private ParticleCompat() {
    }

    public static Particle resolveParticle(String configuredName, Particle fallback) {
        for (String candidate : candidates(configuredName)) {
            try {
                return Particle.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }

    public static boolean isValidParticle(String configuredName) {
        return resolveParticle(configuredName, null) != null;
    }

    public static List<String> selectableParticleNames() {
        return SELECTABLE_PARTICLES.stream()
                .filter(ParticleCompat::isValidParticle)
                .toList();
    }

    public static String nextSelectableParticle(String current, boolean backwards) {
        List<String> options = selectableParticleNames();
        if (options.isEmpty()) {
            return "FLAME";
        }

        int currentIndex = 0;
        for (int i = 0; i < options.size(); i++) {
            if (isSameParticle(current, options.get(i))) {
                currentIndex = i;
                break;
            }
        }

        int delta = backwards ? -1 : 1;
        return options.get((currentIndex + delta + options.size()) % options.size());
    }

    public static boolean isSameParticle(String first, String second) {
        Particle firstParticle = resolveParticle(first, null);
        Particle secondParticle = resolveParticle(second, null);
        return firstParticle != null && firstParticle == secondParticle;
    }

    public static Object createData(Particle particle, CrateParticleEffect effect) {
        if (particle == null || effect == null) {
            return null;
        }

        Class<?> dataType = particle.getDataType();
        if (dataType == null || dataType == Void.class) {
            return null;
        }

        if (dataType == Particle.DustTransition.class) {
            return new Particle.DustTransition(parseColor(effect.getColor()), parseColor(effect.getToColor()), effect.getSize());
        }

        if (dataType == Particle.DustOptions.class) {
            return new Particle.DustOptions(parseColor(effect.getColor()), effect.getSize());
        }

        if (BlockData.class.isAssignableFrom(dataType)) {
            Material material = effect.getBlockMaterial().isBlock() ? effect.getBlockMaterial() : Material.GOLD_BLOCK;
            return material.createBlockData();
        }

        if (ItemStack.class.isAssignableFrom(dataType)) {
            return new ItemStack(effect.getItemMaterial());
        }

        return null;
    }

    public static Color parseColor(String hex) {
        String value = hex == null ? "#FFD700" : hex.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        if (!value.matches("#[0-9a-fA-F]{6}")) {
            value = "#FFD700";
        }
        int rgb = Integer.parseInt(value.substring(1), 16);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static List<String> candidates(String configuredName) {
        String normalized = configuredName == null || configuredName.isBlank()
                ? "FLAME"
                : configuredName.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        List<String> aliases = ALIASES.get(normalized);
        if (aliases != null) {
            for (String alias : aliases) {
                if (!candidates.contains(alias)) {
                    candidates.add(alias);
                }
            }
        }
        return candidates;
    }
}
