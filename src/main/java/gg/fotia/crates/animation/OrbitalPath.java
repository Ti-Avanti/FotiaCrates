package gg.fotia.crates.animation;

final class OrbitalPath {

    private OrbitalPath() {
    }

    static Point sample(int itemIndex, int itemCount, double progress,
                        double radius, double height, double rotations) {
        double resolvedProgress = Math.max(0.0, Math.min(1.0, progress));
        int resolvedCount = Math.max(1, itemCount);
        double contraction = Math.pow(1.0 - resolvedProgress, 1.35);
        double currentRadius = Math.max(0.0, radius) * contraction;
        double baseAngle = Math.PI * 2.0 * itemIndex / resolvedCount;
        double angle = baseAngle + Math.PI * 2.0 * rotations * resolvedProgress;
        double lift = resolvedProgress * resolvedProgress * 0.65;
        double wave = Math.sin(angle * 2.0) * 0.18 * (1.0 - resolvedProgress);

        return new Point(
                Math.cos(angle) * currentRadius,
                height + wave + lift,
                Math.sin(angle) * currentRadius,
                currentRadius
        );
    }

    record Point(double x, double y, double z, double radius) {
    }
}
