package gg.fotia.crates.gui;

import java.util.ArrayList;
import java.util.List;

public final class RewardPreviewDisplayConfig {

    private final boolean appendItemLore;
    private final List<String> lore;
    private final String percentageLine;
    private final String weightLine;
    private final String broadcastLine;

    public RewardPreviewDisplayConfig(boolean appendItemLore, List<String> lore,
                                      String percentageLine, String weightLine, String broadcastLine) {
        this.appendItemLore = appendItemLore;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
        this.percentageLine = percentageLine != null ? percentageLine : "";
        this.weightLine = weightLine != null ? weightLine : "";
        this.broadcastLine = broadcastLine != null ? broadcastLine : "";
    }

    public static RewardPreviewDisplayConfig defaults() {
        return new RewardPreviewDisplayConfig(
                true,
                List.of(
                        "",
                        "<!i><gray>稀有度: {rarity}",
                        "{chance_line}",
                        "{broadcast_line}"
                ),
                "<!i><gray>概率: <!i><yellow>{probability}",
                "<!i><gray>权重: <!i><yellow>{weight}",
                "<!i><gold>★ 稀有奖励"
        );
    }

    public boolean isAppendItemLore() {
        return appendItemLore;
    }

    public static RewardPreviewDisplayConfig editorDefaults(boolean manager) {
        List<String> lines = new ArrayList<>(List.of("",
                "<!i><gray>ID: <!i><white>{reward_id}",
                "<!i><gray>权重: <!i><yellow>{weight}",
                "<!i><gray>概率: <!i><yellow>{probability}",
                "<!i><gray>稀有度: {rarity}", "{broadcast_line}", "",
                "<!i><yellow>左键 编辑奖励"));
        if (manager) lines.add("<!i><aqua>中键 复制奖励");
        lines.add("<!i><red>Shift+右键 删除");
        return new RewardPreviewDisplayConfig(true, lines, defaults().getPercentageLine(),
                defaults().getWeightLine(), "<!i><gold>★ 全服广播");
    }

    public List<String> getLore() {
        return new ArrayList<>(lore);
    }

    public String getPercentageLine() {
        return percentageLine;
    }

    public String getWeightLine() {
        return weightLine;
    }

    public String getBroadcastLine() {
        return broadcastLine;
    }
}
