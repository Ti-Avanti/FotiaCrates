package gg.fotia.crates.listener;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.animation.AnimationManager;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.crate.CrateLocation;
import gg.fotia.crates.crate.CrateOpenService;
import gg.fotia.crates.crate.RewardResult;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.particle.ParticleStage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BlockListener implements Listener {

    private final FotiaCrates plugin;
    private final CrateOpenService crateOpenService;
    private final Set<UUID> openingPlayers = new HashSet<>();
    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 500;

    public BlockListener(FotiaCrates plugin) {
        this.plugin = plugin;
        this.crateOpenService = new CrateOpenService(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (plugin.getKeyManager().isPhysicalKey(item)) {
            event.setCancelled(true);
            return;
        }

        String crateId = plugin.getCrateManager().getCrateIdFromItem(item);
        if (crateId == null) {
            return;
        }

        if (!player.hasPermission("fotiacrates.admin.place")) {
            event.setCancelled(true);
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            event.setCancelled(true);
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

        Location location = event.getBlock().getLocation();
        if (plugin.getCrateManager().isLocationSet(location)) {
            event.setCancelled(true);
            plugin.getLanguageManager().send(player, "already-crate-location");
            return;
        }

        float yaw = 0f;
        if (crate.isModelEnabled()) {
            Location spawnLoc = location.clone().add(0.5, 0, 0.5);
            Location playerLoc = player.getLocation();
            double dx = playerLoc.getX() - spawnLoc.getX();
            double dz = playerLoc.getZ() - spawnLoc.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        plugin.getCrateManager().setCrateLocation(crateId, location, yaw);
        plugin.getLanguageManager().send(player, "crate-block-placed",
                LanguageManager.placeholders("crate", crate.getName()));

        if (crate.isModelEnabled()) {
            plugin.getModelEngineManager().spawnCrateModel(crate, location, player);
        }

        plugin.getHologramManager().createHologram(location, crateId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Location location = block.getLocation();
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) {
            return;
        }

        event.setCancelled(true);

        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                handleShiftRightOpen(player, crate, location);
                return;
            }

            if (!crateOpenService.hasOpenPermission(player, crate)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return;
            }

            if (!plugin.getKeyManager().hasKeyForCrate(player, crate.getId())) {
                plugin.getLanguageManager().send(player, "no-key");
                return;
            }

            openCrate(player, crate, location);
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
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
        if (openingPlayers.contains(playerUuid)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastInteract = interactCooldown.get(playerUuid);
        if (lastInteract != null && now - lastInteract < INTERACT_COOLDOWN_MS) {
            return;
        }
        interactCooldown.put(playerUuid, now);

        openingPlayers.add(playerUuid);

        CrateOpenService.OpenAttempt openAttempt = crateOpenService.prepareOpen(player, crate);
        if (!openAttempt.isSuccess()) {
            openingPlayers.remove(playerUuid);
            crateOpenService.sendOpenFailure(player, openAttempt.failureReason());
            return;
        }

        RewardResult rewardResult = openAttempt.rewardResult();
        plugin.getParticleManager().playStage(ParticleStage.OPEN, player, crate, crateLocation);
        plugin.getHologramManager().hideHologram(crateLocation);

        boolean hasModel = crate.isModelEnabled() && plugin.getModelEngineManager().hasModel(crateLocation);
        if (hasModel) {
            plugin.getModelEngineManager().playOpenAnimation(crate, crateLocation, player);
            int delay = crate.getModelEngineOpenDelay();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> playGuiAnimationAndGiveReward(player, crate, rewardResult, crateLocation), delay);
            return;
        }

        playGuiAnimationAndGiveReward(player, crate, rewardResult, crateLocation);
    }

    private void playGuiAnimationAndGiveReward(Player player, Crate crate, RewardResult rewardResult, Location crateLocation) {
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        String crateId = crate.getId();

        Runnable onComplete = () -> {
            if (!plugin.getCrateManager().isLocationSet(crateLocation)) {
                openingPlayers.remove(playerUuid);
                crateOpenService.deliverRewardSafely(playerUuid, playerName, crate, rewardResult, crateLocation);
                return;
            }

            if (crate.isModelEnabled() && plugin.getModelEngineManager().hasModel(crateLocation)) {
                plugin.getModelEngineManager().playIdleAnimation(crate, crateLocation);
            }

            plugin.getHologramManager().showHologram(crateLocation, crateId);
            openingPlayers.remove(playerUuid);
            crateOpenService.deliverRewardSafely(playerUuid, playerName, crate, rewardResult, crateLocation);
        };

        if (crate.isAnimationEnabled() || crate.isPhysicalAnimationEnabled()) {
            if (!player.isOnline()) {
                onComplete.run();
                return;
            }
            AnimationManager animationManager = new AnimationManager(plugin);
            animationManager.playAnimation(player, crate, rewardResult.getDisplayReward(), crateLocation, onComplete);
            return;
        }

        onComplete.run();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        if (plugin.getCrateManager().isLocationSet(location)) {
            event.setCancelled(true);
            plugin.getLanguageManager().send(event.getPlayer(), "crate-break-denied");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) {
            return;
        }

        if (armorStand.isVisible() || !armorStand.isMarker()) {
            return;
        }

        Location location = armorStand.getLocation().getBlock().getLocation();
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

        if (player.isSneaking()) {
            handleShiftRightOpen(player, crate, location);
            return;
        }

        if (!crateOpenService.hasOpenPermission(player, crate)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        if (!plugin.getKeyManager().hasKeyForCrate(player, crate.getId())) {
            plugin.getLanguageManager().send(player, "no-key");
            return;
        }

        openCrate(player, crate, location);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof ArmorStand armorStand)) {
            return;
        }
        if (armorStand.isVisible() || !armorStand.isMarker()) {
            return;
        }

        Location location = armorStand.getLocation().getBlock().getLocation();
        CrateLocation crateLocation = plugin.getCrateManager().getLocationAt(location);
        if (crateLocation == null) {
            return;
        }

        event.setCancelled(true);

        Crate crate = plugin.getCrateManager().getCrate(crateLocation.getCrateId());
        if (crate == null) {
            plugin.getLanguageManager().send(player, "invalid-crate");
            return;
        }

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

    private void handleShiftRightOpen(Player player, Crate crate, Location location) {
        if (!crateOpenService.hasOpenPermission(player, crate)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        int amount = 1;
        if (crate.isMultiOpenEnabled()) {
            amount = Math.max(1, Math.min(10, crate.getMultiOpenMax()));
        }

        if (amount <= 1) {
            if (!plugin.getKeyManager().hasKeyForCrate(player, crate.getId())) {
                plugin.getLanguageManager().send(player, "no-key");
                return;
            }
            openCrate(player, crate, location);
            return;
        }

        int keys = plugin.getKeyManager().getTotalKeysForCrate(player, crate.getId());
        if (keys < amount) {
            plugin.getLanguageManager().send(player, "no-key");
            return;
        }

        openMultiple(player, crate, amount, location);
    }

    private void removeCrate(Player player, Crate crate, Location location) {
        if (!player.hasPermission("fotiacrates.admin.remove")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        plugin.getModelEngineManager().removeCrateModel(location);
        plugin.getHologramManager().removeHologram(location);
        plugin.getCrateManager().removeLocation(location);
        plugin.getLanguageManager().send(player, "crate-removed",
                LanguageManager.placeholders("crate", crate.getName()));
    }

    private void openMultiple(Player player, Crate crate, int amount, Location location) {
        UUID playerUuid = player.getUniqueId();
        if (openingPlayers.contains(playerUuid)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastInteract = interactCooldown.get(playerUuid);
        if (lastInteract != null && now - lastInteract < INTERACT_COOLDOWN_MS) {
            return;
        }
        interactCooldown.put(playerUuid, now);
        openingPlayers.add(playerUuid);

        plugin.getLanguageManager().send(player, "multi-open-start",
                LanguageManager.placeholders("amount", String.valueOf(amount)));
        plugin.getParticleManager().playStage(ParticleStage.OPEN, player, crate, location);

        try {
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
        } finally {
            openingPlayers.remove(playerUuid);
        }
    }
}
