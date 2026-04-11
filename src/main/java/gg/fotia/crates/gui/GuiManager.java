package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.history.HistoryManager;
import gg.fotia.crates.key.Key;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * GUI管理器
 * 处理所有GUI的创建和显示
 */
public class GuiManager {

    private final FotiaCrates plugin;
    private final GuiConfigManager configManager;
    private final Map<UUID, InputSession> inputSessions = new HashMap<>();

    public GuiManager(FotiaCrates plugin) {
        this.plugin = plugin;
        this.configManager = new GuiConfigManager(plugin);
    }

    /**
     * 打开预览界面
     */
    public void openPreview(Player player, Crate crate) {
        openPreview(player, crate, 0);
    }

    /**
     * 打开预览界面（带分页）
     */
    public void openPreview(Player player, Crate crate, int page) {
        if (!crate.isPreviewEnabled()) {
            return;
        }

        GuiConfig config = configManager.getGuiConfig("preview");
        if (config == null) {
            plugin.getLogger().warning("Preview GUI config not found!");
            return;
        }

        List<Integer> contentSlots = config.getContentSlots();
        List<Reward> rewards = crate.getRewards();
        int itemsPerPage = contentSlots.size();
        int totalPages = (int) Math.ceil((double) rewards.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = config.getTitle()
                .replace("{crate}", MessageUtil.stripColor(crate.getName()))
                .replace("{page}", String.valueOf(page + 1))
                .replace("{total_pages}", String.valueOf(totalPages));

        CrateGuiHolder holder = new CrateGuiHolder(GuiType.PREVIEW, crate);
        holder.setCurrentPage(page);
        holder.setData("total_pages", totalPages);

        Inventory inventory = Bukkit.createInventory(
                holder,
                config.getSize(),
                MessageUtil.parse(title)
        );

        // 填充背景
        fillBackground(inventory, config);

        // 放置固定物品（带分页占位符）
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{crate}", crate.getName());
        placeholders.put("{reward_count}", String.valueOf(rewards.size()));
        placeholders.put("{page}", String.valueOf(page + 1));
        placeholders.put("{total_pages}", String.valueOf(totalPages));
        placeholders.put("{keys}", String.valueOf(plugin.getKeyManager().getTotalKeysForCrate(player, crate.getId())));
        placeFixedItemsWithPlaceholders(inventory, config, player, crate, placeholders);

        // 放置奖励图标（分页）
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, rewards.size());
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slotIndex >= contentSlots.size()) break;
            Reward reward = rewards.get(i);
            int slot = contentSlots.get(slotIndex);
            ItemStack rewardItem = createRewardPreviewItem(reward, crate.isShowChance());
            inventory.setItem(slot, rewardItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    /**
     * 打开管理界面
     */
    public void openAdminGui(Player player) {
        GuiConfig config = configManager.getGuiConfig("admin");
        if (config == null) {
            plugin.getLogger().warning("Admin GUI config not found!");
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN, null),
                config.getSize(),
                MessageUtil.parse(config.getTitle())
        );

        // 填充背景
        fillBackground(inventory, config);

        // 放置固定物品（带占位符替换）
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{crate_count}", String.valueOf(plugin.getCrateManager().getAllCrates().size()));
        placeholders.put("{key_count}", String.valueOf(plugin.getKeyManager().getKeyIds().size()));
        placeFixedItemsWithPlaceholders(inventory, config, player, null, placeholders);

        // 放置抽奖箱列表
        List<Integer> contentSlots = config.getContentSlots();
        int slotIndex = 0;
        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            if (slotIndex >= contentSlots.size()) break;

            int slot = contentSlots.get(slotIndex);
            ItemStack crateItem = createAdminCrateItem(crate);
            inventory.setItem(slot, crateItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    /**
     * 打开抽奖箱编辑界面
     */
    public void openCrateEditGui(Player player, Crate crate) {
        GuiConfig config = configManager.getGuiConfig("admin_crate_edit");
        if (config == null) {
            plugin.getLogger().warning("Admin crate edit GUI config not found!");
            return;
        }

        String title = config.getTitle()
                .replace("{crate}", MessageUtil.stripColor(crate.getName()));
        CrateGuiHolder holder = new CrateGuiHolder(GuiType.ADMIN_CRATE_EDIT, crate);

        Inventory inventory = Bukkit.createInventory(
                holder,
                config.getSize(),
                MessageUtil.parse(title)
        );

        // 填充背景
        fillBackground(inventory, config);

        // 准备占位符
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{crate}", crate.getName());
        placeholders.put("{crate_id}", crate.getId());
        placeholders.put("{reward_count}", String.valueOf(crate.getRewards().size()));
        placeholders.put("{total_chance}", String.format("%.2f", crate.getRewards().stream().mapToDouble(Reward::getChance).sum()));
        placeholders.put("{animation_type}", crate.getAnimationType().name());
        placeholders.put("{animation_duration}", String.valueOf(crate.getAnimationDuration()));
        placeholders.put("{particles_enabled}", crate.isParticlesEnabled() ? "是" : "否");
        placeholders.put("{particle_type}", crate.getParticleType().name());
        placeholders.put("{pity_enabled}", crate.isPityEnabled() ? "是" : "否");
        placeholders.put("{pity_count}", String.valueOf(crate.getPityCount()));
        placeholders.put("{pity_rarity}", crate.getPityRarity());
        placeholders.put("{preview_enabled}", crate.isPreviewEnabled() ? "是" : "否");
        placeholders.put("{show_chance}", crate.isShowChance() ? "是" : "否");
        placeholders.put("{multi_open_enabled}", crate.isMultiOpenEnabled() ? "是" : "否");
        placeholders.put("{multi_open_max}", String.valueOf(crate.getMultiOpenMax()));

        placeFixedItemsWithPlaceholders(inventory, config, player, crate, placeholders);

        // 放置奖励列表
        List<Integer> contentSlots = config.getContentSlots();
        List<Reward> rewards = crate.getRewards();
        int slotIndex = 0;
        for (Reward reward : rewards) {
            if (slotIndex >= contentSlots.size()) break;

            int slot = contentSlots.get(slotIndex);
            ItemStack rewardItem = createAdminRewardItem(reward);
            inventory.setItem(slot, rewardItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    /**
     * 打开钥匙管理界面
     */
    public void openKeysGui(Player player) {
        GuiConfig config = configManager.getGuiConfig("admin_keys");
        if (config == null) {
            plugin.getLogger().warning("Admin keys GUI config not found!");
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_KEYS, null),
                config.getSize(),
                MessageUtil.parse(config.getTitle())
        );

        // 填充背景
        fillBackground(inventory, config);

        // 放置固定物品（带占位符替换）
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{key_count}", String.valueOf(plugin.getKeyManager().getKeyIds().size()));
        placeFixedItemsWithPlaceholders(inventory, config, player, null, placeholders);

        // 放置钥匙列表
        List<Integer> contentSlots = config.getContentSlots();
        int slotIndex = 0;
        for (String keyId : plugin.getKeyManager().getKeyIds()) {
            if (slotIndex >= contentSlots.size()) break;

            Key key = plugin.getKeyManager().getKey(keyId);
            if (key == null) continue;

            int slot = contentSlots.get(slotIndex);
            ItemStack keyItem = createAdminKeyItem(key);
            inventory.setItem(slot, keyItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    /**
     * 打开钥匙编辑界面
     */
    public void openKeyEditGui(Player player, Key key) {
        GuiConfig config = configManager.getGuiConfig("admin_key_edit");
        if (config == null) {
            plugin.getLogger().warning("Admin key edit GUI config not found!");
            return;
        }

        String title = config.getTitle()
                .replace("{key}", MessageUtil.stripColor(key.getName()));
        CrateGuiHolder holder = new CrateGuiHolder(GuiType.ADMIN_KEY_EDIT, null);
        holder.setData("key_id", key.getId());

        Inventory inventory = Bukkit.createInventory(
                holder,
                config.getSize(),
                MessageUtil.parse(title)
        );

        // 填充背景
        fillBackground(inventory, config);

        // 准备占位符
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{key}", key.getName());
        placeholders.put("{key_id}", key.getId());
        placeholders.put("{crate_count}", String.valueOf(key.getCrateIds().size()));
        placeholders.put("{glow}", key.isGlow() ? "是" : "否");

        placeFixedItemsWithPlaceholders(inventory, config, player, null, placeholders);

        // 放置可开启的宝箱列表
        List<Integer> contentSlots = config.getContentSlots();
        int slotIndex = 0;
        for (String crateId : key.getCrateIds()) {
            if (slotIndex >= contentSlots.size()) break;

            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate == null) continue;

            int slot = contentSlots.get(slotIndex);
            ItemStack crateItem = createKeyEditCrateItem(crate);
            inventory.setItem(slot, crateItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    /**
     * 创建管理界面的钥匙物品
     */
    private ItemStack createAdminKeyItem(Key key) {
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>ID: <!i><white>" + key.getId());
        lore.add("<!i><gray>可开启宝箱: <!i><white>" + key.getCrateIds().size() + "个");
        lore.add("");
        lore.add("<!i><yellow>左键 <!i><gray>- 编辑钥匙");
        lore.add("<!i><red>Shift+右键 <!i><gray>- 删除钥匙");

        ItemBuilder builder = new ItemBuilder(key.getItem())
                .name(key.getName())
                .lore(lore);

        if (key.isGlow()) {
            builder.glow(true);
        }

        return builder.build();
    }

    /**
     * 创建钥匙编辑界面的宝箱物品
     */
    private ItemStack createKeyEditCrateItem(Crate crate) {
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>ID: <!i><white>" + crate.getId());
        lore.add("");
        lore.add("<!i><red>Shift+右键 <!i><gray>- 移除此宝箱");

        return new ItemBuilder(crate.getBlockMaterial())
                .name(crate.getName())
                .lore(lore)
                .build();
    }

    /**
     * 打开宝箱选择界面（用于钥匙编辑）
     */
    public void openCrateSelectGui(Player player, Key key) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_KEY_EDIT, null),
                54,
                MessageUtil.parse("<!i><dark_gray>选择宝箱")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("key_id", key.getId());
        holder.setData("select_mode", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回钥匙编辑界面"))
                .build();
        inventory.setItem(45, back);

        // 放置宝箱列表
        List<Integer> contentSlots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        int slotIndex = 0;
        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            if (slotIndex >= contentSlots.size()) break;

            // 跳过已添加的宝箱
            if (key.getCrateIds().contains(crate.getId())) continue;

            int slot = contentSlots.get(slotIndex);
            ItemStack crateItem = new ItemBuilder(crate.getBlockMaterial())
                    .name(crate.getName())
                    .lore(List.of(
                            "<!i><gray>ID: <!i><white>" + crate.getId(),
                            "",
                            "<!i><yellow>左键点击添加"
                    ))
                    .build();
            inventory.setItem(slot, crateItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    /**
     * 打开历史记录界面
     */
    public void openHistoryGui(Player player, UUID targetUuid, String targetName, int page) {
        openHistoryGui(player, targetUuid, targetName, null, page);
    }

    public void openHistoryGui(Player player, UUID targetUuid, String targetName, String crateId, int page) {
        GuiConfig config = configManager.getGuiConfig("history");
        if (config == null) {
            plugin.getLogger().warning("History GUI config not found!");
            return;
        }

        boolean filterByCrate = crateId != null && !crateId.isBlank();
        Crate historyCrate = filterByCrate ? plugin.getCrateManager().getCrate(crateId) : null;
        String crateDisplayName = historyCrate != null
                ? MessageUtil.stripColor(historyCrate.getName())
                : (filterByCrate ? crateId : "");

        List<HistoryManager.HistoryEntry> history = filterByCrate
                ? plugin.getHistoryManager().getHistory(targetUuid, crateId, 100)
                : plugin.getHistoryManager().getHistory(targetUuid, 100);

        int itemsPerPage = config.getContentSlots().size();
        int totalPages = Math.max(1, (int) Math.ceil((double) history.size() / itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = config.getTitle()
                .replace("{player}", targetName)
                .replace("{crate}", crateDisplayName);
        CrateGuiHolder holder = new CrateGuiHolder(GuiType.HISTORY, null);
        holder.setCurrentPage(page);
        holder.setData("target_uuid", targetUuid);
        holder.setData("target_name", targetName);
        holder.setData("crate_id", filterByCrate ? crateId : null);

        Inventory inventory = Bukkit.createInventory(
                holder,
                config.getSize(),
                MessageUtil.parse(title)
        );

        // 填充背景
        fillBackground(inventory, config);

        // 准备占位符
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{page}", String.valueOf(page + 1));
        placeholders.put("{max_page}", String.valueOf(totalPages));
        placeholders.put("{total}", String.valueOf(history.size()));
        placeholders.put("{crate}", crateDisplayName);

        placeFixedItemsWithPlaceholders(inventory, config, player, historyCrate, placeholders);

        // 放置历史记录
        List<Integer> contentSlots = config.getContentSlots();
        int startIndex = page * itemsPerPage;
        int slotIndex = 0;
        for (int i = startIndex; i < history.size() && slotIndex < contentSlots.size(); i++) {
            HistoryManager.HistoryEntry entry = history.get(i);
            int slot = contentSlots.get(slotIndex);
            ItemStack historyItem = createHistoryItem(entry);
            inventory.setItem(slot, historyItem);
            slotIndex++;
        }

        player.openInventory(inventory);
    }

    // ==================== 辅助方法 ====================

    /**
     * 填充背景
     */
    private void fillBackground(Inventory inventory, GuiConfig config) {
        if (!config.isFillEnabled()) return;

        ItemStack fillItem = new ItemBuilder(config.getFillMaterial())
                .name(config.getFillName())
                .build();

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }
    }

    /**
     * 放置固定物品
     */
    private void placeFixedItems(Inventory inventory, GuiConfig config, Player player, Crate crate) {
        placeFixedItemsWithPlaceholders(inventory, config, player, crate, new HashMap<>());
    }

    /**
     * 放置固定物品（带占位符）
     */
    private void placeFixedItemsWithPlaceholders(Inventory inventory, GuiConfig config,
                                                  Player player, Crate crate,
                                                  Map<String, String> extraPlaceholders) {
        for (Map.Entry<Integer, GuiItem> entry : config.getItems().entrySet()) {
            int slot = entry.getKey();
            GuiItem guiItem = entry.getValue();

            String name = guiItem.getName();
            List<String> lore = new ArrayList<>(guiItem.getLore());

            // 替换占位符
            Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
            if (player != null) {
                placeholders.put("{player}", player.getName());
            }
            if (crate != null) {
                placeholders.put("{crate}", crate.getName());
                placeholders.put("{keys}", String.valueOf(plugin.getKeyManager().getTotalKeysForCrate(player, crate.getId())));
                placeholders.put("{reward_count}", String.valueOf(crate.getRewards().size()));
            }

            for (Map.Entry<String, String> ph : placeholders.entrySet()) {
                name = name.replace(ph.getKey(), ph.getValue());
                lore.replaceAll(line -> line.replace(ph.getKey(), ph.getValue()));
            }

            ItemBuilder builder = new ItemBuilder(guiItem.getMaterial())
                    .name(name)
                    .lore(lore);

            if (guiItem.getCustomModelData() > 0) {
                builder.customModelData(guiItem.getCustomModelData());
            }
            if (guiItem.isGlow()) {
                builder.glow(true);
            }

            inventory.setItem(slot, builder.build());
        }
    }

    /**
     * 创建奖励预览物品
     */
    private ItemStack createRewardPreviewItem(Reward reward, boolean showChance) {
        ItemStack item = reward.getDisplayItem();
        ItemBuilder builder = new ItemBuilder(item);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<!i><gray>稀有度: <!i><yellow>" + reward.getRarity());
        if (showChance) {
            lore.add("<!i><gray>概率: <!i><yellow>" + String.format("%.2f%%", reward.getChance()));
        }
        if (reward.shouldBroadcast()) {
            lore.add("<!i><gold>★ 稀有奖励");
        }

        replaceRawRarityLine(lore, reward.getRarity());

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<Component> originalLore = item.getItemMeta().lore();
            List<Component> newLore = new ArrayList<>();
            if (originalLore != null) {
                newLore.addAll(originalLore);
            }
            for (String line : lore) {
                newLore.add(MessageUtil.parse(line));
            }
            builder.loreComponents(newLore);
        } else {
            builder.lore(lore);
        }

        return builder.build();
    }

    /**
     * 创建管理界面的抽奖箱物品
     */
    private ItemStack createAdminCrateItem(Crate crate) {
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>ID: <!i><white>" + crate.getId());
        lore.add("<!i><gray>奖励数量: <!i><white>" + crate.getRewards().size());
        lore.add("");
        lore.add("<!i><yellow>左键 <!i><gray>- 编辑抽奖箱");
        lore.add("<!i><yellow>右键 <!i><gray>- 预览奖励");

        return new ItemBuilder(crate.getBlockMaterial())
                .name(crate.getName())
                .lore(lore)
                .build();
    }

    /**
     * 创建管理界面的奖励物品
     */
    private ItemStack createAdminRewardItem(Reward reward) {
        ItemStack item = reward.getDisplayItem();
        ItemBuilder builder = new ItemBuilder(item);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<!i><gray>ID: <!i><white>" + reward.getId());
        lore.add("<!i><gray>类型: <!i><white>" + reward.getType().name());
        lore.add("<!i><gray>概率: <!i><yellow>" + String.format("%.2f%%", reward.getChance()));
        lore.add("<!i><gray>稀有度: <!i><white>" + reward.getRarity());
        lore.add("");
        lore.add("<!i><yellow>左键 <!i><gray>- 编辑奖励");
        lore.add("<!i><red>Shift+右键 <!i><gray>- 删除奖励");

        replaceRawRarityLine(lore, reward.getRarity());

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<Component> originalLore = item.getItemMeta().lore();
            List<Component> newLore = new ArrayList<>();
            if (originalLore != null) {
                newLore.addAll(originalLore);
            }
            for (String line : lore) {
                newLore.add(MessageUtil.parse(line));
            }
            builder.loreComponents(newLore);
        } else {
            builder.lore(lore);
        }

        return builder.build();
    }

    /**
     * 创建历史记录物品
     */
    private ItemStack createHistoryItem(HistoryManager.HistoryEntry entry) {
        String crateId = entry.getCrateId();
        String rewardId = entry.getRewardId();
        String rewardName = entry.getRewardName();
        String time = entry.formattedTime();

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        String crateName = crate != null ? MessageUtil.stripColor(crate.getName()) : crateId;
        Reward reward = crate != null
                ? crate.getRewards().stream()
                .filter(candidate -> candidate.getId().equals(rewardId))
                .findFirst()
                .orElse(null)
                : null;

        ItemStack displayItem;
        if (reward != null && reward.getDisplayItem() != null && !reward.getDisplayItem().getType().isAir()) {
            displayItem = reward.getDisplayItem().clone();
        } else {
            Material material = crate != null ? crate.getBlockMaterial() : Material.CHEST;
            displayItem = new ItemStack(material);
        }

        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>奖励: <!i><white>" + rewardName);
        lore.add("<!i><gray>时间: <!i><white>" + time);

        return new ItemBuilder(displayItem)
                .name(rewardName)
                .lore(lore)
                .build();
    }

    // ==================== 输入会话管理 ====================

    /**
     * 开始输入会话
     */
    public void startInputSession(Player player, String type, Object data, InputCallback callback) {
        inputSessions.put(player.getUniqueId(), new InputSession(type, data, callback));
        player.closeInventory();
    }

    /**
     * 处理玩家输入
     */
    public boolean handleInput(Player player, String input) {
        InputSession session = inputSessions.remove(player.getUniqueId());
        if (session == null) return false;

        session.getCallback().onInput(player, input, session.getData());
        return true;
    }

    /**
     * 取消输入会话
     */
    public void cancelInputSession(Player player) {
        inputSessions.remove(player.getUniqueId());
    }

    /**
     * 检查玩家是否在输入会话中
     */
    public boolean hasInputSession(Player player) {
        return inputSessions.containsKey(player.getUniqueId());
    }

    /**
     * 获取GUI配置管理器
     */
    public GuiConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 重新加载
     */
    public void reload() {
        configManager.reload();
    }

    /**
     * 检查是否是GUI
     */
    public boolean isGuiInventory(Inventory inventory) {
        return inventory.getHolder() instanceof CrateGuiHolder;
    }

    /**
     * 获取GUI持有者
     */
    public CrateGuiHolder getGuiHolder(Inventory inventory) {
        if (inventory.getHolder() instanceof CrateGuiHolder) {
            return (CrateGuiHolder) inventory.getHolder();
        }
        return null;
    }

    // ==================== 编辑界面 ====================

    /**
     * 打开动画选择界面
     */
    public void openAnimationSelectGui(Player player, Crate crate) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_CRATE_EDIT, crate),
                45,
                MessageUtil.parse("<!i><dark_gray>动画设置")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("select_animation", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回宝箱编辑界面"))
                .build();
        inventory.setItem(36, back);

        // ===== GUI动画开关 =====
        boolean guiAnimEnabled = crate.isAnimationEnabled();
        ItemStack guiAnimToggle = new ItemBuilder(guiAnimEnabled ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(guiAnimEnabled ? "<!i><green>GUI动画: 开启" : "<!i><gray>GUI动画: 关闭")
                .lore(List.of(
                        "<!i><gray>在玩家界面中显示滚动动画",
                        "",
                        "<!i><yellow>点击切换"
                ))
                .build();
        inventory.setItem(10, guiAnimToggle);

        // GUI动画类型选择
        gg.fotia.crates.animation.AnimationType currentType = crate.getAnimationType();

        // 轮盘动画 - 放在中间位置
        ItemStack roulette = new ItemBuilder(Material.CLOCK)
                .name("<!i><yellow>轮盘动画 (ROULETTE)")
                .lore(List.of(
                        "<!i><gray>经典的轮盘滚动动画",
                        "",
                        (currentType == gg.fotia.crates.animation.AnimationType.ROULETTE ||
                         currentType == gg.fotia.crates.animation.AnimationType.CSGO) ? "<!i><green>✓ 当前选中" : "<!i><yellow>点击选择"
                ))
                .glow(currentType == gg.fotia.crates.animation.AnimationType.ROULETTE ||
                      currentType == gg.fotia.crates.animation.AnimationType.CSGO)
                .build();
        inventory.setItem(13, roulette);

        // 无动画
        ItemStack instant = new ItemBuilder(Material.FEATHER)
                .name("<!i><white>无动画 (INSTANT)")
                .lore(List.of(
                        "<!i><gray>直接显示结果",
                        "",
                        currentType == gg.fotia.crates.animation.AnimationType.INSTANT ? "<!i><green>✓ 当前选中" : "<!i><yellow>点击选择"
                ))
                .glow(currentType == gg.fotia.crates.animation.AnimationType.INSTANT)
                .build();
        inventory.setItem(15, instant);

        // ===== 物理动画开关 =====
        boolean physicalAnimEnabled = crate.isPhysicalAnimationEnabled();
        ItemStack physicalAnimToggle = new ItemBuilder(physicalAnimEnabled ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(physicalAnimEnabled ? "<!i><green>物理动画: 开启" : "<!i><gray>物理动画: 关闭")
                .lore(List.of(
                        "<!i><gray>在宝箱上方显示漂浮物品动画",
                        "<!i><gray>可与GUI动画同时开启",
                        "",
                        "<!i><yellow>点击切换"
                ))
                .build();
        inventory.setItem(28, physicalAnimToggle);

        // 物理动画高度设置
        ItemStack heightSetting = new ItemBuilder(Material.LADDER)
                .name("<!i><aqua>物理动画高度: " + crate.getPhysicalAnimationHeight())
                .lore(List.of(
                        "<!i><gray>物品漂浮的高度",
                        "",
                        "<!i><yellow>左键 +0.1",
                        "<!i><yellow>右键 -0.1"
                ))
                .build();
        inventory.setItem(30, heightSetting);

        // ===== 动画时长设置 =====
        ItemStack durationSetting = new ItemBuilder(Material.CLOCK)
                .name("<!i><light_purple>动画时长: " + crate.getAnimationDuration() + "秒")
                .lore(List.of(
                        "<!i><gray>动画播放的总时长",
                        "",
                        "<!i><yellow>左键 +1秒",
                        "<!i><yellow>右键 -1秒",
                        "<!i><aqua>中键 输入精确数值"
                ))
                .build();
        inventory.setItem(32, durationSetting);

        // 提示信息
        ItemStack info = new ItemBuilder(Material.BOOK)
                .name("<!i><gold>动画设置说明")
                .lore(List.of(
                        "<!i><gray>GUI动画和物理动画可以同时开启",
                        "",
                        "<!i><aqua>GUI动画: <!i><white>在玩家界面中显示",
                        "<!i><aqua>物理动画: <!i><white>在宝箱上方显示",
                        "",
                        "<!i><yellow>两者同时开启时会同步播放"
                ))
                .build();
        inventory.setItem(34, info);

        player.openInventory(inventory);
    }

    /**
     * 打开保底编辑界面（多级保底）
     */
    public void openPityEditGui(Player player, Crate crate) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_CRATE_EDIT, crate),
                54,
                MessageUtil.parse("<!i><dark_gray>多级保底设置")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("edit_pity", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回宝箱编辑界面"))
                .build();
        inventory.setItem(45, back);

        // 启用/禁用保底
        ItemStack toggle = new ItemBuilder(crate.isPityEnabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(crate.isPityEnabled() ? "<!i><green>保底已启用" : "<!i><red>保底已禁用")
                .lore(List.of(
                        "<!i><gray>开启后玩家抽奖达到指定次数",
                        "<!i><gray>将保证获得对应稀有度奖励",
                        "",
                        "<!i><yellow>点击切换"
                ))
                .build();
        inventory.setItem(4, toggle);

        // 显示现有保底等级
        List<Crate.PityTier> tiers = crate.getPityTiers();
        int[] tierSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        for (int i = 0; i < tierSlots.length; i++) {
            if (i < tiers.size()) {
                Crate.PityTier tier = tiers.get(i);
                ItemStack tierItem = new ItemBuilder(getRarityMaterial(tier.getRarity()))
                        .name("<!i><gold>保底等级 " + (i + 1))
                        .lore(List.of(
                                "<!i><gray>次数: <!i><white>" + tier.getCount(),
                                "<!i><gray>稀有度: " + getRarityColor(tier.getRarity()) + getRarityDisplayName(tier.getRarity()),
                                "",
                                "<!i><yellow>左键编辑次数",
                                "<!i><aqua>右键切换稀有度",
                                "<!i><red>Shift+右键删除"
                        ))
                        .build();
                inventory.setItem(tierSlots[i], tierItem);
            } else if (i == tiers.size()) {
                // 添加新等级按钮
                ItemStack addTier = new ItemBuilder(Material.EMERALD)
                        .name("<!i><green>+ 添加保底等级")
                        .lore(List.of(
                                "<!i><gray>添加新的保底等级",
                                "",
                                "<!i><yellow>点击添加"
                        ))
                        .build();
                inventory.setItem(tierSlots[i], addTier);
            }
        }

        // 说明信息
        ItemStack info = new ItemBuilder(Material.BOOK)
                .name("<!i><gold>多级保底说明")
                .lore(List.of(
                        "<!i><gray>可以设置多个保底等级",
                        "<!i><gray>例如:",
                        "<!i><white>  25次 → 罕见(uncommon)",
                        "<!i><white>  50次 → 稀有(rare)",
                        "<!i><white>  100次 → 传说(legendary)",
                        "",
                        "<!i><gray>当玩家抽奖次数达到保底次数时",
                        "<!i><gray>将保证获得对应稀有度的奖励",
                        "<!i><gray>达到最高级保底后重置计数"
                ))
                .build();
        inventory.setItem(49, info);

        // 保存按钮
        inventory.setItem(49, new ItemBuilder(Material.BOOK)
                .name("<!i><gold>多级保底说明")
                .lore(buildPityInfoLoreSafe(crate))
                .build());

        inventory.setItem(49, new ItemBuilder(Material.BOOK)
                .name("<!i><gold>\u591a\u7ea7\u4fdd\u5e95\u8bf4\u660e")
                .lore(buildPityInfoLoreSafe(crate))
                .build());

        ItemStack save = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("<!i><green>保存并返回")
                .lore(List.of("<!i><gray>保存保底设置"))
                .build();
        inventory.setItem(53, save);

        player.openInventory(inventory);
    }

    /**
     * 打开奖励编辑界面
     */
    public void openRewardEditGui(Player player, Crate crate, Reward reward) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_REWARD_EDIT, crate),
                54,
                MessageUtil.parse("<!i><dark_gray>编辑奖励: " + MessageUtil.stripColor(reward.getDisplayName()))
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("reward_id", reward.getId());

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回宝箱编辑界面"))
                .build();
        inventory.setItem(45, back);

        // ===== 第一行：显示图标 =====
        ItemStack displayItem = reward.getDisplayItem().clone();
        ItemBuilder displayBuilder = new ItemBuilder(displayItem);
        List<String> displayLore = new ArrayList<>();
        if (displayItem.hasItemMeta() && displayItem.getItemMeta().hasLore()) {
            displayLore.addAll(displayItem.getItemMeta().getLore().stream()
                    .map(MessageUtil::toLegacy).toList());
        }
        displayLore.add("");
        displayLore.add("<!i><aqua>► 显示图标");
        displayLore.add("<!i><gray>用于预览界面和抽奖动画显示");
        displayLore.add("");
        displayLore.add("<!i><yellow>点击设置");
        displayBuilder.lore(displayLore);
        inventory.setItem(4, displayBuilder.build());

        // ===== 第二行：奖励物品 =====
        // 获取所有奖励物品
        ItemStack actualItem = reward.getItem();
        List<ItemStack> extraItems = reward.getExtraItems();

        // 显示奖励物品数量
        int totalItems = (actualItem != null && !actualItem.getType().isAir() ? 1 : 0)
                + (int) extraItems.stream().filter(item -> item != null && !item.getType().isAir()).count();

        ItemStack rewardItemBtn;
        if (totalItems > 0) {
            ItemStack displayBase = (actualItem != null && !actualItem.getType().isAir()) ? actualItem.clone() : new ItemStack(Material.CHEST);
            rewardItemBtn = new ItemBuilder(displayBase)
                    .name("<!i><green>奖励物品 (" + totalItems + "个)")
                    .addLore("")
                    .addLore("<!i><gray>实际给予玩家的物品")
                    .addLore("")
                    .addLore("<!i><yellow>点击管理奖励物品")
                    .build();
        } else {
            rewardItemBtn = new ItemBuilder(Material.CHEST)
                    .name("<!i><yellow>奖励物品 (未设置)")
                    .lore(List.of(
                            "<!i><gray>实际给予玩家的物品",
                            "<!i><gray>当前未设置，使用显示图标",
                            "",
                            "<!i><yellow>点击管理奖励物品"
                    ))
                    .build();
        }
        inventory.setItem(11, rewardItemBtn);

        // ===== 第二行：命令列表 =====
        List<String> commands = reward.getCommands();
        List<String> cmdLore = new ArrayList<>();
        cmdLore.add("<!i><gray>获得奖励时执行的命令");
        cmdLore.add("<!i><gray>使用 %player% 作为玩家占位符");
        cmdLore.add("");
        if (commands.isEmpty()) {
            cmdLore.add("<!i><gray>暂无命令");
        } else {
            cmdLore.add("<!i><white>当前命令 (" + commands.size() + "条):");
            for (int i = 0; i < Math.min(commands.size(), 5); i++) {
                cmdLore.add("<!i><gray>- " + commands.get(i));
            }
            if (commands.size() > 5) {
                cmdLore.add("<!i><gray>... 还有 " + (commands.size() - 5) + " 条");
            }
        }
        cmdLore.add("");
        cmdLore.add("<!i><yellow>左键添加命令");
        cmdLore.add("<!i><red>右键清空所有命令");

        ItemStack cmdItem = new ItemBuilder(Material.COMMAND_BLOCK)
                .name("<!i><light_purple>命令奖励: " + commands.size() + "条")
                .lore(cmdLore)
                .build();
        inventory.setItem(15, cmdItem);

        // ===== 第三行：基本属性 =====
        // 概率设置
        ItemStack chance = new ItemBuilder(Material.PAPER)
                .name("<!i><yellow>概率: " + String.format("%.2f%%", reward.getChance()))
                .lore(List.of(
                        "<!i><gray>获得此奖励的概率",
                        "",
                        "<!i><yellow>左键 +1%  |  右键 -1%",
                        "<!i><yellow>Shift+左键 +5%  |  Shift+右键 -5%",
                        "<!i><aqua>中键 输入精确数值"
                ))
                .build();
        inventory.setItem(28, chance);

        // 稀有度设置
        ItemStack rarity = new ItemBuilder(getRarityMaterial(reward.getRarity()))
                .name("<!i><aqua>稀有度: " + getRarityDisplayName(reward.getRarity()))
                .lore(List.of(
                        "<!i><gray>奖励的稀有度等级",
                        "",
                        "<!i><yellow>点击切换下一级"
                ))
                .build();
        inventory.setItem(30, rarity);

        // 广播设置
        ItemStack broadcast = new ItemBuilder(reward.shouldBroadcast() ? Material.BELL : Material.GRAY_DYE)
                .name(reward.shouldBroadcast() ? "<!i><gold>广播: 开启" : "<!i><gray>广播: 关闭")
                .lore(List.of(
                        "<!i><gray>获得此奖励时是否全服广播",
                        "",
                        "<!i><yellow>点击切换"
                ))
                .build();
        inventory.setItem(32, broadcast);

        // 显示名称
        ItemStack displayName = new ItemBuilder(Material.NAME_TAG)
                .name("<!i><yellow>显示名称")
                .lore(List.of(
                        "<!i><gray>当前: " + MessageUtil.stripColor(reward.getDisplayName()),
                        "",
                        "<!i><yellow>点击修改"
                ))
                .build();
        inventory.setItem(34, displayName);

        // ===== 第四行：权限检测配置 =====
        // 权限检测开关
        boolean permCheckEnabled = reward.isPermissionCheckEnabled();
        ItemStack permToggle = new ItemBuilder(permCheckEnabled ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(permCheckEnabled ? "<!i><green>权限检测: 开启" : "<!i><gray>权限检测: 关闭")
                .lore(List.of(
                        "<!i><gray>检测玩家是否拥有指定权限",
                        "<!i><gray>拥有权限时可跳过或替代奖励",
                        "",
                        "<!i><yellow>点击切换"
                ))
                .build();
        inventory.setItem(37, permToggle);

        // 权限节点设置
        String checkPerm = reward.getCheckPermission();
        ItemStack permNode = new ItemBuilder(Material.PAPER)
                .name("<!i><aqua>权限节点")
                .lore(List.of(
                        "<!i><gray>当前: " + (checkPerm != null && !checkPerm.isEmpty() ? checkPerm : "未设置"),
                        "",
                        "<!i><yellow>点击设置权限节点"
                ))
                .build();
        inventory.setItem(39, permNode);

        // 行为选择
        gg.fotia.crates.reward.PermissionAction permAction = reward.getPermissionAction();
        ItemStack actionBtn = new ItemBuilder(permAction == gg.fotia.crates.reward.PermissionAction.SKIP
                        ? Material.BARRIER : Material.CHEST)
                .name("<!i><light_purple>匹配行为: " + (permAction == gg.fotia.crates.reward.PermissionAction.SKIP ? "跳过" : "替代"))
                .lore(List.of(
                        "<!i><gray>玩家拥有权限时的行为",
                        "",
                        "<!i><white>跳过: 不会抽到此奖励",
                        "<!i><white>替代: 给予替代奖励",
                        "",
                        "<!i><yellow>点击切换"
                ))
                .build();
        inventory.setItem(41, actionBtn);

        // 替代奖励选择
        String altRewardId = reward.getAlternativeRewardId();
        String altRewardName = "未设置";
        if (altRewardId != null && !altRewardId.isEmpty()) {
            Reward altReward = crate.getRewards().stream()
                    .filter(r -> r.getId().equals(altRewardId))
                    .findFirst()
                    .orElse(null);
            if (altReward != null) {
                altRewardName = MessageUtil.stripColor(altReward.getDisplayName());
            } else {
                altRewardName = altRewardId + " (无效)";
            }
        }
        ItemStack altRewardBtn = new ItemBuilder(Material.ENDER_CHEST)
                .name("<!i><gold>替代奖励")
                .lore(List.of(
                        "<!i><gray>当前: " + altRewardName,
                        "",
                        "<!i><gray>玩家拥有权限时给予的替代奖励",
                        "<!i><gray>仅在行为为\"替代\"时生效",
                        "",
                        "<!i><yellow>点击选择替代奖励"
                ))
                .build();
        inventory.setItem(43, altRewardBtn);

        // ===== 底部工具栏 =====
        // 复制奖励
        ItemStack copy = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("<!i><aqua>复制奖励")
                .lore(List.of(
                        "<!i><gray>复制此奖励创建新奖励",
                        "",
                        "<!i><yellow>点击复制"
                ))
                .build();
        inventory.setItem(47, copy);

        // 删除奖励
        ItemStack delete = new ItemBuilder(Material.TNT)
                .name("<!i><red>删除奖励")
                .lore(List.of(
                        "<!i><gray>删除此奖励",
                        "",
                        "<!i><red>Shift+点击确认删除"
                ))
                .build();
        inventory.setItem(49, delete);

        // 保存
        ItemStack save = new ItemBuilder(Material.EMERALD)
                .name("<!i><green>保存并返回")
                .lore(List.of("<!i><gray>保存所有修改并返回"))
                .build();
        inventory.setItem(51, save);

        player.openInventory(inventory);
    }

    /**
     * 打开物品输入界面（用于设置奖励物品）
     * @param player 玩家
     * @param crate 宝箱
     * @param rewardId 奖励ID
     * @param inputType 输入类型: "display_icon" 或 "reward_item"
     */
    public void openItemInputGui(Player player, Crate crate, String rewardId, String inputType) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ITEM_INPUT, crate),
                27,
                MessageUtil.parse("<!i><dark_gray>放入物品")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("reward_id", rewardId);
        holder.setData("input_type", inputType);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, fill);
        }

        // 中央物品槽位（slot 13）- 设为空气允许放入物品
        inventory.setItem(13, new ItemStack(Material.AIR));

        // 提示物品
        String title = inputType.equals("display_icon") ? "显示图标" : "奖励物品";
        ItemStack info = new ItemBuilder(Material.BOOK)
                .name("<!i><yellow>设置" + title)
                .lore(List.of(
                        "<!i><gray>将物品放入中央槽位",
                        "<!i><gray>物品的所有NBT数据将被完整保存",
                        "",
                        "<!i><aqua>支持任何物品，包括：",
                        "<!i><white>- 自定义名称和描述",
                        "<!i><white>- 附魔和属性",
                        "<!i><white>- ItemsAdder/Oraxen物品",
                        "<!i><white>- 任何NBT数据"
                ))
                .build();
        inventory.setItem(4, info);

        // 确认按钮
        ItemStack confirm = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("<!i><green>确认保存")
                .lore(List.of("<!i><gray>点击保存物品并返回"))
                .build();
        inventory.setItem(15, confirm);
        inventory.setItem(16, confirm);

        // 取消按钮
        ItemStack cancel = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("<!i><red>取消")
                .lore(List.of("<!i><gray>点击取消并返回"))
                .build();
        inventory.setItem(10, cancel);
        inventory.setItem(11, cancel);

        // 清除按钮（仅奖励物品有此选项）
        if (inputType.equals("reward_item")) {
            ItemStack clear = new ItemBuilder(Material.BARRIER)
                    .name("<!i><red>清除奖励物品")
                    .lore(List.of(
                            "<!i><gray>清除后将使用显示图标作为奖励",
                            "",
                            "<!i><yellow>点击清除"
                    ))
                    .build();
            inventory.setItem(22, clear);
        }

        player.openInventory(inventory);
    }

    /**
     * 打开奖励物品管理界面（支持多个物品）
     */
    public void openRewardItemsGui(Player player, Crate crate, String rewardId) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.REWARD_ITEMS, crate),
                54,
                MessageUtil.parse("<!i><dark_gray>奖励物品管理")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("reward_id", rewardId);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 获取奖励
        Reward reward = crate.getRewards().stream()
                .filter(r -> r.getId().equals(rewardId))
                .findFirst()
                .orElse(null);

        if (reward == null) {
            player.closeInventory();
            return;
        }

        // 物品槽位 (3行 x 7列 = 21个)
        List<Integer> itemSlots = List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        );

        // 设置所有物品槽位为空气（允许放入物品）
        for (int slot : itemSlots) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
        }

        // 加载现有物品
        int slotIndex = 0;
        ItemStack mainItem = reward.getItem();
        if (mainItem != null && !mainItem.getType().isAir()) {
            inventory.setItem(itemSlots.get(slotIndex++), mainItem.clone());
        }

        for (ItemStack extra : reward.getExtraItems()) {
            if (slotIndex < itemSlots.size() && extra != null && !extra.getType().isAir()) {
                inventory.setItem(itemSlots.get(slotIndex++), extra.clone());
            }
        }

        // 提示信息
        ItemStack info = new ItemBuilder(Material.BOOK)
                .name("<!i><gold>奖励物品管理")
                .lore(List.of(
                        "<!i><gray>将物品放入槽位中",
                        "<!i><gray>所有物品都会作为奖励给予玩家",
                        "",
                        "<!i><aqua>支持任何物品，包括：",
                        "<!i><white>- 自定义名称和描述",
                        "<!i><white>- 附魔和属性",
                        "<!i><white>- ItemsAdder/Oraxen物品",
                        "",
                        "<!i><yellow>最多可添加 " + itemSlots.size() + " 个物品"
                ))
                .build();
        inventory.setItem(4, info);

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回奖励编辑界面"))
                .build();
        inventory.setItem(45, back);

        // 清空所有按钮
        ItemStack clearAll = new ItemBuilder(Material.BARRIER)
                .name("<!i><red>清空所有物品")
                .lore(List.of(
                        "<!i><gray>清除所有奖励物品",
                        "<!i><gray>清除后将使用显示图标作为奖励",
                        "",
                        "<!i><red>Shift+点击确认清空"
                ))
                .build();
        inventory.setItem(49, clearAll);

        // 保存按钮
        ItemStack save = new ItemBuilder(Material.EMERALD)
                .name("<!i><green>保存并返回")
                .lore(List.of("<!i><gray>保存所有物品并返回"))
                .build();
        inventory.setItem(53, save);

        player.openInventory(inventory);
    }

    /**
     * 打开替代奖励选择界面
     */
    public void openAlternativeRewardSelectGui(Player player, Crate crate, String sourceRewardId) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_REWARD_EDIT, crate),
                54,
                MessageUtil.parse("<!i><dark_gray>选择替代奖励")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("source_reward_id", sourceRewardId);
        holder.setData("alternative_select", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 奖励槽位
        List<Integer> rewardSlots = List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        );

        // 显示所有奖励（排除自己）
        List<Reward> rewards = crate.getRewards().stream()
                .filter(r -> !r.getId().equals(sourceRewardId))
                .toList();

        for (int i = 0; i < Math.min(rewards.size(), rewardSlots.size()); i++) {
            Reward reward = rewards.get(i);
            ItemStack item = reward.getDisplayItem().clone();
            ItemBuilder builder = new ItemBuilder(item);

            List<String> lore = new ArrayList<>();
            if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                lore.addAll(item.getItemMeta().getLore().stream()
                        .map(MessageUtil::toLegacy).toList());
            }
            lore.add("");
            lore.add("<!i><gray>ID: " + reward.getId());
            lore.add("<!i><gray>稀有度: " + getRarityDisplayName(reward.getRarity()));
            lore.add("");
            lore.add("<!i><yellow>点击选择此奖励作为替代");

            builder.lore(lore);
            inventory.setItem(rewardSlots.get(i), builder.build());
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回奖励编辑界面"))
                .build();
        inventory.setItem(45, back);

        // 清除替代奖励按钮
        ItemStack clear = new ItemBuilder(Material.BARRIER)
                .name("<!i><red>清除替代奖励")
                .lore(List.of(
                        "<!i><gray>移除当前设置的替代奖励",
                        "",
                        "<!i><yellow>点击清除"
                ))
                .build();
        inventory.setItem(49, clear);

        player.openInventory(inventory);
    }

    /**
     * 获取奖励类型显示名称
     */
    /*
    private List<String> buildPityInfoLore(Crate crate) {
        ItemBuilder builder = new ItemBuilder(displayItem).name(rewardName);
        List<Component> lore = new ArrayList<>();
        lore.add("<!i><gray>可以设置多个保底等级");

        List<Crate.PityTier> tiers = new ArrayList<>(crate.getPityTiers());
        tiers.sort(Comparator.comparingInt(Crate.PityTier::getCount));
        if (!tiers.isEmpty()) {
            lore.add("<!i><gray>当前保底档位:");
            int maxLines = Math.min(3, tiers.size());
            for (int i = 0; i < maxLines; i++) {
                Crate.PityTier tier = tiers.get(i);
                lore.add("<!i><white>  " + tier.getCount() + "次 -> "
                        + getRarityColor(tier.getRarity()) + getRarityDisplayName(tier.getRarity()));
            }
            if (tiers.size() > maxLines) {
                lore.add("<!i><gray>  ...");
            }
        } else {
            List<String> rarityIds = plugin.getConfigManager().getRarityIds();
            if (!rarityIds.isEmpty()) {
                lore.add("<!i><gray>当前稀有度顺序:");
                int maxLines = Math.min(3, rarityIds.size());
                for (int i = 0; i < maxLines; i++) {
                    String rarityId = rarityIds.get(i);
                    lore.add("<!i><white>  " + (i + 1) + ". "
                            + getRarityColor(rarityId) + getRarityDisplayName(rarityId));
                }
            }
        }

        lore.add("");
        lore.add("<!i><gray>达到对应次数后");
        lore.add("<!i><gray>将保证获得该稀有度或更高稀有度奖励");
        lore.add("<!i><gray>达到最高档位后重置保底计数");
        return lore;
    }

    */

    private List<String> buildPityInfoLoreSafe(Crate crate) {
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>\u53ef\u4ee5\u8bbe\u7f6e\u591a\u4e2a\u4fdd\u5e95\u7b49\u7ea7");

        List<Crate.PityTier> tiers = new ArrayList<>(crate.getPityTiers());
        tiers.sort(Comparator.comparingInt(Crate.PityTier::getCount));
        if (!tiers.isEmpty()) {
            lore.add("<!i><gray>\u5f53\u524d\u4fdd\u5e95\u6863\u4f4d:");
            for (Crate.PityTier tier : tiers) {
                lore.add("<!i><white>  " + tier.getCount() + "\u6b21 -> "
                        + getRarityColor(tier.getRarity()) + getRarityDisplayName(tier.getRarity()));
            }
            List<String> rarityIds = plugin.getConfigManager().getRarityIds();
            if (!rarityIds.isEmpty()) {
                lore.add("");
                lore.add("<!i><gray>\u53ef\u7528\u7a00\u6709\u5ea6:");
                for (int i = 0; i < rarityIds.size(); i++) {
                    String rarityId = rarityIds.get(i);
                    lore.add("<!i><white>  " + (i + 1) + ". "
                            + getRarityColor(rarityId) + getRarityDisplayName(rarityId));
                }
            }
        } else {
            lore.add("");
            lore.add("<!i><yellow>\u5f53\u524d\u672a\u8bbe\u7f6e\u4efb\u4f55\u4fdd\u5e95\u6863\u4f4d");
            lore.add("<!i><gray>\u8bf7\u5148\u70b9\u51fb\u7eff\u8272\u6309\u94ae\u6dfb\u52a0\u4fdd\u5e95\u7b49\u7ea7");
        }

        lore.add("");
        lore.add("<!i><gray>\u8fbe\u5230\u5bf9\u5e94\u6b21\u6570\u540e");
        lore.add("<!i><gray>\u5c06\u4fdd\u8bc1\u83b7\u5f97\u8be5\u7a00\u6709\u5ea6\u6216\u66f4\u9ad8\u7a00\u6709\u5ea6\u5956\u52b1");
        lore.add("<!i><gray>\u8fbe\u5230\u6700\u9ad8\u6863\u4f4d\u540e\u91cd\u7f6e\u4fdd\u5e95\u8ba1\u6570");
        return lore;
    }

    private String getRewardTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "ITEM" -> "物品";
            case "COMMAND" -> "命令";
            case "MONEY" -> "金币";
            case "EXPERIENCE" -> "经验";
            default -> type;
        };
    }

    /**
     * 打开奖励管理界面（专门管理奖励的界面）
     */
    public void openRewardManagerGui(Player player, Crate crate, int page) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_REWARD_EDIT, crate),
                54,
                MessageUtil.parse("<!i><dark_gray>奖励管理: " + crate.getName())
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("reward_manager", true);
        holder.setCurrentPage(page);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 奖励槽位 (4行 x 7列 = 28个)
        List<Integer> rewardSlots = List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        );

        List<Reward> rewards = crate.getRewards();
        int itemsPerPage = rewardSlots.size();
        int startIndex = page * itemsPerPage;
        int maxPage = Math.max(0, (rewards.size() - 1) / itemsPerPage);

        // 放置奖励
        for (int i = 0; i < rewardSlots.size(); i++) {
            int rewardIndex = startIndex + i;
            int slot = rewardSlots.get(i);

            if (rewardIndex < rewards.size()) {
                Reward reward = rewards.get(rewardIndex);
                inventory.setItem(slot, createManagerRewardItem(reward));
            }
            // 空槽位保持背景
        }

        // 底部工具栏
        // 返回
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回宝箱编辑界面"))
                .build();
        inventory.setItem(45, back);

        // 上一页
        if (page > 0) {
            ItemStack prevPage = new ItemBuilder(Material.ARROW)
                    .name("<!i><yellow>上一页")
                    .lore(List.of("<!i><gray>第 " + page + "/" + (maxPage + 1) + " 页"))
                    .build();
            inventory.setItem(48, prevPage);
        }

        // 概率信息
        double totalChance = rewards.stream().mapToDouble(Reward::getChance).sum();
        ItemStack info = new ItemBuilder(Material.BOOK)
                .name("<!i><gold>奖励统计")
                .lore(List.of(
                        "<!i><gray>奖励数量: <!i><white>" + rewards.size(),
                        "<!i><gray>总概率: <!i><white>" + String.format("%.2f%%", totalChance),
                        "",
                        totalChance != 100 ? "<!i><red>⚠ 建议总概率为100%" : "<!i><green>✓ 概率配置正确"
                ))
                .build();
        inventory.setItem(49, info);

        // 下一页
        if (page < maxPage) {
            ItemStack nextPage = new ItemBuilder(Material.ARROW)
                    .name("<!i><yellow>下一页")
                    .lore(List.of("<!i><gray>第 " + (page + 2) + "/" + (maxPage + 1) + " 页"))
                    .build();
            inventory.setItem(50, nextPage);
        }

        // 概率平衡
        ItemStack balance = new ItemBuilder(Material.GOLDEN_APPLE)
                .name("<!i><gold>概率平衡")
                .lore(List.of(
                        "<!i><gray>自动调整所有奖励概率",
                        "<!i><gray>使总概率等于100%",
                        "",
                        "<!i><yellow>点击执行"
                ))
                .build();
        inventory.setItem(52, balance);

        // 添加奖励
        ItemStack addReward = new ItemBuilder(Material.EMERALD)
                .name("<!i><green>添加新奖励")
                .lore(List.of(
                        "<!i><gray>创建一个新的奖励",
                        "<!i><gray>然后配置奖励内容",
                        "",
                        "<!i><yellow>点击添加"
                ))
                .build();
        inventory.setItem(53, addReward);

        player.openInventory(inventory);
    }

    /**
     * 创建奖励管理界面的奖励物品
     */
    private ItemStack createManagerRewardItem(Reward reward) {
        ItemStack item = reward.getDisplayItem().clone();
        ItemBuilder builder = new ItemBuilder(item);

        List<String> lore = new ArrayList<>();
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            lore.addAll(item.getItemMeta().getLore().stream()
                    .map(MessageUtil::toLegacy).toList());
        }
        lore.add("");
        lore.add("<!i><gray>ID: <!i><white>" + reward.getId());
        lore.add("<!i><gray>概率: <!i><yellow>" + String.format("%.2f%%", reward.getChance()));
        lore.add("<!i><gray>稀有度: " + getRarityColor(reward.getRarity()) + getRarityDisplayName(reward.getRarity()));
        if (reward.shouldBroadcast()) {
            lore.add("<!i><gold>★ 全服广播");
        }
        lore.add("");
        lore.add("<!i><yellow>左键 编辑奖励");
        lore.add("<!i><aqua>中键 复制奖励");
        lore.add("<!i><red>Shift+右键 删除");

        builder.lore(lore);
        return builder.build();
    }

    /**
     * 获取稀有度显示名称（从配置读取）
     */
    private String getRarityDisplayName(String rarity) {
        return plugin.getConfigManager().getRarityDisplayName(rarity);
    }

    private void replaceRawRarityLine(List<String> lore, String rarity) {
        if (rarity == null || lore.isEmpty()) {
            return;
        }

        String configuredRarityLine = "<!i><gray>\u7a00\u6709\u5ea6: "
                + getRarityColor(rarity)
                + getRarityDisplayName(rarity);

        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains(rarity)) {
                lore.set(i, configuredRarityLine);
            }
        }
    }

    /**
     * 获取稀有度颜色（从配置读取）
     */
    private String getRarityColor(String rarity) {
        String color = plugin.getConfigManager().getRarityColor(rarity);
        return "<!i><" + color + ">";
    }

    /**
     * 根据稀有度获取对应材质
     */
    private Material getRarityMaterial(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> Material.COAL;
            case "uncommon" -> Material.IRON_INGOT;
            case "rare" -> Material.GOLD_INGOT;
            case "epic" -> Material.DIAMOND;
            case "legendary" -> Material.NETHER_STAR;
            case "mythic" -> Material.END_CRYSTAL;
            default -> {
                List<Material> fallbackMaterials = List.of(
                        Material.COAL,
                        Material.IRON_INGOT,
                        Material.GOLD_INGOT,
                        Material.DIAMOND,
                        Material.NETHER_STAR,
                        Material.END_CRYSTAL,
                        Material.DRAGON_BREATH,
                        Material.TOTEM_OF_UNDYING
                );
                int rarityIndex = plugin.getConfigManager().getRarityIndex(rarity);
                if (rarityIndex < 0) {
                    yield Material.PAPER;
                }
                yield fallbackMaterials.get(Math.min(rarityIndex, fallbackMaterials.size() - 1));
            }
        };
    }

    /**
     * 打开稀有度管理界面
     */
    public void openRarityManagerGui(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN, null),
                54,
                MessageUtil.parse("<!i><dark_gray>稀有度管理")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("rarity_manager", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // 稀有度槽位
        int[] raritySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        List<String> rarityIds = plugin.getConfigManager().getRarityIds();

        for (int i = 0; i < raritySlots.length && i < rarityIds.size(); i++) {
            String rarityId = rarityIds.get(i);
            gg.fotia.crates.config.ConfigManager.RarityConfig rarity = plugin.getConfigManager().getRarity(rarityId);

            ItemStack item = new ItemBuilder(getRarityMaterial(rarityId))
                    .name(rarity.getDisplayName())
                    .lore(List.of(
                            "<!i><gray>ID: " + rarityId,
                            "<!i><gray>颜色: " + rarity.getColor(),
                            "",
                            "<!i><yellow>左键编辑显示名称",
                            "<!i><aqua>右键编辑颜色",
                            "<!i><red>Shift+右键删除"
                    ))
                    .build();
            inventory.setItem(raritySlots[i], item);
        }

        // 添加新稀有度按钮
        if (rarityIds.size() < raritySlots.length) {
            ItemStack addBtn = new ItemBuilder(Material.EMERALD)
                    .name("<!i><green>+ 添加稀有度")
                    .lore(List.of("<!i><gray>点击添加新的稀有度"))
                    .build();
            inventory.setItem(raritySlots[Math.min(rarityIds.size(), raritySlots.length - 1)], addBtn);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回管理界面"))
                .build();
        inventory.setItem(45, back);

        player.openInventory(inventory);
    }

    /**
     * 打开音效编辑界面
     */
    public void openSoundEditGui(Player player, Crate crate) {
        // 简化版本，暂时只显示提示
        plugin.getLanguageManager().send(player, "admin-feature-coming-soon");
    }

    /**
     * 打开基本设置编辑界面
     */
    public void openBasicEditGui(Player player, Crate crate) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_CRATE_EDIT, crate),
                27,
                MessageUtil.parse("<!i><dark_gray>基本设置")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("edit_basic", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回宝箱编辑界面"))
                .build();
        inventory.setItem(18, back);

        // 编辑名称
        ItemStack name = new ItemBuilder(Material.NAME_TAG)
                .name("<!i><yellow>编辑名称")
                .lore(List.of(
                        "<!i><gray>当前: " + crate.getName(),
                        "",
                        "<!i><yellow>点击修改"
                ))
                .build();
        inventory.setItem(11, name);

        // 编辑方块
        ItemStack block = new ItemBuilder(crate.getBlockMaterial())
                .name("<!i><aqua>编辑方块")
                .lore(List.of(
                        "<!i><gray>当前: " + crate.getBlockMaterial().name(),
                        "",
                        "<!i><yellow>手持方块点击设置"
                ))
                .build();
        inventory.setItem(13, block);

        // 编辑动画时长
        ItemStack duration = new ItemBuilder(Material.CLOCK)
                .name("<!i><gold>动画时长: " + crate.getAnimationDuration() + "秒")
                .lore(List.of(
                        "<!i><gray>抽奖动画持续时间",
                        "",
                        "<!i><yellow>左键 +1秒",
                        "<!i><yellow>右键 -1秒",
                        "<!i><yellow>Shift+左键 +5秒",
                        "<!i><yellow>Shift+右键 -5秒"
                ))
                .build();
        inventory.setItem(15, duration);

        player.openInventory(inventory);
    }

    /**
     * 打开多连抽编辑界面
     */
    public void openMultiOpenEditGui(Player player, Crate crate) {
        Inventory inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ADMIN_CRATE_EDIT, crate),
                27,
                MessageUtil.parse("<!i><dark_gray>多连抽设置")
        );

        CrateGuiHolder holder = (CrateGuiHolder) inventory.getHolder();
        holder.setData("edit_multi_open", true);

        // 填充背景
        ItemStack fill = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, fill);
        }

        // 返回按钮
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("<!i><red>返回")
                .lore(List.of("<!i><gray>返回宝箱编辑界面"))
                .build();
        inventory.setItem(18, back);

        // 启用/禁用
        ItemStack toggle = new ItemBuilder(crate.isMultiOpenEnabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(crate.isMultiOpenEnabled() ? "<!i><green>多连抽已启用" : "<!i><red>多连抽已禁用")
                .lore(List.of("<!i><yellow>点击切换"))
                .build();
        inventory.setItem(11, toggle);

        // 最大数量
        ItemStack max = new ItemBuilder(Material.CHEST_MINECART)
                .name("<!i><yellow>最大数量: " + crate.getMultiOpenMax())
                .lore(List.of(
                        "<!i><gray>一次最多可以开启的数量",
                        "",
                        "<!i><yellow>左键 +1",
                        "<!i><yellow>右键 -1",
                        "<!i><yellow>Shift+左键 +5",
                        "<!i><yellow>Shift+右键 -5"
                ))
                .build();
        inventory.setItem(15, max);

        player.openInventory(inventory);
    }

    // ==================== 内部类 ====================

    /**
     * 输入会话
     */
    public static class InputSession {
        private final String type;
        private final Object data;
        private final InputCallback callback;

        public InputSession(String type, Object data, InputCallback callback) {
            this.type = type;
            this.data = data;
            this.callback = callback;
        }

        public String getType() { return type; }
        public Object getData() { return data; }
        public InputCallback getCallback() { return callback; }
    }

    /**
     * 输入回调接口
     */
    @FunctionalInterface
    public interface InputCallback {
        void onInput(Player player, String input, Object data);
    }
}
