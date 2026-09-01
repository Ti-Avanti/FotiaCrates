package gg.fotia.crates.animation;

import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public interface BatchAnimation extends Animation {

    void startBatch(Player player, Crate crate, List<Reward> finalRewards,
                    Location crateLocation, Runnable onComplete);

    @Override
    default void start(Player player, Crate crate, Reward finalReward,
                       Location crateLocation, Runnable onComplete) {
        startBatch(player, crate, List.of(finalReward), crateLocation, onComplete);
    }
}
