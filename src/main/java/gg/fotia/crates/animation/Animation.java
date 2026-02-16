package gg.fotia.crates.animation;

import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface Animation {
    void start(Player player, Crate crate, Reward finalReward, Location crateLocation, Runnable onComplete);
    void cancel();
    boolean isRunning();
}
