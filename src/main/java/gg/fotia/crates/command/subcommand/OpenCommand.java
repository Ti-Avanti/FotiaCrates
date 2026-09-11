package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateOpenService;
import gg.fotia.crates.crate.MultiOpenService;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class OpenCommand extends AbstractSubCommand {

    private final CrateOpenService crateOpenService;
    private final MultiOpenService multiOpenService;

    public OpenCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.use", "/crate open <crate> [amount]");
        this.crateOpenService = new CrateOpenService(plugin);
        this.multiOpenService = new MultiOpenService(plugin, crateOpenService);
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

        if (!plugin.getAsyncPlayerDataManager().isReady(player.getUniqueId())) {
            plugin.getLanguageManager().send(player, "player-data-loading");
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
            crateOpenService.sendMissingKeys(player, crate, amount);
            return;
        }

        if (amount > 1) {
            openMultiple(player, crate, amount);
        } else {
            openSingle(player, crate);
        }
    }

    private void openSingle(Player player, Crate crate) {
        UUID playerUuid = player.getUniqueId();
        if (!plugin.getOpenSessionManager().tryBegin(playerUuid)) {
            return;
        }

        CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(player, crate);
        if (!openAttempt.isSuccess()) {
            plugin.getOpenSessionManager().finish(playerUuid);
            crateOpenService.sendOpenFailure(player, crate, openAttempt.failureReason());
            return;
        }

        crateOpenService.commitOpen(player, openAttempt, () -> {
            RewardResult result = openAttempt.rewardResult();
            Location location = plugin.getParticleManager().resolveCrateLocation(player, crate);
            Runnable delivery = () -> crateOpenService.deliverRewardSafely(playerUuid, player.getName(), crate,
                    result, location, () -> plugin.getOpenSessionManager().finish(playerUuid));
            plugin.getCratePresentationManager().play(playerUuid, crate, List.of(result.getDisplayReward()),
                    location, false, true, delivery);
        }, () -> plugin.getOpenSessionManager().finish(playerUuid));
    }

    private void openMultiple(Player player, Crate crate, int amount) {
        UUID playerUuid = player.getUniqueId();
        if (!plugin.getOpenSessionManager().tryBegin(playerUuid)) {
            return;
        }
        Location crateLocation = plugin.getParticleManager().resolveCrateLocation(player, crate);
        multiOpenService.open(player, crate, amount, crateLocation,
                () -> plugin.getOpenSessionManager().finish(playerUuid));
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
