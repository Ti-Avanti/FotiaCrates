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
