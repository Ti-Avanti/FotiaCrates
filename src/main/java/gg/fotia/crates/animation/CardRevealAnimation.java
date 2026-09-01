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

public final class CardRevealAnimation implements Animation {

    private static final List<Integer> DEFAULT_SLOTS =
            List.of(10, 12, 14, 19, 22, 25, 28, 30, 32);

    private final FotiaCrates plugin;
    private final Random random = new Random();
    private AnimationCompletion completion = new AnimationCompletion();
    private Inventory inventory;
    private Player player;
    private BukkitTask revealTask;
    private BukkitTask closeTask;
    private boolean running;

    public CardRevealAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation,
                      Runnable onComplete) {
        this.player = player;
        this.completion = new AnimationCompletion();
        this.running = true;

        AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                .getAnimationTemplate(crate.getAnimationTemplate(), AnimationType.CARD_REVEAL);
        GuiConfig config = template != null ? template.guiConfig() : null;
        CardAnimationSettings settings = template != null
                ? template.cardSettings()
                : CardAnimationSettings.from(null);
        int size = Math.max(45, config != null ? config.getSize() : 45);
        String title = config != null ? config.getTitle() : "<!i><dark_gray>神秘卡牌";
        inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ANIMATION, crate), size, MessageUtil.parse(title));
        placeTemplateItems(config);

        List<Integer> slots = resolveSlots(config, size);
        int winnerSlot = resolveWinnerSlot(config, slots);
        ItemStack cardBack = new ItemBuilder(settings.backMaterial())
                .name(settings.backName())
                .build();
        for (int slot : slots) {
            inventory.setItem(slot, cardBack.clone());
        }
        player.openInventory(inventory);

        List<Reward> decoys = crate.getAvailableRewardsFor(player).stream()
                .filter(reward -> !reward.getId().equals(finalReward.getId()))
                .toList();
        List<Integer> revealOrder = CardRevealSequence.create(slots, winnerSlot, random);
        int[] revealIndex = {0};
        int durationTicks = Math.max(40, Math.max(1, crate.getAnimationDuration()) * 20);
        CardRevealSchedule schedule = CardRevealSchedule.create(
                durationTicks, revealOrder.size(), settings.revealIntervalTicks(), 20);
        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) {
                return;
            }
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                finishInterrupted(onComplete);
                return;
            }
            int slot = revealOrder.get(revealIndex[0]);
            boolean winner = revealIndex[0] == revealOrder.size() - 1;
            inventory.setItem(slot, winner
                    ? finalReward.getDisplayItem()
                    : nextDisplayItem(decoys, finalReward));
            if (crate.getSpinSound() != null) {
                player.playSound(player.getLocation(), crate.getSpinSound(),
                        crate.getSpinVolume(), crate.getSpinPitch());
            }
            revealIndex[0]++;
            if (revealIndex[0] >= revealOrder.size()) {
                finish(crate, onComplete);
            }
        }, schedule.startDelayTicks(), schedule.revealIntervalTicks());
    }

    private void finish(Crate crate, Runnable onComplete) {
        running = false;
        cancelTask(revealTask);
        revealTask = null;
        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
        closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeOwnInventory();
            completion.complete(onComplete);
        }, 20L);
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

    private int resolveWinnerSlot(GuiConfig config, List<Integer> slots) {
        int configured = config != null ? config.getCenterSlot() : -1;
        return slots.contains(configured) ? configured : slots.get(slots.size() / 2);
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
        cancelTask(revealTask);
        cancelTask(closeTask);
        revealTask = null;
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
