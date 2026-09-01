package gg.fotia.crates.animation;

import org.bukkit.entity.Player;

public interface InteractiveAnimation {

    boolean handleClick(Player player, int slot);
}
