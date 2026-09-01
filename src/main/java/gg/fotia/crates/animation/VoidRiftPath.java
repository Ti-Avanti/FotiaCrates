package gg.fotia.crates.animation;

public final class VoidRiftPath {

    private VoidRiftPath() {
    }

    public static Point sample(int index, int count, double progress,
                               VoidRiftAnimationSettings settings) {
        double suction = WorldAnimationSupport.easeOutCubic((progress - 0.08) / 0.64);
        double radius = settings.radius() * (1.0 - suction);
        double baseAngle = Math.PI * 2.0 * index / Math.max(1, count);
        double angle = baseAngle + progress * settings.rotations() * Math.PI * 2.0;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius * 0.72;
        double y = settings.riftHeight() + Math.sin(angle * 1.7) * 0.22 * (1.0 - suction);
        return new Point(x, y, z);
    }

    public record Point(double x, double y, double z) {
    }
}
