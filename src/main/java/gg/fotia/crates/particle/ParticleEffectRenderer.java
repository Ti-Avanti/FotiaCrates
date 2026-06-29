package gg.fotia.crates.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class ParticleEffectRenderer {

    public void render(CrateParticleEffect effect, Location origin, Player player, Location crateLocation, int elapsedTicks) {
        if (effect == null || origin == null || origin.getWorld() == null || !effect.isEnabled()) {
            return;
        }

        switch (effect.getMode()) {
            case POINT -> spawn(effect, origin, effect.getCount(), effect.getOffsetX(), effect.getOffsetY(), effect.getOffsetZ());
            case RANDOM_AREA -> renderRandomArea(effect, origin);
            case CIRCLE -> renderCircle(effect, origin, elapsedTicks, effect.getRadius(), 0.0);
            case DOUBLE_CIRCLE -> {
                renderCircle(effect, origin, elapsedTicks, effect.getRadius(), 0.0);
                renderCircle(effect, origin, -elapsedTicks, effect.getRadius() * 0.75, Math.max(0.3, effect.getHeight() * 0.5));
            }
            case ORBIT -> renderOrbit(effect, origin, elapsedTicks);
            case TRIPLE_ORBIT -> renderTripleOrbit(effect, origin, elapsedTicks);
            case ATOM -> renderAtom(effect, origin, elapsedTicks);
            case HALO -> renderCircle(effect, origin, elapsedTicks, effect.getRadius(), Math.max(1.6, effect.getHeight()));
            case CROWN -> renderCrown(effect, origin, elapsedTicks);
            case HELIX -> renderHelix(effect, origin, elapsedTicks, false);
            case DOUBLE_HELIX -> renderHelix(effect, origin, elapsedTicks, true);
            case DNA_SPIRAL -> renderDnaSpiral(effect, origin, elapsedTicks);
            case VORTEX -> renderVortex(effect, origin, elapsedTicks);
            case BURST -> spawn(effect, origin, effect.getCount(), effect.getRadius(), effect.getRadius(), effect.getRadius());
            case RING_EXPAND -> renderCircle(effect, origin, elapsedTicks, expandingRadius(effect, elapsedTicks), 0.0);
            case SPHERE_EXPAND -> renderSphere(effect, origin, elapsedTicks);
            case FOUNTAIN -> spawn(effect, origin.clone().add(0, 0.2, 0), effect.getCount(), effect.getRadius() * 0.35, effect.getHeight(), effect.getRadius() * 0.35);
            case FALLING -> renderFalling(effect, origin);
            case BEAM -> renderBeam(effect, origin, player, crateLocation);
            case STAR -> renderStar(effect, origin, elapsedTicks);
        }
    }

    private void renderRandomArea(CrateParticleEffect effect, Location origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int points = Math.max(1, effect.getCount());
        for (int i = 0; i < points; i++) {
            Location point = origin.clone().add(
                    random.nextDouble(-effect.getRadius(), effect.getRadius()),
                    random.nextDouble(0.0, Math.max(0.1, effect.getHeight())),
                    random.nextDouble(-effect.getRadius(), effect.getRadius())
            );
            spawn(effect, point, 1, 0, 0, 0);
        }
    }

    private void renderCircle(CrateParticleEffect effect, Location origin, int elapsedTicks, double radius, double yOffset) {
        int points = Math.max(6, effect.getCount());
        double rotation = elapsedTicks * 0.08;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i / points) + rotation;
            Location point = origin.clone().add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
            spawn(effect, point, 1, 0, 0, 0);
        }
    }

    private void renderOrbit(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int satellites = Math.max(2, Math.min(6, effect.getCount() / 4));
        double rotation = elapsedTicks * 0.18;
        for (int i = 0; i < satellites; i++) {
            double angle = (Math.PI * 2 * i / satellites) + rotation;
            double y = 0.35 + (Math.sin(rotation + i) + 1.0) * Math.max(0.2, effect.getHeight()) * 0.25;
            Location point = origin.clone().add(Math.cos(angle) * effect.getRadius(), y, Math.sin(angle) * effect.getRadius());
            spawn(effect, point, Math.max(1, effect.getCount() / satellites), 0.02, 0.02, 0.02);
        }
    }

    private void renderTripleOrbit(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        double height = Math.max(0.4, effect.getHeight());
        renderCircle(effect, origin, elapsedTicks, effect.getRadius(), 0.0);
        renderCircle(effect, origin, -elapsedTicks, effect.getRadius() * 0.78, height * 0.42);
        renderCircle(effect, origin, elapsedTicks * 2, effect.getRadius() * 0.56, height * 0.84);
    }

    private void renderAtom(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int points = Math.max(8, effect.getCount() / 2);
        double radius = Math.max(0.2, effect.getRadius());
        double verticalRadius = Math.max(0.2, effect.getHeight() * 0.45);
        double centerY = Math.max(0.2, effect.getHeight() * 0.5);
        double rotation = elapsedTicks * 0.1;

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i / points) + rotation;
            spawn(effect, origin.clone().add(Math.cos(angle) * radius, centerY, Math.sin(angle) * radius), 1, 0, 0, 0);
            spawn(effect, origin.clone().add(Math.cos(angle) * radius, centerY + Math.sin(angle) * verticalRadius, 0), 1, 0, 0, 0);
            spawn(effect, origin.clone().add(0, centerY + Math.sin(angle) * verticalRadius, Math.cos(angle) * radius), 1, 0, 0, 0);
        }
    }

    private void renderHelix(CrateParticleEffect effect, Location origin, int elapsedTicks, boolean doubleHelix) {
        int points = Math.max(8, effect.getCount());
        double rotation = elapsedTicks * 0.12;
        for (int i = 0; i < points; i++) {
            double progress = (double) i / points;
            double angle = (Math.PI * 2 * progress * 2.0) + rotation;
            double y = progress * Math.max(0.2, effect.getHeight());
            Location point = origin.clone().add(Math.cos(angle) * effect.getRadius(), y, Math.sin(angle) * effect.getRadius());
            spawn(effect, point, 1, 0, 0, 0);
            if (doubleHelix) {
                Location opposite = origin.clone().add(Math.cos(angle + Math.PI) * effect.getRadius(), y,
                        Math.sin(angle + Math.PI) * effect.getRadius());
                spawn(effect, opposite, 1, 0, 0, 0);
            }
        }
    }

    private void renderDnaSpiral(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int points = Math.max(12, effect.getCount());
        double rotation = elapsedTicks * 0.12;
        double height = Math.max(0.4, effect.getHeight());

        for (int i = 0; i < points; i++) {
            double progress = (double) i / points;
            double angle = (Math.PI * 2 * progress * 2.4) + rotation;
            double y = progress * height;
            Location first = origin.clone().add(Math.cos(angle) * effect.getRadius(), y, Math.sin(angle) * effect.getRadius());
            Location second = origin.clone().add(Math.cos(angle + Math.PI) * effect.getRadius(), y,
                    Math.sin(angle + Math.PI) * effect.getRadius());
            spawn(effect, first, 1, 0, 0, 0);
            spawn(effect, second, 1, 0, 0, 0);
            if (i % Math.max(3, points / 8) == 0) {
                drawLine(effect, first, second);
            }
        }
    }

    private void renderVortex(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int points = Math.max(14, effect.getCount());
        double height = Math.max(0.5, effect.getHeight());
        double rotation = elapsedTicks * 0.16;

        for (int i = 0; i < points; i++) {
            double progress = (double) i / points;
            double radius = Math.max(0.05, effect.getRadius() * (1.0 - progress * 0.75));
            double angle = rotation + progress * Math.PI * 7.0;
            Location point = origin.clone().add(Math.cos(angle) * radius, progress * height, Math.sin(angle) * radius);
            spawn(effect, point, 1, 0, 0, 0);
        }
    }

    private void renderSphere(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int points = Math.max(12, effect.getCount());
        double radius = expandingRadius(effect, elapsedTicks);
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1.0 - (i / (double) (points - 1)) * 2.0;
            double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double angle = i * goldenAngle + elapsedTicks * 0.05;
            Location point = origin.clone().add(
                    Math.cos(angle) * horizontal * radius,
                    y * radius + effect.getHeight() * 0.5,
                    Math.sin(angle) * horizontal * radius
            );
            spawn(effect, point, 1, 0, 0, 0);
        }
    }

    private void renderFalling(CrateParticleEffect effect, Location origin) {
        Location top = origin.clone().add(0, Math.max(0.5, effect.getHeight()), 0);
        spawn(effect, top, effect.getCount(), effect.getRadius(), 0.1, effect.getRadius());
    }

    private void renderBeam(CrateParticleEffect effect, Location origin, Player player, Location crateLocation) {
        Location start = crateLocation != null ? crateLocation.clone().add(0.5, 1.1, 0.5) : origin.clone();
        Location end = player != null ? player.getLocation().clone().add(0, 1.1, 0) : origin.clone().add(0, Math.max(1.0, effect.getHeight()), 0);
        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length <= 0.01) {
            spawn(effect, start, effect.getCount(), 0.02, 0.02, 0.02);
            return;
        }

        Vector step = direction.normalize().multiply(Math.max(0.15, length / Math.max(4, effect.getCount())));
        Location point = start.clone();
        int points = Math.max(4, (int) Math.ceil(length / step.length()));
        for (int i = 0; i <= points; i++) {
            spawn(effect, point, 1, 0, 0, 0);
            point.add(step);
        }
    }

    private void renderStar(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int points = 5;
        double rotation = elapsedTicks * 0.08;
        Location[] vertices = new Location[points];
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2 * i / points) - Math.PI / 2;
            vertices[i] = origin.clone().add(Math.cos(angle) * effect.getRadius(), effect.getHeight() * 0.5,
                    Math.sin(angle) * effect.getRadius());
        }
        int[] order = {0, 2, 4, 1, 3, 0};
        for (int i = 0; i < order.length - 1; i++) {
            drawLine(effect, vertices[order[i]], vertices[order[i + 1]]);
        }
    }

    private void renderCrown(CrateParticleEffect effect, Location origin, int elapsedTicks) {
        int points = Math.max(8, Math.min(16, effect.getCount() / 2));
        double rotation = elapsedTicks * 0.08;
        double radius = Math.max(0.2, effect.getRadius());
        double baseY = Math.max(0.4, effect.getHeight() * 0.65);
        double spikeY = Math.max(baseY + 0.25, effect.getHeight());

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i / points) + rotation;
            Location base = origin.clone().add(Math.cos(angle) * radius, baseY, Math.sin(angle) * radius);
            spawn(effect, base, 1, 0, 0, 0);
            if (i % 2 == 0) {
                Location peak = origin.clone().add(Math.cos(angle) * radius * 0.72, spikeY, Math.sin(angle) * radius * 0.72);
                drawLine(effect, base, peak);
            }
        }
    }

    private void drawLine(CrateParticleEffect effect, Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length <= 0.01) {
            return;
        }
        Vector step = direction.normalize().multiply(0.15);
        Location point = start.clone();
        int points = (int) Math.ceil(length / 0.15);
        for (int i = 0; i <= points; i++) {
            spawn(effect, point, 1, 0, 0, 0);
            point.add(step);
        }
    }

    private double expandingRadius(CrateParticleEffect effect, int elapsedTicks) {
        double progress = Math.min(1.0, (double) Math.max(1, elapsedTicks) / Math.max(1, effect.getDuration()));
        return Math.max(0.05, effect.getRadius() * progress);
    }

    private void spawn(CrateParticleEffect effect, Location location, int count, double offsetX, double offsetY, double offsetZ) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Particle particle = ParticleCompat.resolveParticle(effect.getParticle(), Particle.FLAME);
        Object data = ParticleCompat.createData(particle, effect);
        try {
            if (particle.getDataType() != Void.class && data == null) {
                return;
            }
            if (data != null) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, effect.getSpeed(), data);
            } else {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, effect.getSpeed());
            }
        } catch (IllegalArgumentException | LinkageError ignored) {
            world.spawnParticle(Particle.FLAME, location, Math.max(1, count), offsetX, offsetY, offsetZ, effect.getSpeed());
        }
    }
}
