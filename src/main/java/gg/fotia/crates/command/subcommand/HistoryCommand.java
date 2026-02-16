package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.MessageConfig;
import gg.fotia.crates.history.HistoryManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HistoryCommand extends AbstractSubCommand {

    public HistoryCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.history", "/crate history [player]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 0) {
            if (!sender.hasPermission("fotiacrates.history.others")) {
                if (sender instanceof Player p) {
                    plugin.getMessageConfig().send(p, "no-permission");
                } else {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("no-permission"));
                }
                return;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                if (sender instanceof Player p) {
                    plugin.getMessageConfig().send(p, "invalid-player");
                } else {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("invalid-player"));
                }
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageConfig().getMessage("must-be-player"));
                return;
            }
            target = player;
        }

        List<HistoryManager.HistoryEntry> history = plugin.getHistoryManager()
                .getHistory(target.getUniqueId(), 10);

        if (history.isEmpty()) {
            if (sender instanceof Player p) {
                plugin.getMessageConfig().send(p, "no-history");
            } else {
                sender.sendMessage(plugin.getMessageConfig().getMessage("no-history"));
            }
            return;
        }

        if (sender instanceof Player p) {
            plugin.getMessageConfig().send(p, "history-header",
                    MessageConfig.placeholders("player", target.getName()));
        } else {
            sender.sendMessage(plugin.getMessageConfig().getMessage("history-header",
                    MessageConfig.placeholders("player", target.getName())));
        }

        for (HistoryManager.HistoryEntry entry : history) {
            String crateName = plugin.getCrateManager().getCrate(entry.crateId()) != null
                    ? plugin.getCrateManager().getCrate(entry.crateId()).getName()
                    : entry.crateId();

            if (sender instanceof Player p) {
                plugin.getMessageConfig().send(p, "history-entry",
                        MessageConfig.placeholders(
                                "crate", crateName,
                                "reward", entry.rewardName(),
                                "time", entry.formattedTime()
                        ));
            } else {
                sender.sendMessage(plugin.getMessageConfig().getMessage("history-entry",
                        MessageConfig.placeholders(
                                "crate", crateName,
                                "reward", entry.rewardName(),
                                "time", entry.formattedTime()
                        )));
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("fotiacrates.history.others")) {
            return filterCompletions(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).toList(), args[0]);
        }
        return List.of();
    }
}
