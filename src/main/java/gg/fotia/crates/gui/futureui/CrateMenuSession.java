package gg.fotia.crates.gui.futureui;

import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.gui.HistoryReturnContext;
import gg.fotia.crates.history.HistoryManager;
import gg.fotia.crates.reward.Reward;
import java.util.List;
import java.util.UUID;

final class CrateMenuSession {
    final UUID token = UUID.randomUUID();
    final boolean compact;
    Crate crate;
    String view = "preview";
    int page, amount = 1, historyRevision;
    boolean busy, detached, loading;
    String feedback = "ready";
    UUID historyTarget;
    String historyName = "";
    HistoryReturnContext back = HistoryReturnContext.close();
    List<HistoryManager.HistoryEntry> history = List.of();
    List<RewardResult> results = List.of();
    List<Reward> frames = List.of();
    double progress;
    double reelPosition = 1;
    int selectedFrame = -1;
    CrateRollAnimation roll;
    org.bukkit.scheduler.BukkitTask watcher;
    CrateMenuSession(Crate crate, boolean compact) { this.crate = crate; this.compact = compact; }
}
