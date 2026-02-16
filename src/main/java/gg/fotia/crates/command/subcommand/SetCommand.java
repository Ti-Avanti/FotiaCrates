package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.MessageConfig;
import gg.fotia.crates.crate.Crate;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SetCommand extends AbstractSubCommand {

    public SetCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.set", "/crate set <crate>");
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

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            plugin.getMessageConfig().send(player, "no-block");
            return;
        }

        plugin.getCrateManager().setCrateLocation(crate.getId(), targetBlock.getLocation());

        plugin.getMessageConfig().send(player, "crate-set",
                MessageConfig.placeholders("crate", crate.getName()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(new ArrayList<>(plugin.getCrateManager().getCrateIds()), args[0]);
        }
        return List.of();
    }
}
