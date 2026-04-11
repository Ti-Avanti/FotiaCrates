package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.crate.Crate;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class RemoveCommand extends AbstractSubCommand {

    public RemoveCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.remove", "/crate remove");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("must-be-player"));
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            plugin.getLanguageManager().send(player, "no-block");
            return;
        }

        Crate crate = plugin.getCrateManager().getCrateAtLocation(targetBlock.getLocation());
        if (crate == null) {
            plugin.getLanguageManager().send(player, "not-crate-location");
            return;
        }

        // 移除ModelEngine模型
        plugin.getHologramManager().removeHologram(targetBlock.getLocation());
        plugin.getModelEngineManager().removeCrateModel(targetBlock.getLocation());
        plugin.getCrateManager().removeCrateLocation(targetBlock.getLocation());

        plugin.getLanguageManager().send(player, "crate-removed",
                LanguageManager.placeholders("crate", crate.getName()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
