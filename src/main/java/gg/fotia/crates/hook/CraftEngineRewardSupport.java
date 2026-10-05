package gg.fotia.crates.hook;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.reward.config.ExternalRewardItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public final class CraftEngineRewardSupport implements ExternalRewardItems, Listener {
    private final FotiaCrates plugin;
    private boolean dataReady;

    public CraftEngineRewardSupport(FotiaCrates plugin) {
        this.plugin = plugin;
        dataReady = !BukkitCraftEngine.instance().isReloading() && !CraftEngineItems.loadedItems().isEmpty();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean ready() {
        return plugin.getServer().getPluginManager().isPluginEnabled("CraftEngine")
                && dataReady && !BukkitCraftEngine.instance().isReloading();
    }

    @Override
    public ItemStack create(String id) {
        if (!ready()) throw new ExternalRewardItems.Pending("CraftEngine 尚未完成加载或正在重载");
        var definition = CraftEngineItems.byId(id);
        if (definition == null) throw new IllegalArgumentException("CraftEngine 物品不存在: " + id);
        ItemStack item = definition.buildBukkitItem();
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("CraftEngine 返回了空物品: " + id);
        return item.clone();
    }

    @Override
    public boolean exists(String id) {
        try {
            return ready() && CraftEngineItems.byId(id) != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReload(CraftEngineReloadEvent event) {
        // CraftEngine 可在异步线程派发此事件，物品构建及宝箱索引必须回到主线程。
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            dataReady = true;
            refreshWhenReady(40);
        });
    }

    private void refreshWhenReady(int attempts) {
        if (!plugin.isEnabled()) return;
        if (!ready()) {
            if (attempts > 0) plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> refreshWhenReady(attempts - 1), 5L);
            return;
        }
        plugin.getCrateManager().reloadExternalRewardItems();
    }
}
