package gg.fotia.crates.hologram;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.CrateLocation;
import gg.fotia.crates.key.PlayerKeyCountSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Counts candidate checks against a per-tick budget, retaining unfinished player scans. */
final class HologramUpdateQueue {
    interface Target {
        Set<UUID> knownPlayers();
        double searchRadius();
        String update(Player player, CrateLocation location, PlayerKeyCountSnapshot keys);
        void finish(UUID playerId, Set<String> visible);
    }

    private final FotiaCrates plugin;
    private final Target target;
    private final ArrayDeque<UUID> players = new ArrayDeque<>();
    private Scan scan;
    private long tick;
    private long nextRefresh;

    HologramUpdateQueue(FotiaCrates plugin, Target target) {
        this.plugin = plugin;
        this.target = target;
    }

    void reset() {
        players.clear();
        scan = null;
        nextRefresh = 0;
    }

    void tick(int interval, int budget) {
        tick++;
        if (scan == null && players.isEmpty()) {
            if (tick < nextRefresh) return;
            Set<UUID> ids = new LinkedHashSet<>(target.knownPlayers());
            plugin.getServer().getOnlinePlayers().forEach(player -> ids.add(player.getUniqueId()));
            players.addAll(ids);
            nextRefresh = tick + interval;
        }
        for (int processed = 0; processed < budget; processed++) {
            if (scan == null) {
                UUID id = players.poll();
                if (id == null) return;
                Player player = plugin.getServer().getPlayer(id);
                if (player == null || !player.isOnline()) {
                    target.finish(id, Set.of());
                    continue;
                }
                Location origin = player.getLocation();
                scan = new Scan(id, java.util.List.copyOf(plugin.getCrateManager().getNearbyCrateLocations(
                        origin.getWorld().getName(), origin.getBlockX(), origin.getBlockZ(), target.searchRadius())).iterator(),
                        new PlayerKeyCountSnapshot(plugin.getKeyManager(), player));
            }
            Player player = plugin.getServer().getPlayer(scan.playerId);
            if (player == null || !player.isOnline() || !scan.candidates.hasNext()) {
                target.finish(scan.playerId, player == null || !player.isOnline() ? Set.of() : scan.visible);
                scan = null;
                continue;
            }
            String key = target.update(player, scan.candidates.next(), scan.keys);
            if (key != null) scan.visible.add(key);
        }
    }

    private static final class Scan {
        private final UUID playerId;
        private final Iterator<CrateLocation> candidates;
        private final PlayerKeyCountSnapshot keys;
        private final Set<String> visible = new HashSet<>();

        private Scan(UUID playerId, Iterator<CrateLocation> candidates, PlayerKeyCountSnapshot keys) {
            this.playerId = playerId;
            this.candidates = candidates;
            this.keys = keys;
        }
    }
}
