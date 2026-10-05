package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ReloadCommand extends AbstractSubCommand {

    public ReloadCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.reload", "/crate reload");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (plugin.getCrateManager().isConfigSavePending() || plugin.getKeyManager().isConfigSavePending()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("configuration-save-busy"));
            return;
        }
        if (plugin.getGuiManager().getRarityProbabilityEditor().isSaving()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("rarity-probability-busy"));
            return;
        }
        plugin.reload();

        plugin.sendReloadResult(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
