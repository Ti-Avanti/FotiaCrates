package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

public final class ModelLifecycleListener implements Listener {

    private final FotiaCrates plugin;

    public ModelLifecycleListener(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听 EntitiesLoadEvent 而非 ChunkLoadEvent：区块实体在 ChunkLoadEvent 之后才加载，
     * 过早 reconcile 会因找不到已持久化的模型底座实体而误判"不健康"并重复生成模型（短暂重影）。
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();

        // 该区块没有宝箱也没有待清理的残留模型时不调度任务
        if (plugin.getCrateManager().getCrateLocationsInChunk(world.getName(), chunkX, chunkZ).isEmpty()
                && !plugin.getModelEngineManager().hasStaleModelsInChunk(world.getName(), chunkX, chunkZ)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getModelEngineManager().reconcileChunk(world, chunkX, chunkZ));
    }
}
