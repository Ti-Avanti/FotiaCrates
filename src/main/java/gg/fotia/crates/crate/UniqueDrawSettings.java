package gg.fotia.crates.crate;

import org.bukkit.Material;

import java.util.Locale;

public record UniqueDrawSettings(boolean enabled, boolean replaceObtainedInPreview,
                                 ObtainedIcon obtainedIcon) {

    public UniqueDrawSettings {
        obtainedIcon = obtainedIcon != null ? obtainedIcon : ObtainedIcon.defaults();
    }

    public static UniqueDrawSettings defaults() {
        return new UniqueDrawSettings(false, true, ObtainedIcon.defaults());
    }

    public record ObtainedIcon(Material material, int customModelData, String itemModel) {

        public ObtainedIcon {
            material = material != null
                    && material != Material.AIR
                    && material != Material.CAVE_AIR
                    && material != Material.VOID_AIR
                    ? material
                    : Material.BARRIER;
            customModelData = Math.max(0, customModelData);
            itemModel = itemModel == null ? "" : itemModel.trim().toLowerCase(Locale.ROOT);
        }

        public static ObtainedIcon defaults() {
            return new ObtainedIcon(Material.BARRIER, 0, "");
        }
    }
}
