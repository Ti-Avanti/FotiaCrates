package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.CrateBlockInteraction;
import gg.fotia.crates.crate.CrateBlockPlacement;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class BlockListener implements Listener {
    private final FotiaCrates plugin;
    private final CrateBlockInteraction interaction;
    private final CrateBlockPlacement placement;

    public BlockListener(FotiaCrates plugin, CrateBlockInteraction interaction, CrateBlockPlacement placement) {
        this.plugin = plugin;
        this.interaction = interaction;
        this.placement = placement;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getCustomBlockSupport().isCustomBlockItem(event.getItemInHand())) return;
        Location location = event.getBlock().getLocation();
        if (!placement.validate(event.getPlayer(), event.getItemInHand(), location)) {
            event.setCancelled(true);
            return;
        }
        if (!event.canBuild()) return;
        var item = event.getItemInHand().clone();
        var placedType = event.getBlock().getType();
        placement.afterPlacement(event.getPlayer(), item, location,
                () -> !event.isCancelled() && event.canBuild()
                        && location.getBlock().getType() == placedType);
    }

    // 自定义方块由其专用事件处理，避免原版事件先取消导致 ItemsAdder 无法派发交互。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || plugin.getCustomBlockSupport().isCustomBlock(event.getClickedBlock())) return;
        Location location = event.getClickedBlock().getLocation();
        if (!interaction.isCrate(location)) return;
        event.setCancelled(true);
        interaction.interact(event.getPlayer(), location, event.getAction());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!interaction.isCrate(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        if (!plugin.getCustomBlockSupport().isCustomBlock(event.getBlock())) {
            plugin.getLanguageManager().send(event.getPlayer(), "crate-break-denied");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof ArmorStand stand)
                || stand.isVisible() || !stand.isMarker()) return;
        Location location = stand.getLocation().getBlock().getLocation();
        if (!interaction.isCrate(location)) return;
        event.setCancelled(true);
        interaction.interact(event.getPlayer(), location, Action.RIGHT_CLICK_BLOCK);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof ArmorStand stand)
                || stand.isVisible() || !stand.isMarker()) return;
        Location location = stand.getLocation().getBlock().getLocation();
        if (!interaction.isCrate(location)) return;
        event.setCancelled(true);
        interaction.interact(player, location, Action.LEFT_CLICK_BLOCK);
    }
}
