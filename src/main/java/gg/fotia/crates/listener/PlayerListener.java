package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final FotiaCrates plugin;

    public PlayerListener(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getAsyncPlayerDataManager().loadPlayer(event.getPlayer().getUniqueId());
        // 延迟检查待领取奖励，避免登录时消息被淹没
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getPendingRewardManager().onPlayerJoin(event.getPlayer());
        }, 40L); // 2秒后检查
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时动画会自动检测玩家是否在线
        plugin.getAsyncPlayerDataManager().flushAndUnload(event.getPlayer().getUniqueId());
    }
}
