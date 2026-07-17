package gg.fotia.crates.crate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CrateLocationIndex {

    private final Map<BlockPosition, CrateLocation> byPosition = new LinkedHashMap<>();
    private final Map<ChunkPosition, Set<BlockPosition>> byChunk = new HashMap<>();
    private final Map<BlockPosition, Long> insertionOrder = new HashMap<>();
    private long nextInsertionOrder;

    public CrateLocation put(CrateLocation location) {
        Objects.requireNonNull(location, "location");
        BlockPosition position = BlockPosition.of(location);
        CrateLocation previous = byPosition.put(position, location);
        if (previous == null) {
            byChunk.computeIfAbsent(ChunkPosition.of(position), ignored -> new LinkedHashSet<>())
                    .add(position);
            insertionOrder.put(position, nextInsertionOrder++);
        }
        return previous;
    }

    public CrateLocation get(String world, int x, int y, int z) {
        return byPosition.get(new BlockPosition(world, x, y, z));
    }

    public CrateLocation remove(String world, int x, int y, int z) {
        BlockPosition position = new BlockPosition(world, x, y, z);
        CrateLocation removed = byPosition.remove(position);
        if (removed != null) {
            removeFromChunk(position);
            insertionOrder.remove(position);
        }
        return removed;
    }

    public void removeByCrateId(String crateId) {
        Iterator<Map.Entry<BlockPosition, CrateLocation>> iterator = byPosition.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPosition, CrateLocation> entry = iterator.next();
            if (!Objects.equals(crateId, entry.getValue().getCrateId())) {
                continue;
            }
            iterator.remove();
            removeFromChunk(entry.getKey());
            insertionOrder.remove(entry.getKey());
        }
    }

    public List<CrateLocation> values() {
        return List.copyOf(byPosition.values());
    }

    public Collection<CrateLocation> nearby(String world, int blockX, int blockZ, double radius) {
        if (world == null || radius < 0.0 || !Double.isFinite(radius)) {
            return List.of();
        }

        int minChunkX = Math.floorDiv((int) Math.floor(blockX - radius), 16);
        int maxChunkX = Math.floorDiv((int) Math.floor(blockX + radius), 16);
        int minChunkZ = Math.floorDiv((int) Math.floor(blockZ - radius), 16);
        int maxChunkZ = Math.floorDiv((int) Math.floor(blockZ + radius), 16);
        double radiusSquared = radius * radius;
        List<BlockPosition> matches = new ArrayList<>();

        long chunkSpanX = (long) maxChunkX - minChunkX + 1L;
        long chunkSpanZ = (long) maxChunkZ - minChunkZ + 1L;
        long indexedScanThreshold = Math.max(256L, (long) byChunk.size() * 4L);
        if (chunkSpanX <= 0L || chunkSpanZ <= 0L
                || chunkSpanX * chunkSpanZ > indexedScanThreshold) {
            for (BlockPosition position : byPosition.keySet()) {
                if (position.world().equals(world) && isInsideRadius(position, blockX, blockZ, radiusSquared)) {
                    matches.add(position);
                }
            }
        } else {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    Set<BlockPosition> positions = byChunk.get(new ChunkPosition(world, chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (BlockPosition position : positions) {
                        if (isInsideRadius(position, blockX, blockZ, radiusSquared)) {
                            matches.add(position);
                        }
                    }
                }
            }
        }

        matches.sort(Comparator.comparingLong(insertionOrder::get));
        List<CrateLocation> locations = new ArrayList<>(matches.size());
        for (BlockPosition position : matches) {
            CrateLocation location = byPosition.get(position);
            if (location != null) {
                locations.add(location);
            }
        }
        return List.copyOf(locations);
    }

    private boolean isInsideRadius(BlockPosition position, int blockX, int blockZ, double radiusSquared) {
        long deltaX = (long) position.x() - blockX;
        long deltaZ = (long) position.z() - blockZ;
        return (double) deltaX * deltaX + (double) deltaZ * deltaZ <= radiusSquared;
    }

    public List<CrateLocation> inChunk(String world, int chunkX, int chunkZ) {
        if (world == null) {
            return List.of();
        }

        Set<BlockPosition> positions = byChunk.get(new ChunkPosition(world, chunkX, chunkZ));
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }

        List<BlockPosition> ordered = new ArrayList<>(positions);
        ordered.sort(Comparator.comparingLong(insertionOrder::get));
        List<CrateLocation> locations = new ArrayList<>(ordered.size());
        for (BlockPosition position : ordered) {
            CrateLocation location = byPosition.get(position);
            if (location != null) {
                locations.add(location);
            }
        }
        return List.copyOf(locations);
    }

    public int size() {
        return byPosition.size();
    }

    public void clear() {
        byPosition.clear();
        byChunk.clear();
        insertionOrder.clear();
        nextInsertionOrder = 0L;
    }

    private void removeFromChunk(BlockPosition position) {
        ChunkPosition chunk = ChunkPosition.of(position);
        Set<BlockPosition> positions = byChunk.get(chunk);
        if (positions == null) {
            return;
        }
        positions.remove(position);
        if (positions.isEmpty()) {
            byChunk.remove(chunk);
        }
    }

    private record BlockPosition(String world, int x, int y, int z) {
        private BlockPosition {
            Objects.requireNonNull(world, "world");
        }

        private static BlockPosition of(CrateLocation location) {
            return new BlockPosition(location.getWorld(), location.getX(), location.getY(), location.getZ());
        }
    }

    private record ChunkPosition(String world, int x, int z) {
        private static ChunkPosition of(BlockPosition position) {
            return new ChunkPosition(position.world(), Math.floorDiv(position.x(), 16),
                    Math.floorDiv(position.z(), 16));
        }
    }
}
