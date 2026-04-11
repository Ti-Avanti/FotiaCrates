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
            plugin.getLanguageManager().send(p, "help-header");

            if (p.hasPermission("fotiacrates.use")) {
                plugin.getLanguageManager().send(p, "help-open");
            }
            if (p.hasPermission("fotiacrates.claim")) {
                plugin.getLanguageManager().send(p, "help-claim");
            }
            if (p.hasPermission("fotiacrates.preview")) {
                plugin.getLanguageManager().send(p, "help-preview");
            }
            if (p.hasPermission("fotiacrates.history")) {
                plugin.getLanguageManager().send(p, "help-history");
            }
            if (p.hasPermission("fotiacrates.admin.key")) {
                plugin.getLanguageManager().send(p, "help-key");
            }
            if (p.hasPermission("fotiacrates.admin.give")) {
                plugin.getLanguageManager().send(p, "help-give");
            }
            if (p.hasPermission("fotiacrates.admin.set")) {
                plugin.getLanguageManager().send(p, "help-set");
            }
            if (p.hasPermission("fotiacrates.admin.remove")) {
                plugin.getLanguageManager().send(p, "help-remove");
            }
            if (p.hasPermission("fotiacrates.admin.reload")) {
                plugin.getLanguageManager().send(p, "help-reload");
            }
            if (p.hasPermission("fotiacrates.admin.editor")) {
                plugin.getLanguageManager().send(p, "help-editor");
            }

            plugin.getLanguageManager().send(p, "help-footer");
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-header"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-open"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-claim"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-preview"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-history"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-key"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-give"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-set"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-remove"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-reload"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-editor"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("help-footer"));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
