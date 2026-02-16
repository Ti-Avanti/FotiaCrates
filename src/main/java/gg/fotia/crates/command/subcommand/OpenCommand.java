package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationManager;
import gg.fotia.crates.config.MessageConfig;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.key.KeyType;
import gg.fotia.crates.reward.Reward;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class OpenCommand extends AbstractSubCommand {

    public OpenCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.use", "/crate open <crate> [amount]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("must-be-player"));
            return;
        }

        if (args.length < 1) {
            plugin.getMessageConfig().send(player, "usage",
                    MessageConfig.placeholders("usage", getUsage()));
            return;
        }

        String crateId = args[0];
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            plugin.getMessageConfig().send(player, "invalid-crate");
            return;
        }

        if (!player.hasPermission("fotiacrates.open." + crateId) &&
                !player.hasPermission("fotiacrates.open.*")) {
            plugin.getMessageConfig().send(player, "no-permission");
            return;
        }

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1) amount = 1;
                if (crate.isMultiOpenEnabled()) {
                    amount = Math.min(amount, crate.getMultiOpenMax());
                } else {
                    amount = 1;
                }
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }

        int keys = plugin.getKeyManager().getTotalKeysForCrate(player, crateId);
        if (keys < amount) {
            plugin.getMessageConfig().send(player, "no-key");
            return;
        }

        if (amount > 1) {
            openMultiple(player, crate, amount);
        } else {
            openSingle(player, crate);
        }
    }

    private void openSingle(Player player, Crate crate) {
        if (!plugin.getKeyManager().consumeKeyForCrate(player, crate.getId(), KeyType.ALL)) {
            plugin.getMessageConfig().send(player, "no-key");
            return;
        }

        boolean isPity = false;
        if (crate.isPityEnabled()) {
            isPity = plugin.getPityManager().shouldTriggerPity(
                    player.getUniqueId(), crate.getId(), crate.getPityCount());
        }

        Reward reward = isPity ? crate.rollPityReward() : crate.rollReward();
        if (reward == null) {
            plugin.getLogger().warning("No reward found for crate: " + crate.getId());
            return;
        }

        if (crate.isPityEnabled()) {
            if (isPity || reward.getRarity().equalsIgnoreCase(crate.getPityRarity())) {
                plugin.getPityManager().resetPityCount(player.getUniqueId(), crate.getId());
            } else {
                plugin.getPityManager().incrementPityCount(player.getUniqueId(), crate.getId());
            }
        }

        if (crate.isAnimationEnabled()) {
            AnimationManager animationManager = new AnimationManager(plugin);
            animationManager.playAnimation(player, crate, reward, player.getLocation(), () -> {
                giveReward(player, crate, reward);
            });
        } else {
            giveReward(player, crate, reward);
        }
    }

    private void openMultiple(Player player, Crate crate, int amount) {
        plugin.getMessageConfig().send(player, "multi-open-start",
                MessageConfig.placeholders("amount", String.valueOf(amount)));

        for (int i = 0; i < amount; i++) {
            if (!plugin.getKeyManager().consumeKeyForCrate(player, crate.getId(), KeyType.ALL)) {
                break;
            }

            boolean isPity = false;
            if (crate.isPityEnabled()) {
                isPity = plugin.getPityManager().shouldTriggerPity(
                        player.getUniqueId(), crate.getId(), crate.getPityCount());
            }

            Reward reward = isPity ? crate.rollPityReward() : crate.rollReward();
            if (reward == null) continue;

            if (crate.isPityEnabled()) {
                if (isPity || reward.getRarity().equalsIgnoreCase(crate.getPityRarity())) {
                    plugin.getPityManager().resetPityCount(player.getUniqueId(), crate.getId());
                } else {
                    plugin.getPityManager().incrementPityCount(player.getUniqueId(), crate.getId());
                }
            }

            giveReward(player, crate, reward);
        }
    }

    private void giveReward(Player player, Crate crate, Reward reward) {
        reward.give(player);

        plugin.getMessageConfig().send(player, "reward-received",
                MessageConfig.placeholders("reward", reward.getDisplayName()));

        if (reward.shouldBroadcast() && plugin.getConfigManager().isBroadcastRareRewards()) {
            var message = plugin.getMessageConfig().getMessage("broadcast-rare",
                    MessageConfig.placeholders(
                            "player", player.getName(),
                            "crate", crate.getName(),
                            "reward", reward.getDisplayName()
                    ));
            plugin.getServer().broadcast(message);
        }

        plugin.getHistoryManager().addHistory(
                player.getUniqueId(),
                player.getName(),
                crate.getId(),
                reward.getId(),
                reward.getDisplayName()
        );
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(new ArrayList<>(plugin.getCrateManager().getCrateIds()), args[0]);
        }
        if (args.length == 2) {
            return filterCompletions(List.of("1", "5", "10"), args[1]);
        }
        return List.of();
    }
}
