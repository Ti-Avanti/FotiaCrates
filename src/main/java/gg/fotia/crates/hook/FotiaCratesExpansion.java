package gg.fotia.crates.hook;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.key.Key;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FotiaCratesExpansion extends PlaceholderExpansion {

    private final FotiaCrates plugin;

    public FotiaCratesExpansion(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fotiacrates";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "0";
        }

        if (params.startsWith("keys_")) {
            Player player = offlinePlayer.getPlayer();
            if (player == null) {
                return "0";
            }
            return handleKeysPlaceholder(player, params.substring(5));
        }

        if (params.startsWith("pity_count_")) {
            return handlePityCountPlaceholder(offlinePlayer.getUniqueId(), params.substring(11));
        }

        if (params.startsWith("pity_remaining_")) {
            return handlePityRemainingPlaceholder(offlinePlayer.getUniqueId(), params.substring(15));
        }

        return null;
    }

    private String handleKeysPlaceholder(Player player, String remaining) {
        String type;
        String crateId;

        if (remaining.startsWith("virtual_")) {
            type = "virtual";
            crateId = remaining.substring(8);
        } else if (remaining.startsWith("physical_")) {
            type = "physical";
            crateId = remaining.substring(9);
        } else if (remaining.startsWith("total_")) {
            type = "total";
            crateId = remaining.substring(6);
        } else {
            return null;
        }

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            return "0";
        }

        return String.valueOf(getKeysForCrate(player, crateId, type));
    }

    private String handlePityCountPlaceholder(UUID playerUuid, String crateId) {
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null || !crate.isPityEnabled() || crate.getPityTiers().isEmpty()) {
            return "0";
        }

        return String.valueOf(plugin.getPityManager().getPityCount(playerUuid, crateId));
    }

    private String handlePityRemainingPlaceholder(UUID playerUuid, String crateId) {
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null || !crate.isPityEnabled() || crate.getPityTiers().isEmpty()) {
            return "0";
        }

        int currentCount = plugin.getPityManager().getPityCount(playerUuid, crateId);
        return String.valueOf(crate.getRemainingToNextPity(currentCount));
    }

    private int getKeysForCrate(Player player, String crateId, String type) {
        int count = 0;

        for (Key key : plugin.getKeyManager().getKeysForCrate(crateId)) {
            switch (type) {
                case "virtual" -> count += plugin.getKeyManager().getVirtualKeys(player.getUniqueId(), key.getId());
                case "physical" -> count += plugin.getKeyManager().getPhysicalKeys(player, key.getId());
                case "total" -> count += plugin.getKeyManager().getTotalKeys(player, key.getId());
            }
        }

        return count;
    }
}
