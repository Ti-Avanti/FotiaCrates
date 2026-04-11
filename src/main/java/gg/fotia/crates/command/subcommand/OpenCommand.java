package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationManager;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateOpenService;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class OpenCommand extends AbstractSubCommand {

    private final CrateOpenService crateOpenService;

    public OpenCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.use", "/crate open <crate> [amount]");
        this.crateOpenService = new CrateOpenService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("must-be-player"));
            return;
        }

        if (args.length < 1) {
            plugin.getLanguageManager().send(player, "usage",
                    LanguageManager.placeholders("usage", getUsage()));
            return;
        }

        String crateId = args[0];
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

        if (!crateOpenService.hasOpenPermission(player, crate)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1) {
                    amount = 1;
                }
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
            plugin.getLanguageManager().send(player, "no-key");
            return;
        }

        if (amount > 1) {
            openMultiple(player, crate, amount);
        } else {
            openSingle(player, crate);
        }
    }

    private void openSingle(Player player, Crate crate) {
        CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(player, crate);
        if (!openAttempt.isSuccess()) {
            crateOpenService.sendOpenFailure(player, openAttempt.failureReason());
            return;
        }

        RewardResult rewardResult = openAttempt.rewardResult();
        if (crate.isAnimationEnabled()) {
            AnimationManager animationManager = new AnimationManager(plugin);
            animationManager.playAnimation(player, crate, rewardResult.getDisplayReward(), player.getLocation(),
                    () -> crateOpenService.deliverReward(player, crate, rewardResult));
            return;
        }

        crateOpenService.deliverReward(player, crate, rewardResult);
    }

    private void openMultiple(Player player, Crate crate, int amount) {
        plugin.getLanguageManager().send(player, "multi-open-start",
                LanguageManager.placeholders("amount", String.valueOf(amount)));

        for (int i = 0; i < amount; i++) {
            CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(player, crate);
            if (!openAttempt.isSuccess()) {
                if (openAttempt.failureReason() == CrateOpenService.OpenFailureReason.NO_KEY) {
                    break;
                }
                continue;
            }

            crateOpenService.deliverReward(player, crate, openAttempt.rewardResult());
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(plugin.getCrateManager().getCrateIds().stream()
                    .filter(crateId -> {
                        Crate crate = plugin.getCrateManager().getCrate(crateId);
                        return crate != null && (!(sender instanceof Player player) || crateOpenService.hasOpenPermission(player, crate));
                    })
                    .toList(), args[0]);
        }
        if (args.length == 2) {
            return filterCompletions(List.of("1", "5", "10"), args[1]);
        }
        return List.of();
    }
}
