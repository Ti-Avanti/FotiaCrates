package gg.fotia.crates.hook;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import gg.fotia.crates.gui.CrateGuiHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PreviewLorePackets extends PacketListenerAbstract implements Listener {
    private final PreviewLoreFooter footer;
    private final Map<UUID, Integer> topSizes = new ConcurrentHashMap<>();

    PreviewLorePackets(Plugin plugin) {
        // EnchantmentSlots 通过 softdepend 先注册；在其渲染完成后追加每个图标自己的说明。
        super(PacketListenerPriority.MONITOR);
        footer = new PreviewLoreFooter(plugin);
    }

    void register(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    boolean attach(org.bukkit.inventory.ItemStack icon, List<Component> lines) {
        return footer.attach(icon, lines);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void opened(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof CrateGuiHolder holder) {
            switch (holder.getGuiType()) {
                case PREVIEW, ADMIN_CRATE_EDIT, ADMIN_REWARD_EDIT ->
                        topSizes.put(event.getPlayer().getUniqueId(), event.getInventory().getSize());
                default -> { }
            }
        }
    }

    @EventHandler
    public void closed(InventoryCloseEvent event) {
        topSizes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        topSizes.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        var type = event.getPacketType();
        if (type != PacketType.Play.Server.WINDOW_ITEMS && type != PacketType.Play.Server.SET_SLOT) return;
        if (event.isCancelled() || topSizes.isEmpty()) return;
        UUID viewer = event.getUser().getUUID();
        Integer size = viewer == null ? null : topSizes.get(viewer);
        if (size == null) return;
        if (type == PacketType.Play.Server.SET_SLOT) {
            var packet = new WrapperPlayServerSetSlot(event);
            if (packet.getWindowId() <= 0 || packet.getSlot() < 0 || packet.getSlot() >= size) return;
            ItemStack item = packet.getItem();
            ItemStack updated = footer.append(item);
            if (item != updated) {
                packet.setItem(updated);
                event.markForReEncode(true);
            }
        } else {
            var packet = new WrapperPlayServerWindowItems(event);
            if (packet.getWindowId() <= 0) return;
            List<ItemStack> items = packet.getItems();
            List<ItemStack> updated = null;
            for (int slot = 0; slot < Math.min(size, items.size()); slot++) {
                ItemStack item = items.get(slot);
                ItemStack result = footer.append(item);
                if (item == result) continue;
                if (updated == null) updated = new ArrayList<>(items);
                updated.set(slot, result);
            }
            if (updated != null) {
                packet.setItems(updated);
                event.markForReEncode(true);
            }
        }
    }

    void close() {
        HandlerList.unregisterAll(this);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        topSizes.clear();
    }
}
