package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
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
            sender.sendMessage(plugin.getLanguageManager().getMessage("must-be-player"));
            return;
        }

        if (args.length < 1) {
            plugin.getLanguageManager().send(player, "usage",
                    LanguageManager.placeholders("usage", getUsage()));
            return;
        }

        String crateId = args[0];
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            plugin.getLanguageManager().send(player, "no-block");
            return;
        }

        plugin.getCrateManager().setCrateLocation(crate.getId(), targetBlock.getLocation());

        plugin.getLanguageManager().send(player, "crate-set",
                LanguageManager.placeholders("crate", crate.getName()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(new ArrayList<>(plugin.getCrateManager().getCrateIds()), args[0]);
        }
        return List.of();
    }
}
