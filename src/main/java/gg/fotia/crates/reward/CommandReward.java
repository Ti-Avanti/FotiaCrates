package gg.fotia.crates.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CommandReward extends AbstractReward {

    private final List<String> commands;

    public CommandReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                         ItemStack displayItem, List<String> commands) {
        super(id, displayName, rarity, chance, broadcast, displayItem);
        this.commands = commands != null ? commands : new ArrayList<>();
    }

    @Override
    public RewardType getType() { return RewardType.COMMAND; }

    @Override
    public void give(Player player) {
        for (String command : commands) {
            String parsed = command.replace("%player%", player.getName())
                    .replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    @Override
    public List<String> getCommands() { return new ArrayList<>(commands); }
}
