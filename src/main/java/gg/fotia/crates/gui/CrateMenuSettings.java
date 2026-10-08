package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** 配置只在启动和重载时读取，不在绘制或动画帧中访问文件。 */
public record CrateMenuSettings(boolean enabled, boolean compactDefault, Profile standard, Profile compact,
                                int historyLimit, boolean animate, int frameTicks, int holdTicks,
                                int maxAnimationSeconds, int travelSlots, double slowdownPower, double landingSpread, boolean avoidAdjacentDuplicates, String direction,
                                String fallbackIcon, Map<String, String> materials,
                                Map<String, String> rewardIcons, Map<String, String> crateIcons) {
    public record Profile(String menu, int pageSize, int reelSlots) {}
    public Profile profile(boolean small) { return small ? compact : standard; }

    public static CrateMenuSettings load(FotiaCrates plugin) {
        File file = new File(plugin.getDataFolder(), "futureui.yml");
        if (!file.exists()) plugin.saveResource("futureui.yml", false);
        var c = YamlConfiguration.loadConfiguration(file);
        String engine = c.getString("ui-engine", "inventory"), layout = c.getString("default-layout", "standard");
        if (!engine.equals("inventory") && !engine.equals("futureui")) throw new IllegalArgumentException("futureui.yml ui-engine: inventory / futureui");
        if (!layout.equals("standard") && !layout.equals("compact")) throw new IllegalArgumentException("futureui.yml default-layout: standard / compact");
        String direction = c.getString("animation.direction", "left");
        if (!direction.equals("left") && !direction.equals("right")) throw new IllegalArgumentException("animation.direction: left / right");
        return new CrateMenuSettings(engine.equals("futureui"), layout.equals("compact"),
                profile(c, "standard", 6, 7), profile(c, "compact", 2, 5),
                bounded(c, "history-limit", 100, 1, 500), c.getBoolean("animation.enabled", true),
                bounded(c, "animation.frame-ticks", 1, 1, 20), bounded(c, "animation.hold-ticks", 20, 0, 100),
                bounded(c, "animation.max-seconds", 15, 1, 60), bounded(c, "animation.travel-slots", 12, 4, 240),
                decimal(c, "animation.slowdown-power", 3.0, 1.0, 5.0), decimal(c, "animation.landing-spread", 0.35, 0, 0.4),
                c.getBoolean("animation.avoid-adjacent-duplicates", true),
                direction, c.getString("icons.fallback", "paper"),
                strings(c.getConfigurationSection("icons.materials")), strings(c.getConfigurationSection("icons.rewards")),
                strings(c.getConfigurationSection("icons.crates")));
    }
    private static Profile profile(ConfigurationSection c, String id, int size, int defaultSlots) {
        String menu = c.getString("layouts." + id + ".menu", "fotiacrates/" + id);
        if (menu.isBlank()) throw new IllegalArgumentException("FutureUI menu must not be blank");
        String slotPath = "layouts." + id + ".reel-slots";
        int slots = bounded(c, slotPath, defaultSlots, 3, 9);
        if (slots % 2 == 0) throw new IllegalArgumentException(slotPath + ": 3 / 5 / 7 / 9");
        return new Profile(menu, bounded(c, "layouts." + id + ".page-size", size, 1, 36), slots);
    }
    private static double decimal(ConfigurationSection c, String path, double fallback, double min, double max) {
        double value = c.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(path + ": " + min + ".." + max);
        return value;
    }
    private static int bounded(ConfigurationSection c, String path, int fallback, int min, int max) {
        int value = c.getInt(path, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(path + ": " + min + ".." + max);
        return value;
    }
    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> values = new LinkedHashMap<>();
        if (section != null) section.getValues(false).forEach((k, v) -> values.put(k, String.valueOf(v)));
        return Map.copyOf(values);
    }
}
