package gg.fotia.crates.gui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.ArrayList;

/** 只迁移旧版默认文案，保留自定义布局、显示模板和按钮。 */
final class RewardEditorGuiMigration {
    private RewardEditorGuiMigration() {
    }

    static boolean applyInput(ConfigurationSection section) {
        boolean changed = false;
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) changed |= applyInput(child);
        }
        if ("adjust_chance".equals(section.getString("action"))) {
            String name = section.getString("name", "");
            if (name.contains("{chance}%")) {
                section.set("name", name.replace("概率:", "权重:").replace("{chance}%", "{chance}"));
                List<String> lore = new ArrayList<>(section.getStringList("lore").stream()
                        .map(line -> line.replace("+1%", "+1").replace("-1%", "-1").replace("5%", "5"))
                        .toList());
                if (lore.stream().noneMatch(line -> line.contains("{probability}"))) {
                    lore.add(0, "<!i><gray>玩家展示概率: <!i><white>{probability}");
                }
                section.set("lore", lore);
                changed = true;
            }
        }
        return changed;
    }

    static boolean apply(ConfigurationSection config, boolean manager) {
        boolean changed = migrateLabels(config);
        if (!config.contains("reward-display")) {
            RewardPreviewDisplayConfig defaults = RewardPreviewDisplayConfig.editorDefaults(manager);
            config.set("reward-display.append-item-lore", defaults.isAppendItemLore());
            config.set("reward-display.lore", defaults.getLore());
            config.set("reward-display.broadcast-line", defaults.getBroadcastLine());
            changed = true;
        }
        return changed;
    }

    private static boolean migrateLabels(ConfigurationSection section) {
        boolean changed = false;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                changed |= migrateLabels(child);
            } else if (value instanceof List<?> list) {
                List<?> updated = list.stream().map(entry -> entry instanceof String line
                        ? line.replace("总概率:", "总权重:").replace("{total_chance}%", "{total_weight}") : entry).toList();
                if (!updated.equals(list)) {
                    section.set(key, updated);
                    changed = true;
                }
            }
        }
        if ("balance_chances".equals(section.getString("action"))) {
            if ("<!i><gold>概率平衡".equals(section.getString("name"))) {
                section.set("name", "<!i><gold>权重调整");
                changed = true;
            }
            if (section.getStringList("lore").contains("<!i><gray>自动调整所有奖励概率到 100%")) {
                section.set("lore", List.of(
                        "<!i><gray>归一化保留原比例，总权重调整为100",
                        "<!i><gray>平均分配会将所有奖励改为相同权重",
                        "", "<!i><yellow>左键 按比例归一化",
                        "<!i><red>Shift+右键 平均分配（改变原概率）"));
                changed = true;
            }
        }
        return changed;
    }
}
