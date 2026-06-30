package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationManager;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateOpenService;
import gg.fotia.crates.gui.CrateGuiHolder;
import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;
import gg.fotia.crates.gui.GuiType;
import gg.fotia.crates.key.KeyType;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.particle.CrateParticleEffect;
import gg.fotia.crates.particle.ParticleCompat;
import gg.fotia.crates.particle.ParticleEffectMode;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.particle.ParticleTarget;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuiListener implements Listener {

    private final FotiaCrates plugin;
    private final CrateOpenService crateOpenService;
    // 防止重复开箱的冷却集合
    private final Set<UUID> openingPlayers = new HashSet<>();
    // 防止短时间内重复触发的冷却时间戳（毫秒）
    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 500; // 500毫秒冷却

    public GuiListener(FotiaCrates plugin) {
        this.plugin = plugin;
        this.crateOpenService = new CrateOpenService(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof CrateGuiHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GuiType guiType = holder.getGuiType();

        // 物品输入界面特殊处理 - 允许物品交互
        if (guiType == GuiType.ITEM_INPUT) {
            handleItemInputClick(event, player, holder);
            return;
        }

        // 奖励物品管理界面特殊处理 - 允许物品交互
        if (guiType == GuiType.REWARD_ITEMS) {
            handleRewardItemsClick(event, player, holder);
            return;
        }

        // 取消所有点击事件，防止物品被取出
        event.setCancelled(true);

        // 只处理点击GUI顶部的事件，忽略点击玩家背包
        if (event.getClickedInventory() != inventory) {
            return;
        }

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        switch (guiType) {
            case PREVIEW -> handlePreviewClick(event, player, holder);
            case ADMIN -> handleAdminClick(event, player, holder);
            case ADMIN_CRATE_EDIT -> handleCrateEditClick(event, player, holder);
            case ADMIN_REWARD_EDIT -> handleRewardEditClick(event, player, holder);
            case ADMIN_KEYS -> handleKeysClick(event, player, holder);
            case ADMIN_KEY_EDIT -> handleKeyEditClick(event, player, holder);
            case HISTORY -> handleHistoryClick(event, player, holder);
            case ANIMATION, MAIN_MENU -> {} // 动画GUI不处理点击
        }
    }

    private void handlePreviewClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("preview");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleAction(player, guiItem.getAction(), guiItem.getActionValue(), holder);
            }
        }
    }

    private void handleAdminClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();

        // 检查是否是稀有度管理界面
        Boolean isRarityManager = holder.getData("rarity_manager");
        if (isRarityManager != null && isRarityManager) {
            handleRarityManagerClick(event, player, holder);
            return;
        }

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("admin");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleAction(player, guiItem.getAction(), guiItem.getActionValue(), holder);
                return;
            }
        }

        // 检查是否点击了抽奖箱
        List<Integer> contentSlots = config != null ? config.getContentSlots() : List.of();
        int slotIndex = contentSlots.indexOf(slot);
        if (slotIndex >= 0) {
            List<Crate> crates = plugin.getCrateManager().getAllCrates().stream().toList();
            if (slotIndex < crates.size()) {
                Crate crate = crates.get(slotIndex);
                if (event.isLeftClick()) {
                    plugin.getGuiManager().openCrateEditGui(player, crate);
                } else if (event.isRightClick()) {
                    plugin.getGuiManager().openPreview(player, crate);
                }
            }
        }
    }

    private void handleRarityManagerClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();

        // 稀有度槽位
        List<Integer> raritySlots = contentSlots("admin_rarity_manager",
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25));
        List<String> rarityIds = plugin.getConfigManager().getRarityIds();
        String action = actionOrFallback("admin_rarity_manager", slot, Map.of(45, "back"));

        // 返回按钮
        if ("back".equals(action)) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }

        // 检查是否点击了稀有度槽位
        for (int i = 0; i < raritySlots.size(); i++) {
            if (slot == raritySlots.get(i)) {
                if (i < rarityIds.size()) {
                    // 编辑现有稀有度
                    String rarityId = rarityIds.get(i);
                    gg.fotia.crates.config.ConfigManager.RarityConfig rarity = plugin.getConfigManager().getRarity(rarityId);

                    if (event.isShiftClick() && event.isRightClick()) {
                        // 删除稀有度
                        plugin.getConfigManager().deleteRarity(rarityId);
                        plugin.getLanguageManager().send(player, "admin-rarity-deleted",
                                LanguageManager.placeholders("rarity", rarityId));
                        plugin.getGuiManager().openRarityManagerGui(player);
                    } else if (event.isRightClick()) {
                        // 编辑颜色
                        plugin.getLanguageManager().send(player, "admin-input-color");
                        plugin.getGuiManager().startInputSession(player, "rarity_color", rarityId, (p, input, data) -> {
                            String id = (String) data;
                            gg.fotia.crates.config.ConfigManager.RarityConfig r = plugin.getConfigManager().getRarity(id);
                            if (r != null) {
                                plugin.getConfigManager().saveRarity(id, r.getDisplayName(), input);
                                plugin.getGuiManager().openRarityManagerGui(p);
                            }
                        });
                    } else if (event.isLeftClick()) {
                        // 编辑显示名称
                        plugin.getLanguageManager().send(player, "admin-input-name");
                        plugin.getGuiManager().startInputSession(player, "rarity_name", rarityId, (p, input, data) -> {
                            String id = (String) data;
                            gg.fotia.crates.config.ConfigManager.RarityConfig r = plugin.getConfigManager().getRarity(id);
                            if (r != null) {
                                plugin.getConfigManager().saveRarity(id, input, r.getColor());
                                plugin.getGuiManager().openRarityManagerGui(p);
                            }
                        });
                    }
                } else if (i == rarityIds.size()) {
                    // 添加新稀有度
                    plugin.getLanguageManager().send(player, "admin-input-rarity-id");
                    plugin.getGuiManager().startInputSession(player, "new_rarity", null, (p, input, data) -> {
                        String newId = input.toLowerCase().replace(" ", "_");
                        if (plugin.getConfigManager().getRarity(newId) != null) {
                            plugin.getLanguageManager().send(p, "admin-rarity-exists");
                            plugin.getGuiManager().openRarityManagerGui(p);
                            return;
                        }
                        plugin.getConfigManager().saveRarity(newId, "<!i><white>" + input, "white");
                        plugin.getLanguageManager().send(p, "admin-rarity-created",
                                LanguageManager.placeholders("rarity", newId));
                        plugin.getGuiManager().openRarityManagerGui(p);
                    });
                }
                break;
            }
        }
    }

    private void handleCrateEditClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();
        Crate crate = holder.getCrate();

        if (crate == null) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }

        // 检查是否是动画选择模式
        Boolean selectAnimation = holder.getData("select_animation");
        if (selectAnimation != null && selectAnimation) {
            handleAnimationSelectClick(event, player, crate, slot);
            return;
        }

        // 检查是否是保底编辑模式
        Boolean editPity = holder.getData("edit_pity");
        if (editPity != null && editPity) {
            handlePityEditClick(event, player, crate, slot);
            return;
        }

        // 检查是否是基本设置编辑模式
        Boolean editBasic = holder.getData("edit_basic");
        if (editBasic != null && editBasic) {
            handleBasicEditClick(event, player, crate, slot);
            return;
        }

        // 检查是否是多连抽编辑模式
        Boolean editMultiOpen = holder.getData("edit_multi_open");
        if (editMultiOpen != null && editMultiOpen) {
            handleMultiOpenEditClick(event, player, crate, slot);
            return;
        }

        Boolean editParticles = holder.getData("edit_particles");
        if (editParticles != null && editParticles) {
            handleParticleEditClick(event, player, crate, holder, slot);
            return;
        }

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("admin_crate_edit");
        if (slot == 3 && config != null && !hasConfiguredAction(config, "edit_particles")) {
            plugin.getGuiManager().openParticleEditGui(player, crate);
            return;
        }
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleCrateEditAction(player, guiItem.getAction(), crate, holder, event.isShiftClick());
                return;
            }
        }

        // 检查是否点击了奖励
        List<Integer> contentSlots = config != null ? config.getContentSlots() : List.of();
        int slotIndex = contentSlots.indexOf(slot);
        if (slotIndex >= 0) {
            List<Reward> rewards = crate.getRewards();
            if (slotIndex < rewards.size()) {
                Reward reward = rewards.get(slotIndex);
                if (event.isShiftClick() && event.isRightClick()) {
                    // 删除奖励
                    plugin.getCrateManager().removeReward(crate.getId(), reward.getId());
                    plugin.getLanguageManager().send(player, "admin-reward-removed");
                    plugin.getGuiManager().openCrateEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
                } else if (event.isLeftClick()) {
                    // 编辑奖励
                    plugin.getGuiManager().openRewardEditGui(player, crate, reward);
                }
            }
        }
    }

    private void handleAnimationSelectClick(InventoryClickEvent event, Player player, Crate crate, int slot) {
        String action = actionOrFallback("admin_animation_select", slot, Map.of(
                36, "back",
                10, "toggle_gui_animation",
                13, "select_roulette",
                15, "select_instant",
                28, "toggle_physical_animation",
                30, "adjust_physical_height",
                32, "adjust_animation_duration"
        ));
        if (action == null) return;

        switch (action) {
            case "back" -> plugin.getGuiManager().openCrateEditGui(player, crate); // 返回
            case "toggle_gui_animation" -> { // GUI动画开关
                plugin.getCrateManager().toggleGuiAnimation(crate.getId());
                Crate updated = plugin.getCrateManager().getCrate(crate.getId());
                String status = updated.isAnimationEnabled() ? "开启" : "关闭";
                plugin.getLanguageManager().send(player, "admin-animation-updated",
                        LanguageManager.placeholders("type", "GUI动画 " + status));
                plugin.getGuiManager().openAnimationSelectGui(player, updated);
            }
            case "select_roulette" -> { // ROULETTE (轮盘动画)
                plugin.getCrateManager().updateAnimationType(crate.getId(), gg.fotia.crates.animation.AnimationType.ROULETTE);
                plugin.getLanguageManager().send(player, "admin-animation-updated",
                        LanguageManager.placeholders("type", "ROULETTE"));
                plugin.getGuiManager().openAnimationSelectGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case "select_instant" -> { // INSTANT (无动画)
                plugin.getCrateManager().updateAnimationType(crate.getId(), gg.fotia.crates.animation.AnimationType.INSTANT);
                plugin.getLanguageManager().send(player, "admin-animation-updated",
                        LanguageManager.placeholders("type", "INSTANT"));
                plugin.getGuiManager().openAnimationSelectGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case "toggle_physical_animation" -> { // 物理动画开关
                plugin.getCrateManager().togglePhysicalAnimation(crate.getId());
                Crate updated = plugin.getCrateManager().getCrate(crate.getId());
                String status = updated.isPhysicalAnimationEnabled() ? "开启" : "关闭";
                plugin.getLanguageManager().send(player, "admin-animation-updated",
                        LanguageManager.placeholders("type", "物理动画 " + status));
                plugin.getGuiManager().openAnimationSelectGui(player, updated);
            }
            case "adjust_physical_height" -> { // 物理动画高度
                double delta = 0;
                if (event.isLeftClick()) {
                    delta = 0.1;
                } else if (event.isRightClick()) {
                    delta = -0.1;
                }
                double newHeight = Math.max(0.5, Math.min(5.0, crate.getPhysicalAnimationHeight() + delta));
                newHeight = Math.round(newHeight * 10.0) / 10.0; // 保留一位小数
                plugin.getCrateManager().updatePhysicalAnimationHeight(crate.getId(), newHeight);
                plugin.getGuiManager().openAnimationSelectGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case "adjust_animation_duration" -> { // 动画时长
                if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    // 中键输入精确数值
                    plugin.getLanguageManager().send(player, "admin-input-duration");
                    plugin.getGuiManager().startInputSession(player, "animation_duration", crate.getId(), (p, input, data) -> {
                        String crateId = (String) data;
                        try {
                            int newDuration = Integer.parseInt(input);
                            newDuration = Math.max(1, Math.min(30, newDuration));
                            plugin.getCrateManager().updateAnimationDuration(crateId, newDuration);
                            plugin.getGuiManager().openAnimationSelectGui(p, plugin.getCrateManager().getCrate(crateId));
                        } catch (NumberFormatException e) {
                            plugin.getLanguageManager().send(p, "invalid-number");
                            plugin.getGuiManager().openAnimationSelectGui(p, plugin.getCrateManager().getCrate(crateId));
                        }
                    });
                } else {
                    int delta = 0;
                    if (event.isLeftClick()) {
                        delta = 1;
                    } else if (event.isRightClick()) {
                        delta = -1;
                    }
                    int newDuration = Math.max(1, Math.min(30, crate.getAnimationDuration() + delta));
                    plugin.getCrateManager().updateAnimationDuration(crate.getId(), newDuration);
                    plugin.getGuiManager().openAnimationSelectGui(player, plugin.getCrateManager().getCrate(crate.getId()));
                }
            }
        }
    }

    private void handlePityEditClick(InventoryClickEvent event, Player player, Crate crate, int slot) {
        // 保底等级槽位
        List<Integer> tierSlots = contentSlots("admin_pity_edit", List.of(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34));
        List<Crate.PityTier> tiers = crate.getPityTiers();
        String action = actionOrFallback("admin_pity_edit", slot, Map.of(
                45, "back",
                4, "toggle_pity",
                53, "save"
        ));

        if ("back".equals(action)) {
            plugin.getGuiManager().openCrateEditGui(player, crate);
            return;
        }
        if ("toggle_pity".equals(action)) {
            plugin.getCrateManager().togglePity(crate.getId());
            plugin.getGuiManager().openPityEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            return;
        }
        if ("save".equals(action)) {
            plugin.getCrateManager().saveCrate(crate.getId());
            plugin.getLanguageManager().send(player, "admin-crate-saved");
            plugin.getGuiManager().openCrateEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            return;
        }

        switch (slot) {
            case 45 -> plugin.getGuiManager().openCrateEditGui(player, crate); // 返回
            case 4 -> { // 切换启用/禁用
                plugin.getCrateManager().togglePity(crate.getId());
                plugin.getGuiManager().openPityEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case 53 -> { // 保存并返回
                plugin.getCrateManager().saveCrate(crate.getId());
                plugin.getLanguageManager().send(player, "admin-crate-saved");
                plugin.getGuiManager().openCrateEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            default -> {
                // 检查是否点击了保底等级槽位
                for (int i = 0; i < tierSlots.size(); i++) {
                    if (slot == tierSlots.get(i)) {
                        if (i < tiers.size()) {
                            // 编辑现有等级
                            Crate.PityTier tier = tiers.get(i);
                            if (event.isShiftClick() && event.isRightClick()) {
                                // 删除
                                plugin.getCrateManager().removePityTier(crate.getId(), i);
                                plugin.getLanguageManager().send(player, "admin-pity-tier-removed");
                                plugin.getGuiManager().openPityEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
                            } else if (event.isRightClick()) {
                                // 切换稀有度
                                String newRarity = plugin.getConfigManager().getNextRarity(tier.getRarity());
                                plugin.getCrateManager().updatePityTier(crate.getId(), i, tier.getCount(), newRarity);
                                plugin.getGuiManager().openPityEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
                            } else if (event.isLeftClick()) {
                                // 编辑次数
                                final int tierIndex = i;
                                plugin.getLanguageManager().send(player, "admin-input-pity-count");
                                plugin.getGuiManager().startInputSession(player, "pity_count",
                                        new Object[]{crate.getId(), tierIndex, tier.getRarity()}, (p, input, data) -> {
                                    Object[] params = (Object[]) data;
                                    String crateId = (String) params[0];
                                    int idx = (int) params[1];
                                    String rarity = (String) params[2];
                                    try {
                                        int newCount = Integer.parseInt(input);
                                        if (newCount > 0) {
                                            plugin.getCrateManager().updatePityTier(crateId, idx, newCount, rarity);
                                            plugin.getGuiManager().openPityEditGui(p, plugin.getCrateManager().getCrate(crateId));
                                        } else {
                                            plugin.getLanguageManager().send(p, "invalid-number");
                                        }
                                    } catch (NumberFormatException e) {
                                        plugin.getLanguageManager().send(p, "invalid-number");
                                    }
                                });
                            }
                        } else if (i == tiers.size()) {
                            // 添加新等级
                            int defaultCount = tiers.isEmpty() ? 25 : tiers.get(tiers.size() - 1).getCount() + 25;
                            plugin.getCrateManager().addPityTier(crate.getId(), defaultCount,
                                    plugin.getConfigManager().getDefaultPityRarityId());
                            plugin.getLanguageManager().send(player, "admin-pity-tier-added");
                            plugin.getGuiManager().openPityEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
                        }
                        break;
                    }
                }
            }
        }
    }

    private void handleBasicEditClick(InventoryClickEvent event, Player player, Crate crate, int slot) {
        String action = actionOrFallback("admin_basic_edit", slot, Map.of(
                18, "back",
                11, "edit_name",
                13, "edit_block",
                15, "adjust_animation_duration"
        ));
        if ("back".equals(action)) {
            plugin.getGuiManager().openCrateEditGui(player, crate);
            return;
        }
        if ("edit_name".equals(action)) {
            plugin.getLanguageManager().send(player, "admin-input-name");
            plugin.getGuiManager().startInputSession(player, "crate_name", crate.getId(), (p, input, data) -> {
                String crateId = (String) data;
                plugin.getCrateManager().updateCrateName(crateId, input);
                plugin.getLanguageManager().send(p, "admin-crate-name-updated",
                        LanguageManager.placeholders("name", input));
                Crate updatedCrate = plugin.getCrateManager().getCrate(crateId);
                if (updatedCrate != null) {
                    plugin.getGuiManager().openBasicEditGui(p, updatedCrate);
                }
            });
            return;
        }
        if ("edit_block".equals(action)) {
            org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir() || !item.getType().isBlock()) {
                plugin.getLanguageManager().send(player, "admin-hold-block");
                return;
            }
            plugin.getCrateManager().updateCrateBlock(crate.getId(), item.getType());
            plugin.getLanguageManager().send(player, "admin-block-updated");
            plugin.getGuiManager().openBasicEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            return;
        }
        if ("adjust_animation_duration".equals(action)) {
            int delta = 0;
            if (event.isLeftClick()) {
                delta = event.isShiftClick() ? 5 : 1;
            } else if (event.isRightClick()) {
                delta = event.isShiftClick() ? -5 : -1;
            }
            int newDuration = Math.max(1, crate.getAnimationDuration() + delta);
            plugin.getCrateManager().updateAnimationDuration(crate.getId(), newDuration);
            plugin.getGuiManager().openBasicEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            return;
        }

        switch (slot) {
            case 18 -> plugin.getGuiManager().openCrateEditGui(player, crate); // 返回
            case 11 -> { // 编辑名称
                plugin.getLanguageManager().send(player, "admin-input-name");
                plugin.getGuiManager().startInputSession(player, "crate_name", crate.getId(), (p, input, data) -> {
                    String crateId = (String) data;
                    plugin.getCrateManager().updateCrateName(crateId, input);
                    plugin.getLanguageManager().send(p, "admin-crate-name-updated",
                            LanguageManager.placeholders("name", input));
                    Crate updatedCrate = plugin.getCrateManager().getCrate(crateId);
                    if (updatedCrate != null) {
                        plugin.getGuiManager().openBasicEditGui(p, updatedCrate);
                    }
                });
            }
            case 13 -> { // 编辑方块
                org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir() || !item.getType().isBlock()) {
                    plugin.getLanguageManager().send(player, "admin-hold-block");
                    return;
                }
                plugin.getCrateManager().updateCrateBlock(crate.getId(), item.getType());
                plugin.getLanguageManager().send(player, "admin-block-updated");
                plugin.getGuiManager().openBasicEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case 15 -> { // 调整动画时长
                int delta = 0;
                if (event.isLeftClick()) {
                    delta = event.isShiftClick() ? 5 : 1;
                } else if (event.isRightClick()) {
                    delta = event.isShiftClick() ? -5 : -1;
                }
                int newDuration = Math.max(1, crate.getAnimationDuration() + delta);
                plugin.getCrateManager().updateAnimationDuration(crate.getId(), newDuration);
                plugin.getGuiManager().openBasicEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
        }
    }

    private void handleMultiOpenEditClick(InventoryClickEvent event, Player player, Crate crate, int slot) {
        String action = actionOrFallback("admin_multi_open_edit", slot, Map.of(
                18, "back",
                11, "toggle_multi_open",
                15, "adjust_multi_open_max"
        ));
        if ("back".equals(action)) {
            plugin.getGuiManager().openCrateEditGui(player, crate);
            return;
        }
        if ("toggle_multi_open".equals(action)) {
            plugin.getCrateManager().toggleMultiOpen(crate.getId());
            plugin.getGuiManager().openMultiOpenEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            return;
        }
        if ("adjust_multi_open_max".equals(action)) {
            int delta = 0;
            if (event.isLeftClick()) {
                delta = event.isShiftClick() ? 5 : 1;
            } else if (event.isRightClick()) {
                delta = event.isShiftClick() ? -5 : -1;
            }
            int newMax = Math.max(1, crate.getMultiOpenMax() + delta);
            plugin.getCrateManager().updateMultiOpenMax(crate.getId(), newMax);
            plugin.getGuiManager().openMultiOpenEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            return;
        }

        switch (slot) {
            case 18 -> plugin.getGuiManager().openCrateEditGui(player, crate); // 返回
            case 11 -> { // 切换启用/禁用
                plugin.getCrateManager().toggleMultiOpen(crate.getId());
                plugin.getGuiManager().openMultiOpenEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case 15 -> { // 调整最大数量
                int delta = 0;
                if (event.isLeftClick()) {
                    delta = event.isShiftClick() ? 5 : 1;
                } else if (event.isRightClick()) {
                    delta = event.isShiftClick() ? -5 : -1;
                }
                int newMax = Math.max(1, crate.getMultiOpenMax() + delta);
                plugin.getCrateManager().updateMultiOpenMax(crate.getId(), newMax);
                plugin.getGuiManager().openMultiOpenEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
        }
    }

    private void handleParticleEditClick(InventoryClickEvent event, Player player, Crate crate, CrateGuiHolder holder, int slot) {
        String stageName = holder.getData("particle_stage");
        if (stageName == null) {
            handleParticleMainClick(player, crate, slot);
            return;
        }

        ParticleStage stage = ParticleStage.fromPath(stageName);
        String materialKey = holder.getData("particle_material_key");
        if (materialKey != null) {
            handleParticleMaterialSelectClick(event, player, crate, stage, holder, slot, materialKey);
            return;
        }

        CrateParticleEffect effect = crate.getParticleEffect(stage);
        String action = actionOrFallback("admin_particle_stage_edit", slot, Map.ofEntries(
                Map.entry(45, "back"),
                Map.entry(4, "toggle_stage"),
                Map.entry(10, "cycle_particle"),
                Map.entry(12, "cycle_mode"),
                Map.entry(14, "cycle_target"),
                Map.entry(16, "preview"),
                Map.entry(19, "adjust_count"),
                Map.entry(20, "adjust_radius"),
                Map.entry(21, "adjust_height"),
                Map.entry(22, "adjust_speed"),
                Map.entry(23, "adjust_size"),
                Map.entry(28, "adjust_interval"),
                Map.entry(29, "adjust_duration"),
                Map.entry(30, "edit_color"),
                Map.entry(31, "edit_to_color"),
                Map.entry(32, "select_block_material"),
                Map.entry(33, "select_item_material")
        ));
        if (action == null) return;

        switch (action) {
            case "back" -> plugin.getGuiManager().openParticleEditGui(player, crate);
            case "toggle_stage" -> {
                plugin.getCrateManager().toggleParticleStage(crate.getId(), stage);
                sendParticleUpdated(player, stage.displayName() + "阶段开关");
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "cycle_particle" -> {
                String particle = ParticleCompat.nextSelectableParticle(effect.getParticle(), event.isRightClick());
                plugin.getCrateManager().updateParticleType(crate.getId(), stage, particle);
                sendParticleUpdated(player, "粒子类型");
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "cycle_mode" -> {
                ParticleEffectMode mode = event.isRightClick() ? effect.getMode().previous() : effect.getMode().next();
                plugin.getCrateManager().updateParticleMode(crate.getId(), stage, mode);
                sendParticleUpdated(player, "特效模式");
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "cycle_target" -> {
                ParticleTarget target = event.isRightClick() ? effect.getTarget().previous(stage) : effect.getTarget().next(stage);
                plugin.getCrateManager().updateParticleTarget(crate.getId(), stage, target);
                sendParticleUpdated(player, "播放目标");
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "preview" -> {
                plugin.getParticleManager().previewStage(player, crate, stage);
                plugin.getLanguageManager().send(player, "admin-particles-preview");
            }
            case "adjust_count" -> {
                int delta = signedDelta(event, event.isShiftClick() ? 5 : 1);
                plugin.getCrateManager().updateParticleInt(crate.getId(), stage, "count", effect.getCount() + delta, 1, 500);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "adjust_radius" -> {
                double delta = signedDelta(event, event.isShiftClick() ? 0.5 : 0.1);
                plugin.getCrateManager().updateParticleDouble(crate.getId(), stage, "radius", effect.getRadius() + delta, 0.0, 8.0);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "adjust_height" -> {
                double delta = signedDelta(event, event.isShiftClick() ? 0.5 : 0.1);
                plugin.getCrateManager().updateParticleDouble(crate.getId(), stage, "height", effect.getHeight() + delta, 0.0, 8.0);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "adjust_speed" -> {
                double delta = signedDelta(event, event.isShiftClick() ? 0.05 : 0.01);
                plugin.getCrateManager().updateParticleDouble(crate.getId(), stage, "speed", effect.getSpeed() + delta, 0.0, 2.0);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "adjust_size" -> {
                double delta = signedDelta(event, event.isShiftClick() ? 0.5 : 0.1);
                plugin.getCrateManager().updateParticleDouble(crate.getId(), stage, "size", effect.getSize() + delta, 0.1, 5.0);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "adjust_interval" -> {
                int delta = signedDelta(event, event.isShiftClick() ? 5 : 1);
                plugin.getCrateManager().updateParticleInt(crate.getId(), stage, "interval", effect.getInterval() + delta, 1, 200);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "adjust_duration" -> {
                int delta = signedDelta(event, event.isShiftClick() ? 20 : 5);
                plugin.getCrateManager().updateParticleInt(crate.getId(), stage, "duration", effect.getDuration() + delta, 1, 400);
                reopenParticleStage(player, crate.getId(), stage);
            }
            case "edit_color" -> startParticleColorInput(player, crate, stage, "color");
            case "edit_to_color" -> startParticleColorInput(player, crate, stage, "to-color");
            case "select_block_material" -> plugin.getGuiManager().openParticleMaterialSelectGui(player, crate, stage, "block", 0);
            case "select_item_material" -> plugin.getGuiManager().openParticleMaterialSelectGui(player, crate, stage, "item", 0);
        }
    }

    private void handleParticleMaterialSelectClick(InventoryClickEvent event, Player player, Crate crate,
                                                   ParticleStage stage, CrateGuiHolder holder, int slot,
                                                   String materialKey) {
        int page = holder.getData("particle_material_page", 0);
        String action = actionOrFallback("admin_particle_material_select", slot, Map.of(
                45, "back",
                48, "previous_page",
                50, "next_page"
        ));
        if ("back".equals(action)) {
            plugin.getGuiManager().openParticleStageEditGui(player, crate, stage);
            return;
        }
        if ("previous_page".equals(action)) {
            plugin.getGuiManager().openParticleMaterialSelectGui(
                    player, crate, stage, materialKey, Math.max(0, page - 1));
            return;
        }
        if ("next_page".equals(action)) {
            plugin.getGuiManager().openParticleMaterialSelectGui(
                    player, crate, stage, materialKey,
                    Math.min(plugin.getGuiManager().getParticleMaterialMaxPage(materialKey), page + 1));
            return;
        }

        switch (slot) {
            case 45 -> plugin.getGuiManager().openParticleStageEditGui(player, crate, stage);
            case 48 -> plugin.getGuiManager().openParticleMaterialSelectGui(
                    player, crate, stage, materialKey, Math.max(0, page - 1));
            case 50 -> plugin.getGuiManager().openParticleMaterialSelectGui(
                    player, crate, stage, materialKey,
                    Math.min(plugin.getGuiManager().getParticleMaterialMaxPage(materialKey), page + 1));
            default -> {
                Material material = plugin.getGuiManager().getParticleMaterialSelection(materialKey, page, slot);
                if (material == null) {
                    return;
                }
                plugin.getCrateManager().updateParticleMaterial(crate.getId(), stage, materialKey, material);
                sendParticleUpdated(player, "block".equalsIgnoreCase(materialKey) ? "方块粒子材质" : "物品粒子材质");
                plugin.getGuiManager().openParticleMaterialSelectGui(player,
                        plugin.getCrateManager().getCrate(crate.getId()), stage, materialKey, page);
            }
        }
    }

    private void handleParticleMainClick(Player player, Crate crate, int slot) {
        String action = actionOrFallback("admin_particle_edit", slot, Map.of(
                4, "toggle_particles",
                20, "edit_idle_stage",
                22, "edit_open_stage",
                24, "edit_reward_stage",
                45, "back",
                49, "preview_all"
        ));
        if (action == null) return;

        switch (action) {
            case "toggle_particles" -> {
                plugin.getCrateManager().toggleParticles(crate.getId());
                sendParticleUpdated(player, "粒子总开关");
                plugin.getGuiManager().openParticleEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
            case "edit_idle_stage" -> plugin.getGuiManager().openParticleStageEditGui(player, crate, ParticleStage.IDLE);
            case "edit_open_stage" -> plugin.getGuiManager().openParticleStageEditGui(player, crate, ParticleStage.OPEN);
            case "edit_reward_stage" -> plugin.getGuiManager().openParticleStageEditGui(player, crate, ParticleStage.REWARD);
            case "back" -> plugin.getGuiManager().openCrateEditGui(player, crate);
            case "preview_all" -> {
                plugin.getParticleManager().previewAll(player, crate);
                plugin.getLanguageManager().send(player, "admin-particles-preview");
            }
        }
    }

    private int signedDelta(InventoryClickEvent event, int amount) {
        return event.isRightClick() ? -amount : amount;
    }

    private double signedDelta(InventoryClickEvent event, double amount) {
        return event.isRightClick() ? -amount : amount;
    }

    private void startParticleTypeInput(Player player, Crate crate, ParticleStage stage) {
        plugin.getLanguageManager().send(player, "admin-input-particle-type");
        plugin.getGuiManager().startInputSession(player, "particle_type", new String[]{crate.getId(), stage.name()}, (p, input, data) -> {
            String[] params = (String[]) data;
            String crateId = params[0];
            ParticleStage selectedStage = ParticleStage.fromPath(params[1]);
            if (!ParticleCompat.isValidParticle(input)) {
                plugin.getLanguageManager().send(p, "invalid-particle");
                reopenParticleStage(p, crateId, selectedStage);
                return;
            }
            plugin.getCrateManager().updateParticleType(crateId, selectedStage, input);
            sendParticleUpdated(p, "粒子类型");
            reopenParticleStage(p, crateId, selectedStage);
        });
    }

    private void startParticleColorInput(Player player, Crate crate, ParticleStage stage, String key) {
        plugin.getLanguageManager().send(player, "admin-input-particle-color");
        plugin.getGuiManager().startInputSession(player, "particle_color", new String[]{crate.getId(), stage.name(), key}, (p, input, data) -> {
            String[] params = (String[]) data;
            String crateId = params[0];
            ParticleStage selectedStage = ParticleStage.fromPath(params[1]);
            String colorKey = params[2];
            String normalized = input.trim();
            if (!normalized.startsWith("#")) {
                normalized = "#" + normalized;
            }
            if (!normalized.matches("#[0-9a-fA-F]{6}")) {
                plugin.getLanguageManager().send(p, "invalid-color");
                reopenParticleStage(p, crateId, selectedStage);
                return;
            }
            plugin.getCrateManager().updateParticleColor(crateId, selectedStage, colorKey, normalized);
            sendParticleUpdated(p, "粒子颜色");
            reopenParticleStage(p, crateId, selectedStage);
        });
    }

    private void reopenParticleStage(Player player, String crateId, ParticleStage stage) {
        Crate updated = plugin.getCrateManager().getCrate(crateId);
        if (updated == null) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }
        plugin.getGuiManager().openParticleStageEditGui(player, updated, stage);
    }

    private void sendParticleUpdated(Player player, String field) {
        plugin.getLanguageManager().send(player, "admin-particles-updated",
                LanguageManager.placeholders("field", field));
    }

    private boolean hasConfiguredAction(GuiConfig config, String action) {
        return config.getItems().values().stream()
                .anyMatch(item -> action.equalsIgnoreCase(item.getAction()));
    }

    private String actionOrFallback(String guiId, int slot, Map<Integer, String> fallbackActions) {
        String action = plugin.getGuiManager().getConfiguredAction(guiId, slot);
        if (action != null && !action.isBlank()) {
            return action.toLowerCase();
        }
        return fallbackActions.get(slot);
    }

    private List<Integer> contentSlots(String guiId, List<Integer> fallback) {
        return plugin.getGuiManager().getConfiguredContentSlots(guiId, fallback);
    }

    private void handleCrateEditAction(Player player, String action, Crate crate, CrateGuiHolder holder, boolean isShiftClick) {
        switch (action.toLowerCase()) {
            case "edit_basic" -> {
                plugin.getGuiManager().openBasicEditGui(player, crate);
            }
            case "edit_animation" -> {
                plugin.getGuiManager().openAnimationSelectGui(player, crate);
            }
            case "edit_sounds" -> {
                plugin.getLanguageManager().send(player, "admin-feature-coming-soon");
            }
            case "edit_particles" -> {
                plugin.getGuiManager().openParticleEditGui(player, crate);
            }
            case "edit_pity" -> {
                plugin.getGuiManager().openPityEditGui(player, crate);
            }
            case "edit_preview" -> {
                plugin.getCrateManager().togglePreview(crate.getId());
                Crate updatedCrate = plugin.getCrateManager().getCrate(crate.getId());
                plugin.getGuiManager().openCrateEditGui(player, updatedCrate);
            }
            case "edit_multi_open" -> {
                plugin.getGuiManager().openMultiOpenEditGui(player, crate);
            }
            case "manage_rewards" -> {
                plugin.getGuiManager().openRewardManagerGui(player, crate, 0);
            }
            case "add_reward" -> {
                // 创建空奖励，然后进入编辑界面
                String newRewardId = plugin.getCrateManager().createEmptyReward(crate.getId());
                if (newRewardId != null) {
                    plugin.getLanguageManager().send(player, "admin-reward-added");
                    Crate updatedCrate = plugin.getCrateManager().getCrate(crate.getId());
                    Reward newReward = updatedCrate.getRewards().stream()
                            .filter(r -> r.getId().equals(newRewardId))
                            .findFirst()
                            .orElse(null);
                    if (newReward != null) {
                        plugin.getGuiManager().openRewardEditGui(player, updatedCrate, newReward);
                    } else {
                        plugin.getGuiManager().openCrateEditGui(player, updatedCrate);
                    }
                }
            }
            case "save_crate" -> {
                plugin.getCrateManager().saveCrate(crate.getId());
                plugin.getLanguageManager().send(player, "admin-crate-saved");
            }
            case "delete_crate" -> {
                if (isShiftClick) {
                    plugin.getCrateManager().deleteCrate(crate.getId());
                    plugin.getLanguageManager().send(player, "admin-crate-deleted",
                            LanguageManager.placeholders("crate", crate.getName()));
                    plugin.getGuiManager().openAdminGui(player);
                } else {
                    plugin.getLanguageManager().send(player, "admin-shift-to-delete");
                }
            }
            case "open_gui" -> {
                plugin.getGuiManager().openAdminGui(player);
            }
            case "back" -> plugin.getGuiManager().openAdminGui(player);
            case "close" -> player.closeInventory();
        }
    }

    private void handleRewardEditClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();
        Crate crate = holder.getCrate();

        // 检查是否是奖励管理界面
        Boolean isRewardManager = holder.getData("reward_manager");
        if (isRewardManager != null && isRewardManager) {
            handleRewardManagerClick(event, player, holder);
            return;
        }

        // 检查是否是替代奖励选择界面
        Boolean isAlternativeSelect = holder.getData("alternative_select");
        if (isAlternativeSelect != null && isAlternativeSelect) {
            handleAlternativeRewardSelectClick(event, player, holder);
            return;
        }

        String rewardId = holder.getData("reward_id");

        if (crate == null || rewardId == null) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }

        Reward reward = crate.getRewards().stream()
                .filter(r -> r.getId().equals(rewardId))
                .findFirst()
                .orElse(null);

        if (reward == null) {
            plugin.getGuiManager().openCrateEditGui(player, crate);
            return;
        }

        String action = actionOrFallback("admin_reward_edit", slot, Map.ofEntries(
                Map.entry(45, "back"),
                Map.entry(4, "edit_display_icon"),
                Map.entry(11, "edit_reward_items"),
                Map.entry(15, "edit_commands"),
                Map.entry(28, "adjust_chance"),
                Map.entry(30, "cycle_rarity"),
                Map.entry(32, "toggle_broadcast"),
                Map.entry(34, "edit_display_name"),
                Map.entry(37, "toggle_permission_check"),
                Map.entry(39, "edit_permission_node"),
                Map.entry(41, "cycle_permission_action"),
                Map.entry(43, "select_alternative_reward"),
                Map.entry(47, "copy_reward"),
                Map.entry(49, "delete_reward"),
                Map.entry(51, "save")
        ));
        if (action != null) {
            slot = switch (action) {
                case "back" -> 45;
                case "edit_display_icon" -> 4;
                case "edit_reward_items" -> 11;
                case "edit_commands" -> 15;
                case "adjust_chance" -> 28;
                case "cycle_rarity" -> 30;
                case "toggle_broadcast" -> 32;
                case "edit_display_name" -> 34;
                case "toggle_permission_check" -> 37;
                case "edit_permission_node" -> 39;
                case "cycle_permission_action" -> 41;
                case "select_alternative_reward" -> 43;
                case "copy_reward" -> 47;
                case "delete_reward" -> 49;
                case "save" -> 51;
                default -> slot;
            };
        }

        switch (slot) {
            case 45 -> plugin.getGuiManager().openCrateEditGui(player, crate); // 返回
            case 4 -> { // 显示图标 - 点击打开物品输入界面
                if (event.isRightClick()) {
                    plugin.getCrateManager().resetRewardDisplayIconAuto(crate.getId(), rewardId);
                    plugin.getLanguageManager().send(player, "admin-reward-auto-display-icon-reset");
                    refreshRewardEditGui(player, crate.getId(), rewardId);
                } else {
                    plugin.getGuiManager().openItemInputGui(player, crate, rewardId, "display_icon");
                }
            }
            case 11 -> { // 奖励物品 - 打开奖励物品管理界面
                plugin.getGuiManager().openRewardItemsGui(player, crate, rewardId);
            }
            case 15 -> { // 命令奖励
                if (event.isRightClick()) {
                    plugin.getCrateManager().clearRewardCommands(crate.getId(), rewardId);
                    plugin.getLanguageManager().send(player, "admin-reward-command-removed");
                    refreshRewardEditGui(player, crate.getId(), rewardId);
                } else {
                    plugin.getLanguageManager().send(player, "admin-input-command");
                    plugin.getGuiManager().startInputSession(player, "reward_command",
                            new String[]{crate.getId(), rewardId}, (p, input, data) -> {
                        String[] ids = (String[]) data;
                        plugin.getCrateManager().addRewardCommand(ids[0], ids[1], input);
                        plugin.getLanguageManager().send(p, "admin-reward-command-added");
                        refreshRewardEditGui(p, ids[0], ids[1]);
                    });
                }
            }
            case 28 -> { // 调整概率
                if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    // 中键输入精确数值
                    plugin.getLanguageManager().send(player, "admin-input-chance");
                    plugin.getGuiManager().startInputSession(player, "reward_chance",
                            new String[]{crate.getId(), rewardId}, (p, input, data) -> {
                        String[] ids = (String[]) data;
                        try {
                            double newChance = Double.parseDouble(input);
                            newChance = Math.max(0.01, Math.min(100, newChance));
                            plugin.getCrateManager().updateRewardChance(ids[0], ids[1], newChance);
                            refreshRewardEditGui(p, ids[0], ids[1]);
                        } catch (NumberFormatException e) {
                            plugin.getLanguageManager().send(p, "invalid-number");
                            refreshRewardEditGui(p, ids[0], ids[1]);
                        }
                    });
                } else {
                    double delta = 0;
                    if (event.isLeftClick()) {
                        delta = event.isShiftClick() ? 5.0 : 1.0;
                    } else if (event.isRightClick()) {
                        delta = event.isShiftClick() ? -5.0 : -1.0;
                    }
                    double newChance = Math.max(0.01, Math.min(100, reward.getChance() + delta));
                    plugin.getCrateManager().updateRewardChance(crate.getId(), rewardId, newChance);
                    refreshRewardEditGui(player, crate.getId(), rewardId);
                }
            }
            case 30 -> { // 切换稀有度
                String newRarity = plugin.getConfigManager().getNextRarity(reward.getRarity());
                plugin.getCrateManager().updateRewardRarity(crate.getId(), rewardId, newRarity);
                refreshRewardEditGui(player, crate.getId(), rewardId);
            }
            case 32 -> { // 切换广播
                plugin.getCrateManager().toggleRewardBroadcast(crate.getId(), rewardId);
                String status = reward.shouldBroadcast() ? "关闭" : "开启";
                plugin.getLanguageManager().send(player, "admin-reward-broadcast-toggled",
                        LanguageManager.placeholders("status", status));
                refreshRewardEditGui(player, crate.getId(), rewardId);
            }
            case 34 -> { // 显示名称
                if (event.isRightClick()) {
                    plugin.getCrateManager().resetRewardDisplayNameAuto(crate.getId(), rewardId);
                    plugin.getLanguageManager().send(player, "admin-reward-auto-display-name-reset");
                    refreshRewardEditGui(player, crate.getId(), rewardId);
                } else {
                    plugin.getLanguageManager().send(player, "admin-input-name");
                    plugin.getGuiManager().startInputSession(player, "reward_name",
                            new String[]{crate.getId(), rewardId}, (p, input, data) -> {
                        String[] ids = (String[]) data;
                        plugin.getCrateManager().updateRewardDisplayName(ids[0], ids[1], input);
                        refreshRewardEditGui(p, ids[0], ids[1]);
                    });
                }
            }
            // ===== 权限检测配置 =====
            case 37 -> { // 权限检测开关
                plugin.getCrateManager().toggleRewardPermissionCheck(crate.getId(), rewardId);
                String status = reward.isPermissionCheckEnabled() ? "关闭" : "开启";
                plugin.getLanguageManager().send(player, "admin-permission-check-toggled",
                        LanguageManager.placeholders("status", status));
                refreshRewardEditGui(player, crate.getId(), rewardId);
            }
            case 39 -> { // 权限节点设置
                plugin.getLanguageManager().send(player, "admin-input-permission");
                plugin.getGuiManager().startInputSession(player, "reward_permission",
                        new String[]{crate.getId(), rewardId}, (p, input, data) -> {
                    String[] ids = (String[]) data;
                    plugin.getCrateManager().updateRewardCheckPermission(ids[0], ids[1], input);
                    plugin.getLanguageManager().send(p, "admin-permission-updated");
                    refreshRewardEditGui(p, ids[0], ids[1]);
                });
            }
            case 41 -> { // 行为选择
                gg.fotia.crates.reward.PermissionAction currentAction = reward.getPermissionAction();
                gg.fotia.crates.reward.PermissionAction newAction = currentAction == gg.fotia.crates.reward.PermissionAction.SKIP
                        ? gg.fotia.crates.reward.PermissionAction.ALTERNATIVE
                        : gg.fotia.crates.reward.PermissionAction.SKIP;
                plugin.getCrateManager().updateRewardPermissionAction(crate.getId(), rewardId, newAction);
                String actionName = newAction == gg.fotia.crates.reward.PermissionAction.SKIP ? "跳过" : "替代";
                plugin.getLanguageManager().send(player, "admin-permission-action-updated",
                        LanguageManager.placeholders("action", actionName));
                refreshRewardEditGui(player, crate.getId(), rewardId);
            }
            case 43 -> { // 替代奖励选择
                plugin.getGuiManager().openAlternativeRewardSelectGui(player, crate, rewardId);
            }
            case 47 -> { // 复制奖励
                String newRewardId = plugin.getCrateManager().copyReward(crate.getId(), rewardId);
                if (newRewardId != null) {
                    plugin.getLanguageManager().send(player, "admin-reward-copied");
                    Crate updatedCrate = plugin.getCrateManager().getCrate(crate.getId());
                    Reward newReward = updatedCrate.getRewards().stream()
                            .filter(r -> r.getId().equals(newRewardId))
                            .findFirst()
                            .orElse(null);
                    if (newReward != null) {
                        plugin.getGuiManager().openRewardEditGui(player, updatedCrate, newReward);
                    }
                }
            }
            case 49 -> { // 删除奖励
                if (event.isShiftClick()) {
                    plugin.getCrateManager().removeReward(crate.getId(), rewardId);
                    plugin.getLanguageManager().send(player, "admin-reward-removed");
                    plugin.getGuiManager().openCrateEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
                } else {
                    plugin.getLanguageManager().send(player, "admin-shift-to-delete");
                }
            }
            case 51 -> { // 保存并返回
                plugin.getCrateManager().saveCrate(crate.getId());
                plugin.getLanguageManager().send(player, "admin-crate-saved");
                plugin.getGuiManager().openCrateEditGui(player, plugin.getCrateManager().getCrate(crate.getId()));
            }
        }
    }

    /**
     * 处理替代奖励选择界面点击
     */
    private void handleAlternativeRewardSelectClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();
        Crate crate = holder.getCrate();
        String sourceRewardId = holder.getData("source_reward_id");

        if (crate == null || sourceRewardId == null) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }

        // 奖励槽位
        List<Integer> rewardSlots = contentSlots("admin_alternative_reward_select", List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        ));

        int slotIndex = rewardSlots.indexOf(slot);
        if (slotIndex >= 0) {
            // 获取排除自己后的奖励列表
            List<Reward> rewards = crate.getRewards().stream()
                    .filter(r -> !r.getId().equals(sourceRewardId))
                    .toList();

            if (slotIndex < rewards.size()) {
                Reward selectedReward = rewards.get(slotIndex);
                plugin.getCrateManager().updateRewardAlternativeReward(crate.getId(), sourceRewardId, selectedReward.getId());
                plugin.getLanguageManager().send(player, "admin-alternative-reward-set",
                        LanguageManager.placeholders("reward", selectedReward.getDisplayName()));
                refreshRewardEditGui(player, crate.getId(), sourceRewardId);
            }
            return;
        }

        String action = actionOrFallback("admin_alternative_reward_select", slot, Map.of(
                45, "back",
                49, "clear_alternative_reward"
        ));
        if (action != null) {
            slot = switch (action) {
                case "back" -> 45;
                case "clear_alternative_reward" -> 49;
                default -> slot;
            };
        }

        switch (slot) {
            case 45 -> { // 返回
                refreshRewardEditGui(player, crate.getId(), sourceRewardId);
            }
            case 49 -> { // 清除替代奖励
                plugin.getCrateManager().updateRewardAlternativeReward(crate.getId(), sourceRewardId, null);
                plugin.getLanguageManager().send(player, "admin-alternative-reward-cleared");
                refreshRewardEditGui(player, crate.getId(), sourceRewardId);
            }
        }
    }

    private void handleRewardManagerClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();
        Crate crate = holder.getCrate();
        int page = holder.getCurrentPage();

        if (crate == null) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }

        // 奖励槽位
        List<Integer> rewardSlots = contentSlots("admin_reward_manager", List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        ));

        int slotIndex = rewardSlots.indexOf(slot);
        if (slotIndex >= 0) {
            int rewardIndex = page * rewardSlots.size() + slotIndex;
            List<Reward> rewards = crate.getRewards();

            if (rewardIndex < rewards.size()) {
                // 点击已有奖励
                Reward reward = rewards.get(rewardIndex);
                if (event.isLeftClick() && !event.isShiftClick()) {
                    // 左键编辑
                    plugin.getGuiManager().openRewardEditGui(player, crate, reward);
                } else if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    // 中键复制
                    String newRewardId = plugin.getCrateManager().copyReward(crate.getId(), reward.getId());
                    if (newRewardId != null) {
                        plugin.getLanguageManager().send(player, "admin-reward-copied");
                        plugin.getGuiManager().openRewardManagerGui(player, plugin.getCrateManager().getCrate(crate.getId()), page);
                    }
                } else if (event.isShiftClick() && event.isRightClick()) {
                    // Shift+右键删除
                    plugin.getCrateManager().removeReward(crate.getId(), reward.getId());
                    plugin.getLanguageManager().send(player, "admin-reward-removed");
                    plugin.getGuiManager().openRewardManagerGui(player, plugin.getCrateManager().getCrate(crate.getId()), page);
                }
            }
            return;
        }

        String action = actionOrFallback("admin_reward_manager", slot, Map.of(
                45, "back",
                48, "previous_page",
                50, "next_page",
                52, "balance_chances",
                53, "add_reward"
        ));
        if (action != null) {
            slot = switch (action) {
                case "back" -> 45;
                case "previous_page" -> 48;
                case "next_page" -> 50;
                case "balance_chances" -> 52;
                case "add_reward" -> 53;
                default -> slot;
            };
        }

        // 底部工具栏
        switch (slot) {
            case 45 -> plugin.getGuiManager().openCrateEditGui(player, crate); // 返回
            case 48 -> { // 上一页
                if (page > 0) {
                    plugin.getGuiManager().openRewardManagerGui(player, crate, page - 1);
                }
            }
            case 50 -> { // 下一页
                int maxPage = Math.max(0, (crate.getRewards().size() - 1) / rewardSlots.size());
                if (page < maxPage) {
                    plugin.getGuiManager().openRewardManagerGui(player, crate, page + 1);
                }
            }
            case 52 -> { // 概率平衡
                plugin.getCrateManager().balanceRewardChances(crate.getId());
                plugin.getLanguageManager().send(player, "admin-reward-balanced");
                plugin.getGuiManager().openRewardManagerGui(player, plugin.getCrateManager().getCrate(crate.getId()), page);
            }
            case 53 -> { // 添加新奖励
                String newRewardId = plugin.getCrateManager().createEmptyReward(crate.getId());
                if (newRewardId != null) {
                    plugin.getLanguageManager().send(player, "admin-reward-added");
                    Crate updatedCrate = plugin.getCrateManager().getCrate(crate.getId());
                    Reward newReward = updatedCrate.getRewards().stream()
                            .filter(r -> r.getId().equals(newRewardId))
                            .findFirst()
                            .orElse(null);
                    if (newReward != null) {
                        plugin.getGuiManager().openRewardEditGui(player, updatedCrate, newReward);
                    }
                }
            }
        }
    }

    private void refreshRewardEditGui(Player player, String crateId, String rewardId) {
        Crate updatedCrate = plugin.getCrateManager().getCrate(crateId);
        if (updatedCrate == null) return;
        Reward updatedReward = updatedCrate.getRewards().stream()
                .filter(r -> r.getId().equals(rewardId))
                .findFirst()
                .orElse(null);
        if (updatedReward != null) {
            plugin.getGuiManager().openRewardEditGui(player, updatedCrate, updatedReward);
        }
    }

    /**
     * 处理物品输入界面点击 - 允许真正的物品拖放
     */
    private void handleItemInputClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int rawSlot = event.getRawSlot();
        Inventory topInventory = event.getInventory();
        Crate crate = holder.getCrate();
        String rewardId = holder.getData("reward_id");
        String inputType = holder.getData("input_type");

        if (crate == null || rewardId == null || inputType == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int topSize = topInventory.getSize(); // 27
        List<Integer> inputSlots = contentSlots("admin_item_input", List.of(13));
        int inputSlot = inputSlots.isEmpty() ? 13 : inputSlots.get(0);

        // 点击玩家背包区域 - 允许所有操作
        if (rawSlot >= topSize) {
            // 不取消事件，允许正常背包操作
            // Shift点击时特殊处理：放入slot 13
            if (event.isShiftClick() && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                event.setCancelled(true);
                org.bukkit.inventory.ItemStack slot13Item = topInventory.getItem(inputSlot);
                if (slot13Item == null || slot13Item.getType().isAir()) {
                    topInventory.setItem(inputSlot, event.getCurrentItem().clone());
                    event.setCurrentItem(null);
                }
            }
            return;
        }

        // 点击GUI的slot 13 - 允许所有物品操作
        if (rawSlot == inputSlot) {
            // 完全不取消，允许正常的放入/取出/交换
            return;
        }

        // 其他GUI槽位 - 取消默认行为但处理按钮
        event.setCancelled(true);

        // 如果光标有物品，放入slot 13
        org.bukkit.inventory.ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            org.bukkit.inventory.ItemStack slot13Item = topInventory.getItem(inputSlot);
            if (slot13Item == null || slot13Item.getType().isAir()) {
                topInventory.setItem(inputSlot, cursor.clone());
                player.setItemOnCursor(null);
            }
            return;
        }

        // 按钮处理
        String action = actionOrFallback("admin_item_input", rawSlot, Map.of(
                15, "confirm",
                16, "confirm",
                10, "cancel",
                11, "cancel",
                22, "clear_reward_item"
        ));
        if (action != null) {
            rawSlot = switch (action) {
                case "confirm" -> 15;
                case "cancel" -> 10;
                case "clear_reward_item" -> 22;
                default -> rawSlot;
            };
        }

        switch (rawSlot) {
            case 15, 16 -> { // 确认按钮
                org.bukkit.inventory.ItemStack inputItem = topInventory.getItem(inputSlot);
                if (inputItem != null && !inputItem.getType().isAir()) {
                    // 标记已保存，关闭时不返还物品
                    holder.setData("saved", true);

                    if (inputType.equals("display_icon")) {
                        plugin.getCrateManager().updateRewardDisplayIconFull(crate.getId(), rewardId, inputItem.clone());
                    } else {
                        plugin.getCrateManager().updateRewardItemFull(crate.getId(), rewardId, inputItem.clone());
                    }
                    plugin.getLanguageManager().send(player, "admin-key-item-updated");
                    topInventory.setItem(inputSlot, null);
                }
                returnToRewardEdit(player, crate.getId(), rewardId);
            }
            case 10, 11 -> { // 取消按钮
                returnToRewardEdit(player, crate.getId(), rewardId);
            }
            case 22 -> { // 清除按钮
                if (inputType.equals("reward_item")) {
                    plugin.getCrateManager().clearRewardItem(crate.getId(), rewardId);
                    plugin.getLanguageManager().send(player, "admin-reward-item-cleared");
                    topInventory.setItem(inputSlot, null);
                    returnToRewardEdit(player, crate.getId(), rewardId);
                }
            }
        }
    }

    private void returnToRewardEdit(Player player, String crateId, String rewardId) {
        Crate updatedCrate = plugin.getCrateManager().getCrate(crateId);
        if (updatedCrate == null) {
            plugin.getGuiManager().openAdminGui(player);
            return;
        }
        Reward reward = updatedCrate.getRewards().stream()
                .filter(r -> r.getId().equals(rewardId))
                .findFirst()
                .orElse(null);
        if (reward != null) {
            plugin.getGuiManager().openRewardEditGui(player, updatedCrate, reward);
        } else {
            plugin.getGuiManager().openCrateEditGui(player, updatedCrate);
        }
    }

    /**
     * 处理奖励物品管理界面点击 - 允许多物品拖放
     */
    private void handleRewardItemsClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int rawSlot = event.getRawSlot();
        Inventory topInventory = event.getInventory();
        Crate crate = holder.getCrate();
        String rewardId = holder.getData("reward_id");

        if (crate == null || rewardId == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int topSize = topInventory.getSize(); // 54

        // 物品槽位
        List<Integer> itemSlots = contentSlots("admin_reward_items", List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        ));

        // 点击玩家背包区域 - 允许所有操作
        if (rawSlot >= topSize) {
            // Shift点击时放入第一个空槽位
            if (event.isShiftClick() && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                event.setCancelled(true);
                for (int slot : itemSlots) {
                    org.bukkit.inventory.ItemStack slotItem = topInventory.getItem(slot);
                    if (slotItem == null || slotItem.getType().isAir()) {
                        topInventory.setItem(slot, event.getCurrentItem().clone());
                        event.setCurrentItem(null);
                        break;
                    }
                }
            }
            return;
        }

        // 点击物品槽位 - 允许所有物品操作
        if (itemSlots.contains(rawSlot)) {
            return; // 不取消，允许正常操作
        }

        // 其他GUI槽位 - 取消默认行为但处理按钮
        event.setCancelled(true);

        // 如果光标有物品，放入第一个空槽位
        org.bukkit.inventory.ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            for (int slot : itemSlots) {
                org.bukkit.inventory.ItemStack slotItem = topInventory.getItem(slot);
                if (slotItem == null || slotItem.getType().isAir()) {
                    topInventory.setItem(slot, cursor.clone());
                    player.setItemOnCursor(null);
                    break;
                }
            }
            return;
        }

        // 按钮处理
        String action = actionOrFallback("admin_reward_items", rawSlot, Map.of(
                45, "back",
                49, "clear_all",
                53, "save"
        ));
        if (action != null) {
            rawSlot = switch (action) {
                case "back" -> 45;
                case "clear_all" -> 49;
                case "save" -> 53;
                default -> rawSlot;
            };
        }

        switch (rawSlot) {
            case 45 -> { // 返回
                returnToRewardEdit(player, crate.getId(), rewardId);
            }
            case 49 -> { // 清空所有
                if (event.isShiftClick()) {
                    for (int slot : itemSlots) {
                        topInventory.setItem(slot, null);
                    }
                    plugin.getCrateManager().clearRewardItems(crate.getId(), rewardId);
                    plugin.getLanguageManager().send(player, "admin-reward-item-cleared");
                }
            }
            case 53 -> { // 保存并返回
                // 收集所有物品
                List<org.bukkit.inventory.ItemStack> items = new java.util.ArrayList<>();
                for (int slot : itemSlots) {
                    org.bukkit.inventory.ItemStack item = topInventory.getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        items.add(item.clone());
                    }
                }

                // 标记已保存，关闭时不返还物品
                holder.setData("saved", true);

                // 保存物品
                plugin.getCrateManager().updateRewardItems(crate.getId(), rewardId, items);
                plugin.getLanguageManager().send(player, "admin-key-item-updated");
                returnToRewardEdit(player, crate.getId(), rewardId);
            }
        }
    }

    private void handleKeysClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("admin_keys");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleAction(player, guiItem.getAction(), guiItem.getActionValue(), holder);
                return;
            }
        }

        // 检查是否点击了钥匙
        List<Integer> contentSlots = config != null ? config.getContentSlots() : List.of();
        int slotIndex = contentSlots.indexOf(slot);
        if (slotIndex >= 0) {
            List<String> keyIds = plugin.getKeyManager().getKeyIds().stream().toList();
            if (slotIndex < keyIds.size()) {
                String keyId = keyIds.get(slotIndex);
                gg.fotia.crates.key.Key key = plugin.getKeyManager().getKey(keyId);
                if (key != null) {
                    if (event.isLeftClick()) {
                        plugin.getGuiManager().openKeyEditGui(player, key);
                    } else if (event.isShiftClick() && event.isRightClick()) {
                        // 删除钥匙
                        plugin.getKeyManager().deleteKey(keyId);
                        plugin.getLanguageManager().send(player, "admin-key-deleted",
                                LanguageManager.placeholders("key", key.getName()));
                        plugin.getGuiManager().openKeysGui(player);
                    }
                }
            }
        }
    }

    private void handleKeyEditClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();
        String keyId = holder.getData("key_id");
        gg.fotia.crates.key.Key key = plugin.getKeyManager().getKey(keyId);

        if (key == null) {
            plugin.getGuiManager().openKeysGui(player);
            return;
        }

        // 检查是否是宝箱选择模式
        Boolean selectMode = holder.getData("select_mode");
        if (selectMode != null && selectMode) {
            handleCrateSelectClick(event, player, holder, key);
            return;
        }

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("admin_key_edit");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleKeyEditAction(player, guiItem.getAction(), key, holder);
                return;
            }
        }

        // 检查是否点击了宝箱（移除宝箱）
        List<Integer> contentSlots = config != null ? config.getContentSlots() : List.of();
        int slotIndex = contentSlots.indexOf(slot);
        if (slotIndex >= 0 && event.isShiftClick() && event.isRightClick()) {
            List<String> crateIds = key.getCrateIds().stream().toList();
            if (slotIndex < crateIds.size()) {
                String crateId = crateIds.get(slotIndex);
                key.removeCrate(crateId);
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-crate-removed",
                        LanguageManager.placeholders("crate", crateId));
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
        }
    }

    private void handleCrateSelectClick(InventoryClickEvent event, Player player, CrateGuiHolder holder, gg.fotia.crates.key.Key key) {
        int slot = event.getSlot();
        String action = actionOrFallback("admin_crate_select", slot, Map.of(45, "back"));

        // 返回按钮
        if ("back".equals(action)) {
            plugin.getGuiManager().openKeyEditGui(player, key);
            return;
        }

        // 检查是否点击了宝箱
        List<Integer> contentSlots = contentSlots("admin_crate_select",
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34));
        if (contentSlots.contains(slot) && event.isLeftClick()) {
            // 获取点击的宝箱
            int slotIndex = contentSlots.indexOf(slot);
            List<Crate> availableCrates = plugin.getCrateManager().getAllCrates().stream()
                    .filter(c -> !key.getCrateIds().contains(c.getId()))
                    .toList();

            if (slotIndex < availableCrates.size()) {
                Crate crate = availableCrates.get(slotIndex);
                key.addCrate(crate.getId());
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-crate-added",
                        LanguageManager.placeholders("crate", crate.getName()));
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
        }
    }

    private void handleKeyEditAction(Player player, String action, gg.fotia.crates.key.Key key, CrateGuiHolder holder) {
        switch (action.toLowerCase()) {
            case "edit_key_name" -> {
                plugin.getLanguageManager().send(player, "admin-input-name");
                plugin.getGuiManager().startInputSession(player, "key_name", key.getId(), (p, input, data) -> {
                    String keyId = (String) data;
                    gg.fotia.crates.key.Key k = plugin.getKeyManager().getKey(keyId);
                    if (k != null) {
                        k.setName(input);
                        plugin.getKeyManager().saveKey(k);
                        plugin.getLanguageManager().send(p, "admin-key-name-updated",
                                LanguageManager.placeholders("name", input));
                        plugin.getGuiManager().openKeyEditGui(p, k);
                    }
                });
            }
            case "edit_key_item" -> {
                org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    plugin.getLanguageManager().send(player, "reward-hold-item");
                    return;
                }
                key.setItem(item.clone());
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-item-updated");
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
            case "toggle_key_glow" -> {
                key.setGlow(!key.isGlow());
                plugin.getKeyManager().saveKey(key);
                plugin.getGuiManager().openKeyEditGui(player, key);
            }
            case "add_key_crate" -> {
                // 打开宝箱选择界面
                plugin.getGuiManager().openCrateSelectGui(player, key);
            }
            case "save_key" -> {
                plugin.getKeyManager().saveKey(key);
                plugin.getLanguageManager().send(player, "admin-key-saved");
            }
            case "delete_key" -> {
                plugin.getKeyManager().deleteKey(key.getId());
                plugin.getLanguageManager().send(player, "admin-key-deleted",
                        LanguageManager.placeholders("key", key.getName()));
                plugin.getGuiManager().openKeysGui(player);
            }
            case "open_gui" -> {
                plugin.getGuiManager().openKeysGui(player);
            }
            case "close" -> player.closeInventory();
        }
    }

    private void handleHistoryClick(InventoryClickEvent event, Player player, CrateGuiHolder holder) {
        int slot = event.getSlot();

        GuiConfig config = plugin.getGuiManager().getConfigManager().getGuiConfig("history");
        if (config != null) {
            GuiItem guiItem = config.getItem(slot);
            if (guiItem != null && guiItem.getAction() != null) {
                handleAction(player, guiItem.getAction(), guiItem.getActionValue(), holder);
            }
        }
    }

    private void handleAction(Player player, String action, String value, CrateGuiHolder holder) {
        switch (action.toLowerCase()) {
            case "close" -> player.closeInventory();
            case "open_gui" -> {
                switch (value) {
                    case "admin" -> plugin.getGuiManager().openAdminGui(player);
                    case "admin_keys" -> plugin.getGuiManager().openKeysGui(player);
                    case "rarity_manager" -> plugin.getGuiManager().openRarityManagerGui(player);
                }
            }
            case "open_crate" -> {
                if (holder.getCrate() != null) {
                    player.closeInventory();
                    openCrate(player, holder.getCrate());
                }
            }
            case "open_history" -> {
                if (!player.hasPermission("fotiacrates.history")) {
                    plugin.getLanguageManager().send(player, "no-permission");
                    return;
                }

                Crate crate = holder.getCrate();
                if (crate != null) {
                    plugin.getGuiManager().openHistoryGui(
                            player,
                            player.getUniqueId(),
                            player.getName(),
                            crate.getId(),
                            0
                    );
                }
            }
            case "clear_history" -> {
                if (!player.hasPermission("fotiacrates.history")) {
                    plugin.getLanguageManager().send(player, "no-permission");
                    return;
                }

                UUID targetUuid = holder.getData("target_uuid");
                String targetName = holder.getData("target_name");
                String crateId = holder.getData("crate_id");
                if (targetUuid == null || targetName == null) {
                    return;
                }

                if (!player.getUniqueId().equals(targetUuid) && !player.hasPermission("fotiacrates.history.others")) {
                    plugin.getLanguageManager().send(player, "no-permission");
                    return;
                }

                int cleared = crateId != null && !crateId.isBlank()
                        ? plugin.getHistoryManager().clearHistory(targetUuid, crateId)
                        : plugin.getHistoryManager().clearHistory(targetUuid);

                if (cleared > 0) {
                    plugin.getLanguageManager().send(player, "history-cleared",
                            LanguageManager.placeholders("count", String.valueOf(cleared)));
                } else {
                    plugin.getLanguageManager().send(player, "no-history");
                }

                plugin.getGuiManager().openHistoryGui(
                        player,
                        targetUuid,
                        targetName,
                        crateId,
                        holder.getCurrentPage()
                );
            }
            case "create_key" -> {
                plugin.getLanguageManager().send(player, "admin-input-name");
                plugin.getGuiManager().startInputSession(player, "create_key", null, (p, input, data) -> {
                    String keyId = input.toLowerCase().replace(" ", "_");
                    if (plugin.getKeyManager().getKey(keyId) != null) {
                        plugin.getLanguageManager().send(p, "admin-key-exists");
                        plugin.getGuiManager().openKeysGui(p);
                        return;
                    }
                    gg.fotia.crates.key.Key newKey = new gg.fotia.crates.key.Key(keyId, input);
                    plugin.getKeyManager().saveKey(newKey);
                    plugin.getLanguageManager().send(p, "admin-key-created",
                            LanguageManager.placeholders("key", input));
                    plugin.getGuiManager().openKeyEditGui(p, newKey);
                });
            }
            case "create_crate" -> {
                plugin.getLanguageManager().send(player, "admin-input-name");
                plugin.getGuiManager().startInputSession(player, "create_crate", null, (p, input, data) -> {
                    String crateId = input.toLowerCase().replace(" ", "_");
                    if (plugin.getCrateManager().getCrate(crateId) != null) {
                        plugin.getLanguageManager().send(p, "admin-crate-exists");
                        plugin.getGuiManager().openAdminGui(p);
                        return;
                    }
                    plugin.getCrateManager().createCrate(crateId, input);
                    plugin.getLanguageManager().send(p, "admin-crate-created",
                            LanguageManager.placeholders("crate", input));
                    Crate newCrate = plugin.getCrateManager().getCrate(crateId);
                    if (newCrate != null) {
                        plugin.getGuiManager().openCrateEditGui(p, newCrate);
                    }
                });
            }
            case "prev_page" -> {
                int page = Math.max(0, holder.getCurrentPage() - 1);
                if (holder.getGuiType() == GuiType.HISTORY) {
                    plugin.getGuiManager().openHistoryGui(player,
                            holder.getData("target_uuid"),
                            holder.getData("target_name"),
                            holder.getData("crate_id"),
                            page);
                } else if (holder.getGuiType() == GuiType.PREVIEW && holder.getCrate() != null) {
                    plugin.getGuiManager().openPreview(player, holder.getCrate(), page);
                }
            }
            case "next_page" -> {
                int page = holder.getCurrentPage() + 1;
                if (holder.getGuiType() == GuiType.HISTORY) {
                    plugin.getGuiManager().openHistoryGui(player,
                            holder.getData("target_uuid"),
                            holder.getData("target_name"),
                            holder.getData("crate_id"),
                            page);
                } else if (holder.getGuiType() == GuiType.PREVIEW && holder.getCrate() != null) {
                    int totalPages = holder.getData("total_pages") != null ? (int) holder.getData("total_pages") : 1;
                    if (page < totalPages) {
                        plugin.getGuiManager().openPreview(player, holder.getCrate(), page);
                    }
                }
            }
            case "reload" -> {
                plugin.reload();
                plugin.getLanguageManager().send(player, "reload-success");
            }
            case "back" -> {
                if (holder.getGuiType() == GuiType.ADMIN_CRATE_EDIT) {
                    plugin.getGuiManager().openAdminGui(player);
                } else if (holder.getGuiType() == GuiType.ADMIN_KEY_EDIT) {
                    plugin.getGuiManager().openKeysGui(player);
                }
            }
        }
    }

    private void openCrate(Player player, Crate crate) {
        // 防止重复开箱
        if (openingPlayers.contains(player.getUniqueId())) {
            return;
        }

        // 检查交互冷却（防止短时间内多次触发）
        long now = System.currentTimeMillis();
        Long lastInteract = interactCooldown.get(player.getUniqueId());
        if (lastInteract != null && now - lastInteract < INTERACT_COOLDOWN_MS) {
            return;
        }
        interactCooldown.put(player.getUniqueId(), now);

        openingPlayers.add(player.getUniqueId());

        if (!crateOpenService.hasOpenPermission(player, crate)) {
            openingPlayers.remove(player.getUniqueId());
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        if (!plugin.getKeyManager().hasKeyForCrate(player, crate.getId())) {
            openingPlayers.remove(player.getUniqueId());
            plugin.getLanguageManager().send(player, "no-key");
            return;
        }

        // 先检查是否有可用奖励（在消耗钥匙之前）
        boolean isPity = false;
        if (crate.isPityEnabled()) {
            isPity = plugin.getPityManager().shouldTriggerPity(
                    player.getUniqueId(), crate.getId(), crate.getPityCount());
        }

        Reward reward = isPity ? crate.rollPityRewardWithPermissionCheck(player, crate.getPityRarity())
                : crate.rollRewardWithPermissionCheck(player);
        if (reward == null) {
            // 没有可用奖励，不消耗钥匙
            openingPlayers.remove(player.getUniqueId());
            plugin.getLanguageManager().send(player, "no-available-reward");
            return;
        }

        // 有可用奖励，消耗钥匙
        if (!plugin.getKeyManager().consumeKeyForCrate(player, crate.getId(), KeyType.ALL)) {
            openingPlayers.remove(player.getUniqueId());
            plugin.getLanguageManager().send(player, "no-key");
            return;
        }

        if (crate.isPityEnabled()) {
            if (isPity || reward.getRarity().equalsIgnoreCase(crate.getPityRarity())) {
                plugin.getPityManager().resetPityCount(player.getUniqueId(), crate.getId());
            } else {
                plugin.getPityManager().incrementPityCount(player.getUniqueId(), crate.getId());
            }
        }

        Location crateLocation = plugin.getParticleManager().resolveCrateLocation(player, crate);
        plugin.getParticleManager().playStage(ParticleStage.OPEN, player, crate, crateLocation);

        if (crate.isAnimationEnabled()) {
            AnimationManager animationManager = new AnimationManager(plugin);
            animationManager.playAnimation(player, crate, reward, crateLocation, () -> {
                giveReward(player, crate, reward, crateLocation);
                openingPlayers.remove(player.getUniqueId());
            });
        } else {
            giveReward(player, crate, reward, crateLocation);
            openingPlayers.remove(player.getUniqueId());
        }
    }

    private void giveReward(Player player, Crate crate, Reward reward) {
        giveReward(player, crate, reward, plugin.getParticleManager().resolveCrateLocation(player, crate));
    }

    private void giveReward(Player player, Crate crate, Reward reward, Location crateLocation) {
        reward.give(player);

        plugin.getLanguageManager().send(player, "reward-received",
                LanguageManager.placeholders("reward", reward.getDisplayName()));

        if (reward.shouldBroadcast() && plugin.getConfigManager().isBroadcastRareRewards()) {
            var message = plugin.getLanguageManager().getMessage(player, "broadcast-rare",
                    LanguageManager.placeholders(
                            "player", player.getName(),
                            "crate", crate.getName(),
                            "reward", reward.getDisplayName()
                    ));
            plugin.getServer().broadcast(message);
        }

        plugin.getHistoryManager().addHistory(
                player.getUniqueId(),
                player.getName(),
                crate.getId(),
                reward.getId(),
                reward.getDisplayName()
        );

        plugin.getParticleManager().playStage(ParticleStage.REWARD, player, crate, crateLocation);

        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CrateGuiHolder holder) {
            // 物品输入界面允许拖拽到slot 13
            if (holder.getGuiType() == GuiType.ITEM_INPUT) {
                // 只允许拖拽到slot 13
                List<Integer> inputSlots = contentSlots("admin_item_input", List.of(13));
                int inputSlot = inputSlots.isEmpty() ? 13 : inputSlots.get(0);
                if (event.getRawSlots().size() == 1 && event.getRawSlots().contains(inputSlot)) {
                    return; // 允许
                }
            }
            // 奖励物品管理界面允许拖拽到物品槽位
            if (holder.getGuiType() == GuiType.REWARD_ITEMS) {
                List<Integer> itemSlots = contentSlots("admin_reward_items", List.of(
                        10, 11, 12, 13, 14, 15, 16,
                        19, 20, 21, 22, 23, 24, 25,
                        28, 29, 30, 31, 32, 33, 34
                ));
                // 检查所有拖拽的槽位是否都在允许范围内
                boolean allAllowed = event.getRawSlots().stream().allMatch(itemSlots::contains);
                if (allAllowed) {
                    return; // 允许
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 物品输入界面关闭时返还物品
        if (event.getInventory().getHolder() instanceof CrateGuiHolder holder) {
            if (holder.getGuiType() == GuiType.ITEM_INPUT) {
                // 如果已保存则不返还
                Boolean saved = holder.getData("saved");
                if (saved != null && saved) return;

                List<Integer> inputSlots = contentSlots("admin_item_input", List.of(13));
                int inputSlot = inputSlots.isEmpty() ? 13 : inputSlots.get(0);
                org.bukkit.inventory.ItemStack item = event.getInventory().getItem(inputSlot);
                if (item != null && !item.getType().isAir()) {
                    if (event.getPlayer() instanceof Player player) {
                        player.getInventory().addItem(item);
                    }
                }
            }
            // 奖励物品管理界面关闭时返还所有物品（除非已保存）
            if (holder.getGuiType() == GuiType.REWARD_ITEMS) {
                // 如果已保存则不返还
                Boolean saved = holder.getData("saved");
                if (saved != null && saved) return;

                List<Integer> itemSlots = contentSlots("admin_reward_items", List.of(
                        10, 11, 12, 13, 14, 15, 16,
                        19, 20, 21, 22, 23, 24, 25,
                        28, 29, 30, 31, 32, 33, 34
                ));
                if (event.getPlayer() instanceof Player player) {
                    for (int slot : itemSlots) {
                        org.bukkit.inventory.ItemStack item = event.getInventory().getItem(slot);
                        if (item != null && !item.getType().isAir()) {
                            player.getInventory().addItem(item);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGuiManager().hasInputSession(player)) {
            event.setCancelled(true);
            String message = event.getMessage();

            // 在主线程处理输入
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (message.equalsIgnoreCase("cancel")) {
                    plugin.getGuiManager().cancelInputSession(player);
                    plugin.getLanguageManager().send(player, "admin-input-cancelled");
                } else {
                    plugin.getGuiManager().handleInput(player, message);
                }
            });
        }
    }
}
