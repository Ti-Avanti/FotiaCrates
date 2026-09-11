package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.RarityProbabilitySettings;
import gg.fotia.crates.crate.Crate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Disk I/O runs off-thread; Bukkit item deserialization and cache publication stay on the server thread. */
public final class RarityProbabilityService {
    private final FotiaCrates plugin;
    private final Set<String> saving = new HashSet<>();

    public RarityProbabilityService(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public boolean isSaving() {
        return !saving.isEmpty();
    }

    public void save(Crate expected, RarityProbabilityAdjustment.Plan plan,
                     BooleanSupplier authorized, Consumer<String> completion) {
        if (isSaving() || plugin.getCrateManager().isConfigSavePending()) {
            completion.accept("busy");
            return;
        }
        if (plugin.getCrateManager().getCrate(expected.getId()) != expected) {
            completion.accept("stale");
            return;
        }
        saving.add(expected.getId());
        Path path = plugin.getDataFolder().toPath().resolve("crates").resolve(expected.getId() + ".yml");
        List<RarityProbabilityAdjustment.Entry> snapshot = RarityProbabilityAdjustment.snapshot(expected.getRewards());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String original = Files.readString(path, StandardCharsets.UTF_8);
                onMain(() -> prepare(expected, snapshot, plan, path, original, authorized, completion));
            } catch (Exception exception) {
                fail(expected, exception, completion);
            }
        });
    }

    private void prepare(Crate expected, List<RarityProbabilityAdjustment.Entry> snapshot,
                         RarityProbabilityAdjustment.Plan plan, Path path, String original,
                         BooleanSupplier authorized, Consumer<String> completion) {
        if (!authorized.getAsBoolean() || !RarityProbabilitySettings.read(plugin.getConfig()).enabled()) {
            finish(expected, "disabled", completion);
            return;
        }
        if (plugin.getCrateManager().getCrate(expected.getId()) != expected) {
            finish(expected, "stale", completion);
            return;
        }
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(original);
            ConfigurationSection rewards = config.getConfigurationSection("rewards");
            if (!snapshot.equals(readEntries(rewards, plugin.getConfigManager().getDefaultRarityId()))) {
                finish(expected, "stale", completion);
                return;
            }
            plan.weights().forEach((id, weight) -> rewards.getConfigurationSection(id).set("chance", weight));
            String updated = config.saveToString();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    boolean saved = RarityProbabilityFileStore.save(path, original, updated);
                    onMain(() -> {
                        try {
                            if (saved) plugin.getCrateManager().reloadCrate(expected.getId(), config);
                            finish(expected, saved ? "saved" : "stale", completion);
                        } catch (RuntimeException exception) {
                            fail(expected, exception, completion);
                        }
                    });
                } catch (Exception exception) {
                    fail(expected, exception, completion);
                }
            });
        } catch (Exception exception) {
            fail(expected, exception, completion);
        }
    }

    public static List<RarityProbabilityAdjustment.Entry> readEntries(ConfigurationSection rewards, String defaultRarity) {
        if (rewards == null) return List.of();
        return rewards.getKeys(false).stream().filter(rewards::isConfigurationSection).map(id -> {
            ConfigurationSection reward = rewards.getConfigurationSection(id);
            return new RarityProbabilityAdjustment.Entry(id, reward.getString("rarity", defaultRarity),
                    reward.getDouble("chance", 10));
        }).toList();
    }

    private void fail(Crate crate, Exception exception, Consumer<String> completion) {
        plugin.getLogger().warning("Could not save rarity probabilities for " + crate.getId() + ": " + exception.getMessage());
        onMain(() -> finish(crate, "save-failed", completion));
    }

    private void finish(Crate crate, String result, Consumer<String> completion) {
        saving.remove(crate.getId());
        completion.accept(result);
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
