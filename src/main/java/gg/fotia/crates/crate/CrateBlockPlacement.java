package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.function.BooleanSupplier;

/** 放置权限检查与最终成功后的登记，避免被后续监听器取消时留下幽灵宝箱。 */
public final class CrateBlockPlacement {
    private final FotiaCrates plugin;

    public CrateBlockPlacement(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public boolean validate(Player player, ItemStack item, Location location) {
        if (plugin.getKeyManager().isPhysicalKey(item)) return false;
        String crateId = plugin.getCrateManager().getCrateIdFromItem(item);
        if (crateId == null) return true;
        String failure = !player.hasPermission("fotiacrates.admin.place") ? "no-permission"
                : plugin.getCrateManager().getCrate(crateId) == null ? "invalid-crate"
                : plugin.getCrateManager().isLocationSet(location) ? "already-crate-location" : null;
        if (failure == null) return true;
        plugin.getLanguageManager().send(player, failure);
        return false;
    }

    public void afterPlacement(Player player, ItemStack item, Location location, BooleanSupplier succeeded) {
        String crateId = plugin.getCrateManager().getCrateIdFromItem(item);
        if (crateId == null) return;
        Location playerLocation = player.getLocation();
        float yaw = (float) Math.toDegrees(Math.atan2(-(playerLocation.getX() - location.getX() - 0.5),
                playerLocation.getZ() - location.getZ() - 0.5));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || !succeeded.getAsBoolean() || plugin.getCrateManager().isLocationSet(location)) return;
            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate == null) return;
            CrateLocation placed = new CrateLocation(location.getWorld().getName(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ(), crateId, crate.isModelEnabled() ? yaw : 0f);
            plugin.getCrateManager().addLocationAsync(placed, () -> {
                if (plugin.getCrateManager().getLocationAt(location) != placed) return;
                if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    if (crate.isModelEnabled()) plugin.getModelEngineManager().ensureCrateModel(crate, location, yaw);
                    plugin.getHologramManager().createHologram(location, crateId);
                }
                if (player.isOnline()) plugin.getLanguageManager().send(player, "crate-block-placed",
                        LanguageManager.placeholders("crate", crate.getName()));
            }, exception -> {
                plugin.getLogger().severe("Failed to save crate location: " + exception.getMessage());
                if (player.isOnline()) plugin.getLanguageManager().send(player, "crate-location-save-failed");
            });
        });
    }
}
