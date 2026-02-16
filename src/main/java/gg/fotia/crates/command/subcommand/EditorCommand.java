package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class EditorCommand extends AbstractSubCommand {

    public EditorCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.editor", "/crate editor");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageConfig().send(null, "must-be-player");
            sender.sendMessage(plugin.getMessageConfig().getMessage("must-be-player"));
            return;
        }

        plugin.getGuiManager().openAdminGui(player);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
