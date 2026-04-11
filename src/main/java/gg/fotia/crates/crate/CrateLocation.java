package gg.fotia.crates.crate;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public class CrateLocation {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String crateId;
    private final float yaw; // 模型朝向

    public CrateLocation(String world, int x, int y, int z, String crateId) {
        this(world, x, y, z, crateId, 0f);
    }

    public CrateLocation(String world, int x, int y, int z, String crateId, float yaw) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.crateId = crateId;
        this.yaw = yaw;
    }

    public CrateLocation(Location location, String crateId) {
        this(location, crateId, 0f);
    }

    public CrateLocation(Location location, String crateId, float yaw) {
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.crateId = crateId;
        this.yaw = yaw;
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getCrateId() { return crateId; }
    public float getYaw() { return yaw; }

    public boolean matches(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return location.getWorld().getName().equals(world) &&
                location.getBlockX() == x &&
                location.getBlockY() == y &&
                location.getBlockZ() == z;
    }

    public Location toLocation(World world) {
        if (world == null) {
            return null;
        }
        Location loc = new Location(world, x, y, z);
        loc.setYaw(yaw);
        return loc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrateLocation that = (CrateLocation) o;
        return x == that.x && y == that.y && z == that.z && Objects.equals(world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }
}
