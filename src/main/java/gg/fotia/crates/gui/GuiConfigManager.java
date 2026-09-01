package gg.fotia.crates.gui;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationTemplate;
import gg.fotia.crates.animation.AnimationTemplateSelection;
import gg.fotia.crates.animation.AnimationType;
import gg.fotia.crates.animation.AnimationSlotResolver;
import gg.fotia.crates.animation.CardAnimationSettings;
import gg.fotia.crates.animation.MeteorAnimationSettings;
import gg.fotia.crates.animation.OrbitalAnimationSettings;
import gg.fotia.crates.animation.VoidRiftAnimationSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
            "admin_animation_template_select.yml",
            "admin_pity_edit.yml",
            "admin_basic_edit.yml",
            "admin_multi_open_edit.yml",
            "admin_unique_draw_edit.yml",
            "admin_unique_icon_material_select.yml",
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
    private final Map<String, AnimationTemplate> animationTemplates = new LinkedHashMap<>();

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
        prepareAnimationTemplateFiles(guisFolder);

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

        loadAnimationTemplates(new File(guisFolder, "animations"));

        plugin.getLogger().info("Loaded " + guiConfigs.size() + " GUI configs.");
        plugin.getLogger().info("Loaded " + animationTemplates.size() + " animation templates.");
    }

    private void prepareAnimationTemplateFiles(File guisFolder) {
        File templatesFolder = new File(guisFolder, "animations");
        if (!templatesFolder.exists() && !templatesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create animation template folder.");
            return;
        }

        File defaultTemplate = new File(templatesFolder, "default.yml");
        if (!defaultTemplate.exists()) {
            File legacyAnimation = new File(guisFolder, "animation.yml");
            try {
                if (legacyAnimation.exists()) {
                    Files.copy(legacyAnimation.toPath(), defaultTemplate.toPath());
                } else {
                    plugin.saveResource("guis/animations/default.yml", false);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to create default animation template: "
                        + exception.getMessage());
            }
        }
        ensureDefaultAnimationTemplateMetadata(defaultTemplate);

        for (String fileName : List.of(
                "golden.yml", "card-reveal.yml", "orbital.yml",
                "void-rift.yml", "meteor.yml")) {
            File templateFile = new File(templatesFolder, fileName);
            if (!templateFile.exists()) {
                try {
                    plugin.saveResource("guis/animations/" + fileName, false);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Failed to save animation template " + fileName
                            + ": " + exception.getMessage());
                }
            }
        }
        migrateBuiltInAnimationTemplates(templatesFolder);
    }

    private void migrateBuiltInAnimationTemplates(File templatesFolder) {
        migrateCardRevealTemplate(new File(templatesFolder, "card-reveal.yml"));
    }

    private void migrateCardRevealTemplate(File file) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        List<String> oldLayout = List.of(
                "#########", "#A#A#A###", "#A##A##A#", "#A#A#A###", "#########");
        List<Integer> oldSlots = List.of(10, 12, 14, 19, 22, 25, 28, 30, 32);
        if (oldLayout.equals(config.getStringList("layout"))
                && oldSlots.equals(config.getIntegerList("animation-slots"))) {
            config.set("layout", List.of(
                    "####S####", "#AAAAAAA#", "#AAAAAAA#", "#AAAAAAA#", "#########"));
            config.set("icons.S.display.material", "NETHER_STAR");
            config.set("icons.S.display.name", "<!i><gold>奖池展示");
            config.set("icons.S.display.lore",
                    List.of("<!i><gray>展示结束后点击牌背揭晓奖励"));
            config.set("animation-slots", List.of(
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34));
            changed = true;
        }
        if ("<!i><gold>奖池展示".equals(config.getString("icons.S.display.name"))) {
            config.set("icons.S.display.name", "<!i><gold>奖池闪烁");
            config.set("icons.S.display.lore",
                    List.of("<!i><gray>卡牌闪烁结束后选择一张揭晓奖励"));
            changed = true;
        }
        int legacyShowcaseTicks = config.getInt("card-reveal.showcase-page-ticks", 20);
        int legacyShuffleTicks = config.getInt("card-reveal.shuffle-ticks", 30);
        String legacyFlickerStatus = config.getString("card-reveal.status.showcase-name",
                "<!i><gold>奖池闪烁中...");
        String legacyCoverStatus = config.getString("card-reveal.status.shuffle-name",
                "<!i><light_purple>秘匣封牌中...");
        boolean legacyBuiltInDefaults = legacyShowcaseTicks == 20 && legacyShuffleTicks == 30;
        if ("<!i><gold>奖池展示".equals(legacyFlickerStatus)) {
            legacyFlickerStatus = "<!i><gold>奖池闪烁中...";
        }
        if ("<!i><light_purple>秘匣洗牌中...".equals(legacyCoverStatus)) {
            legacyCoverStatus = "<!i><light_purple>秘匣封牌中...";
        }
        boolean previousMigrationDefaults = config.getInt(
                "card-reveal.flicker-min-ticks", -1) == 50
                && config.getInt("card-reveal.cover-delay-ticks", -1) == 30
                && "<!i><gold>奖池展示".equals(config.getString(
                "card-reveal.status.flicker-name"))
                && "<!i><light_purple>秘匣洗牌中...".equals(config.getString(
                "card-reveal.status.cover-name"));
        if (previousMigrationDefaults) {
            config.set("card-reveal.flicker-min-ticks", 40);
            config.set("card-reveal.cover-delay-ticks", 10);
            config.set("card-reveal.status.flicker-name", "<!i><gold>奖池闪烁中...");
            config.set("card-reveal.status.cover-name", "<!i><light_purple>秘匣封牌中...");
            changed = true;
        }
        changed |= setIfMissing(config, "card-reveal.card-count", 15);
        changed |= setIfMissing(config, "card-reveal.flicker-interval-ticks", 3);
        changed |= setIfMissing(config, "card-reveal.flicker-min-cycles", 1);
        changed |= setIfMissing(config, "card-reveal.flicker-min-ticks",
                legacyBuiltInDefaults ? 40 : Math.max(10, legacyShowcaseTicks + legacyShuffleTicks));
        changed |= setIfMissing(config, "card-reveal.cover-delay-ticks",
                legacyBuiltInDefaults ? 10 : Math.max(1, Math.min(100, legacyShuffleTicks)));
        changed |= setIfMissing(config, "card-reveal.selection-timeout-ticks", 300);
        changed |= setIfMissing(config, "card-reveal.page-transition-ticks", 20);
        changed |= setIfMissing(config, "card-reveal.result-hold-ticks", 40);
        changed |= setIfMissing(config, "card-reveal.status-slot", 4);
        changed |= setIfMissing(config, "card-reveal.status.flicker-name",
                legacyFlickerStatus);
        changed |= setIfMissing(config, "card-reveal.status.cover-name",
                legacyCoverStatus);
        changed |= setIfMissing(config, "card-reveal.status.select-name",
                "<!i><green>请选择卡牌 <!i><gray>({revealed}/{total})");
        changed |= setIfMissing(config, "card-reveal.status.complete-name",
                "<!i><gold>全部奖励已揭晓");
        changed |= removeIfPresent(config, "card-reveal.showcase-page-ticks");
        changed |= removeIfPresent(config, "card-reveal.shuffle-ticks");
        changed |= removeIfPresent(config, "card-reveal.status.showcase-name");
        changed |= removeIfPresent(config, "card-reveal.status.shuffle-name");
        if (changed) {
            saveMigratedGui(file, config, "animations/card-reveal");
        }
    }

    private boolean setIfMissing(YamlConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private boolean removeIfPresent(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return false;
        }
        config.set(path, null);
        return true;
    }

    private void ensureDefaultAnimationTemplateMetadata(File defaultTemplate) {
        if (!defaultTemplate.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(defaultTemplate);
        boolean changed = false;
        if (!config.contains("display-name")) {
            config.set("display-name", "<!i><yellow>默认轮盘");
            changed = true;
        }
        if (!config.contains("selector")) {
            config.set("selector.material", "CLOCK");
            config.set("selector.name", "<!i><yellow>{template}");
            config.set("selector.lore", List.of(
                    "<!i><gray>模板ID: <!i><white>{template_id}",
                    "",
                    "{selected_line}"
            ));
            changed = true;
        }
        if (changed) {
            saveMigratedGui(defaultTemplate, config, "animations/default");
        }
    }

    private void loadAnimationTemplates(File templatesFolder) {
        animationTemplates.clear();
        File[] files = templatesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            String id = AnimationTemplateSelection.normalize(
                    file.getName().substring(0, file.getName().length() - 4));
            try {
                YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
                GuiConfig guiConfig = loadGuiConfig("animation_template_" + id, file);
                Material selectorMaterial = Material.matchMaterial(
                        raw.getString("selector.material", "CLOCK"));
                if (selectorMaterial == null || !selectorMaterial.isItem()) {
                    selectorMaterial = Material.CLOCK;
                }
                AnimationType animationType = parseAnimationType(
                        raw.getString("animation-type", "ROULETTE"));
                if (animationType == AnimationType.TRIPLE_REEL) {
                    plugin.getLogger().warning("Ignoring removed TRIPLE_REEL animation template: "
                            + id);
                    continue;
                }
                warnInvalidAnimationSlots(id, animationType, guiConfig);
                AnimationTemplate template = new AnimationTemplate(
                        id,
                        animationType,
                        raw.getString("display-name", id),
                        selectorMaterial,
                        raw.getString("selector.name", "<!i><yellow>{template}"),
                        raw.getStringList("selector.lore"),
                        Math.max(0, raw.getInt("selector.custom-model-data", 0)),
                        raw.getString("selector.item-model",
                                raw.getString("selector.item_model", "")),
                        raw.getBoolean("selector.glow", false),
                        guiConfig,
                        CardAnimationSettings.from(
                                raw.getConfigurationSection("card-reveal")),
                        OrbitalAnimationSettings.from(
                                raw.getConfigurationSection("orbit")),
                        VoidRiftAnimationSettings.from(
                                raw.getConfigurationSection("void-rift")),
                        MeteorAnimationSettings.from(
                                raw.getConfigurationSection("meteor"))
                );
                animationTemplates.put(id, template);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to load animation template " + id
                        + ": " + exception.getMessage());
            }
        }
    }

    private void warnInvalidAnimationSlots(String templateId, AnimationType animationType,
                                           GuiConfig guiConfig) {
        AnimationSlotResolver.Resolution resolution;
        if (animationType == AnimationType.CARD_REVEAL) {
            resolution = AnimationSlotResolver.forCards(
                    guiConfig.getAnimationSlots(), guiConfig.getSize(), List.of(10));
        } else {
            return;
        }
        if (resolution.usedFallback()) {
            plugin.getLogger().warning("Animation template " + templateId + " "
                    + resolution.problem() + "; using built-in fallback slots.");
        }
    }

    private AnimationType parseAnimationType(String configuredType) {
        try {
            return AnimationType.valueOf(configuredType == null
                    ? "ROULETTE"
                    : configuredType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AnimationType.ROULETTE;
        }
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

        List<String> backLore = new ArrayList<>(config.getStringList("items.back.lore"));
        if (replaceLine(backLore, "<!i><gray>返回宝箱编辑界面",
                List.of("<!i><gray>返回{return_gui}"))) {
            config.set("items.back.lore", backLore);
            changed = true;
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
        migratePreviewRewardDisplayGui(guisFolder);
        migratePreviewMultiOpenHintGui(guisFolder);
        migratePreviewDisplayModeGui(guisFolder);
        migrateUniqueDrawShortcutGui(guisFolder);
        migrateHistoryBackGui(guisFolder);
        migrateAnimationTemplateEditorGui(guisFolder);
        migrateAnimationTypeSelectorGui(guisFolder);
        migratePaginationButtonDisplays(guisFolder);
        migrateAdminLayoutWidth(guisFolder);
    }

    private void migrateAdminLayoutWidth(File guisFolder) {
        File file = new File(guisFolder, "admin.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> layout = new ArrayList<>(config.getStringList("layout"));
        if (layout.size() < 6 || !"#N#K#S#R#X".equals(layout.get(5))) {
            return;
        }
        layout.set(5, "#N#K#S#RX");
        config.set("layout", layout);
        saveMigratedGui(file, config, "admin");
    }

    private void migrateAnimationTypeSelectorGui(File guisFolder) {
        File file = new File(guisFolder, "admin_animation_select.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        boolean legacyDefaultLayout = config.getInt("items.roulette.slot", -1) == 13
                && config.getInt("items.instant.slot", -1) == 15
                && config.getInt("items.physical_toggle.slot", -1) == 28
                && config.getInt("items.duration.slot", -1) == 32;
        if (legacyDefaultLayout) {
            config.set("items.roulette.slot", 19);
            config.set("items.instant.slot", 28);
            config.set("items.physical_toggle.slot", 16);
            config.set("items.duration.slot", 14);
            changed = true;
        }
        List<String> rouletteLore = new ArrayList<>(
                config.getStringList("items.roulette.lore"));
        if (!containsLine(rouletteLore, "{animation_roulette_state}")) {
            rouletteLore.removeIf(line -> line != null && line.contains("点击选择"));
            rouletteLore.add("{animation_roulette_state}");
            config.set("items.roulette.lore", rouletteLore);
            changed = true;
        }
        List<String> instantLore = new ArrayList<>(
                config.getStringList("items.instant.lore"));
        if (!containsLine(instantLore, "{animation_instant_state}")) {
            instantLore.removeIf(line -> line != null && line.contains("点击选择"));
            instantLore.add("{animation_instant_state}");
            config.set("items.instant.lore", instantLore);
            changed = true;
        }

        changed |= removeAnimationItem(config, "triple_reel", "select_triple_reel");
        changed |= moveAnimationItem(config, "info", 34, 39);
        changed |= moveAnimationItem(config, "physical_height", 30, 34);
        changed |= moveAnimationItem(config, "instant", 28, 30);
        changed |= moveAnimationItem(config, "card_reveal", 23, 21);
        changed |= moveAnimationItem(config, "orbital_convergence", 25, 23);
        changed |= replaceKnownLoreLine(config, "items.card_reveal.lore",
                "<!i><gray>九宫格卡牌逐张揭示",
                "<!i><gray>随机卡牌闪烁奖池后由玩家选择");
        changed |= replaceKnownLoreLine(config, "items.info.lore",
                "<!i><gray>星轨动画使用玩家独立展示实体",
                "<!i><gray>世界动画使用玩家独立展示实体");

        changed |= addAnimationTypeItem(config, "card_reveal", 21, "PAPER",
                "<!i><light_purple>秘匣翻牌", "<!i><gray>随机卡牌闪烁奖池后由玩家选择",
                "{animation_card_reveal_state}", "select_card_reveal");
        changed |= addAnimationTypeItem(config, "orbital_convergence", 23, "END_CRYSTAL",
                "<!i><aqua>星轨汇聚", "<!i><gray>奖励围绕宝箱旋转并收束",
                "{animation_orbital_state}", "select_orbital_convergence");
        changed |= addAnimationTypeItem(config, "void_rift", 25, "ENDER_EYE",
                "<!i><dark_aqua>虚空裂隙", "<!i><gray>候选奖励被裂隙吸入，中奖物品降临",
                "{animation_void_rift_state}", "select_void_rift");
        changed |= addAnimationTypeItem(config, "meteor_judgment", 28, "FIRE_CHARGE",
                "<!i><gold>流星裁决", "<!i><gray>诱饵流星落空，金色流星揭晓奖励",
                "{animation_meteor_state}", "select_meteor_judgment");
        if (!config.contains("items.preview")) {
            int slot = findAvailableAnimationSlot(config, 32);
            if (slot >= 0) {
                config.set("items.preview.slot", slot);
                config.set("items.preview.material", "SPYGLASS");
                config.set("items.preview.name", "<!i><green>预览当前动画");
                config.set("items.preview.lore", List.of(
                        "<!i><gray>不扣钥匙、不发奖励、不写历史",
                        "",
                        "<!i><yellow>点击播放"));
                config.set("items.preview.action", "preview_animation");
                changed = true;
            }
        }
        if (changed) {
            saveMigratedGui(file, config, "admin_animation_select");
        }
    }

    private boolean addAnimationTypeItem(YamlConfiguration config, String key, int preferredSlot,
                                         String material, String name, String description,
                                         String stateLine, String action) {
        if (config.contains("items." + key)) {
            return false;
        }
        int slot = findAvailableAnimationSlot(config, preferredSlot);
        if (slot < 0) {
            return false;
        }
        String path = "items." + key;
        config.set(path + ".slot", slot);
        config.set(path + ".material", material);
        config.set(path + ".name", name);
        config.set(path + ".lore", List.of(description, "", stateLine));
        config.set(path + ".action", action);
        return true;
    }

    private boolean removeAnimationItem(YamlConfiguration config, String key, String action) {
        String path = "items." + key;
        if (!action.equalsIgnoreCase(config.getString(path + ".action", ""))) {
            return false;
        }
        config.set(path, null);
        return true;
    }

    private boolean replaceKnownLoreLine(YamlConfiguration config, String path,
                                         String oldLine, String newLine) {
        List<String> lore = new ArrayList<>(config.getStringList(path));
        int index = lore.indexOf(oldLine);
        if (index < 0) {
            return false;
        }
        lore.set(index, newLine);
        config.set(path, lore);
        return true;
    }

    private boolean moveAnimationItem(YamlConfiguration config, String key,
                                      int oldSlot, int newSlot) {
        String path = "items." + key;
        if (!config.contains(path) || config.getInt(path + ".slot", -1) != oldSlot
                || isAnimationSlotOccupied(config, newSlot, key)) {
            return false;
        }
        config.set(path + ".slot", newSlot);
        return true;
    }

    private boolean isAnimationSlotOccupied(YamlConfiguration config, int slot, String ignoredKey) {
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) {
            return false;
        }
        for (String key : items.getKeys(false)) {
            if (key.equals(ignoredKey)) {
                continue;
            }
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null && item.getInt("slot", -1) == slot) {
                return true;
            }
        }
        return false;
    }

    private int findAvailableAnimationSlot(YamlConfiguration config, int preferredSlot) {
        Set<Integer> occupied = new HashSet<>();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item != null && item.contains("slot")) {
                    occupied.add(item.getInt("slot"));
                }
            }
        }
        if (!occupied.contains(preferredSlot)) {
            return preferredSlot;
        }
        for (int candidate : List.of(
                19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43, 11, 13, 15)) {
            if (!occupied.contains(candidate)) {
                return candidate;
            }
        }
        return -1;
    }

    private void migratePaginationButtonDisplays(File guisFolder) {
        migratePaginationButtonDisplay(guisFolder, "preview.yml",
                "icons.<.display.unavailable", "icons.>.display.unavailable");
        migratePaginationButtonDisplay(guisFolder, "history.yml",
                "icons.<.display.unavailable", "icons.>.display.unavailable");
        migratePaginationButtonDisplay(guisFolder, "admin_reward_manager.yml",
                "items.previous.unavailable", "items.next.unavailable");
        migratePaginationButtonDisplay(guisFolder, "admin_particle_material_select.yml",
                "items.previous.unavailable", "items.next.unavailable");
        migratePaginationButtonDisplay(guisFolder, "admin_unique_icon_material_select.yml",
                "items.previous.unavailable", "items.next.unavailable");
    }

    private void migratePaginationButtonDisplay(File guisFolder, String fileName,
                                                String previousPath, String nextPath) {
        File file = new File(guisFolder, fileName);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = addUnavailableDisplay(config, previousPath, true);
        changed |= addUnavailableDisplay(config, nextPath, false);
        if (changed) {
            saveMigratedGui(file, config, fileName.substring(0, fileName.length() - 4));
        }
    }

    private boolean addUnavailableDisplay(YamlConfiguration config, String path, boolean previous) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path + ".material", "YELLOW_STAINED_GLASS_PANE");
        config.set(path + ".name", previous
                ? "<!i><gray>没有上一页"
                : "<!i><gray>没有下一页");
        config.set(path + ".lore", List.of(previous
                ? "<!i><dark_gray>当前已经是第一页"
                : "<!i><dark_gray>当前已经是最后一页"));
        return true;
    }

    private void migrateAnimationTemplateEditorGui(File guisFolder) {
        File animationFile = new File(guisFolder, "admin_animation_select.yml");
        if (animationFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(animationFile);
            if (!config.contains("items.template")) {
                ConfigurationSection items = config.getConfigurationSection("items");
                boolean slotOccupied = items != null && items.getKeys(false).stream()
                        .map(items::getConfigurationSection)
                        .filter(Objects::nonNull)
                        .anyMatch(section -> section.getInt("slot", -1) == 12);
                if (slotOccupied) {
                    plugin.getLogger().warning("Could not add animation template button because slot 12 "
                            + "in admin_animation_select.yml is customized.");
                } else {
                    config.set("items.template.slot", 12);
                    config.set("items.template.material", "PAINTING");
                    config.set("items.template.name", "<!i><aqua>动画模板: {animation_template}");
                    config.set("items.template.lore", List.of(
                            "<!i><gray>每个抽奖箱可使用独立模板",
                            "<!i><gray>模板位于 guis/animations/",
                            "",
                            "<!i><yellow>点击选择"
                    ));
                    config.set("items.template.action", "open_animation_templates");
                    saveMigratedGui(animationFile, config, "admin_animation_select");
                }
            }
        }

        File crateEditorFile = new File(guisFolder, "admin_crate_edit.yml");
        if (!crateEditorFile.exists()) {
            return;
        }
        YamlConfiguration crateEditor = YamlConfiguration.loadConfiguration(crateEditorFile);
        List<String> lore = new ArrayList<>(crateEditor.getStringList("icons.A.display.lore"));
        if (containsLine(lore, "{animation_template}")) {
            return;
        }
        int typeLine = -1;
        for (int index = 0; index < lore.size(); index++) {
            if (lore.get(index) != null && lore.get(index).contains("{animation_type}")) {
                typeLine = index;
                break;
            }
        }
        lore.add(typeLine >= 0 ? typeLine + 1 : lore.size(),
                "<!i><yellow>模板: {animation_template}");
        crateEditor.set("icons.A.display.lore", lore);
        saveMigratedGui(crateEditorFile, crateEditor, "admin_crate_edit");
    }

    private void migratePreviewMultiOpenHintGui(File guisFolder) {
        File file = new File(guisFolder, "preview.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("multi-open-hint")) {
            return;
        }
        PreviewMultiOpenHintConfig defaults = PreviewMultiOpenHintConfig.defaults();
        config.set("multi-open-hint.available", defaults.available());
        config.set("multi-open-hint.unavailable", defaults.unavailable());
        saveMigratedGui(file, config, "preview");
    }

    private void migrateHistoryBackGui(File guisFolder) {
        File file = new File(guisFolder, "history.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> layout = new ArrayList<>(config.getStringList("layout"));
        if (config.contains("icons.B") || layout.size() < 6
                || !"<#P#I#N#X".equals(layout.get(5))) {
            return;
        }
        layout.set(5, "B#P#I#N#X");
        config.set("layout", layout);
        config.set("icons.B.display.material", "ARROW");
        config.set("icons.B.display.name", "<!i><yellow>返回");
        config.set("icons.B.display.lore", List.of("<!i><gray>返回上一界面"));
        config.set("icons.B.action", "back");
        saveMigratedGui(file, config, "history");
    }

    private void migrateUniqueDrawShortcutGui(File guisFolder) {
        File file = new File(guisFolder, "admin_crate_edit.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("icons.U")) {
            return;
        }

        List<String> layout = new ArrayList<>(config.getStringList("layout"));
        if (layout.isEmpty() || layout.get(0) == null || layout.get(0).length() != 9
                || layout.get(0).charAt(5) != '#') {
            plugin.getLogger().warning("Could not add the unique draw editor shortcut because slot 5 "
                    + "in admin_crate_edit.yml is already customized.");
            return;
        }

        String firstRow = layout.get(0);
        layout.set(0, firstRow.substring(0, 5) + 'U' + firstRow.substring(6));
        config.set("layout", layout);
        config.set("icons.U.display.material", "RECOVERY_COMPASS");
        config.set("icons.U.display.name", "<!i><aqua>不重复抽奖设置");
        config.set("icons.U.display.lore", List.of(
                "<!i><gray>配置玩家永久不重复获得奖励",
                "",
                "<!i><yellow>启用: {unique_draw_enabled}",
                "<!i><yellow>已获得图标替换: {unique_preview_replace}",
                "",
                "<!i><yellow>左键点击编辑"
        ));
        config.set("icons.U.action", "edit_unique_draw");
        saveMigratedGui(file, config, "admin_crate_edit");
    }

    private void migratePreviewRewardDisplayGui(File guisFolder) {
        File file = new File(guisFolder, "preview.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("reward-display")) {
            return;
        }

        RewardPreviewDisplayConfig defaults = RewardPreviewDisplayConfig.defaults();
        config.set("reward-display.append-item-lore", defaults.isAppendItemLore());
        config.set("reward-display.lore", defaults.getLore());
        config.set("reward-display.chance-lines.percentage", defaults.getPercentageLine());
        config.set("reward-display.chance-lines.weight", defaults.getWeightLine());
        config.set("reward-display.broadcast-line", defaults.getBroadcastLine());
        saveMigratedGui(file, config, "preview");
    }

    private void migratePreviewDisplayModeGui(File guisFolder) {
        File file = new File(guisFolder, "admin_crate_edit.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> lore = new ArrayList<>(config.getStringList("icons.M.display.lore"));
        boolean changed = false;
        if (!containsLine(lore, "{chance_mode_percentage}")) {
            lore.removeIf(line -> line != null && line.contains("显示概率: {show_chance}"));
            lore.add("<!i><gray>左键切换预览开关");
            lore.add("<!i><gray>右键切换数值显示");
            lore.add("");
            lore.add("{chance_mode_percentage}");
            lore.add("{chance_mode_weight}");
            lore.add("{chance_mode_hidden}");
            changed = true;
        }
        if (!containsLine(lore, "{sort_mode_config_order}")) {
            lore.add("");
            lore.add("<!i><gray>Shift+右键切换预览排序");
            lore.add("{sort_mode_config_order}");
            lore.add("{sort_mode_weight_desc}");
            lore.add("{sort_mode_weight_asc}");
            changed = true;
        }
        if (!changed) {
            return;
        }
        config.set("icons.M.display.lore", lore);
        saveMigratedGui(file, config, "admin_crate_edit");
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
            boolean changed = false;
            String currentName = config.getString("items.animation.name", "");
            if (currentName.contains("十连首抽动画")) {
                config.set("items.animation.name",
                        "<!i><aqua>多连抽动画: {multi_open_animation_enabled}");
                changed = true;
            }
            List<String> lore = config.getStringList("items.animation.lore");
            if (lore.stream().anyMatch(line -> line.contains("只播放第一抽动画"))) {
                config.set("items.animation.lore", List.of(
                        "<!i><gray>翻牌动画会逐张揭示整批奖励",
                        "<!i><gray>其他动画仅展示第一抽",
                        "<!i><gray>动画结束后显示全部结果",
                        "",
                        "<!i><yellow>点击切换"
                ));
                changed = true;
            }
            if (changed) {
                saveMigratedGui(file, config, "admin_multi_open_edit");
            }
            return;
        }

        config.set("items.animation.slot", 13);
        config.set("items.animation.material", "FIREWORK_ROCKET");
        config.set("items.animation.name", "<!i><aqua>多连抽动画: {multi_open_animation_enabled}");
        config.set("items.animation.lore", List.of(
                "<!i><gray>翻牌动画会逐张揭示整批奖励",
                "<!i><gray>其他动画仅展示第一抽",
                "<!i><gray>动画结束后显示全部结果",
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
        List<Integer> invalidLayoutRows = GuiLayoutValidator.invalidRows(layout);
        if (!invalidLayoutRows.isEmpty()) {
            plugin.getLogger().warning("GUI config " + id
                    + " has layout rows that are not 9 characters wide: "
                    + invalidLayoutRows.stream().map(row -> String.valueOf(row + 1)).toList());
        }

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

                    GuiItemDisplay display = GuiItemDisplayParser.parse(displaySection, null);
                    ConfigurationSection unavailableSection =
                            displaySection.getConfigurationSection("unavailable");
                    GuiItemDisplay unavailableDisplay = unavailableSection == null
                            ? null
                            : GuiItemDisplayParser.parse(unavailableSection, display);

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

                    GuiItem guiItem = new GuiItem(slots.get(0), display, unavailableDisplay,
                            action, actionValue);

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

                    GuiItemDisplay display = GuiItemDisplayParser.parse(itemSection, null);
                    ConfigurationSection unavailableSection =
                            itemSection.getConfigurationSection("unavailable");
                    GuiItemDisplay unavailableDisplay = unavailableSection == null
                            ? null
                            : GuiItemDisplayParser.parse(unavailableSection, display);
                    String action = itemSection.getString("action", "");
                    String actionValue = itemSection.getString("action-value", "");

                    GuiItem guiItem = new GuiItem(slot, display, unavailableDisplay, action, actionValue);
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

        RewardPreviewDisplayConfig rewardPreviewDisplay = loadRewardPreviewDisplay(config);
        PreviewMultiOpenHintConfig previewMultiOpenHint = loadPreviewMultiOpenHint(config);

        return new GuiConfig(id, title, size, fillEnabled, fillMaterial, fillName,
                items, contentSlots, animationSlots, centerSlot,
                rewardPreviewDisplay, previewMultiOpenHint);
    }

    private PreviewMultiOpenHintConfig loadPreviewMultiOpenHint(YamlConfiguration config) {
        PreviewMultiOpenHintConfig defaults = PreviewMultiOpenHintConfig.defaults();
        return new PreviewMultiOpenHintConfig(
                config.getString("multi-open-hint.available", defaults.available()),
                config.getString("multi-open-hint.unavailable", defaults.unavailable())
        );
    }

    private RewardPreviewDisplayConfig loadRewardPreviewDisplay(YamlConfiguration config) {
        RewardPreviewDisplayConfig defaults = RewardPreviewDisplayConfig.defaults();
        ConfigurationSection section = config.getConfigurationSection("reward-display");
        if (section == null) {
            return defaults;
        }

        List<String> lore = section.getStringList("lore");
        if (lore.isEmpty()) {
            lore = defaults.getLore();
        }
        return new RewardPreviewDisplayConfig(
                section.getBoolean("append-item-lore", defaults.isAppendItemLore()),
                lore,
                section.getString("chance-lines.percentage", defaults.getPercentageLine()),
                section.getString("chance-lines.weight", defaults.getWeightLine()),
                section.getString("broadcast-line", defaults.getBroadcastLine())
        );
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

    public AnimationTemplate getAnimationTemplate(String requestedId) {
        String resolved = AnimationTemplateSelection.resolve(requestedId, animationTemplates.keySet());
        return animationTemplates.get(resolved);
    }

    public AnimationTemplate getAnimationTemplate(String requestedId, AnimationType animationType) {
        Map<String, AnimationType> templateTypes = new LinkedHashMap<>();
        for (AnimationTemplate template : animationTemplates.values()) {
            templateTypes.put(template.id(), template.animationType());
        }
        String resolved = AnimationTemplateSelection.resolve(requestedId, templateTypes, animationType);
        return animationTemplates.get(resolved);
    }

    public List<AnimationTemplate> getAnimationTemplates() {
        return List.copyOf(animationTemplates.values());
    }

    public List<AnimationTemplate> getAnimationTemplates(AnimationType animationType) {
        if (animationType == null) {
            return List.of();
        }
        AnimationType family = animationType.templateFamily();
        return animationTemplates.values().stream()
                .filter(template -> template.animationType().templateFamily() == family)
                .toList();
    }

    public List<String> getAnimationTemplateIds() {
        return List.copyOf(animationTemplates.keySet());
    }

    /**
     * 重新加载GUI配置
     */
    public void reload() {
        loadGuiConfigs();
    }
}
