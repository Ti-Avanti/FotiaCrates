package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.MessageConfig;
import gg.fotia.crates.crate.Crate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PreviewCommand extends AbstractSubCommand {

    public PreviewCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.preview", "/crate preview <crate>");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("must-be-player"));
            return;
        }

        if (args.length < 1) {
            plugin.getMessageConfig().send(player, "usage",
                    MessageConfig.placeholders("usage", getUsage()));
            return;
        }

        String crateId = args[0];
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            plugin.getMessageConfig().send(player, "invalid-crate");
            return;
        }

        if (!crate.isPreviewEnabled()) {
            plugin.getMessageConfig().send(player, "no-permission");
            return;
        }

        plugin.getGuiManager().openPreview(player, crate);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(new ArrayList<>(plugin.getCrateManager().getCrateIds()), args[0]);
        }
        return List.of();
    }
}
