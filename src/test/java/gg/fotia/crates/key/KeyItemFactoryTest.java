package gg.fotia.crates.key;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyItemFactoryTest {

    @Test
    void resolvesCraftEngineItemBeforeMaterialFallback() {
        AtomicReference<String> requestedId = new AtomicReference<>();
        KeyItemProvider provider = itemId -> {
            requestedId.set(itemId);
            return Optional.of(new ItemStack(Material.DIAMOND));
        };
        Key key = new Key("ce_key", "CE Key", Material.PAPER, "CE Key",
                List.of(), 0, false, List.of("*"));
        key.setCraftEngineId("payitem:alipay");

        ItemStack result = new KeyItemFactory(provider).createBaseItem(key);

        assertEquals("payitem:alipay", requestedId.get());
        assertEquals(Material.DIAMOND, result.getType());
    }
}
