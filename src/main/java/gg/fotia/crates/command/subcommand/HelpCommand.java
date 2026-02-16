package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HelpCommand extends AbstractSubCommand {

    public HelpCommand(FotiaCrates plugin) {
        super(plugin, null, "/crate help");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (sender instanceof Player p) {
            plugin.getMessageConfig().send(p, "help-header");

            if (p.hasPermission("fotiacrates.use")) {
                plugin.getMessageConfig().send(p, "help-open");
            }
            if (p.hasPermission("fotiacrates.preview")) {
                plugin.getMessageConfig().send(p, "help-preview");
            }
            if (p.hasPermission("fotiacrates.history")) {
                plugin.getMessageConfig().send(p, "help-history");
            }
            if (p.hasPermission("fotiacrates.admin.key")) {
                plugin.getMessageConfig().send(p, "help-key");
            }
            if (p.hasPermission("fotiacrates.admin.set")) {
                plugin.getMessageConfig().send(p, "help-set");
            }
            if (p.hasPermission("fotiacrates.admin.remove")) {
                plugin.getMessageConfig().send(p, "help-remove");
            }
            if (p.hasPermission("fotiacrates.admin.reload")) {
                plugin.getMessageConfig().send(p, "help-reload");
            }
            if (p.hasPermission("fotiacrates.admin.reward")) {
                plugin.getMessageConfig().send(p, "help-reward");
            }
            if (p.hasPermission("fotiacrates.admin.editor")) {
                plugin.getMessageConfig().send(p, "help-editor");
            }

            plugin.getMessageConfig().send(p, "help-footer");
        } else {
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-header"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-open"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-preview"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-history"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-key"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-set"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-remove"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-reload"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-reward"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-editor"));
            sender.sendMessage(plugin.getMessageConfig().getMessage("help-footer"));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
