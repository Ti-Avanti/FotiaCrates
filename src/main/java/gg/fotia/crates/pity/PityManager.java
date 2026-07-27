package gg.fotia.crates.pity;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PityManager {

    private final FotiaCrates plugin;

    public PityManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * 共享计数器（自上次最高档保底后累计抽数），供 PAPI 变量与 GUI 显示使用
     */
    public int getPityCount(UUID uuid, String crateId) {
        return plugin.getAsyncPlayerDataManager().getPityCount(uuid, crateId);
    }

    public void setPityCount(UUID uuid, String crateId, int count) {
        plugin.getAsyncPlayerDataManager().setPityCount(uuid, crateId, count);
    }

    public void incrementPityCount(UUID uuid, String crateId) {
        int current = getPityCount(uuid, crateId);
        setPityCount(uuid, crateId, current + 1);
    }

    public void resetPityCount(UUID uuid, String crateId) {
        setPityCount(uuid, crateId, 0);
    }

    public boolean shouldTriggerPity(UUID uuid, String crateId, int pityThreshold) {
        return getPityCount(uuid, crateId) >= pityThreshold - 1;
    }

    /**
     * 获取某个保底档位的独立计数（自该档上次触发/满足以来的抽数）
     */
    public int getTierCount(UUID uuid, String crateId, Crate.PityTier tier) {
        return plugin.getAsyncPlayerDataManager().getPityCount(
                uuid, PityProgressKey.stableKey(crateId, tier));
    }

    public void setTierCount(UUID uuid, String crateId, Crate.PityTier tier, int count) {
        plugin.getAsyncPlayerDataManager().setPityCount(
                uuid, PityProgressKey.stableKey(crateId, tier), count);
    }

    /**
     * 将旧版按次数阈值保存的分层计数迁移到稳定层级 ID。
     * 每个稳定键独立判断是否已初始化，因此后续新增档位也能正确建立进度。
     */
    public void ensureTierMigration(UUID uuid, Crate crate) {
        var dataManager = plugin.getAsyncPlayerDataManager();
        String crateId = crate.getId();
        List<Crate.PityTier> tiers = crate.getPityTiers();
        String migrationMarkerKey = PityProgressKey.migrationMarkerKey(crateId);
        boolean migrateLegacyTiers = !dataManager.hasPityCount(uuid, migrationMarkerKey);
        Map<String, Integer> legacyTierCounts = migrateLegacyTiers
                ? dataManager.getPityCountsByPrefix(uuid, PityProgressKey.legacyPrefix(crateId))
                : Map.of();

        Set<String> exactLegacyKeys = new HashSet<>();
        for (Crate.PityTier tier : tiers) {
            String exactKey = PityProgressKey.legacyKey(crateId, tier.getCount());
            if (legacyTierCounts.containsKey(exactKey)) {
                exactLegacyKeys.add(exactKey);
            }
        }

        List<Map.Entry<String, Integer>> unmatchedLegacy = legacyTierCounts.entrySet().stream()
                .filter(entry -> !exactLegacyKeys.contains(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> legacyThreshold(
                        entry.getKey(), PityProgressKey.legacyPrefix(crateId))))
                .toList();
        int unmatchedIndex = 0;
        int sharedCount = getPityCount(uuid, crateId);

        for (Crate.PityTier tier : tiers) {
            String stableKey = PityProgressKey.stableKey(crateId, tier);
            if (dataManager.hasPityCount(uuid, stableKey)) {
                continue;
            }

            String exactLegacyKey = PityProgressKey.legacyKey(crateId, tier.getCount());
            Integer initialCount = legacyTierCounts.get(exactLegacyKey);
            if (initialCount == null && unmatchedIndex < unmatchedLegacy.size()) {
                initialCount = unmatchedLegacy.get(unmatchedIndex++).getValue();
            }
            if (initialCount == null) {
                initialCount = sharedCount > 0 ? sharedCount % tier.getCount() : 0;
            }
            dataManager.setPityCount(uuid, stableKey, initialCount);
        }

        if (migrateLegacyTiers) {
            dataManager.setPityCount(uuid, migrationMarkerKey, 1);
        }
    }

    private int legacyThreshold(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
