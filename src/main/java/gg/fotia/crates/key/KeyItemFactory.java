package gg.fotia.crates.key;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class KeyItemFactory {

    private final KeyItemProvider craftEngineProvider;

    public KeyItemFactory(KeyItemProvider craftEngineProvider) {
        this.craftEngineProvider = craftEngineProvider;
    }

    public ItemStack createBaseItem(Key key) {
        return resolve(key).item();
    }

    public Resolution resolve(Key key) {
        ItemStack customItem = key.getCustomItem();
        if (isUsable(customItem)) {
            return new Resolution(customItem, true);
        }

        String craftEngineId = key.getCraftEngineId();
        if (craftEngineId != null && !craftEngineId.isBlank()) {
            Optional<ItemStack> externalItem = craftEngineProvider.create(craftEngineId.trim());
            if (externalItem.isPresent() && isUsable(externalItem.get())) {
                return new Resolution(externalItem.get().clone(), true);
            }
        }

        return new Resolution(new ItemStack(key.getMaterial()), false);
    }

    private boolean isUsable(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    public record Resolution(ItemStack item, boolean configuredItem) {
    }
}
