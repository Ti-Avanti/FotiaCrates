package gg.fotia.crates.animation;

import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;

import java.util.Comparator;
import java.util.List;

public final class AnimationTemplatePlacements {

    private AnimationTemplatePlacements() {
    }

    public static List<Placement> collect(GuiConfig config, int inventorySize) {
        if (config == null || inventorySize <= 0) {
            return List.of();
        }
        return config.getItems().entrySet().stream()
                .filter(entry -> entry.getKey() >= 0 && entry.getKey() < inventorySize)
                .sorted(Comparator.comparingInt(entry -> entry.getKey()))
                .map(entry -> new Placement(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record Placement(int slot, GuiItem item) {
    }
}
