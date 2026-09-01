package gg.fotia.crates.animation;

import gg.fotia.crates.gui.GuiConfig;
import org.bukkit.Material;

import java.util.List;

public record AnimationTemplate(
        String id,
        AnimationType animationType,
        String displayName,
        Material selectorMaterial,
        String selectorName,
        List<String> selectorLore,
        int selectorCustomModelData,
        String selectorItemModel,
        boolean selectorGlow,
        GuiConfig guiConfig,
        CardAnimationSettings cardSettings,
        OrbitalAnimationSettings orbitalSettings,
        VoidRiftAnimationSettings voidRiftSettings,
        MeteorAnimationSettings meteorSettings
) {

    public AnimationTemplate {
        animationType = animationType == null ? AnimationType.ROULETTE : animationType;
        selectorLore = selectorLore == null ? List.of() : List.copyOf(selectorLore);
        selectorItemModel = selectorItemModel == null ? "" : selectorItemModel;
        cardSettings = cardSettings == null ? CardAnimationSettings.from(null) : cardSettings;
        orbitalSettings = orbitalSettings == null
                ? OrbitalAnimationSettings.from(null)
                : orbitalSettings;
        voidRiftSettings = voidRiftSettings == null
                ? VoidRiftAnimationSettings.from(null)
                : voidRiftSettings;
        meteorSettings = meteorSettings == null
                ? MeteorAnimationSettings.from(null)
                : meteorSettings;
    }
}
