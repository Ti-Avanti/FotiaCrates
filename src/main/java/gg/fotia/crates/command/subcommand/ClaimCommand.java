package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.reward.PendingRewardManager;
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

        int pendingCount = plugin.getPendingRewardManager().getPendingRewardCount(player.getUniqueId());
        if (pendingCount <= 0) {
            plugin.getLanguageManager().send(player, "no-pending-rewards");
            return;
        }

        PendingRewardManager.ClaimSummary summary = plugin.getPendingRewardManager().claimAllPendingRewards(player);
        if (summary.claimedCount() == 0 && summary.failedCount() == 0) {
            plugin.getLanguageManager().send(player, "no-pending-rewards");
            return;
        }

        if (summary.failedCount() > 0) {
            plugin.getLanguageManager().send(player, "pending-reward-unavailable");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
