package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.RarityProbabilitySettings;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.RarityProbabilityAdjustment;
import gg.fotia.crates.reward.RarityProbabilityMode;
import gg.fotia.crates.reward.RarityProbabilityService;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RarityProbabilityEditor {
    private static final String SELECT = "admin_rarity_probability_select";
    private static final String EDIT = "admin_rarity_probability_edit";
    private final FotiaCrates plugin;
    private final RarityProbabilityService service;

    private record Draft(Crate crate, int returnPage, String rarity, RarityProbabilityMode mode,
                         Double percentage, int page) {
        Draft change(RarityProbabilityMode nextMode, Double value, int nextPage) {
            return new Draft(crate, returnPage, rarity, nextMode, value, nextPage);
        }
    }

    public RarityProbabilityEditor(FotiaCrates plugin) {
        this.plugin = plugin;
        this.service = new RarityProbabilityService(plugin);
    }

    public boolean isSaving() {
        return service.isSaving();
    }

    public void open(Player player, Crate crate, int returnPage) {
        if (!allowed(player)) return;
        show(player, new Draft(crate, returnPage, null, settings().mode(), null, 0));
    }

    private RarityProbabilitySettings settings() {
        return RarityProbabilitySettings.read(plugin.getConfig());
    }

    private boolean allowed(Player player) {
        if (!player.hasPermission("fotiacrates.admin.editor")) {
            plugin.getLanguageManager().send(player, "no-permission");
            player.closeInventory();
            return false;
        }
        if (!settings().enabled()) {
            send(player, "disabled");
            return false;
        }
        return true;
    }

    public void click(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        if (!allowed(player)) return;
        Draft draft = holder.getData("probability_draft");
        if (draft == null) return;
        if (service.isSaving()) { send(player, "busy"); return; }
        String rarity = holder.getData("rarity_" + event.getSlot());
        if (rarity != null) {
            show(player, new Draft(draft.crate(), draft.returnPage(), rarity, draft.mode(), settings().preset(rarity), 0));
            return;
        }
        GuiConfig config = config(draft);
        GuiItem item = config == null ? null : config.getItem(event.getSlot());
        if (item == null || item.getAction() == null) return;
        switch (item.getAction()) {
            case "back", "cancel" -> returnToManager(player, draft);
            case "previous_page" -> show(player, draft.change(draft.mode(), draft.percentage(), draft.page() - 1));
            case "next_page" -> show(player, draft.change(draft.mode(), draft.percentage(), draft.page() + 1));
            case "probability_mode" -> show(player, draft.change(draft.mode().next(), draft.percentage(), 0));
            case "probability_preset" -> show(player, draft.change(draft.mode(), settings().preset(draft.rarity()), 0));
            case "probability_input" -> {
                send(player, "input");
                plugin.getGuiManager().startInputSession(player, "rarity_probability", draft, (p, input, data) -> {
                    if (!allowed(p)) return;
                    Draft previous = (Draft) data;
                    if ("cancel".equalsIgnoreCase(input)) { show(p, previous); return; }
                    try {
                        show(p, previous.change(previous.mode(), RarityProbabilityAdjustment.parse(input), 0));
                    } catch (IllegalArgumentException exception) {
                        send(p, "invalid-number");
                        show(p, previous);
                    }
                });
            }
            case "probability_confirm" -> confirm(player, draft);
            default -> { }
        }
    }

    private void confirm(Player player, Draft draft) {
        if (draft.rarity() == null || draft.percentage() == null) { send(player, "missing-preset"); return; }
        RarityProbabilityAdjustment.Plan plan;
        try {
            plan = calculate(draft);
        } catch (IllegalArgumentException exception) {
            send(player, exception.getMessage());
            return;
        }
        service.save(draft.crate(), plan, () -> player.isOnline() && player.hasPermission("fotiacrates.admin.editor"), result -> {
            if (!player.isOnline()) return;
            send(player, result);
            returnToManager(player, draft);
        });
    }

    private void show(Player player, Draft draft) {
        if (!player.isOnline()) return;
        GuiConfig config = config(draft);
        if (config == null || RarityProbabilityView.content(config).isEmpty()) { send(player, "gui-invalid"); return; }
        List<String> rarities = rarities(draft.crate());
        List<Reward> matched = draft.crate().getRewards().stream()
                .filter(r -> draft.rarity() != null && r.getRarity().equalsIgnoreCase(draft.rarity())).toList();
        List<Integer> slots = RarityProbabilityView.content(config);
        int count = draft.rarity() == null ? rarities.size() : matched.size();
        int maxPage = Math.max(0, (count - 1) / slots.size());
        int page = Math.max(0, Math.min(draft.page(), maxPage));
        Draft current = draft.change(draft.mode(), draft.percentage(), page);
        Map<String, String> values = values(player, current);
        values.put("page", String.valueOf(page + 1));
        values.put("pages", String.valueOf(maxPage + 1));
        CrateGuiHolder holder = new CrateGuiHolder(GuiType.ADMIN_RARITY_PROBABILITY, draft.crate());
        holder.setData("probability_draft", current);
        Inventory inventory = RarityProbabilityView.create(config, holder, values);
        GuiItem template = config.getItemByAction(draft.rarity() == null ? "select_rarity" : "probability_reward");
        if (template == null) { send(player, "gui-invalid"); return; }
        RarityProbabilityAdjustment.Plan plan = preview(current);
        for (int i = 0; i < slots.size() && page * slots.size() + i < count; i++) {
            int index = page * slots.size() + i;
            Map<String, String> itemValues = new HashMap<>(values);
            if (draft.rarity() == null) {
                String id = rarities.get(index);
                populateRarity(player, current.crate(), id, itemValues);
                Double preset = settings().preset(id);
                itemValues.put("percentage", preset == null ? raw(player, "missing-preset") : RewardProbability.format(preset));
                holder.setData("rarity_" + slots.get(i), id);
            } else {
                Reward reward = matched.get(index);
                itemValues.put("reward", reward.getDisplayName());
                itemValues.put("reward_id", reward.getId());
                itemValues.put("old_weight", RewardProbability.formatWeight(reward.getChance()));
                itemValues.put("new_weight", plan == null ? "-" : RewardProbability.formatWeight(plan.weights().get(reward.getId())));
                itemValues.put("old_probability", RewardProbability.format(RewardProbability.percentage(reward, draft.crate().getRewards())));
                itemValues.put("new_probability", plan == null ? "-" : RewardProbability.format(plan.weights().get(reward.getId())));
            }
            inventory.setItem(slots.get(i), RarityProbabilityView.item(template, itemValues));
        }
        player.openInventory(inventory);
    }

    private Map<String, String> values(Player player, Draft draft) {
        Map<String, String> values = new HashMap<>();
        values.put("crate", draft.crate().getName());
        values.put("mode", modeName(player, draft.mode()));
        for (RarityProbabilityMode mode : RarityProbabilityMode.values()) {
            String name = modeName(player, mode);
            values.put("mode_" + mode.name().toLowerCase(Locale.ROOT), draft.mode() == mode
                    ? raw(player, "mode-selected").replace("{mode}", name) : name);
        }
        values.put("percentage", draft.percentage() == null ? raw(player, "missing-preset") : RewardProbability.format(draft.percentage()));
        if (draft.rarity() == null) return values;
        populateRarity(player, draft.crate(), draft.rarity(), values);
        RarityProbabilityAdjustment.Plan plan = preview(draft);
        String status = "ready";
        try {
            if (draft.percentage() == null) status = "missing-preset";
            else calculate(draft);
        } catch (IllegalArgumentException exception) { status = exception.getMessage(); }
        values.put("status", raw(player, status));
        values.put("new_total", plan == null ? "-" : RewardProbability.format(plan.targetTotal()));
        values.put("other_total", plan == null ? "-" : RewardProbability.format(100 - plan.targetTotal()));
        return values;
    }

    private void populateRarity(Player player, Crate crate, String id, Map<String, String> values) {
        List<Reward> matches = crate.getRewards().stream().filter(r -> r.getRarity().equalsIgnoreCase(id)).toList();
        values.put("rarity", plugin.getConfigManager().getRarityDisplayName(id));
        values.put("rarity_id", id);
        values.put("matched", String.valueOf(matches.stream().filter(r -> r.getChance() > 0).count()));
        values.put("excluded", String.valueOf(matches.stream().filter(r -> r.getChance() == 0).count()));
        double max = crate.getRewards().stream().mapToDouble(Reward::getChance).max().orElse(0);
        double total = max > 0 ? crate.getRewards().stream().mapToDouble(r -> r.getChance() / max).sum() : 0;
        double selected = total > 0 ? matches.stream().mapToDouble(r -> (r.getChance() / max) / total * 100).sum() : 0;
        values.put("old_total", RewardProbability.format(selected));
    }

    private RarityProbabilityAdjustment.Plan preview(Draft draft) {
        if (draft.rarity() == null || draft.percentage() == null) return null;
        try { return calculate(draft); } catch (IllegalArgumentException exception) { return null; }
    }

    private RarityProbabilityAdjustment.Plan calculate(Draft draft) {
        return RarityProbabilityAdjustment.plan(RarityProbabilityAdjustment.snapshot(draft.crate().getRewards()),
                draft.rarity(), draft.mode(), draft.percentage());
    }

    private List<String> rarities(Crate crate) {
        var ids = new LinkedHashSet<>(plugin.getConfigManager().getRarityIds());
        crate.getRewards().forEach(r -> ids.add(r.getRarity().toLowerCase(Locale.ROOT)));
        return List.copyOf(ids);
    }

    private GuiConfig config(Draft draft) {
        return plugin.getGuiManager().getConfigManager().getGuiConfig(draft.rarity() == null ? SELECT : EDIT);
    }

    private void returnToManager(Player player, Draft draft) {
        Crate crate = plugin.getCrateManager().getCrate(draft.crate().getId());
        if (crate == null) plugin.getGuiManager().openAdminGui(player);
        else plugin.getGuiManager().openRewardManagerGui(player, crate, draft.returnPage());
    }

    private String modeName(Player player, RarityProbabilityMode mode) {
        return raw(player, "mode-" + mode.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private String raw(Player player, String key) {
        return plugin.getLanguageManager().getRawMessage(player, "rarity-probability-" + key);
    }

    private void send(Player player, String key) {
        plugin.getLanguageManager().send(player, "rarity-probability-" + key);
    }
}
