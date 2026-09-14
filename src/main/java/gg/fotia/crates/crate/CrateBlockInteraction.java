package gg.fotia.crates.crate;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import java.util.UUID;

/** 原版方块、自定义方块及模型共用的开箱入口。 */
public final class CrateBlockInteraction {
    private static final long INTERACT_COOLDOWN_MS = 500;
    private final FotiaCrates plugin;
    private final CrateOpenService crateOpenService;
    private final MultiOpenService multiOpenService;

    public CrateBlockInteraction(FotiaCrates plugin) {
        this.plugin = plugin;
        this.crateOpenService = new CrateOpenService(plugin);
        this.multiOpenService = new MultiOpenService(plugin, crateOpenService);
    }

    public boolean isCrate(Location location) {
        return plugin.getCrateManager().isLocationSet(location);
    }

    public void interact(Player player, Location location, Action action) {
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) {
            return;
        }

        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                handleShiftRightOpen(player, crate, location);
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

            // 无钥匙的情况由 prepareOpen 统一返回 NO_KEY 并提示，不再预检（省一次背包扫描）
            openCrate(player, crate, location);
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                removeCrate(player, crate, location);
                return;
            }

            if (!player.hasPermission("fotiacrates.preview")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return;
            }

            plugin.getGuiManager().openPreview(player, crate);
        }
    }

    private void openCrate(Player player, Crate crate, Location crateLocation) {
        UUID playerUuid = player.getUniqueId();
        if (plugin.getOpenSessionManager().isActive(playerUuid)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!plugin.getOpenSessionManager().tryInteract(playerUuid, now, INTERACT_COOLDOWN_MS)
                || !plugin.getOpenSessionManager().tryBegin(playerUuid)) {
            return;
        }

        CrateOpenService.OpenAttempt openAttempt;
        try {
            openAttempt = crateOpenService.prepareOpen(player, crate);
            if (!openAttempt.isSuccess()) {
                plugin.getOpenSessionManager().finish(playerUuid);
                crateOpenService.sendOpenFailure(player, crate, openAttempt.failureReason());
                return;
            }

            crateOpenService.commitOpen(player, openAttempt, () -> {
                RewardResult result = openAttempt.rewardResult();
                Runnable delivery = () -> crateOpenService.deliverRewardSafely(playerUuid, player.getName(), crate,
                        result, crateLocation, () -> plugin.getOpenSessionManager().finish(playerUuid));
                plugin.getCratePresentationManager().play(playerUuid, crate, java.util.List.of(result.getDisplayReward()),
                        crateLocation, true, true, delivery);
            }, () -> plugin.getOpenSessionManager().finish(playerUuid));
        } catch (RuntimeException exception) {
            // 同步段抛异常时必须释放会话，否则该玩家将被永久拦截
            plugin.getOpenSessionManager().finish(playerUuid);
            throw exception;
        }
    }

    private void handleShiftRightOpen(Player player, Crate crate, Location location) {
        if (!crateOpenService.hasOpenPermission(player, crate)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        if (!plugin.getAsyncPlayerDataManager().isReady(player.getUniqueId())) {
            plugin.getLanguageManager().send(player, "player-data-loading");
            return;
        }

        int amount = 1;
        if (crate.isMultiOpenEnabled()) {
            amount = crate.getMultiOpenMax();
        }

        if (amount <= 1) {
            openCrate(player, crate, location);
            return;
        }

        int keys = plugin.getKeyManager().getTotalKeysForCrate(player, crate.getId());
        if (keys < amount) {
            crateOpenService.sendMissingKeys(player, crate, amount);
            return;
        }

        openMultiple(player, crate, amount, location);
    }

    private void removeCrate(Player player, Crate crate, Location location) {
        if (!player.hasPermission("fotiacrates.admin.remove")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        plugin.getCrateManager().removeLocationAsync(location, () -> {
            plugin.getModelEngineManager().removeCrateModel(location);
            plugin.getHologramManager().removeHologram(location);
            if (player.isOnline()) plugin.getLanguageManager().send(player, "crate-removed",
                    LanguageManager.placeholders("crate", crate.getName()));
        }, exception -> {
            plugin.getLogger().severe("Failed to remove crate location: " + exception.getMessage());
            if (player.isOnline()) plugin.getLanguageManager().send(player, "crate-location-save-failed");
        });
    }

    private void openMultiple(Player player, Crate crate, int amount, Location location) {
        UUID playerUuid = player.getUniqueId();
        if (plugin.getOpenSessionManager().isActive(playerUuid)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!plugin.getOpenSessionManager().tryInteract(playerUuid, now, INTERACT_COOLDOWN_MS)
                || !plugin.getOpenSessionManager().tryBegin(playerUuid)) {
            return;
        }

        try {
            multiOpenService.open(player, crate, amount, location,
                    () -> plugin.getOpenSessionManager().finish(playerUuid));
        } catch (RuntimeException exception) {
            // 同步段抛异常时必须释放会话，否则该玩家将被永久拦截
            plugin.getOpenSessionManager().finish(playerUuid);
            throw exception;
        }
    }
}
