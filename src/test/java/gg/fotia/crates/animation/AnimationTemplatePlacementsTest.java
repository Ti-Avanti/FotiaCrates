package gg.fotia.crates.animation;

import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationTemplatePlacementsTest {

    @Test
    void preservesEveryMappedSlotForRepeatedLayoutIcons() {
        GuiItem border = new GuiItem(0, Material.BLACK_STAINED_GLASS_PANE,
                " ", List.of(), 0, false, "", "");
        GuiConfig config = new GuiConfig(
                "card", "Cards", 9, false, Material.AIR, "",
                Map.of(0, border, 1, border, 8, border),
                List.of(), List.of(4), 4, null, null);

        List<Integer> slots = AnimationTemplatePlacements.collect(config, 9).stream()
                .map(AnimationTemplatePlacements.Placement::slot)
                .toList();

        assertEquals(List.of(0, 1, 8), slots);
    }
}
