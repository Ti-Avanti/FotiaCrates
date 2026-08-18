package gg.fotia.crates.hook;

import gg.fotia.crates.key.KeyItemProvider;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class CraftEngineKeyItemProvider implements KeyItemProvider {

    private final Logger logger;
    private final Set<String> warnedItemIds = new HashSet<>();

    public CraftEngineKeyItemProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Optional<ItemStack> create(String itemId) {
        try {
            BukkitItemDefinition definition = CraftEngineItems.byId(itemId);
            if (definition == null) {
                warnOnce(itemId, "CraftEngine item does not exist");
                return Optional.empty();
            }

            ItemStack item = definition.buildBukkitItem();
            if (item == null || item.getType().isAir()) {
                warnOnce(itemId, "CraftEngine returned an empty item");
                return Optional.empty();
            }

            warnedItemIds.remove(itemId);
            return Optional.of(item);
        } catch (LinkageError | RuntimeException exception) {
            warnOnce(itemId, "CraftEngine API failed: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private void warnOnce(String itemId, String reason) {
        if (warnedItemIds.add(itemId)) {
            logger.warning("Unable to create CraftEngine key item '" + itemId + "': " + reason);
        }
    }
}
