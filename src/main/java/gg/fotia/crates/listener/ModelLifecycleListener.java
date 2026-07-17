package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public final class ModelLifecycleListener implements Listener {

    private final FotiaCrates plugin;

    public ModelLifecycleListener(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getModelEngineManager().reconcileChunk(event.getWorld(), chunkX, chunkZ));
    }
}
