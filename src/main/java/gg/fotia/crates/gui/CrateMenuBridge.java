package gg.fotia.crates.gui;

import gg.fotia.crates.animation.Animation;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.history.HistoryManager;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

/** 隔离可选显示引擎，原版服务器无需加载 FutureUI API。 */
public interface CrateMenuBridge extends AutoCloseable {
    boolean preview(Player player, Crate crate, int page);
    boolean history(Player player, UUID target, String name, String crateId, int page,
                    List<HistoryManager.HistoryEntry> entries, HistoryReturnContext back);
    boolean results(Player player, Crate crate, List<RewardResult> results);
    Animation animation(Player player, Crate crate);
    @Override void close();
}
