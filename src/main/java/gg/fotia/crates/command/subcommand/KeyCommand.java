package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.key.Key;
import gg.fotia.crates.key.KeyType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KeyCommand extends AbstractSubCommand {

    public KeyCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.key", "/crate key <give|take|check> <player> <key> <amount> [virtual|physical]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsage(sender, getUsage());
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "give" -> handleGive(sender, args);
            case "take" -> handleTake(sender, args);
            case "check" -> handleCheck(sender, args);
            default -> sendUsage(sender, getUsage());
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/crate key give <player> <key> <amount> [virtual|physical]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMessage(sender, "invalid-player");
            return;
        }

        String keyId = args[2];
        Key key = plugin.getKeyManager().getKey(keyId);
        if (key == null) {
            sendMessage(sender, "invalid-key");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 1) {
                sendUsage(sender, "/crate key give <player> <key> <amount> [virtual|physical]");
                return;
            }
        } catch (NumberFormatException e) {
            sendUsage(sender, "/crate key give <player> <key> <amount> [virtual|physical]");
            return;
        }

        KeyType keyType = KeyType.VIRTUAL;
        if (args.length > 4) {
            keyType = args[4].equalsIgnoreCase("physical") ? KeyType.PHYSICAL : KeyType.VIRTUAL;
        }

        if (keyType == KeyType.PHYSICAL) {
            plugin.getKeyManager().givePhysicalKeys(target, keyId, amount);
        } else {
            if (!ensureVirtualDataReady(sender, target)) {
                return;
            }
            if (!plugin.getKeyManager().addVirtualKeys(target.getUniqueId(), keyId, amount)) {
                sendMessage(sender, "player-data-loading");
                return;
            }
        }

        plugin.getLanguageManager().send(target, "key-given",
                LanguageManager.placeholders("amount", String.valueOf(amount), "key", key.getName()));

        if (sender instanceof Player p && !p.equals(target)) {
            plugin.getLanguageManager().send(p, "key-given-other",
                    LanguageManager.placeholders("amount", String.valueOf(amount),
                            "key", key.getName(), "player", target.getName()));
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("key-given-other",
                    LanguageManager.placeholders("amount", String.valueOf(amount),
                            "key", key.getName(), "player", target.getName())));
        }
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/crate key take <player> <key> <amount> [virtual|physical]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMessage(sender, "invalid-player");
            return;
        }

        String keyId = args[2];
        Key key = plugin.getKeyManager().getKey(keyId);
        if (key == null) {
            sendMessage(sender, "invalid-key");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 1) {
                sendUsage(sender, "/crate key take <player> <key> <amount> [virtual|physical]");
                return;
            }
        } catch (NumberFormatException e) {
            sendUsage(sender, "/crate key take <player> <key> <amount> [virtual|physical]");
            return;
        }

        KeyType keyType = KeyType.VIRTUAL;
        if (args.length > 4) {
            keyType = args[4].equalsIgnoreCase("physical") ? KeyType.PHYSICAL : KeyType.VIRTUAL;
        }

        if (keyType == KeyType.PHYSICAL) {
            plugin.getKeyManager().removePhysicalKeys(target, keyId, amount);
        } else {
            if (!ensureVirtualDataReady(sender, target)) {
                return;
            }
            if (!plugin.getKeyManager().removeVirtualKeys(target.getUniqueId(), keyId, amount)) {
                sendMessage(sender, "player-data-loading");
                return;
            }
        }

        plugin.getLanguageManager().send(target, "key-removed",
                LanguageManager.placeholders("amount", String.valueOf(amount), "key", key.getName()));

        if (sender instanceof Player p && !p.equals(target)) {
            plugin.getLanguageManager().send(p, "key-removed-other",
                    LanguageManager.placeholders("amount", String.valueOf(amount),
                            "key", key.getName(), "player", target.getName()));
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("key-removed-other",
                    LanguageManager.placeholders("amount", String.valueOf(amount),
                            "key", key.getName(), "player", target.getName())));
        }
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/crate key check <player> [key]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendMessage(sender, "invalid-player");
            return;
        }
        if (!ensureVirtualDataReady(sender, target)) {
            return;
        }

        if (args.length > 2) {
            String keyId = args[2];
            Key key = plugin.getKeyManager().getKey(keyId);
            if (key == null) {
                sendMessage(sender, "invalid-key");
                return;
            }

            int virtual = plugin.getKeyManager().getVirtualKeys(target.getUniqueId(), keyId);
            int physical = plugin.getKeyManager().getPhysicalKeys(target, keyId);
            int total = virtual + physical;

            sendMessage(sender, "key-check",
                    LanguageManager.placeholders(
                            "player", target.getName(),
                            "amount", String.valueOf(total),
                            "key", key.getName(),
                            "virtual", String.valueOf(virtual),
                            "physical", String.valueOf(physical)
                    ));
        } else {
            for (Key key : plugin.getKeyManager().getAllKeys()) {
                int virtual = plugin.getKeyManager().getVirtualKeys(target.getUniqueId(), key.getId());
                int physical = plugin.getKeyManager().getPhysicalKeys(target, key.getId());
                int total = virtual + physical;

                if (total > 0) {
                    sendMessage(sender, "key-check",
                            LanguageManager.placeholders(
                                    "player", target.getName(),
                                    "amount", String.valueOf(total),
                                    "key", key.getName(),
                                    "virtual", String.valueOf(virtual),
                                    "physical", String.valueOf(physical)
                            ));
                }
            }
        }
    }

    private void sendUsage(CommandSender sender, String usage) {
        if (sender instanceof Player p) {
            plugin.getLanguageManager().send(p, "usage", LanguageManager.placeholders("usage", usage));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("usage",
                    LanguageManager.placeholders("usage", usage)));
        }
    }

    private boolean ensureVirtualDataReady(CommandSender sender, Player target) {
        if (plugin.getAsyncPlayerDataManager().isReady(target.getUniqueId())) {
            return true;
        }
        sendMessage(sender, "player-data-loading");
        return false;
    }

    private void sendMessage(CommandSender sender, String key) {
        if (sender instanceof Player p) {
            plugin.getLanguageManager().send(p, key);
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage(key));
        }
    }

    private void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player p) {
            plugin.getLanguageManager().send(p, key, placeholders);
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage(key, placeholders));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(List.of("give", "take", "check"), args[0]);
        }
        if (args.length == 2) {
            return filterCompletions(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3) {
            return filterCompletions(new ArrayList<>(plugin.getKeyManager().getKeyIds()), args[2]);
        }
        if (args.length == 4 && !args[0].equalsIgnoreCase("check")) {
            return filterCompletions(List.of("1", "5", "10", "64"), args[3]);
        }
        if (args.length == 5 && !args[0].equalsIgnoreCase("check")) {
            return filterCompletions(List.of("virtual", "physical"), args[4]);
        }
        return List.of();
    }
}
