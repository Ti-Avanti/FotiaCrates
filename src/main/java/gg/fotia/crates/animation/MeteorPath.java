package gg.fotia.crates.animation;

public final class MeteorPath {

    private MeteorPath() {
    }

    public static Point sample(Point start, Point end, double progress) {
        double eased = progress * progress;
        double x = lerp(start.x(), end.x(), eased);
        double y = lerp(start.y(), end.y(), eased) + Math.sin(Math.PI * eased) * 0.35;
        double z = lerp(start.z(), end.z(), eased);
        return new Point(x, y, z);
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * WorldAnimationSupport.clamp01(progress);
    }

    public record Point(double x, double y, double z) {
    }
}
