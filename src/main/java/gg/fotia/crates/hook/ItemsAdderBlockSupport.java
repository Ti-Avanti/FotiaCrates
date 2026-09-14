package gg.fotia.crates.hook;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.ItemsAdder;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import dev.lone.itemsadder.api.Events.CustomBlockPlaceEvent;
import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.CrateBlockInteraction;
import gg.fotia.crates.crate.CrateBlockPlacement;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 仅在 ItemsAdder 已启用时加载，使用公开 API，不反射或伪造原版事件。 */
public final class ItemsAdderBlockSupport implements CustomBlockSupport, Listener {
    private final FotiaCrates plugin;
    private final CrateBlockInteraction interaction;
    private final CrateBlockPlacement placement;

    public ItemsAdderBlockSupport(FotiaCrates plugin, CrateBlockInteraction interaction,
                                  CrateBlockPlacement placement) {
        this.plugin = plugin;
        this.interaction = interaction;
        this.placement = placement;
    }

    @Override
    public boolean isReady() {
        return plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder") && ItemsAdder.areItemsLoaded();
    }

    @Override
    public boolean isCustomBlockItem(ItemStack item) {
        return isReady() && item != null && !item.getType().isAir() && CustomBlock.byItemStack(item) != null;
    }

    @Override
    public boolean isCustomBlock(Block block) {
        return isReady() && block != null && CustomBlock.byAlreadyPlaced(block) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(CustomBlockPlaceEvent event) {
        var location = event.getBlock().getLocation();
        // 必须读取实际放置物品，注册表模板不含 FotiaCrates 的 crate_block 标记。
        ItemStack item = event.getItemInHand();
        if (!placement.validate(event.getPlayer(), item, location)) {
            event.setCancelled(true);
            return;
        }
        if (!event.isCanBuild()) return;
        String blockId = event.getNamespacedID();
        placement.afterPlacement(event.getPlayer(), item, location, () -> {
            if (event.isCancelled() || !event.isCanBuild() || !isReady()) return false;
            CustomBlock placed = CustomBlock.byAlreadyPlaced(location.getBlock());
            return placed != null && blockId.equals(placed.getNamespacedID());
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(CustomBlockInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getBlockClicked() == null) return;
        var location = event.getBlockClicked().getLocation();
        if (!interaction.isCrate(location)) return;
        event.setCancelled(true);
        interaction.interact(event.getPlayer(), location, event.getAction());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(CustomBlockBreakEvent event) {
        if (!interaction.isCrate(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        plugin.getLanguageManager().send(event.getPlayer(), "crate-break-denied");
    }
}
