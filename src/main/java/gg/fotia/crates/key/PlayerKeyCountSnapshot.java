package gg.fotia.crates.key;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** One inventory scan per player's hologram refresh, shared by every nearby crate. */
public final class PlayerKeyCountSnapshot {
    private final KeyManager keys;
    private final Player player;
    private final Map<String, Integer> crateCounts = new HashMap<>();
    private Map<String, Integer> physicalCounts;

    public PlayerKeyCountSnapshot(KeyManager keys, Player player) {
        this.keys = keys;
        this.player = player;
    }

    public int count(String crateId) {
        return crateCounts.computeIfAbsent(crateId, ignored -> {
            if (physicalCounts == null) {
                physicalCounts = new HashMap<>();
                for (ItemStack item : player.getInventory().getContents()) {
                    String id = keys.getKeyId(item);
                    if (id != null) physicalCounts.merge(id, item.getAmount(), Integer::sum);
                }
            }
            int total = 0;
            for (Key key : keys.getKeysForCrate(crateId)) {
                total += physicalCounts.getOrDefault(key.getId(), 0)
                        + keys.getVirtualKeys(player.getUniqueId(), key.getId());
            }
            return total;
        });
    }
}
