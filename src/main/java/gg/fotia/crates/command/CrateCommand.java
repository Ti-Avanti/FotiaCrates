package gg.fotia.crates.command;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.command.subcommand.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private final FotiaCrates plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CrateCommand(FotiaCrates plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        subCommands.put("open", new OpenCommand(plugin));
        subCommands.put("claim", new ClaimCommand(plugin));
        subCommands.put("preview", new PreviewCommand(plugin));
        subCommands.put("key", new KeyCommand(plugin));
        subCommands.put("give", new GiveCommand(plugin));
        subCommands.put("set", new SetCommand(plugin));
        subCommands.put("remove", new RemoveCommand(plugin));
        subCommands.put("history", new HistoryCommand(plugin));
        subCommands.put("reload", new ReloadCommand(plugin));
        subCommands.put("editor", new EditorCommand(plugin));
        subCommands.put("help", new HelpCommand(plugin));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // 没有参数时显示帮助
            subCommands.get("help").execute(sender, args);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            subCommands.get("help").execute(sender, args);
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        subCommand.execute(sender, subArgs);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                if (entry.getValue().hasPermission(sender)) {
                    completions.add(entry.getKey());
                }
            }
            return filterCompletions(completions, args[0]);
        }

        if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.tabComplete(sender, subArgs);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        if (input.isEmpty()) {
            return completions;
        }
        String lowerInput = input.toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerInput))
                .toList();
    }
}
