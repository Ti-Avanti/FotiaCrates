package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.key.Key;
import gg.fotia.crates.key.KeyType;
import gg.fotia.crates.key.distribution.KeyDistributionBatch;
import gg.fotia.crates.key.distribution.KeyDistributionConfirmation;
import gg.fotia.crates.key.distribution.KeyDistributionRequest;
import gg.fotia.crates.key.distribution.KeyDistributionScope;
import gg.fotia.crates.key.distribution.KeyDistributionTarget;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KeyCommand extends AbstractSubCommand {

    public KeyCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.key", "/crate key <give|take|check|confirm> ...");
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
            case "confirm" -> handleConfirm(sender, args);
            default -> sendUsage(sender, getUsage());
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/crate key give <player> <key> <amount> [virtual|physical]");
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

        KeyDistributionTarget target = KeyDistributionTarget.parse(args[1]);
        if (target.kind() == KeyDistributionTarget.Kind.PLAYER) {
            giveToPlayer(sender, target.playerName(), key, amount, keyType);
            return;
        }

        if (target.kind() == KeyDistributionTarget.Kind.ONLINE) {
            if (!requirePermission(sender, "fotiacrates.admin.key.giveonline")) {
                return;
            }
            List<UUID> recipients = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getUniqueId)
                    .toList();
            if (recipients.isEmpty()) {
                sendMessage(sender, "key-distribution-empty");
                return;
            }
            submitDistribution(sender, new KeyDistributionRequest(
                    KeyDistributionScope.ONLINE, keyId, amount, keyType,
                    senderIdentity(sender), sender.getName(), recipients));
            return;
        }

        if (!requirePermission(sender, "fotiacrates.admin.key.giveall")) {
            return;
        }
        if (keyType == KeyType.PHYSICAL
                && !plugin.getConfigManager().isPendingPhysicalKeyDeliveryEnabled()) {
            sendMessage(sender, "key-distribution-physical-disabled");
            return;
        }

        KeyDistributionRequest request = new KeyDistributionRequest(
                KeyDistributionScope.ALL, keyId, amount, keyType,
                senderIdentity(sender), sender.getName(), List.of());
        if (!plugin.getConfigManager().isKeyDistributionConfirmationRequired()) {
            submitDistribution(sender, request);
            return;
        }
        KeyDistributionConfirmation confirmation =
                plugin.getKeyDistributionManager().requestConfirmation(request);
        sendMessage(sender, "key-distribution-confirm",
                LanguageManager.placeholders(
                        "scope", "@all",
                        "amount", String.valueOf(amount),
                        "key", key.getName(),
                        "type", keyType.name().toLowerCase(),
                        "seconds", String.valueOf(
                                plugin.getConfigManager().getKeyDistributionConfirmationTimeoutMillis() / 1_000L),
                        "token", confirmation.token()));
    }

    private void giveToPlayer(CommandSender sender, String playerName, Key key,
                              int amount, KeyType keyType) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sendMessage(sender, "invalid-player");
            return;
        }
        if (keyType == KeyType.PHYSICAL) {
            plugin.getKeyManager().givePhysicalKeys(target, key.getId(), amount);
        } else {
            if (!ensureVirtualDataReady(sender, target)) {
                return;
            }
            if (!plugin.getKeyManager().addVirtualKeys(target.getUniqueId(), key.getId(), amount)) {
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

    private void handleConfirm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/crate key confirm <确认码>");
            return;
        }
        if (!requirePermission(sender, "fotiacrates.admin.key.giveall")) {
            return;
        }
        KeyDistributionRequest request = plugin.getKeyDistributionManager()
                .consumeConfirmation(senderIdentity(sender), args[1]);
        if (request == null) {
            sendMessage(sender, "key-distribution-confirm-invalid");
            return;
        }
        submitDistribution(sender, request);
    }

    private void submitDistribution(CommandSender sender, KeyDistributionRequest request) {
        plugin.getKeyDistributionManager().createDistribution(
                request,
                batch -> sendDistributionCreated(sender, batch),
                exception -> {
                    plugin.getLogger().severe("Failed to create key distribution: " + exception.getMessage());
                    sendMessage(sender, "key-distribution-failed");
                }
        );
    }

    private void sendDistributionCreated(CommandSender sender, KeyDistributionBatch batch) {
        if (batch.targetCount() == 0) {
            sendMessage(sender, "key-distribution-empty");
            return;
        }
        sendMessage(sender, "key-distribution-created",
                LanguageManager.placeholders(
                        "batch", batch.id().substring(0, 8),
                        "total", String.valueOf(batch.targetCount())));
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sendMessage(sender, "no-permission");
        return false;
    }

    private String senderIdentity(CommandSender sender) {
        if (sender instanceof Player player) {
            return "player:" + player.getUniqueId();
        }
        return "sender:" + sender.getName().toLowerCase();
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
            return filterCompletions(List.of("give", "take", "check", "confirm"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("confirm")) {
                return List.of();
            }
            List<String> targets = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).toList());
            if (args[0].equalsIgnoreCase("give")) {
                if (sender.hasPermission("fotiacrates.admin.key.giveonline")) {
                    targets.add("@online");
                }
                if (sender.hasPermission("fotiacrates.admin.key.giveall")) {
                    targets.add("@all");
                }
            }
            return filterCompletions(targets, args[1]);
        }
        if (args.length == 3 && !args[0].equalsIgnoreCase("confirm")) {
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
