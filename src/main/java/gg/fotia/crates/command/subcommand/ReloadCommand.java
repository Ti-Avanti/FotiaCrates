package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ReloadCommand extends AbstractSubCommand {

    public ReloadCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.reload", "/crate reload");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reload();

        if (sender instanceof Player p) {
            plugin.getLanguageManager().send(p, "reload-success");
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("reload-success"));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
