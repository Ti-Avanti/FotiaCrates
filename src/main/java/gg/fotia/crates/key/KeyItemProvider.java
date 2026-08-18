package gg.fotia.crates.key;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

@FunctionalInterface
public interface KeyItemProvider {

    Optional<ItemStack> create(String itemId);
}
