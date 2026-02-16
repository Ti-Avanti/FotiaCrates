package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.gui.CrateGuiHolder;
import gg.fotia.crates.gui.GuiConfig;
import gg.fotia.crates.gui.GuiItem;
import gg.fotia.crates.gui.GuiType;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.util.ItemBuilder;
import gg.fotia.crates.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class RouletteAnimation implements Animation {

    private final FotiaCrates plugin;
    private BukkitTask task;
    private boolean running = false;
    private Inventory inventory;
    private Player player;
    private List<Integer> animationSlots;
    private int centerSlot;
    private boolean finalRewardPlaced = false; // 标记最终奖励是否已放入

    public RouletteAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation, Runnable onComplete) {
        this.player = player;
        this.running = true;
        this.finalRewardPlaced = false; // 重置标记

        // 从GUI配置读取动画设置
        GuiConfig animConfig = plugin.getGuiManager().getConfigManager().getGuiConfig("animation");

        // 优先使用宝箱配置的标题，否则使用GUI配置的标题
        String title = crate.getAnimationTitle();
        if (title == null || title.isEmpty()) {
            title = "<!i><dark_gray>正在抽奖...";
        }
        int size = 27;
        Material borderMaterial = Material.BLACK_STAINED_GLASS_PANE;
        String borderName = " ";
        Material indicatorMaterial = Material.YELLOW_STAINED_GLASS_PANE;
        String indicatorName = " ";

        // 默认动画槽位和中心槽位
        this.animationSlots = List.of(10, 11, 12, 13, 14, 15, 16);
        this.centerSlot = 13;

        if (animConfig != null) {
            // 如果宝箱没有配置标题，使用GUI配置的标题
            if (crate.getAnimationTitle() == null || crate.getAnimationTitle().isEmpty()) {
                title = animConfig.getTitle();
            }
            size = animConfig.getSize();

            // 读取动画槽位配置
            List<Integer> configSlots = animConfig.getAnimationSlots();
            if (!configSlots.isEmpty()) {
                this.animationSlots = configSlots;
            }
            // 读取中心槽位配置
            int configCenterSlot = animConfig.getCenterSlot();
            if (configCenterSlot >= 0) {
                this.centerSlot = configCenterSlot;
            }

            // 从配置的物品中获取边框和指示器设置
            Map<Integer, GuiItem> items = animConfig.getItems();
            for (GuiItem item : items.values()) {
                // 查找边框物品（通常在槽位0）
                if (item.getSlot() == 0) {
                    borderMaterial = item.getMaterial();
                    borderName = item.getName();
                }
                // 查找指示器物品（通常在槽位4）
                if (item.getSlot() == 4) {
                    indicatorMaterial = item.getMaterial();
                    indicatorName = item.getName();
                }
            }
        }

        Component titleComponent = MessageUtil.parse(title);
        inventory = Bukkit.createInventory(new CrateGuiHolder(GuiType.ANIMATION, crate), size, titleComponent);

        // 使用配置的边框物品填充GUI
        if (animConfig != null) {
            Map<Integer, GuiItem> items = animConfig.getItems();
            for (Map.Entry<Integer, GuiItem> entry : items.entrySet()) {
                int slot = entry.getKey();
                GuiItem item = entry.getValue();
                ItemStack itemStack = new ItemBuilder(item.getMaterial()).name(item.getName()).build();
                inventory.setItem(slot, itemStack);
            }
        } else {
            // 默认边框
            ItemStack border = new ItemBuilder(borderMaterial).name(borderName).build();
            for (int i = 0; i < 9; i++) {
                inventory.setItem(i, border);
                inventory.setItem(18 + i, border);
            }
            inventory.setItem(9, border);
            inventory.setItem(17, border);

            // 使用配置的指示器物品
            ItemStack indicator = new ItemBuilder(indicatorMaterial).name(indicatorName).build();
            inventory.setItem(4, indicator);
            inventory.setItem(22, indicator);
        }

        player.openInventory(inventory);

        List<Reward> rewards = crate.getRewards();
        Random random = new Random();

        // 动画时长（秒），至少5秒
        int durationSeconds = Math.max(crate.getAnimationDuration(), 5);
        int totalTicks = durationSeconds * 20;

        runAnimationStep(player, crate, finalReward, rewards, random, 0, totalTicks, 1, 0, onComplete);
    }

    private void runAnimationStep(Player player, Crate crate, Reward finalReward,
                                   List<Reward> rewards, Random random,
                                   int currentStep, int totalTicks, int currentDelay, int elapsedTicks, Runnable onComplete) {
        if (!running || !player.isOnline()) {
            return;
        }

        // 计算进度
        float progress = (float) elapsedTicks / totalTicks;

        // 计算距离结束还有多少tick
        int remainingTicks = totalTicks - elapsedTicks;
        // 估算剩余滚动次数（基于当前delay）
        int estimatedRemainingSteps = remainingTicks / Math.max(currentDelay, 1);

        // 计算中心槽位在动画槽位列表中的索引
        int centerIndex = animationSlots.indexOf(centerSlot);
        if (centerIndex < 0) {
            centerIndex = animationSlots.size() / 2; // 默认使用中间位置
        }
        // 从最后一个槽位滚动到中心需要的步数
        int stepsToCenter = animationSlots.size() - 1 - centerIndex;

        // 只在恰好需要放入最终奖励时放入一次，之后不再放入
        boolean placeForFinal = false;
        if (!finalRewardPlaced && estimatedRemainingSteps <= stepsToCenter + 1 && estimatedRemainingSteps > 0) {
            placeForFinal = true;
            finalRewardPlaced = true;
        }

        shiftItems(rewards, random, finalReward, placeForFinal);

        if (crate.getSpinSound() != null) {
            player.playSound(player.getLocation(), crate.getSpinSound(),
                    crate.getSpinVolume(), crate.getSpinPitch());
        }

        // 计算下一次的delay（逐渐减速）
        int nextDelay;
        if (progress < 0.3f) {
            nextDelay = 1;
        } else if (progress < 0.5f) {
            nextDelay = 2;
        } else if (progress < 0.65f) {
            nextDelay = 3;
        } else if (progress < 0.75f) {
            nextDelay = 5;
        } else if (progress < 0.85f) {
            nextDelay = 8;
        } else {
            nextDelay = 12;
        }

        int nextElapsedTicks = elapsedTicks + currentDelay;

        if (nextElapsedTicks >= totalTicks) {
            running = false;

            // 强制确保中心位置显示最终奖励
            inventory.setItem(centerSlot, finalReward.getDisplayItem());

            if (crate.getWinSound() != null) {
                player.playSound(player.getLocation(), crate.getWinSound(),
                        crate.getWinVolume(), crate.getWinPitch());
            }

            if (crate.isParticlesEnabled()) {
                player.getWorld().spawnParticle(crate.getParticleType(),
                        player.getLocation().add(0, 1, 0),
                        crate.getParticleCount(), 0.5, 0.5, 0.5, 0.1);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.closeInventory();
                onComplete.run();
            }, 40L);
            return;
        }

        int finalNextDelay = nextDelay;
        int finalNextElapsedTicks = nextElapsedTicks;
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runAnimationStep(player, crate, finalReward, rewards, random, currentStep + 1, totalTicks, finalNextDelay, finalNextElapsedTicks, onComplete);
        }, currentDelay);
    }

    private void shiftItems(List<Reward> rewards, Random random, Reward finalReward, boolean placeForFinal) {
        // 从第一个槽位开始，每个槽位获取下一个槽位的物品
        for (int i = 0; i < animationSlots.size() - 1; i++) {
            int currentSlot = animationSlots.get(i);
            int nextSlot = animationSlots.get(i + 1);
            inventory.setItem(currentSlot, inventory.getItem(nextSlot));
        }

        // 在最后一个槽位放入新物品
        int lastSlot = animationSlots.get(animationSlots.size() - 1);
        ItemStack newItem;
        if (placeForFinal) {
            // 在正确时机放入最终奖励，让它自然滚动到中间
            newItem = finalReward.getDisplayItem();
        } else {
            // 随机选择一个奖励（排除最终奖励，增加悬念）
            Reward randomReward;
            if (rewards.size() > 1) {
                do {
                    randomReward = rewards.get(random.nextInt(rewards.size()));
                } while (randomReward.getId().equals(finalReward.getId()) && random.nextInt(3) != 0);
            } else {
                randomReward = rewards.get(0);
            }
            newItem = randomReward.getDisplayItem();
        }
        inventory.setItem(lastSlot, newItem);
    }

    @Override
    public void cancel() {
        running = false;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
