package gg.fotia.crates.gui.futureui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.Animation;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.gui.CrateMenuBridge;
import gg.fotia.crates.gui.CrateMenuSettings;
import gg.fotia.crates.gui.HistoryReturnContext;
import gg.fotia.crates.history.HistoryManager;
import gg.fotia.futureui.api.FutureUIService;
import gg.fotia.futureui.api.MenuContext;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 显示会话与生命周期；扣钥匙和奖励发放全部留在原抽奖服务。 */
public final class FutureCrateMenu implements CrateMenuBridge, Listener {
    static final String TOKEN = "fotiacrates.session", EXTENSION = "fotiacrates:menu";
    final FotiaCrates plugin;
    final CrateMenuSettings settings;
    final CrateMenuData data;
    private final NamespacedKey layoutKey;
    private final Map<UUID, CrateMenuSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> lastResults = new HashMap<>();
    private final Set<UUID> leaving = new HashSet<>();
    private final List<AutoCloseable> registrations = new ArrayList<>();
    private FutureUIService service;
    private boolean closed, releasing;

    public FutureCrateMenu(FotiaCrates plugin, CrateMenuSettings settings) {
        this.plugin = plugin; this.settings = settings; data = new CrateMenuData(plugin, settings);
        layoutKey = new NamespacedKey(plugin, "crate-menu-layout");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private boolean connect() {
        if (closed || releasing || !Bukkit.getPluginManager().isPluginEnabled("FutureUI")) return false;
        var current = Bukkit.getServicesManager().load(FutureUIService.class);
        if (current == null) return false;
        if (current == service) return true;
        release(); service = current;
        var actions = new CrateMenuActions(this);
        registrations.add(service.registerAction(plugin, EXTENSION, actions::run));
        registrations.add(service.registerData(plugin, EXTENSION, (ctx, node) -> {
            mainThread(); var session = current(ctx);
            return CompletableFuture.completedFuture(session == null || !allowed(ctx.player(), session)
                    ? List.of() : data.rows(ctx.player(), session, node.text("view", "page")));
        }));
        return true;
    }
    private boolean compact(Player player) {
        String value = player.getPersistentDataContainer().get(layoutKey, PersistentDataType.STRING);
        return value == null ? settings.compactDefault() : value.equals("compact");
    }
    private boolean available(Player player, boolean compact) {
        return !leaving.contains(player.getUniqueId()) && player.isOnline() && connect() && service.canRender(player, settings.profile(compact).menu());
    }
    private boolean open(Player player, CrateMenuSession session) {
        CrateMenuSession old = sessions.put(player.getUniqueId(), session);
        if (service.open(player, settings.profile(session.compact).menu(), arguments(session))) return true;
        if (old != null && visible(player, old)) sessions.put(player.getUniqueId(), old);
        else sessions.remove(player.getUniqueId(), session);
        return false;
    }
    @Override public boolean preview(Player player, Crate crate, int page) {
        if (!available(player, compact(player))) return false;
        if (!player.hasPermission("fotiacrates.preview") || !crate.isPreviewEnabled()) return reject(player);
        if (plugin.getOpenSessionManager().isActive(player.getUniqueId())) return true;
        var s = new CrateMenuSession(crate, compact(player)); s.page = Math.max(0, page);
        open(player, s); return true;
    }
    @Override public boolean history(Player player, UUID target, String name, String crateId, int page,
                                     List<HistoryManager.HistoryEntry> entries, HistoryReturnContext back) {
        if (!available(player, compact(player))) return false;
        if (!historyAllowed(player, target)) return reject(player);
        if (plugin.getOpenSessionManager().isActive(player.getUniqueId())) return true;
        var s = new CrateMenuSession(crateId == null ? null : plugin.getCrateManager().getCrate(crateId), compact(player));
        s.view = "history"; s.historyTarget = target; s.historyName = name; s.page = Math.max(0, page);
        s.history = List.copyOf(entries); s.back = back == null ? HistoryReturnContext.close() : back;
        open(player, s); return true;
    }
    @Override public boolean results(Player player, Crate crate, List<RewardResult> results) {
        if (results.isEmpty() || !available(player, compact(player))) return false;
        UUID receipt = results.get(0).getSettlementId();
        if (receipt.equals(lastResults.put(player.getUniqueId(), receipt))) {
            var existing = sessions.get(player.getUniqueId());
            if (existing != null) refresh(player, existing);
            return true;
        }
        var s = sessions.get(player.getUniqueId());
        if (s != null && s.detached) { sessions.remove(player.getUniqueId(), s); return true; }
        boolean update = s != null && visible(player, s);
        if (!update) s = new CrateMenuSession(crate, compact(player));
        s.crate = crate; s.busy = false; s.view = "results"; s.feedback = "complete"; s.page = 0;
        s.loading = false; s.historyRevision++;
        s.results = List.copyOf(results); s.roll = null;
        if (update) refresh(player, s); else open(player, s);
        return true;
    }
    @Override public Animation animation(Player player, Crate crate) {
        return settings.animate() && available(player, compact(player)) ? new CrateRollAnimation(this) : null;
    }
    CrateMenuSession startRoll(Player player, Crate crate, CrateRollAnimation roll) {
        if (!available(player, compact(player))) return null;
        var s = sessions.get(player.getUniqueId());
        if (s != null && s.detached) return null;
        boolean update = s != null && visible(player, s);
        if (!update) s = new CrateMenuSession(crate, compact(player));
        s.crate = crate; s.busy = true; s.view = "spin"; s.feedback = "spinning"; s.progress = 0; s.roll = roll;
        s.frames = roll.frames(s.compact, 0);
        s.reelPosition = 1;
        s.selectedFrame = -1;
        s.loading = false; s.historyRevision++;
        if (update) refresh(player, s); else if (!open(player, s)) return null;
        return s;
    }
    void showHistory(Player player, CrateMenuSession s) {
        UUID target = s.historyTarget == null ? player.getUniqueId() : s.historyTarget;
        if (!historyAllowed(player, target)) { reject(player); return; }
        s.view = "history"; s.loading = true; s.page = 0; s.historyTarget = target;
        int revision = ++s.historyRevision;
        plugin.getHistoryManager().getHistoryAsync(target, s.crate == null ? null : s.crate.getId(),
                settings.historyLimit(), entries -> {
                    if (sessions.get(player.getUniqueId()) != s || s.detached || !s.view.equals("history") || s.historyRevision != revision) return;
                    s.history = List.copyOf(entries); s.loading = false; refresh(player, s);
                });
    }
    void watchCompletion(Player player, CrateMenuSession s) {
        if (s.watcher != null) s.watcher.cancel();
        s.watcher = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != s || !s.busy) {
                s.watcher.cancel(); return;
            }
            if (!plugin.getOpenSessionManager().isActive(player.getUniqueId())) {
                s.watcher.cancel(); s.busy = false; s.view = "preview"; s.feedback = "failed";
                if (s.detached) sessions.remove(player.getUniqueId(), s); else refresh(player, s);
            }
        }, 1, 5);
    }
    void layout(Player player, CrateMenuSession s, boolean small) {
        if (!available(player, small)) return;
        var next = new CrateMenuSession(s.crate, small);
        next.view = s.view; next.results = s.results; next.history = s.history; next.historyTarget = s.historyTarget;
        next.historyName = s.historyName; next.back = s.back; next.amount = s.amount;
        if (open(player, next)) player.getPersistentDataContainer().set(layoutKey, PersistentDataType.STRING, small ? "compact" : "standard");
    }
    CrateMenuSession current(MenuContext ctx) {
        var s = sessions.get(ctx.player().getUniqueId());
        return s != null && s.token.toString().equals(Objects.toString(ctx.variables().get(TOKEN), ""))
                && settings.profile(s.compact).menu().equals(ctx.session().menu.id()) ? s : null;
    }
    boolean allowed(Player player, CrateMenuSession s) {
        if (s.detached) return false;
        return switch (s.view) {
            case "history" -> historyAllowed(player, s.historyTarget);
            case "preview" -> player.hasPermission("fotiacrates.preview") && s.crate != null && s.crate.isPreviewEnabled();
            default -> player.hasPermission("fotiacrates.use");
        };
    }
    static boolean historyAllowed(Player p, UUID target) {
        return p.hasPermission("fotiacrates.history") && (target == null || target.equals(p.getUniqueId()) || p.hasPermission("fotiacrates.history.others"));
    }
    boolean visible(Player player, CrateMenuSession s) {
        return service != null && service.isOpen(player, settings.profile(s.compact).menu(), arguments(s));
    }
    void refresh(Player player, CrateMenuSession s) { if (!s.detached && visible(player, s)) service.refresh(player); }
    void dismiss(Player player, CrateMenuSession s) {
        s.detached = true;
        if (s.roll != null) s.roll.finish(true);
        if (!plugin.getOpenSessionManager().isActive(player.getUniqueId())) sessions.remove(player.getUniqueId(), s);
    }
    void closeScreen(Player player) { service.close(player); }
    static void mainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Crate menus require the server thread"); }
    private static Map<String, String> arguments(CrateMenuSession s) { return Map.of(TOKEN, s.token.toString()); }
    private boolean reject(Player player) { plugin.getLanguageManager().send(player, "no-permission"); return true; }
    @EventHandler(priority = EventPriority.LOWEST) public void quit(PlayerQuitEvent e) {
        leaving.add(e.getPlayer().getUniqueId());
        var s = sessions.get(e.getPlayer().getUniqueId()); if (s != null) dismiss(e.getPlayer(), s);
        if (s != null && s.watcher != null) s.watcher.cancel();
        sessions.remove(e.getPlayer().getUniqueId()); lastResults.remove(e.getPlayer().getUniqueId());
        UUID id = e.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> leaving.remove(id));
    }
    @EventHandler public void join(PlayerJoinEvent e) { leaving.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void disabled(PluginDisableEvent e) { if (e.getPlugin().getName().equals("FutureUI")) release(); }
    private void release() {
        releasing = true;
        for (var entry : List.copyOf(sessions.entrySet())) {
            var s = entry.getValue(); s.detached = true;
            if (s.watcher != null) s.watcher.cancel();
            if (s.roll != null) s.roll.finish(true);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && visible(p, s)) service.close(p);
        }
        sessions.clear(); lastResults.clear(); leaving.clear();
        for (var r : registrations) try { r.close(); } catch (Exception e) { plugin.getLogger().warning("注销 FutureUI 抽奖菜单失败: " + e.getMessage()); }
        registrations.clear(); service = null; releasing = false;
    }
    @Override public void close() { closed = true; release(); HandlerList.unregisterAll(this); }
}
