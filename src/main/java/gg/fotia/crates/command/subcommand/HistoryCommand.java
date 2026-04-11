package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
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
        Player viewer = sender instanceof Player player ? player : null;
        Player target;

        if (args.length > 0) {
            if (!sender.hasPermission("fotiacrates.history.others")) {
                if (viewer != null) {
                    plugin.getLanguageManager().send(viewer, "no-permission");
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("no-permission"));
                }
                return;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                if (viewer != null) {
                    plugin.getLanguageManager().send(viewer, "invalid-player");
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("invalid-player"));
                }
                return;
            }
        } else {
            if (viewer == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("must-be-player"));
                return;
            }
            target = viewer;
        }

        if (viewer != null) {
            plugin.getGuiManager().openHistoryGui(viewer, target.getUniqueId(), target.getName(), 0);
            return;
        }

        List<HistoryManager.HistoryEntry> history = plugin.getHistoryManager()
                .getHistory(target.getUniqueId(), 10);

        if (history.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("no-history"));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("history-header",
                LanguageManager.placeholders("player", target.getName())));

        for (HistoryManager.HistoryEntry entry : history) {
            String crateName = plugin.getCrateManager().getCrate(entry.crateId()) != null
                    ? plugin.getCrateManager().getCrate(entry.crateId()).getName()
                    : entry.crateId();

            sender.sendMessage(plugin.getLanguageManager().getMessage("history-entry",
                    LanguageManager.placeholders(
                            "crate", crateName,
                            "reward", entry.rewardName(),
                            "time", entry.formattedTime()
                    )));
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
