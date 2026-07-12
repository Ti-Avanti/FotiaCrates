package gg.fotia.crates.pity;

import gg.fotia.crates.FotiaCrates;

import java.util.UUID;

public class PityManager {

    private final FotiaCrates plugin;

    public PityManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

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
}
