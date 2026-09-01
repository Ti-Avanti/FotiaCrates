package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

final class WorldAnimationSupport {

    private WorldAnimationSupport() {
    }

    static Location resolveOrigin(Player player, Location configured) {
        if (configured != null && configured.getWorld() != null) {
            return configured.clone().add(0.5, 0.0, 0.5);
        }
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) {
            direction.setX(0).setY(0).setZ(1);
        } else {
            direction.normalize();
        }
        return location.add(direction.multiply(2.0));
    }

    static ItemDisplay spawnPrivateItem(FotiaCrates plugin, Player viewer, Location location,
                                        ItemStack item, float scale) {
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setTransformation(transformation(scale, 0.0f));
            entity.setViewRange(48.0f);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    static void transform(ItemDisplay display, float scale, float rotation) {
        display.setTransformation(transformation(scale, rotation));
    }

    static void remove(ItemDisplay display) {
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    static double easeOutCubic(double value) {
        double clamped = clamp01(value);
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }

    private static Transformation transformation(float scale, float rotation) {
        return new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(rotation, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        );
    }
}
