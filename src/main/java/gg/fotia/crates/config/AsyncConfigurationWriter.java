package gg.fotia.crates.config;

import gg.fotia.crates.FotiaCrates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes disk writes, coalescing edits to the same file without retaining Bukkit objects. */
public final class AsyncConfigurationWriter {
    private final FotiaCrates plugin;
    private final Map<Path, Change> pending = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean stopping;
    private volatile long delayMillis;
    private volatile long retryMillis;
    private long lastWarning;

    public AsyncConfigurationWriter(FotiaCrates plugin) {
        this.plugin = plugin;
        executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "FotiaCrates-Config");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        reload();
    }

    public void reload() {
        delayMillis = plugin.getConfigManager().getConfigurationWriteDelayTicks() * 50;
        retryMillis = plugin.getConfigManager().getConfigurationWriteRetryDelayTicks() * 50;
    }

    public void save(Path path, String contents) throws IOException {
        if (stopping) throw new IOException("Configuration writer is stopping");
        pending.put(path, new Change(contents));
        queue(delayMillis);
    }

    public void delete(Path path) throws IOException {
        save(path, null);
    }

    public boolean isPending() {
        return !pending.isEmpty();
    }

    private void queue(long delay) {
        if (!stopping && queued.compareAndSet(false, true)) {
            executor.schedule(() -> {
                boolean success = flush();
                queued.set(false);
                if (!pending.isEmpty()) queue(success ? delayMillis : retryMillis);
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private boolean flush() {
        boolean success = true;
        for (Map.Entry<Path, Change> entry : List.copyOf(pending.entrySet())) {
            try {
                if (entry.getValue().contents == null) Files.deleteIfExists(entry.getKey());
                else writeAtomically(entry.getKey(), entry.getValue().contents);
                pending.remove(entry.getKey(), entry.getValue());
            } catch (IOException exception) {
                success = false;
                long now = System.currentTimeMillis();
                if (stopping || now - lastWarning >= 30_000) {
                    lastWarning = now;
                    plugin.getLogger().severe("Could not save " + entry.getKey()
                            + "; latest edit retained for retry: " + exception.getMessage());
                }
            }
        }
        return success;
    }

    private void writeAtomically(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".fotiacrates-", ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void shutdown() {
        stopping = true;
        executor.execute(this::flush);
        executor.shutdown();
        boolean stopped = false;
        try {
            stopped = executor.awaitTermination(plugin.getConfigManager().getPersistenceShutdownFlushTimeoutMillis(),
                    TimeUnit.MILLISECONDS);
            if (!stopped) {
                executor.shutdownNow();
                stopped = executor.awaitTermination(1_000, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        if (stopped && !pending.isEmpty()) flush();
        if (!pending.isEmpty()) plugin.getLogger().severe("Configuration writes still pending at shutdown: " + pending.keySet());
    }

    // Identity equality ensures that a newer edit is not removed after an older write completes.
    private static final class Change {
        private final String contents;
        private Change(String contents) { this.contents = contents; }
    }
}
