package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SetCommand extends AbstractSubCommand {

    public SetCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.set", "/crate set <crate>");
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

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            plugin.getLanguageManager().send(player, "no-block");
            return;
        }

        var location = targetBlock.getLocation();
        CrateLocation placed = new CrateLocation(location, crate.getId());
        plugin.getCrateManager().addLocationAsync(placed, () -> {
            if (plugin.getCrateManager().getLocationAt(location) != placed) return;
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                plugin.getModelEngineManager().ensureCrateModel(crate, location);
                plugin.getHologramManager().createHologram(location, crate.getId());
            }
            if (player.isOnline()) plugin.getLanguageManager().send(player, "crate-set",
                    LanguageManager.placeholders("crate", crate.getName()));
        }, exception -> {
            plugin.getLogger().severe("Failed to set crate location: " + exception.getMessage());
            if (player.isOnline()) plugin.getLanguageManager().send(player, "crate-location-save-failed");
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterCompletions(new ArrayList<>(plugin.getCrateManager().getCrateIds()), args[0]);
        }
        return List.of();
    }
}
