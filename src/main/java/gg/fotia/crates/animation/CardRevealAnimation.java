package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.gui.CrateGuiHolder;
import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiType;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class CardRevealAnimation implements BatchAnimation, InteractiveAnimation {

    private static final List<Integer> DEFAULT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private final FotiaCrates plugin;
    private final Random random = new Random();
    private AnimationCompletion completion = new AnimationCompletion();
    private Inventory inventory;
    private CardRevealBoard board;
    private CardRevealSelection selection;
    private CardAnimationSettings settings;
    private List<Reward> finalRewards = List.of();
    private List<Reward> displayRewards = List.of();
    private List<Integer> allowedSlots = List.of();
    private List<Integer> activeSlots = List.of();
    private Player player;
    private Crate crate;
    private Runnable onComplete;
    private Phase phase = Phase.COMPLETE;
    private BukkitTask phaseTask;
    private BukkitTask timeoutTask;
    private BukkitTask watchdogTask;
    private BukkitTask closeTask;
    private boolean running;

    public CardRevealAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void startBatch(Player player, Crate crate, List<Reward> finalRewards,
                           Location crateLocation, Runnable onComplete) {
        if (finalRewards == null || finalRewards.isEmpty()) {
            onComplete.run();
            return;
        }
        this.player = player;
        this.crate = crate;
        this.finalRewards = List.copyOf(finalRewards);
        this.onComplete = onComplete;
        this.completion = new AnimationCompletion();
        this.running = true;

        AnimationTemplate template = plugin.getGuiManager().getConfigManager()
                .getAnimationTemplate(crate.getAnimationTemplate(), AnimationType.CARD_REVEAL);
        GuiConfig config = template != null ? template.guiConfig() : null;
        this.settings = template != null
                ? template.cardSettings()
                : CardAnimationSettings.from(null);
        int size = Math.max(45, config != null ? config.getSize() : 45);
        String title = config != null ? config.getTitle() : "<!i><dark_gray>神秘卡牌";
        inventory = Bukkit.createInventory(
                new CrateGuiHolder(GuiType.ANIMATION, crate), size, MessageUtil.parse(title));
        AnimationTemplateRenderer.render(inventory, config);

        allowedSlots = resolveSlots(config, size);
        activeSlots = chooseSlots(this.finalRewards.size());
        selection = new CardRevealSelection(activeSlots, this.finalRewards.size());
        board = new CardRevealBoard(inventory, settings);
        board.useSlots(activeSlots);
        List<Reward> available = crate.getAvailableRewardsFor(player);
        displayRewards = available.isEmpty() ? this.finalRewards : List.copyOf(available);

        player.openInventory(inventory);
        startWatchdog();
        beginFlicker();
    }

    private void beginFlicker() {
        if (!isViewAvailable()) {
            finishInterrupted();
            return;
        }
        phase = Phase.FLICKERING;
        board.status(settings.flickerStatusName(), selection.revealedCount(),
                selection.rewardCount());
        List<List<Integer>> frames = CardFlickerSequence.create(
                displayRewards.size(), activeSlots.size(), settings.flickerMinCycles(),
                minimumFlickerFrames(), random);
        if (frames.isEmpty()) {
            beginCover();
            return;
        }

        int[] frameIndex = {0};
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isViewAvailable()) {
                finishInterrupted();
                return;
            }
            if (frameIndex[0] >= frames.size()) {
                cancelTask(phaseTask);
                phaseTask = null;
                beginCover();
                return;
            }
            board.showFrame(displayRewards, frames.get(frameIndex[0]));
            if ((frameIndex[0] & 1) == 0) {
                playSpinSound();
            }
            frameIndex[0]++;
        }, 0L, settings.flickerIntervalTicks());
    }

    private void beginCover() {
        if (!isViewAvailable()) {
            finishInterrupted();
            return;
        }
        phase = Phase.COVERING;
        board.status(settings.coverStatusName(), selection.revealedCount(),
                selection.rewardCount());
        board.cover(false);
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin,
                this::beginSelection, settings.coverDelayTicks());
    }

    private void beginSelection() {
        if (!isViewAvailable()) {
            finishInterrupted();
            return;
        }
        phase = Phase.SELECTING;
        updateSelectionStatus();
        armSelectionTimeout();
    }

    @Override
    public boolean handleClick(Player clickingPlayer, int slot) {
        if (!running || phase != Phase.SELECTING || player == null
                || !player.getUniqueId().equals(clickingPlayer.getUniqueId())) {
            return false;
        }
        CardRevealSelection.Selection revealed = selection.select(slot).orElse(null);
        if (revealed == null) {
            return false;
        }

        cancelTask(timeoutTask);
        timeoutTask = null;
        board.reveal(slot, finalRewards.get(revealed.rewardIndex()));
        playRevealSound(revealed.rewardIndex());
        updateSelectionStatus();
        if (revealed.allRewardsRevealed()) {
            completeSelection();
        } else if (revealed.pageComplete()) {
            phase = Phase.PAGE_TRANSITION;
            phaseTask = Bukkit.getScheduler().runTaskLater(plugin,
                    this::beginNextSelectionPage, settings.pageTransitionTicks());
        } else {
            armSelectionTimeout();
        }
        return true;
    }

    private void beginNextSelectionPage() {
        if (!isViewAvailable()) {
            finishInterrupted();
            return;
        }
        prepareNextPage();
        beginFlicker();
    }

    private void prepareNextPage() {
        int remaining = selection.rewardCount() - selection.revealedCount();
        activeSlots = chooseSlots(remaining);
        selection.startNextPage(activeSlots);
        board.useSlots(activeSlots);
    }

    private void armSelectionTimeout() {
        cancelTask(timeoutTask);
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                this::beginAutoReveal, settings.selectionTimeoutTicks());
    }

    private void beginAutoReveal() {
        if (!isViewAvailable()) {
            finishInterrupted();
            return;
        }
        phase = Phase.AUTO_REVEAL;
        timeoutTask = null;
        revealNextAutomatically();
    }

    private void revealNextAutomatically() {
        if (!isViewAvailable()) {
            finishInterrupted();
            return;
        }
        if (selection.waitingForNextPage()) {
            phaseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isViewAvailable()) {
                    finishInterrupted();
                    return;
                }
                prepareNextPage();
                board.cover(false);
                revealNextAutomatically();
            }, settings.pageTransitionTicks());
            return;
        }

        List<Integer> available = selection.availableSlots();
        if (available.isEmpty()) {
            completeSelection();
            return;
        }
        int randomSlot = available.get(random.nextInt(available.size()));
        CardRevealSelection.Selection revealed = selection.select(randomSlot).orElseThrow();
        board.reveal(revealed.slot(), finalRewards.get(revealed.rewardIndex()));
        playRevealSound(revealed.rewardIndex());
        updateSelectionStatus();
        if (revealed.allRewardsRevealed()) {
            completeSelection();
            return;
        }
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin,
                this::revealNextAutomatically, settings.revealIntervalTicks());
    }

    private void completeSelection() {
        if (!running || phase == Phase.COMPLETE) {
            return;
        }
        phase = Phase.COMPLETE;
        cancelTask(phaseTask);
        cancelTask(timeoutTask);
        phaseTask = null;
        timeoutTask = null;
        board.status(settings.completeStatusName(), selection.revealedCount(),
                selection.rewardCount());
        playWinSound();
        closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            running = false;
            cancelTask(watchdogTask);
            watchdogTask = null;
            closeOwnInventory();
            completion.complete(onComplete);
        }, settings.resultHoldTicks());
    }

    private void updateSelectionStatus() {
        board.status(settings.selectStatusName(), selection.revealedCount(),
                selection.rewardCount());
    }

    private List<Integer> chooseSlots(int remainingRewards) {
        List<Integer> shuffled = new ArrayList<>(allowedSlots);
        Collections.shuffle(shuffled, random);
        int required = Math.min(Math.max(1, remainingRewards), shuffled.size());
        int count = Math.min(shuffled.size(), Math.max(settings.cardCount(), required));
        List<Integer> selected = new ArrayList<>(shuffled.subList(0, count));
        Collections.sort(selected);
        return List.copyOf(selected);
    }

    private int minimumFlickerFrames() {
        return Math.max(1, (int) Math.ceil(
                (double) settings.flickerMinTicks() / settings.flickerIntervalTicks()));
    }

    private void startWatchdog() {
        watchdogTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (running && !isViewAvailable()) {
                finishInterrupted();
            }
        }, 5L, 5L);
    }

    private void playSpinSound() {
        if (crate.getSpinSound() != null) {
            player.playSound(player.getLocation(), crate.getSpinSound(),
                    crate.getSpinVolume(), crate.getSpinPitch());
        }
    }

    private void playRevealSound(int rewardIndex) {
        if (crate.getSpinSound() != null) {
            player.playSound(player.getLocation(), crate.getSpinSound(), crate.getSpinVolume(),
                    Math.min(2.0f, crate.getSpinPitch() + rewardIndex * 0.04f));
        }
    }

    private void playWinSound() {
        if (crate.getWinSound() != null) {
            player.playSound(player.getLocation(), crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }
    }

    private List<Integer> resolveSlots(GuiConfig config, int size) {
        List<Integer> configured = config != null ? config.getAnimationSlots() : List.of();
        return AnimationSlotResolver.forCards(configured, size, DEFAULT_SLOTS).slots();
    }

    private boolean isViewAvailable() {
        return running && player != null && player.isOnline()
                && player.getOpenInventory().getTopInventory() == inventory;
    }

    private void finishInterrupted() {
        cleanup(false);
        completion.complete(onComplete);
    }

    private void closeOwnInventory() {
        if (player != null && player.isOnline()
                && player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    private void cleanup(boolean closeInventory) {
        running = false;
        cancelTask(phaseTask);
        cancelTask(timeoutTask);
        cancelTask(watchdogTask);
        cancelTask(closeTask);
        phaseTask = null;
        timeoutTask = null;
        watchdogTask = null;
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

    private enum Phase {
        FLICKERING,
        COVERING,
        SELECTING,
        AUTO_REVEAL,
        PAGE_TRANSITION,
        COMPLETE
    }
}
