package gg.fotia.crates.crate;

import gg.fotia.crates.animation.AnimationType;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.*;

public class Crate {

    private final String id;
    private final String name;
    private final Material blockMaterial;
    private final String blockItemName;
    private final List<String> blockItemLore;
    private final boolean modelEngineEnabled;
    private final String modelEngineId;
    private final String modelEngineIdleAnimation;
    private final String modelEngineOpenAnimation;
    private final int modelEngineOpenDelay;
    private final int modelEngineViewRange; // 模型可视距离
    private final double physicalAnimationHeight;
    private final double hologramHeight;
    private final List<String> hologramLines;
    private final List<Reward> rewards;
    private final boolean previewEnabled;
    private final boolean showChance;
    private final String previewTitle;
    private final boolean animationEnabled;
    private final AnimationType animationType;
    private final int animationDuration;
    private final String animationTitle;
    private final boolean physicalAnimationEnabled;
    private final boolean particlesEnabled;
    private final Particle particleType;
    private final int particleCount;
    private final Sound spinSound;
    private final float spinVolume;
    private final float spinPitch;
    private final Sound winSound;
    private final float winVolume;
    private final float winPitch;
    private final boolean pityEnabled;
    private final List<PityTier> pityTiers; // 多级保底
    private final boolean multiOpenEnabled;
    private final int multiOpenMax;
    private final String permission; // 开箱权限节点
    private final Random random = new Random();

    /**
     * 保底等级
     */
    public static class PityTier {
        private final int count;
        private final String rarity;

        public PityTier(int count, String rarity) {
            this.count = count;
            this.rarity = rarity;
        }

        public int getCount() { return count; }
        public String getRarity() { return rarity; }
    }

    public Crate(String id, String name, Material blockMaterial,
                 String blockItemName, List<String> blockItemLore,
                 boolean modelEngineEnabled, String modelEngineId,
                 String modelEngineIdleAnimation, String modelEngineOpenAnimation,
                 int modelEngineOpenDelay, int modelEngineViewRange, double physicalAnimationHeight,
                 double hologramHeight, List<String> hologramLines,
                 List<Reward> rewards, boolean previewEnabled, boolean showChance, String previewTitle,
                 boolean animationEnabled, AnimationType animationType, int animationDuration,
                 String animationTitle, boolean physicalAnimationEnabled,
                 boolean particlesEnabled, Particle particleType, int particleCount,
                 Sound spinSound, float spinVolume, float spinPitch,
                 Sound winSound, float winVolume, float winPitch,
                 boolean pityEnabled, List<PityTier> pityTiers,
                 boolean multiOpenEnabled, int multiOpenMax, String permission) {
        this.id = id;
        this.name = name;
        this.blockMaterial = blockMaterial;
        this.blockItemName = blockItemName;
        this.blockItemLore = blockItemLore != null ? blockItemLore : new ArrayList<>();
        this.modelEngineEnabled = modelEngineEnabled;
        this.modelEngineId = modelEngineId;
        this.modelEngineIdleAnimation = modelEngineIdleAnimation;
        this.modelEngineOpenAnimation = modelEngineOpenAnimation;
        this.modelEngineOpenDelay = modelEngineOpenDelay;
        this.modelEngineViewRange = modelEngineViewRange;
        this.physicalAnimationHeight = physicalAnimationHeight;
        this.hologramHeight = hologramHeight;
        this.hologramLines = hologramLines != null ? hologramLines : new ArrayList<>();
        this.rewards = rewards != null ? rewards : new ArrayList<>();
        this.previewEnabled = previewEnabled;
        this.showChance = showChance;
        this.previewTitle = previewTitle;
        this.animationEnabled = animationEnabled;
        this.animationType = animationType;
        this.animationDuration = animationDuration;
        this.animationTitle = animationTitle;
        this.physicalAnimationEnabled = physicalAnimationEnabled;
        this.particlesEnabled = particlesEnabled;
        this.particleType = particleType;
        this.particleCount = particleCount;
        this.spinSound = spinSound;
        this.spinVolume = spinVolume;
        this.spinPitch = spinPitch;
        this.winSound = winSound;
        this.winVolume = winVolume;
        this.winPitch = winPitch;
        this.pityEnabled = pityEnabled;
        this.pityTiers = pityTiers != null ? pityTiers : new ArrayList<>();
        this.multiOpenEnabled = multiOpenEnabled;
        this.multiOpenMax = multiOpenMax;
        this.permission = permission;
    }

    public Reward rollReward() {
        double totalChance = rewards.stream().mapToDouble(Reward::getChance).sum();
        double roll = random.nextDouble() * totalChance;
        double cumulative = 0;

        for (Reward reward : rewards) {
            cumulative += reward.getChance();
            if (roll < cumulative) {
                return reward;
            }
        }

        return rewards.isEmpty() ? null : rewards.get(rewards.size() - 1);
    }

    /**
     * 根据当前抽奖次数获取应该触发的保底等级
     * @param currentCount 当前抽奖次数
     * @return 触发的保底等级，如果没有触发则返回null
     */
    public PityTier getTriggeredPityTier(int currentCount) {
        if (!pityEnabled || pityTiers.isEmpty()) return null;

        // 按次数从小到大排序
        List<PityTier> sortedTiers = new ArrayList<>(pityTiers);
        sortedTiers.sort(Comparator.comparingInt(PityTier::getCount));

        // 检查是否触发某个保底
        for (PityTier tier : sortedTiers) {
            if (currentCount > 0 && currentCount % tier.getCount() == 0) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 获取最高级保底的次数（用于重置计数）
     */
    public int getMaxPityCount() {
        if (pityTiers.isEmpty()) return 0;
        return pityTiers.stream().mapToInt(PityTier::getCount).max().orElse(0);
    }

    /**
     * 根据指定稀有度抽取保底奖励
     */
    public Reward rollPityReward(String targetRarity) {
        List<Reward> pityRewards = rewards.stream()
                .filter(r -> r.getRarity().equalsIgnoreCase(targetRarity) ||
                        isRarityHigherOrEqual(r.getRarity(), targetRarity))
                .toList();

        if (pityRewards.isEmpty()) {
            return rollReward();
        }

        double totalChance = pityRewards.stream().mapToDouble(Reward::getChance).sum();
        double roll = random.nextDouble() * totalChance;
        double cumulative = 0;

        for (Reward reward : pityRewards) {
            cumulative += reward.getChance();
            if (roll < cumulative) {
                return reward;
            }
        }

        return pityRewards.get(pityRewards.size() - 1);
    }

    /**
     * 旧版兼容方法
     */
    @Deprecated
    public Reward rollPityReward() {
        if (pityTiers.isEmpty()) return rollReward();
        // 使用最高级保底的稀有度
        PityTier highestTier = pityTiers.stream()
                .max(Comparator.comparingInt(PityTier::getCount))
                .orElse(null);
        if (highestTier == null) return rollReward();
        return rollPityReward(highestTier.getRarity());
    }

    private boolean isRarityHigherOrEqual(String rarity, String target) {
        return getRarityLevel(rarity) >= getRarityLevel(target);
    }

    private int getRarityLevel(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "common" -> 1;
            case "uncommon" -> 2;
            case "rare" -> 3;
            case "epic" -> 4;
            case "legendary" -> 5;
            default -> 0;
        };
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getBlockMaterial() { return blockMaterial; }
    public String getBlockItemName() { return blockItemName; }
    public List<String> getBlockItemLore() { return new ArrayList<>(blockItemLore); }
    public boolean isModelEngineEnabled() { return modelEngineEnabled; }
    public String getModelEngineId() { return modelEngineId; }
    public String getModelEngineIdleAnimation() { return modelEngineIdleAnimation; }
    public String getModelEngineOpenAnimation() { return modelEngineOpenAnimation; }
    public int getModelEngineOpenDelay() { return modelEngineOpenDelay; }
    public int getModelEngineViewRange() { return modelEngineViewRange; }
    public double getPhysicalAnimationHeight() { return physicalAnimationHeight; }
    public double getHologramHeight() { return hologramHeight; }
    public List<String> getHologramLines() { return new ArrayList<>(hologramLines); }
    public List<Reward> getRewards() { return new ArrayList<>(rewards); }
    public boolean isPreviewEnabled() { return previewEnabled; }
    public boolean isShowChance() { return showChance; }
    public String getPreviewTitle() { return previewTitle; }
    public boolean isAnimationEnabled() { return animationEnabled; }
    public AnimationType getAnimationType() { return animationType; }
    public int getAnimationDuration() { return animationDuration; }
    public String getAnimationTitle() { return animationTitle; }
    public boolean isPhysicalAnimationEnabled() { return physicalAnimationEnabled; }
    public boolean isParticlesEnabled() { return particlesEnabled; }
    public Particle getParticleType() { return particleType; }
    public int getParticleCount() { return particleCount; }
    public Sound getSpinSound() { return spinSound; }
    public float getSpinVolume() { return spinVolume; }
    public float getSpinPitch() { return spinPitch; }
    public Sound getWinSound() { return winSound; }
    public float getWinVolume() { return winVolume; }
    public float getWinPitch() { return winPitch; }
    public boolean isPityEnabled() { return pityEnabled; }
    public List<PityTier> getPityTiers() { return new ArrayList<>(pityTiers); }
    public boolean isMultiOpenEnabled() { return multiOpenEnabled; }
    public int getMultiOpenMax() { return multiOpenMax; }
    public String getPermission() { return permission; }

    // 兼容旧版
    @Deprecated
    public int getPityCount() {
        return getMaxPityCount();
    }

    @Deprecated
    public String getPityRarity() {
        if (pityTiers.isEmpty()) return "legendary";
        return pityTiers.stream()
                .max(Comparator.comparingInt(PityTier::getCount))
                .map(PityTier::getRarity)
                .orElse("legendary");
    }
}
