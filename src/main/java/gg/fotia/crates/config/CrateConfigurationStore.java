package gg.fotia.crates.config;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Main-thread editable configurations; the writer receives only immutable UTF-8 text. */
public final class CrateConfigurationStore {
    private final Path directory;
    private final AsyncConfigurationWriter writer;
    private final Map<Path, YamlConfiguration> configurations = new HashMap<>();
    private final java.util.Set<Path> deleted = new java.util.HashSet<>();

    public CrateConfigurationStore(FotiaCrates plugin) {
        directory = plugin.getDataFolder().toPath().resolve("crates").toAbsolutePath().normalize();
        writer = new AsyncConfigurationWriter(plugin);
    }

    public YamlConfiguration read(File file) {
        Path path = path(file);
        return configurations.computeIfAbsent(path, ignored -> YamlConfiguration.loadConfiguration(file));
    }

    public void remember(File file, YamlConfiguration configuration) {
        Path path = path(file);
        configurations.put(path, configuration);
        deleted.remove(path);
    }

    public boolean exists(File file) {
        Path path = path(file);
        return !deleted.contains(path) && (configurations.containsKey(path) || file.exists());
    }

    public void save(YamlConfiguration configuration, File file) throws IOException {
        String snapshot = configuration.saveToString();
        writer.save(path(file), snapshot);
        remember(file, configuration);
    }

    public void delete(File file) throws IOException {
        Path path = path(file);
        writer.delete(path);
        configurations.remove(path);
        deleted.add(path);
    }

    public boolean isSaving() { return writer.isPending(); }

    public void reset() {
        if (writer.isPending()) throw new IllegalStateException("Crate configuration writes are still pending");
        configurations.clear();
        deleted.clear();
        writer.reload();
    }

    public void shutdown() { writer.shutdown(); }

    private Path path(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!path.startsWith(directory) || path.equals(directory)) throw new IllegalArgumentException("Invalid crate configuration path");
        return path;
    }
}
