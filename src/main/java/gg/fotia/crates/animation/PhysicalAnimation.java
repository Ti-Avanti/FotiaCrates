package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.util.ChestLidUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物理动画 - 在宝箱上方显示多个漂浮物品滚动效果
 * 使用 ItemDisplay 实体（1.19.4+）
 * 物品水平排列在玩家面前，中间物品放大显示
 */
public class PhysicalAnimation implements Animation {

    private final FotiaCrates plugin;
    private boolean running = false;
    private BukkitTask animationTask;
    private final List<ItemDisplay> displayEntities = new ArrayList<>();
    private Location crateLocation;
    private Runnable onComplete;
    private Player targetPlayer; // 目标玩家

    // 滚动显示的物品数量（奇数，中间为选中物品）
    private static final int DISPLAY_COUNT = 9;
    // 物品之间的间距
    private static final double ITEM_SPACING = 0.42;
    // 普通物品大小
    private static final float ITEM_SCALE = 0.35f;
    // 中间物品大小（放大）
    private static final float CENTER_SCALE = 0.65f;
    // 物品高度
    private static final double HEIGHT_OFFSET = 1.5;
    // 默认动画时长（秒），如果配置的时间太短则使用此值
    private static final int MIN_DURATION_SECONDS = 5;

    public PhysicalAnimation(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * 设置完成回调
     */
    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    @Override
    public void start(Player player, Crate crate, Reward finalReward, Location crateLocation, Runnable onComplete) {
        this.running = true;
        this.crateLocation = crateLocation;
        this.onComplete = onComplete;
        this.targetPlayer = player;

        // 打开箱子盖子（只对该玩家显示）
        openChestLid(player, crateLocation);

        // 准备滚动物品列表
        List<Reward> rewards = crate.getRewards();
        List<ItemStack> allItems = new ArrayList<>();
        for (Reward reward : rewards) {
            allItems.add(reward.getDisplayItem());
        }

        // 多复制几份以便滚动
        List<ItemStack> displayItems = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<ItemStack> shuffled = new ArrayList<>(allItems);
            Collections.shuffle(shuffled);
            displayItems.addAll(shuffled);
        }
        // 确保最终奖励在列表末尾
        displayItems.add(finalReward.getDisplayItem());

        // 计算中心位置（宝箱上方，使用配置的高度）
        double heightOffset = crate.getPhysicalAnimationHeight();
        Location centerLoc = crateLocation.clone().add(0.5, heightOffset, 0.5);

        // 计算玩家面向宝箱的方向，物品水平排列（左右方向）
        // 获取从宝箱到玩家的方向向量
        Vector toPlayer = player.getLocation().toVector().subtract(centerLoc.toVector());
        toPlayer.setY(0); // 只考虑水平方向
        toPlayer.normalize();

        // 垂直于玩家视线的方向（左右方向）= 叉乘向上向量
        Vector right = toPlayer.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        double rightX = right.getX();
        double rightZ = right.getZ();

        // 生成多个 ItemDisplay 实体
        int centerIndex = DISPLAY_COUNT / 2;
        for (int i = 0; i < DISPLAY_COUNT; i++) {
            final int index = i;
            double offset = (i - centerIndex) * ITEM_SPACING;
            Location displayLoc = centerLoc.clone().add(rightX * offset, 0, rightZ * offset);

            // 计算物品大小（中间大，两边小）
            float scale = calculateScale(i, centerIndex);

            ItemDisplay display = crateLocation.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
                d.setItemStack(displayItems.get(index % displayItems.size()));
                d.setBillboard(Display.Billboard.CENTER);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                d.setViewRange(64);
                d.setShadowRadius(0);
                d.setShadowStrength(0);
                d.setPersistent(false);
            });

            // 只对开箱玩家显示，对其他玩家隐藏
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.hideEntity(plugin, display);
                }
            }

            displayEntities.add(display);
        }

        // 动画时长，至少5秒
        int durationSeconds = Math.max(crate.getAnimationDuration(), MIN_DURATION_SECONDS);
        int totalTicks = durationSeconds * 20;

        animationTask = new BukkitRunnable() {
            int tick = 0;
            int scrollCount = 0;
            int lastScrollTick = 0;

            // 当前显示的物品索引数组
            int[] currentItemIndices = new int[DISPLAY_COUNT];

            // 是否已放入最终奖励
            boolean finalRewardInserted = false;

            {
                // 初始化显示的物品
                for (int i = 0; i < DISPLAY_COUNT; i++) {
                    currentItemIndices[i] = i % (displayItems.size() - 1);
                }
            }

            @Override
            public void run() {
                if (!running || displayEntities.isEmpty()) {
                    cleanup();
                    if (PhysicalAnimation.this.onComplete != null) {
                        PhysicalAnimation.this.onComplete.run();
                    }
                    return;
                }

                float progress = (float) tick / totalTicks;

                // 计算当前滚动间隔（逐渐减速）
                int ticksPerScroll;
                if (progress < 0.3f) {
                    ticksPerScroll = 1;
                } else if (progress < 0.5f) {
                    ticksPerScroll = 2;
                } else if (progress < 0.65f) {
                    ticksPerScroll = 3;
                } else if (progress < 0.75f) {
                    ticksPerScroll = 5;
                } else if (progress < 0.85f) {
                    ticksPerScroll = 8;
                } else if (progress < 0.95f) {
                    ticksPerScroll = 12;
                } else {
                    ticksPerScroll = 20; // 最后阶段几乎不滚动
                }

                // 检查是否应该滚动
                boolean shouldScroll = (tick - lastScrollTick) >= ticksPerScroll;

                // 预估剩余可滚动次数
                int estimatedRemainingScrolls = estimateRemainingScrolls(tick, totalTicks);

                // 滚动逻辑
                if (shouldScroll && tick < totalTicks - 10) {
                    // 如果还没放入最终奖励，且剩余滚动次数接近需要的次数，放入最终奖励
                    if (!finalRewardInserted && estimatedRemainingScrolls <= centerIndex + 2) {
                        doScroll(true);
                        finalRewardInserted = true;
                    } else if (!finalRewardInserted) {
                        // 还没到放入最终奖励的时机，正常滚动
                        doScroll(false);
                    } else {
                        // 已经放入最终奖励，继续滚动但不再放入新的最终奖励
                        doScrollAfterFinal();
                    }
                    lastScrollTick = tick;
                }

                // 更新物品位置和大小（浮动效果）
                double yOffset = Math.sin(tick * 0.1) * 0.03;

                for (int i = 0; i < displayEntities.size(); i++) {
                    ItemDisplay display = displayEntities.get(i);
                    if (display == null || display.isDead()) continue;

                    double offset = (i - centerIndex) * ITEM_SPACING;
                    Location newLoc = centerLoc.clone().add(rightX * offset, yOffset, rightZ * offset);
                    display.teleport(newLoc);

                    float scale = calculateScale(i, centerIndex);
                    float rotation = 0;
                    if (i == centerIndex) {
                        rotation = (tick * 2) % 360;
                    }

                    display.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f((float) Math.toRadians(rotation), 0, 1, 0),
                            new Vector3f(scale, scale, scale),
                            new AxisAngle4f(0, 0, 1, 0)
                    ));
                }

                tick++;

                // 动画结束
                if (tick >= totalTicks) {
                    // 确保中间位置是最终奖励
                    ItemDisplay centerDisplay = displayEntities.get(centerIndex);
                    if (centerDisplay != null && !centerDisplay.isDead()) {
                        centerDisplay.setItemStack(finalReward.getDisplayItem());
                    }

                    // 延迟一点再完成动画
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        finishAnimation(player, crate, finalReward, centerLoc, centerIndex);
                    }, 10L);
                    cancel();
                }
            }

            private void doScroll(boolean insertFinalReward) {
                scrollCount++;

                // 所有物品向左移动一格
                for (int i = 0; i < DISPLAY_COUNT - 1; i++) {
                    currentItemIndices[i] = currentItemIndices[i + 1];
                }

                // 最右边放入新物品
                if (insertFinalReward) {
                    currentItemIndices[DISPLAY_COUNT - 1] = displayItems.size() - 1; // 最终奖励的索引
                } else {
                    // 放入随机物品（排除最终奖励）
                    currentItemIndices[DISPLAY_COUNT - 1] = (scrollCount * 7) % (displayItems.size() - 1);
                }

                // 更新显示
                for (int i = 0; i < displayEntities.size(); i++) {
                    ItemDisplay display = displayEntities.get(i);
                    if (display == null || display.isDead()) continue;
                    display.setItemStack(displayItems.get(currentItemIndices[i]));
                }

                // 播放滚动音效
                if (crate.getSpinSound() != null) {
                    player.playSound(centerLoc, crate.getSpinSound(),
                            crate.getSpinVolume() * 0.3f, crate.getSpinPitch());
                }
            }

            // 最终奖励放入后的滚动（不再放入新物品，让最终奖励自然滚动到中间）
            private void doScrollAfterFinal() {
                scrollCount++;

                // 所有物品向左移动一格
                for (int i = 0; i < DISPLAY_COUNT - 1; i++) {
                    currentItemIndices[i] = currentItemIndices[i + 1];
                }

                // 最右边放入随机物品（不是最终奖励）
                currentItemIndices[DISPLAY_COUNT - 1] = (scrollCount * 7) % (displayItems.size() - 1);

                // 更新显示
                for (int i = 0; i < displayEntities.size(); i++) {
                    ItemDisplay display = displayEntities.get(i);
                    if (display == null || display.isDead()) continue;
                    display.setItemStack(displayItems.get(currentItemIndices[i]));
                }

                // 播放滚动音效
                if (crate.getSpinSound() != null) {
                    player.playSound(centerLoc, crate.getSpinSound(),
                            crate.getSpinVolume() * 0.3f, crate.getSpinPitch());
                }
            }

            private int estimateRemainingScrolls(int currentTick, int totalTicks) {
                int scrolls = 0;
                int t = currentTick;
                while (t < totalTicks - 10) {
                    float p = (float) t / totalTicks;
                    int tps;
                    if (p < 0.3f) tps = 1;
                    else if (p < 0.5f) tps = 2;
                    else if (p < 0.65f) tps = 3;
                    else if (p < 0.75f) tps = 5;
                    else if (p < 0.85f) tps = 8;
                    else if (p < 0.95f) tps = 12;
                    else tps = 20;

                    if ((t - currentTick) % tps == 0) scrolls++;
                    t++;
                }
                return scrolls;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 计算物品大小（中间大，两边逐渐变小）
     */
    private float calculateScale(int index, int centerIndex) {
        int distance = Math.abs(index - centerIndex);
        if (distance == 0) {
            return CENTER_SCALE;
        } else if (distance == 1) {
            return ITEM_SCALE * 1.15f;
        } else if (distance == 2) {
            return ITEM_SCALE * 1.0f;
        } else if (distance == 3) {
            return ITEM_SCALE * 0.85f;
        } else {
            return ITEM_SCALE * 0.7f;
        }
    }

    /**
     * 完成动画，展示最终奖励
     */
    private void finishAnimation(Player player, Crate crate, Reward finalReward, Location centerLoc, int centerIndex) {
        // 移除两边的物品，只保留中间的最终奖励
        for (int i = 0; i < displayEntities.size(); i++) {
            ItemDisplay display = displayEntities.get(i);
            if (display == null || display.isDead()) continue;

            if (i == centerIndex) {
                // 中间物品：设置为最终奖励，放大显示
                display.setItemStack(finalReward.getDisplayItem());
                display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(CENTER_SCALE * 1.5f, CENTER_SCALE * 1.5f, CENTER_SCALE * 1.5f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
            } else {
                // 两边物品：移除
                display.remove();
            }
        }

        // 播放获胜音效
        if (crate.getWinSound() != null) {
            player.playSound(centerLoc, crate.getWinSound(),
                    crate.getWinVolume(), crate.getWinPitch());
        }

        // 延迟后清理
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            if (onComplete != null) {
                onComplete.run();
            }
        }, 50L);
    }

    /**
     * 打开箱子盖子（只对特定玩家显示）
     */
    private void openChestLid(Player player, Location location) {
        if (location == null || player == null) return;
        if (ChestLidUtil.isProtocolLibAvailable()) {
            ChestLidUtil.openChestLid(player, location);
        }
    }

    /**
     * 关闭箱子盖子（只对特定玩家显示）
     */
    private void closeChestLid(Player player, Location location) {
        if (location == null || player == null) return;
        if (ChestLidUtil.isProtocolLibAvailable()) {
            ChestLidUtil.closeChestLid(player, location);
        }
    }

    /**
     * 清理动画资源
     */
    private void cleanup() {
        running = false;
        for (ItemDisplay display : displayEntities) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        displayEntities.clear();
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
            animationTask = null;
        }
        closeChestLid(targetPlayer, crateLocation);
    }

    @Override
    public void cancel() {
        cleanup();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
