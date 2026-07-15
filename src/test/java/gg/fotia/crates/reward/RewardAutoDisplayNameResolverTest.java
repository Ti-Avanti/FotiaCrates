package gg.fotia.crates.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardAutoDisplayNameResolverTest {

    @Test
    void preservesTranslatableItemNameComponentsForClientRendering() {
        Component itemName = Component.translatable("item.default.drill")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        String configuredName = RewardAutoDisplayNameResolver.toConfigName(itemName, Material.PAPER);

        assertEquals(itemName, MiniMessage.miniMessage().deserialize(configuredName));
    }

    @Test
    void onlyEnablesItemNameLookupOnSupportedMinecraftVersions() {
        assertEquals(false, RewardAutoDisplayNameResolver.supportsItemName("1.20.4"));
        assertEquals(true, RewardAutoDisplayNameResolver.supportsItemName("1.20.5"));
        assertEquals(true, RewardAutoDisplayNameResolver.supportsItemName("1.21.11"));
    }
}
