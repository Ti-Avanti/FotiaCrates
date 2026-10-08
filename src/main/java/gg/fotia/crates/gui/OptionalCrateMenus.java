package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.Animation;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.history.HistoryManager;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

/** 只在管理员启用且依赖可用时加载适配器。 */
public final class OptionalCrateMenus implements AutoCloseable {
    private final FotiaCrates plugin;
    private CrateMenuSettings settings;
    private CrateMenuBridge bridge;
    private boolean unavailable;
    public OptionalCrateMenus(FotiaCrates plugin) { this.plugin = plugin; reload(); }
    public void reload() { close(); unavailable = false; settings = CrateMenuSettings.load(plugin); }
    public int historyLimit() { return settings.enabled() ? settings.historyLimit() : 100; }
    private CrateMenuBridge bridge() {
        if (!settings.enabled() || unavailable || !plugin.getServer().getPluginManager().isPluginEnabled("FutureUI")) return null;
        try {
            if (bridge == null) bridge = new gg.fotia.crates.gui.futureui.FutureCrateMenu(plugin, settings);
            return bridge;
        } catch (LinkageError error) {
            unavailable = true;
            plugin.getLogger().warning("FutureUI 接口不可用，使用原抽奖界面: " + error.getMessage());
            return null;
        }
    }
    public boolean preview(Player player, Crate crate, int page) {
        var b = bridge(); return b != null && b.preview(player, crate, page);
    }
    public boolean history(Player player, UUID target, String name, String crateId, int page,
                           List<HistoryManager.HistoryEntry> entries, HistoryReturnContext back) {
        var b = bridge(); return b != null && b.history(player, target, name, crateId, page, entries, back);
    }
    public boolean results(Player player, Crate crate, List<RewardResult> results) {
        var b = bridge(); return b != null && b.results(player, crate, results);
    }
    public Animation animation(Player player, Crate crate) {
        var b = bridge(); return b == null ? null : b.animation(player, crate);
    }
    @Override public void close() { if (bridge != null) { bridge.close(); bridge = null; } }
}
