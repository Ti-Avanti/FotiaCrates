package gg.fotia.crates.modelengine;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateBlockInteraction;
import gg.fotia.fotiamodel.api.FotiaModelApi;
import gg.fotia.fotiamodel.api.ModelInstance;
import gg.fotia.fotiamodel.api.ModelOptions;
import gg.fotia.fotiamodel.api.event.ModelHitEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** FotiaModel 可选原生后端；只在安装配套插件且 Java 版本满足要求时加载。 */
public final class FotiaModelManager implements Listener {
    private record Position(UUID world,int x,int y,int z) {
        static Position of(Location location){return new Position(location.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ());}
    }
    private final FotiaCrates plugin;
    private final CrateBlockInteraction interaction;
    private final Map<Position,ModelInstance> models=new HashMap<>();
    private final Map<UUID,Position> instances=new HashMap<>();
    private final Map<Position,Integer> legacyRanges=new HashMap<>();
    private final Map<Position,java.util.Set<UUID>> audiences=new HashMap<>();
    private final Map<String,Long> reported=new HashMap<>();
    private final org.bukkit.scheduler.BukkitTask visibilityTask;
    private long visibilityCountdown;

    public FotiaModelManager(FotiaCrates plugin){
        this.plugin=plugin;interaction=new CrateBlockInteraction(plugin);plugin.getServer().getPluginManager().registerEvents(this,plugin);
        visibilityTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::refreshAudiences,1,1);
    }
    private FotiaModelApi api(){return plugin.getServer().getPluginManager().isPluginEnabled("FotiaModel")?plugin.getServer().getServicesManager().load(FotiaModelApi.class):null;}
    public boolean isAvailable(){return api()!=null;}
    public boolean hasModel(Location location){var model=models.get(Position.of(location));return isAvailable()&&model!=null&&!model.closed();}

    public void spawnCrateModel(Crate crate,Location location,float yaw) {
        var api=api();if(api==null){report("model-fotiamodel-unavailable",crate,"");return;}
        if(!api.hasModel(crate.getModelEngineId())){report("model-fotiamodel-missing",crate,crate.getModelEngineId());return;}
        Location block=location.getBlock().getLocation();var point=block.clone().add(.5,0,.5);point.setYaw(yaw);
        ModelInstance created=null;
        try {
            created=api.spawn(point,crate.getModelEngineId(),ModelOptions.DEFAULT);
            boolean fallback=false;
            if(crate.getModelEngineViewRange()>0)try{created.viewRange(crate.getModelEngineViewRange());}
            catch(NoSuchMethodError|AbstractMethodError|UnsupportedOperationException unavailable){fallback=true;created.audience(List.of());}
            // 使用原生已验证的骨骼命中事件，保留方块交互作为入口。
            play(created,crate,crate.getModelEngineIdleAnimation(),true);
            removeCrateModel(block);
            block.getBlock().setType(Material.BARRIER);
            models.put(Position.of(block),created);instances.put(created.id(),Position.of(block));
            if(fallback){legacyRanges.put(Position.of(block),crate.getModelEngineViewRange());refreshAudience(Position.of(block));}
        }catch(RuntimeException error){if(created!=null)created.close();report("model-fotiamodel-error",crate,error.getMessage());}
    }
    public void playOpenAnimation(Crate crate,Location location){playAt(crate,location,crate.getModelEngineOpenAnimation(),false);}
    public void playIdleAnimation(Crate crate,Location location){playAt(crate,location,crate.getModelEngineIdleAnimation(),true);}
    private void playAt(Crate crate,Location location,String animation,boolean loop){var model=models.get(Position.of(location));if(model!=null&&!model.closed())try{play(model,crate,animation,loop);}catch(RuntimeException error){report("model-fotiamodel-error",crate,error.getMessage());}}
    private void play(ModelInstance model,Crate crate,String animation,boolean loop){
        if(animation==null||animation.isBlank()){model.stopAnimation();return;}
        if(!model.play(animation,loop,1))report("model-fotiamodel-animation-missing",crate,animation);
    }
    public void removeCrateModel(Location location){remove(Position.of(location));}
    private void remove(Position position){legacyRanges.remove(position);audiences.remove(position);var model=models.remove(position);if(model!=null){instances.remove(model.id());model.close();}}
    public void cleanup(){visibilityTask.cancel();for(var position:List.copyOf(models.keySet()))remove(position);reported.clear();HandlerList.unregisterAll(this);}

    /** 兼容已部署的旧 FotiaModel API：按可视距离更新观察者集合，不重建模型或重复资源加载。 */
    private void refreshAudiences(){
        if(legacyRanges.isEmpty()||--visibilityCountdown>0)return;
        visibilityCountdown=Math.max(1,plugin.getConfig().getLong("performance.models.fotiamodel-visibility-interval-ticks",10));
        for(var position:List.copyOf(legacyRanges.keySet()))refreshAudience(position);
    }
    private void refreshAudience(Position position){
        var model=models.get(position);var world=plugin.getServer().getWorld(position.world());if(model==null||model.closed()||world==null){remove(position);return;}
        double range=legacyRanges.get(position);var center=new Location(world,position.x()+.5,position.y(),position.z()+.5);
        var players=world.getPlayers().stream().filter(player->player.getLocation().distanceSquared(center)<=range*range).toList();
        var ids=players.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        if(!ids.equals(audiences.get(position))){model.audience(players);audiences.put(position,ids);}
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onHit(ModelHitEvent event){
        Position position=instances.get(event.getInstanceId());if(position==null||!(event.getDamager() instanceof Player player))return;
        event.setCancelled(true);var world=plugin.getServer().getWorld(position.world());if(world==null||!player.getWorld().equals(world))return;
        interaction.interact(player,new Location(world,position.x(),position.y(),position.z()),event.isAttack()?Action.LEFT_CLICK_BLOCK:Action.RIGHT_CLICK_BLOCK);
    }
    @EventHandler public void onUnload(EntitiesUnloadEvent event){for(var position:List.copyOf(models.keySet()))if(position.world().equals(event.getWorld().getUID())&&(position.x()>>4)==event.getChunk().getX()&&(position.z()>>4)==event.getChunk().getZ())remove(position);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onWorldUnload(WorldUnloadEvent event){for(var position:List.copyOf(models.keySet()))if(position.world().equals(event.getWorld().getUID()))remove(position);}
    @EventHandler public void onProviderDisable(PluginDisableEvent event){if(event.getPlugin().getName().equals("FotiaModel"))for(var position:List.copyOf(models.keySet()))remove(position);}
    private void report(String key,Crate crate,String detail){
        String id=key+":"+crate.getId()+":"+detail;long now=System.nanoTime();Long last=reported.get(id);if(last!=null&&now-last<30_000_000_000L)return;
        if(reported.size()>=256)reported.clear();reported.put(id,now);
        plugin.getServer().getConsoleSender().sendMessage(plugin.getLanguageManager().getMessage(key,Map.of("crate",crate.getId(),"model",crate.getModelEngineId(),"detail",detail==null?"":detail)));
    }
}
