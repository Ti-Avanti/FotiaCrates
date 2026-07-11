package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * GUI配置管理器
 * 支持Layout布局方式和传统slot方式
 */
public class GuiConfigManager {

    private static final List<String> DEFAULT_GUI_FILES = List.of(
            "preview.yml",
            "multi_open_result.yml",
            "animation.yml",
            "history.yml",
            "admin.yml",
            "admin_crate_edit.yml",
            "admin_keys.yml",
            "admin_key_edit.yml",
            "admin_crate_select.yml",
            "admin_animation_select.yml",
            "admin_pity_edit.yml",
            "admin_basic_edit.yml",
            "admin_multi_open_edit.yml",
            "admin_reward_edit.yml",
            "admin_reward_manager.yml",
            "admin_item_input.yml",
            "admin_reward_items.yml",
            "admin_alternative_reward_select.yml",
            "admin_rarity_manager.yml",
            "admin_particle_edit.yml",
            "admin_particle_stage_edit.yml",
            "admin_particle_material_select.yml"
    );

    private final FotiaCrates plugin;
    private final Map<String, GuiConfig> guiConfigs = new HashMap<>();

    public GuiConfigManager(FotiaCrates plugin) {
        this.plugin = plugin;
        loadGuiConfigs();
    }

    /**
     * 加载所有GUI配置
     */
    public void loadGuiConfigs() {
        guiConfigs.clear();

        File guisFolder = new File(plugin.getDataFolder(), "guis");
        if (!guisFolder.exists()) {
            guisFolder.mkdirs();
            // 保存默认GUI配置
        }

        for (String fileName : DEFAULT_GUI_FILES) {
            saveDefaultGui(fileName);
        }
        migrateRewardEditGui(guisFolder);
        migrateFeatureGuiConfigs(guisFolder);

        File[] files = guisFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                GuiConfig config = loadGuiConfig(id, file);
                if (config != null) {
                    guiConfigs.put(id, config);
                    plugin.getLogger().info("Loaded GUI config: " + id);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load GUI config " + id + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + guiConfigs.size() + " GUI configs.");
    }

    /**
     * 保存默认GUI配置
     */
    private void saveDefaultGui(String fileName) {
        File file = new File(plugin.getDataFolder(), "guis/" + fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource("guis/" + fileName, false);
            } catch (Exception e) {
                // 文件不存在于jar中，忽略
            }
        }
    }

    private void migrateRewardEditGui(File guisFolder) {
        File file = new File(guisFolder, "admin_reward_edit.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        List<String> displayIconLore = new ArrayList<>(config.getStringList("items.display_icon.lore"));
        if (!containsLine(displayIconLore, "{auto_icon_status}")) {
            int insertIndex = findFirstBlank(displayIconLore);
            displayIconLore.add(insertIndex >= 0 ? insertIndex : displayIconLore.size(),
                    "<!i><gray>自动状态: <!i><white>{auto_icon_status}");
            changed = true;
        }
        if (replaceLine(displayIconLore, "<!i><yellow>点击设置预览图标",
                List.of("<!i><yellow>左键设置预览图标", "<!i><green>右键恢复自动同步第一个奖励物品"))) {
            changed = true;
        } else if (!containsLine(displayIconLore, "右键恢复自动同步第一个奖励物品")) {
            displayIconLore.add("<!i><green>右键恢复自动同步第一个奖励物品");
            changed = true;
        }
        if (changed) {
            config.set("items.display_icon.lore", displayIconLore);
        }

        List<String> rewardItemsLore = new ArrayList<>(config.getStringList("items.reward_items.lore"));
        if (!containsLine(rewardItemsLore, "保存后可作为默认图标和名称来源")) {
            int insertIndex = findFirstBlank(rewardItemsLore);
            rewardItemsLore.add(insertIndex >= 0 ? insertIndex : rewardItemsLore.size(),
                    "<!i><gray>保存后可作为默认图标和名称来源");
            config.set("items.reward_items.lore", rewardItemsLore);
            changed = true;
        }

        List<String> displayNameLore = new ArrayList<>(config.getStringList("items.display_name.lore"));
        if (!containsLine(displayNameLore, "{auto_name_status}")) {
            int insertIndex = findFirstBlank(displayNameLore);
            displayNameLore.add(insertIndex >= 0 ? insertIndex : displayNameLore.size(),
                    "<!i><gray>自动状态: <!i><white>{auto_name_status}");
            changed = true;
        }
        if (replaceLine(displayNameLore, "<!i><yellow>点击修改",
                List.of("<!i><yellow>左键修改", "<!i><green>右键恢复自动同步第一个奖励物品"))) {
            changed = true;
        } else if (!containsLine(displayNameLore, "右键恢复自动同步第一个奖励物品")) {
            displayNameLore.add("<!i><green>右键恢复自动同步第一个奖励物品");
            changed = true;
        }
        if (changed) {
            config.set("items.display_name.lore", displayNameLore);
        }

        if (!changed) {
            return;
        }

        try {
            config.save(file);
            plugin.getLogger().info("Migrated GUI config: admin_reward_edit");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to migrate admin_reward_edit GUI config: " + e.getMessage());
        }
    }

    private void migrateFeatureGuiConfigs(File guisFolder) {
        migratePityEarlyResetGui(guisFolder);
        migrateMultiOpenAnimationGui(guisFolder);
    }

    private void migratePityEarlyResetGui(File guisFolder) {
        File file = new File(guisFolder, "admin_pity_edit.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("items.early_reset")) {
            return;
        }

        config.set("items.early_reset.slot", 6);
        config.set("items.early_reset.material", "CLOCK");
        config.set("items.early_reset.name", "<!i><aqua>提前出货重置: {pity_early_reset}");
        config.set("items.early_reset.lore", List.of(
                "<!i><gray>提前获得达到保底稀有度的奖励时",
                "<!i><gray>是否立即清空当前保底计数",
                "",
                "<!i><yellow>点击切换"
        ));
        config.set("items.early_reset.action", "toggle_pity_early_reset");
        saveMigratedGui(file, config, "admin_pity_edit");
    }

    private void migrateMultiOpenAnimationGui(File guisFolder) {
        File file = new File(guisFolder, "admin_multi_open_edit.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("items.animation")) {
            return;
        }

        config.set("items.animation.slot", 13);
        config.set("items.animation.material", "FIREWORK_ROCKET");
        config.set("items.animation.name", "<!i><aqua>十连首抽动画: {multi_open_animation_enabled}");
        config.set("items.animation.lore", List.of(
                "<!i><gray>开启后只播放第一抽动画",
                "<!i><gray>随后立即展示全部抽奖结果",
                "",
                "<!i><yellow>点击切换"
        ));
        config.set("items.animation.action", "toggle_multi_open_animation");
        saveMigratedGui(file, config, "admin_multi_open_edit");
    }

    private void saveMigratedGui(File file, YamlConfiguration config, String guiId) {
        try {
            config.save(file);
            plugin.getLogger().info("Migrated GUI config: " + guiId);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to migrate GUI config " + guiId + ": " + e.getMessage());
        }
    }

    private boolean containsLine(List<String> lines, String needle) {
        for (String line : lines) {
            if (line != null && line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int findFirstBlank(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private boolean replaceLine(List<String> lines, String target, List<String> replacement) {
        for (int i = 0; i < lines.size(); i++) {
            if (Objects.equals(lines.get(i), target)) {
                lines.remove(i);
                lines.addAll(i, replacement);
                return true;
            }
        }
        return false;
    }

    /**
     * 从文件加载GUI配置
     */
    private GuiConfig loadGuiConfig(String id, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String title = config.getString("title", "<!i><dark_gray>GUI");

        // 检查是否使用Layout布局
        List<String> layout = config.getStringList("layout");

        Map<Integer, GuiItem> items = new HashMap<>();
        List<Integer> contentSlots = new ArrayList<>();
        int size;

        // 填充设置
        boolean fillEnabled = config.getBoolean("fill.enabled", false);
        Material fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
        String fillName = " ";
        if (fillEnabled) {
            try {
                fillMaterial = Material.valueOf(config.getString("fill.material", "GRAY_STAINED_GLASS_PANE").toUpperCase());
            } catch (Exception e) {
                fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
            }
            fillName = config.getString("fill.name", " ");
        }

        if (!layout.isEmpty()) {
            // 使用Layout布局方式
            size = layout.size() * 9;

            // 解析Layout
            Map<Character, List<Integer>> charSlots = new HashMap<>();
            for (int row = 0; row < layout.size(); row++) {
                String rowStr = layout.get(row);
                for (int col = 0; col < Math.min(rowStr.length(), 9); col++) {
                    char c = rowStr.charAt(col);
                    int slot = row * 9 + col;
                    charSlots.computeIfAbsent(c, k -> new ArrayList<>()).add(slot);
                }
            }

            // 加载Icons配置
            ConfigurationSection iconsSection = config.getConfigurationSection("icons");
            if (iconsSection != null) {
                for (String key : iconsSection.getKeys(false)) {
                    if (key.length() != 1) continue;
                    char c = key.charAt(0);
                    List<Integer> slots = charSlots.get(c);
                    if (slots == null || slots.isEmpty()) continue;

                    ConfigurationSection iconSection = iconsSection.getConfigurationSection(key);
                    if (iconSection == null) continue;

                    // 检查是否是内容槽位标记
                    if (iconSection.getBoolean("content", false)) {
                        contentSlots.addAll(slots);
                        continue;
                    }

                    // 解析display部分
                    ConfigurationSection displaySection = iconSection.getConfigurationSection("display");
                    if (displaySection == null) continue;

                    Material material;
                    try {
                        material = Material.valueOf(displaySection.getString("material", "STONE").toUpperCase());
                    } catch (Exception e) {
                        material = Material.STONE;
                    }
                    String name = displaySection.getString("name", "");
                    List<String> lore = displaySection.getStringList("lore");
                    int customModelData = displaySection.getInt("custom-model-data", 0);
                    String itemModel = displaySection.getString("item_model", "");
                    boolean glow = displaySection.getBoolean("glow", false);

                    // 解析actions部分
                    String action = "";
                    String actionValue = "";
                    ConfigurationSection actionsSection = iconSection.getConfigurationSection("actions");
                    if (actionsSection != null) {
                        // 支持多种点击类型
                        for (String clickType : actionsSection.getKeys(false)) {
                            if (clickType.contains("left") || clickType.equals("all")) {
                                Object actionObj = actionsSection.get(clickType);
                                if (actionObj instanceof String) {
                                    action = parseAction((String) actionObj);
                                    actionValue = parseActionValue((String) actionObj);
                                } else if (actionObj instanceof ConfigurationSection) {
                                    ConfigurationSection clickSection = (ConfigurationSection) actionObj;
                                    List<String> actionsList = clickSection.getStringList("actions");
                                    if (!actionsList.isEmpty()) {
                                        for (String act : actionsList) {
                                            if (act.startsWith("action:")) {
                                                action = act.substring(7).trim();
                                            }
                                        }
                                    }
                                    action = clickSection.getString("action", action);
                                    actionValue = clickSection.getString("action-value", actionValue);
                                }
                                break;
                            }
                        }
                        // 直接读取action字段
                        if (action.isEmpty()) {
                            action = actionsSection.getString("action", "");
                            actionValue = actionsSection.getString("action-value", "");
                        }
                    }
                    // 也支持直接在icon下配置action
                    if (action.isEmpty()) {
                        action = iconSection.getString("action", "");
                        actionValue = iconSection.getString("action-value", "");
                    }

                    GuiItem guiItem = new GuiItem(slots.get(0), material, name, lore, customModelData, glow, action, actionValue);
                    guiItem.setItemModel(itemModel);

                    // 为所有匹配的槽位添加物品
                    for (int slot : slots) {
                        items.put(slot, guiItem);
                    }
                }
            }
        } else {
            // 使用传统slot方式
            size = config.getInt("size", 54);

            // 加载物品配置
            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                    if (itemSection == null) continue;

                    int slot = itemSection.getInt("slot", -1);
                    if (slot < 0 || slot >= size) continue;

                    Material material;
                    try {
                        material = Material.valueOf(itemSection.getString("material", "STONE").toUpperCase());
                    } catch (Exception e) {
                        material = Material.STONE;
                    }
                    String name = itemSection.getString("name", "");
                    List<String> lore = itemSection.getStringList("lore");
                    int customModelData = itemSection.getInt("custom-model-data", 0);
                    boolean glow = itemSection.getBoolean("glow", false);
                    String action = itemSection.getString("action", "");
                    String actionValue = itemSection.getString("action-value", "");

                    GuiItem guiItem = new GuiItem(slot, material, name, lore, customModelData, glow, action, actionValue);
                    items.put(slot, guiItem);
                }
            }

            // 加载内容槽位
            contentSlots = config.getIntegerList("content-slots");
        }

        if (contentSlots.isEmpty()) {
            // 默认内容槽位（中间区域）
            contentSlots = generateDefaultContentSlots(size);
        }

        // 读取动画槽位配置
        List<Integer> animationSlots = config.getIntegerList("animation-slots");
        if (animationSlots.isEmpty()) {
            // 默认动画槽位
            animationSlots = List.of(10, 11, 12, 13, 14, 15, 16);
        }

        // 读取中心槽位配置
        int centerSlot = config.getInt("center-slot", -1);
        if (centerSlot < 0) {
            // 默认使用动画槽位的中间位置
            centerSlot = animationSlots.get(animationSlots.size() / 2);
        }

        return new GuiConfig(id, title, size, fillEnabled, fillMaterial, fillName, items, contentSlots, animationSlots, centerSlot);
    }

    /**
     * 解析action字符串
     */
    private String parseAction(String actionStr) {
        if (actionStr.startsWith("menu:")) return "open_menu";
        if (actionStr.startsWith("close")) return "close";
        if (actionStr.startsWith("sound:")) return "sound";
        return actionStr;
    }

    /**
     * 解析action值
     */
    private String parseActionValue(String actionStr) {
        if (actionStr.contains(":")) {
            return actionStr.substring(actionStr.indexOf(":") + 1).trim();
        }
        return "";
    }

    /**
     * 生成默认内容槽位
     */
    private List<Integer> generateDefaultContentSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        int rows = size / 9;

        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }

        return slots;
    }

    /**
     * 获取GUI配置
     */
    public GuiConfig getGuiConfig(String id) {
        return guiConfigs.get(id);
    }

    /**
     * 获取所有GUI配置
     */
    public Collection<GuiConfig> getAllGuiConfigs() {
        return guiConfigs.values();
    }

    /**
     * 重新加载GUI配置
     */
    public void reload() {
        loadGuiConfigs();
    }
}
