package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ClaimCommand extends AbstractSubCommand {

    public ClaimCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.claim", "/crate claim");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("must-be-player"));
            return;
        }

        boolean started = plugin.getPendingRewardManager().claimAllPendingRewards(player, summary -> {
            if (!player.isOnline()) {
                return;
            }
            if (summary.claimedCount() == 0 && summary.failedCount() == 0) {
                plugin.getLanguageManager().send(player, "no-pending-rewards");
                return;
            }
            if (summary.failedCount() > 0) {
                plugin.getLanguageManager().send(player, "pending-reward-unavailable");
            }
        });
        if (!started) {
            plugin.getLanguageManager().send(player, "pending-reward-processing");
            return;
        }
        plugin.getLanguageManager().send(player, "pending-reward-processing");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
