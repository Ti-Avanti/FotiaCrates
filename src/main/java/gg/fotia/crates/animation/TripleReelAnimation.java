package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.gui.CrateGuiHolder;
import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;
import gg.fotia.crates.gui.GuiType;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

public final class TripleReelAnimation implements Animation {

    private static final List<Integer> DEFAULT_SLOTS =
            List.of(11, 13, 15, 20, 22, 24, 29, 31, 33);

    private final FotiaCrates plugin;
    private final Random random = new Random();
    private AnimationCompletion completion = new AnimationCompletion();
    private Inventory inventory;
    private Player player;
    private BukkitTask animationTask;
    private BukkitTask closeTask;
    private boolean running;

    public TripleReelAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation,
                      Runnable onComplete) {
        this.player = player;
        this.completion = new AnimationCompletion();
        this.running = true;

        AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                .getAnimationTemplate(crate.getAnimationTemplate(), AnimationType.TRIPLE_REEL);
        GuiConfig config = template != null ? template.guiConfig() : null;
        TripleReelAnimationSettings settings = template != null
                ? template.tripleReelSettings()
                : TripleReelAnimationSettings.from(null);
        int size = Math.max(45, config != null ? config.getSize() : 45);
        String title = config != null ? config.getTitle() : "<!i><dark_gray>幸运三轴老虎机";
        inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ANIMATION, crate), size, MessageUtil.parse(title));
        placeTemplateItems(config);

        List<Integer> slots = resolveSlots(config, size);
        List<Reward> rewards = crate.getAvailableRewardsFor(player);
        for (int slot : slots) {
            inventory.setItem(slot, nextDisplayItem(rewards, finalReward));
        }
        player.openInventory(inventory);

        int requestedTicks = Math.max(1, crate.getAnimationDuration()) * 20;
        TripleReelSchedule schedule = new TripleReelSchedule(
                requestedTicks, settings.stopGapTicks());
        int interval = settings.spinIntervalTicks();
        int[] elapsed = {0};
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) {
                return;
            }
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                finishInterrupted(onComplete);
                return;
            }

            boolean moved = false;
            for (int reel = 0; reel < TripleReelSchedule.REEL_COUNT; reel++) {
                if (schedule.isSpinning(reel, elapsed[0])) {
                    shiftReel(slots, reel, rewards, finalReward);
                    moved = true;
                }
            }
            if (moved && crate.getSpinSound() != null) {
                player.playSound(player.getLocation(), crate.getSpinSound(),
                        crate.getSpinVolume(), crate.getSpinPitch());
            }
            elapsed[0] += interval;
            if (elapsed[0] >= schedule.totalTicks()) {
                finish(finalReward, crate, slots, onComplete);
            }
        }, 0L, interval);
    }

    private void shiftReel(List<Integer> slots, int reel, List<Reward> rewards, Reward fallback) {
        int top = slots.get(reel);
        int middle = slots.get(3 + reel);
        int bottom = slots.get(6 + reel);
        inventory.setItem(top, inventory.getItem(middle));
        inventory.setItem(middle, inventory.getItem(bottom));
        inventory.setItem(bottom, nextDisplayItem(rewards, fallback));
    }

    private void finish(Reward finalReward, Crate crate, List<Integer> slots, Runnable onComplete) {
        running = false;
        cancelTask(animationTask);
        animationTask = null;
        ItemStack display = finalReward.getDisplayItem();
        for (int reel = 0; reel < TripleReelSchedule.REEL_COUNT; reel++) {
            inventory.setItem(slots.get(3 + reel), display.clone());
        }
        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
        closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeOwnInventory();
            completion.complete(onComplete);
        }, 30L);
    }

    private void finishInterrupted(Runnable onComplete) {
        cleanup(false);
        completion.complete(onComplete);
    }

    private ItemStack nextDisplayItem(List<Reward> rewards, Reward fallback) {
        Reward selected = RewardProbability.select(rewards, random);
        return (selected != null ? selected : fallback).getDisplayItem();
    }

    private List<Integer> resolveSlots(GuiConfig config, int size) {
        List<Integer> configured = config != null ? config.getAnimationSlots() : List.of();
        List<Integer> valid = new ArrayList<>(new LinkedHashSet<>(configured));
        valid.removeIf(slot -> slot == null || slot < 0 || slot >= size);
        return valid.size() >= 9 ? List.copyOf(valid.subList(0, 9)) : DEFAULT_SLOTS;
    }

    private void placeTemplateItems(GuiConfig config) {
        if (config == null) {
            return;
        }
        for (GuiItem item : config.getItems().values()) {
            if (item.getSlot() < 0 || item.getSlot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(item.getSlot(), new ItemBuilder(item.getMaterial())
                    .name(item.getName())
                    .lore(item.getLore())
                    .customModelData(item.getCustomModelData())
                    .itemModel(item.getItemModel())
                    .glow(item.isGlow())
                    .build());
        }
    }

    private void closeOwnInventory() {
        if (player != null && player.isOnline()
                && player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    private void cleanup(boolean closeInventory) {
        running = false;
        cancelTask(animationTask);
        cancelTask(closeTask);
        animationTask = null;
        closeTask = null;
        if (closeInventory) {
            closeOwnInventory();
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public void cancel() {
        cleanup(true);
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
