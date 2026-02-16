package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import org.bukkit.command.CommandSender;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSubCommand implements SubCommand {

    protected final FotiaCrates plugin;
    protected final String permission;
    protected final String usage;

    public AbstractSubCommand(FotiaCrates plugin, String permission, String usage) {
        this.plugin = plugin;
        this.permission = permission;
        this.usage = usage;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    @Override
    public String getPermission() { return permission; }

    @Override
    public String getUsage() { return usage; }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    protected List<String> filterCompletions(List<String> completions, String input) {
        if (input.isEmpty()) {
            return completions;
        }
        String lowerInput = input.toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerInput))
                .toList();
    }
}
