package gg.fotia.crates.gui.futureui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateOpenService;
import gg.fotia.crates.crate.RewardPreviewSorter;
import gg.fotia.crates.gui.CrateMenuSettings;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import gg.fotia.crates.util.MessageUtil;
import gg.fotia.futureui.config.Node;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.*;

/** 绘制只读内存快照；历史查询由菜单控制器异步加载。 */
final class CrateMenuData {
    private final FotiaCrates plugin;
    private final CrateMenuSettings settings;
    private final CrateOpenService opening;
    CrateMenuData(FotiaCrates plugin, CrateMenuSettings settings) { this.plugin = plugin; this.settings = settings; opening = new CrateOpenService(plugin); }
    int keys(Player p, CrateMenuSession s) { return s.crate == null ? 0 : plugin.getKeyManager().getTotalKeysForCrate(p, s.crate.getId()); }
    int maxAmount(Player p, CrateMenuSession s) {
        return Math.max(1, Math.min(keys(p, s), s.crate != null && s.crate.isMultiOpenEnabled() ? s.crate.getMultiOpenMax() : 1));
    }
    boolean canDraw(Player p, CrateMenuSession s) {
        return s.crate != null && p.hasPermission("fotiacrates.use") && opening.hasOpenPermission(p, s.crate)
                && plugin.getAsyncPlayerDataManager().isReady(p.getUniqueId()) && keys(p, s) > 0;
    }
    int count(CrateMenuSession s) {
        return switch (s.view) { case "history" -> s.history.size(); case "results" -> s.results.size(); default -> s.crate == null ? 0 : s.crate.getRewards().size(); };
    }
    int pages(CrateMenuSession s) { return Math.max(1, (count(s) + settings.profile(s.compact).pageSize() - 1) / settings.profile(s.compact).pageSize()); }
    List<Node> rows(Player p, CrateMenuSession s, String view) {
        s.page = Math.max(0, Math.min(s.page, pages(s) - 1));
        s.amount = Math.max(1, Math.min(s.amount, maxAmount(p, s)));
        if (view.equals("page")) return List.of(page(p, s));
        if (view.equals("frames")) {
            List<Node> rows = new ArrayList<>();
            for (int i = 0; i < s.frames.size(); i++) {
                int slots = settings.profile(s.compact).reelSlots();
                int width = ((s.compact ? 252 : 540 - 9 * (slots - 1)) / slots) - 8;
                var values = reward(s.crate, s.frames.get(i), width).values();
                Map<String, Object> copy = new LinkedHashMap<>(values); copy.put("selected", s.progress >= 1 && i == s.selectedFrame);
                rows.add(Node.of(copy));
            }
            return rows;
        }
        if (!view.equals("entries")) return List.of();
        List<Node> entries = new ArrayList<>();
        boolean singleResult = s.view.equals("results") && s.results.size() == 1;
        int nameWidth = s.compact ? (singleResult ? 153 : 108) : (singleResult ? 330 : 70);
        int first = s.page * settings.profile(s.compact).pageSize(), end = Math.min(count(s), first + settings.profile(s.compact).pageSize());
        List<Reward> rewards = s.crate == null ? List.of() : RewardPreviewSorter.sort(s.crate.getRewards(), s.crate.getPreviewSortMode());
        Set<String> collected = s.crate != null && s.crate.isUniqueDrawEnabled()
                ? plugin.getAsyncPlayerDataManager().getCollectedRewardIds(p.getUniqueId(), s.crate.getId()) : Set.of();
        for (int i = first; i < end; i++) {
            Map<String, Object> value = new LinkedHashMap<>();
            if (s.view.equals("history")) {
                var entry = s.history.get(i); Crate crate = plugin.getCrateManager().getCrate(entry.getCrateId());
                Reward reward = crate == null ? null : crate.getRewards().stream().filter(r -> r.getId().equals(entry.getRewardId())).findFirst().orElse(null);
                value.putAll(reward(crate, reward, s.compact ? 108 : 70).values());
                value.put("name", clipped(entry.getRewardName(), s.compact ? 108 : 70));
                value.put("detail", Component.text(entry.formattedTime().substring(5, 16)));
                value.put("mode", "history");
                value.put("history", true);
            } else {
                Reward reward = s.view.equals("results") ? s.results.get(i).getActualReward() : rewards.get(i);
                value.putAll(reward(s.crate, reward, nameWidth).values());
                String detail = s.view.equals("results") ? "#" + (i + 1) : switch (s.crate.getPreviewChanceDisplayMode()) {
                    case PERCENTAGE -> RewardProbability.format(RewardProbability.percentage(reward, rewards));
                    case WEIGHT -> RewardProbability.formatWeight(reward.getChance());
                    case HIDDEN -> "";
                };
                value.put("detail", Component.text(detail));
                value.put("mode", s.view.equals("results") ? "results" : s.crate.getPreviewChanceDisplayMode().name().toLowerCase(Locale.ROOT));
                value.put("obtained", s.view.equals("preview") && collected.contains(reward.getId()));
                value.put("history", false);
            }
            value.put("draw-index", s.view.equals("results") ? i + 1 : 0);
            entries.add(Node.of(value));
        }
        return entries;
    }
    private Node page(Player p, CrateMenuSession s) {
        Map<String, Object> v = new LinkedHashMap<>();
        boolean idle = !s.busy && !plugin.getOpenSessionManager().isActive(p.getUniqueId());
        v.put("name", s.crate == null ? Component.empty() : clipped(s.crate.getName(), s.compact ? 72 : 153));
        v.put("crate-icon", s.crate == null ? settings.fallbackIcon() : settings.crateIcons().getOrDefault(s.crate.getId(), settings.fallbackIcon()));
        v.put("view", s.view); v.put("feedback", s.feedback); v.put("busy", s.busy); v.put("idle", idle);
        v.put("keys", keys(p, s)); v.put("amount", s.amount); v.put("maximum", maxAmount(p, s));
        v.put("can-draw", idle && canDraw(p, s)); v.put("can-less", idle && s.amount > 1);
        v.put("can-more", idle && s.amount < maxAmount(p, s)); v.put("can-layout", idle && !s.loading);
        v.put("can-preview", idle && s.crate != null && s.crate.isPreviewEnabled() && p.hasPermission("fotiacrates.preview"));
        v.put("can-history", idle && p.hasPermission("fotiacrates.history")); v.put("can-results", idle && !s.results.isEmpty());
        v.put("page", s.page + 1); v.put("pages", pages(s)); v.put("count", count(s));
        v.put("previous", idle && s.page > 0); v.put("next", idle && s.page + 1 < pages(s));
        v.put("empty", count(s) == 0); v.put("loading", s.loading); v.put("progress", s.progress);
        v.put("reel-slots", settings.profile(s.compact).reelSlots());
        v.put("reel-position", s.reelPosition); v.put("reel-direction", settings.direction());
        v.put("history-other", s.historyTarget != null && !s.historyTarget.equals(p.getUniqueId()));
        v.put("history-name", clipped(s.historyName, s.compact ? 72 : 153));
        Crate.PityTier tier = null; int pity = 0;
        if (s.crate != null && s.crate.isPityEnabled()) for (var candidate : s.crate.getPityTiers()) {
            int current = plugin.getPityManager().getTierCount(p.getUniqueId(), s.crate.getId(), candidate);
            if (tier == null || candidate.getCount() - current < tier.getCount() - pity) { tier = candidate; pity = current; }
        }
        v.put("has-pity", tier != null); v.put("pity", pity); v.put("pity-target", tier == null ? 0 : tier.getCount());
        v.put("pity-progress", tier == null ? 0 : Math.min(1, (double) pity / tier.getCount()));
        v.put("pity-rarity", tier == null ? Component.empty() : clipped(plugin.getConfigManager().getRarityDisplayName(tier.getRarity()), 100));
        return Node.of(v);
    }
    private Node reward(Crate crate, Reward reward, int width) {
        if (reward == null) return Node.of(Map.of("image", settings.fallbackIcon(), "name", Component.empty(), "rarity", Component.empty(), "rare", false));
        String key = crate == null ? "" : crate.getId() + "/" + reward.getId();
        String image = settings.rewardIcons().getOrDefault(key, settings.materials().getOrDefault(reward.getDisplayItem().getType().name().toLowerCase(Locale.ROOT), settings.fallbackIcon()));
        return Node.of(Map.of("image", image, "name", clipped(reward.getDisplayName(), width),
                "rarity", MessageUtil.parse(plugin.getConfigManager().getRarityDisplayName(reward.getRarity())), "rare", reward.shouldBroadcast()));
    }
    private static Component clipped(String text, int width) {
        String plain = MessageUtil.stripColor(text == null ? "" : text); int used = 0; StringBuilder out = new StringBuilder();
        int[] points = plain.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int next = points[i] > 255 ? 9 : 6;
            if (used + next + (i + 1 < points.length ? 9 : 0) > width) { out.append('…'); break; }
            out.appendCodePoint(points[i]); used += next;
        }
        return Component.text(out.toString());
    }
}
