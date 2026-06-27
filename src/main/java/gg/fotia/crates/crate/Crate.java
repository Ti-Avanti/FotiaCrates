package gg.fotia.crates.crate;

import gg.fotia.crates.animation.AnimationType;
import gg.fotia.crates.particle.CrateParticleEffect;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.reward.PermissionAction;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class Crate {

    private final String id;
    private final String name;
    private final Material blockMaterial;
    private final String blockItemName;
    private final List<String> blockItemLore;
    private final String modelProvider;
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
    private final Map<ParticleStage, CrateParticleEffect> particleEffects;
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
    private final List<String> rarityOrder;
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
                 String modelProvider, boolean modelEngineEnabled, String modelEngineId,
                 String modelEngineIdleAnimation, String modelEngineOpenAnimation,
                 int modelEngineOpenDelay, int modelEngineViewRange, double physicalAnimationHeight,
                 double hologramHeight, List<String> hologramLines,
                 List<Reward> rewards, boolean previewEnabled, boolean showChance, String previewTitle,
                 boolean animationEnabled, AnimationType animationType, int animationDuration,
                 String animationTitle, boolean physicalAnimationEnabled,
                 boolean particlesEnabled, Particle particleType, int particleCount,
                 Map<ParticleStage, CrateParticleEffect> particleEffects,
                 Sound spinSound, float spinVolume, float spinPitch,
                 Sound winSound, float winVolume, float winPitch,
                 boolean pityEnabled, List<PityTier> pityTiers,
                 boolean multiOpenEnabled, int multiOpenMax, String permission,
                 List<String> rarityOrder) {
        this.id = id;
        this.name = name;
        this.blockMaterial = blockMaterial;
        this.blockItemName = blockItemName;
        this.blockItemLore = blockItemLore != null ? blockItemLore : new ArrayList<>();
        this.modelProvider = normalizeModelProvider(modelProvider);
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
        this.particleEffects = particleEffects != null ? new EnumMap<>(particleEffects) : new EnumMap<>(ParticleStage.class);
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
        this.rarityOrder = rarityOrder != null ? new ArrayList<>(rarityOrder) : new ArrayList<>();
    }

    private String normalizeModelProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "modelengine";
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.equals("bettermodel") ? "bettermodel" : "modelengine";
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
     * 抽取奖励（带权限检测）
     * @param player 玩家
     * @return 最终奖励，如果所有奖励都被跳过则返回null
     */
    public Reward rollRewardWithPermissionCheck(Player player) {
        RewardResult result = rollRewardWithPermissionCheckResult(player);
        return result != null ? result.getActualReward() : null;
    }

    /**
     * 抽取奖励（带权限检测），返回完整结果
     * @param player 玩家
     * @return 抽奖结果（包含显示奖励和实际奖励），如果所有奖励都被跳过则返回null
     */
    public RewardResult rollRewardWithPermissionCheckResult(Player player) {
        return rollRewardWithPermissionCheckResult(player, new HashSet<>());
    }

    /**
     * 抽取奖励（带权限检测，递归防循环）
     * @param player 玩家
     * @param checkedRewardIds 已检测过的奖励ID集合（防止循环引用）
     * @return 抽奖结果，如果所有奖励都被跳过则返回null
     */
    private RewardResult rollRewardWithPermissionCheckResult(Player player, Set<String> checkedRewardIds) {
        // 过滤掉需要跳过的奖励
        List<Reward> availableRewards = new ArrayList<>();
        for (Reward reward : rewards) {
            if (shouldSkipReward(player, reward, checkedRewardIds)) {
                continue;
            }
            availableRewards.add(reward);
        }

        // 如果没有可用奖励，返回null
        if (availableRewards.isEmpty()) {
            return null;
        }

        // 从可用奖励中抽取
        double totalChance = availableRewards.stream().mapToDouble(Reward::getChance).sum();
        double roll = random.nextDouble() * totalChance;
        double cumulative = 0;

        Reward selectedReward = null;
        for (Reward reward : availableRewards) {
            cumulative += reward.getChance();
            if (roll < cumulative) {
                selectedReward = reward;
                break;
            }
        }

        if (selectedReward == null) {
            selectedReward = availableRewards.get(availableRewards.size() - 1);
        }

        // 检查是否需要替代奖励
        return processPermissionCheckResult(player, selectedReward, selectedReward, checkedRewardIds);
    }

    /**
     * 抽取奖励（带权限检测，递归防循环）- 旧方法保留兼容
     */
    private Reward rollRewardWithPermissionCheck(Player player, Set<String> checkedRewardIds) {
        RewardResult result = rollRewardWithPermissionCheckResult(player, checkedRewardIds);
        return result != null ? result.getActualReward() : null;
    }

    /**
     * 检查是否应该跳过该奖励
     */
    private boolean shouldSkipReward(Player player, Reward reward, Set<String> checkedRewardIds) {
        if (!reward.isPermissionCheckEnabled()) {
            return false;
        }

        String permission = reward.getCheckPermission();
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        // 检查玩家是否拥有权限
        if (!player.hasPermission(permission)) {
            return false;
        }

        // 玩家拥有权限，检查行为
        if (reward.getPermissionAction() == PermissionAction.SKIP) {
            return true; // 跳过该奖励
        }

        return false; // ALTERNATIVE行为不跳过，后续处理
    }

    /**
     * 处理权限检测，返回最终奖励
     */
    private Reward processPermissionCheck(Player player, Reward reward, Set<String> checkedRewardIds) {
        RewardResult result = processPermissionCheckResult(player, reward, reward, checkedRewardIds);
        return result != null ? result.getActualReward() : null;
    }

    /**
     * 处理权限检测，返回完整结果
     * @param player 玩家
     * @param originalReward 原始抽中的奖励（用于显示）
     * @param currentReward 当前处理的奖励
     * @param checkedRewardIds 已检测过的奖励ID
     * @return 抽奖结果
     */
    private RewardResult processPermissionCheckResult(Player player, Reward originalReward, Reward currentReward, Set<String> checkedRewardIds) {
        if (!currentReward.isPermissionCheckEnabled()) {
            return originalReward == currentReward
                    ? RewardResult.normal(currentReward)
                    : RewardResult.replaced(originalReward, currentReward);
        }

        String permission = currentReward.getCheckPermission();
        if (permission == null || permission.isEmpty()) {
            return originalReward == currentReward
                    ? RewardResult.normal(currentReward)
                    : RewardResult.replaced(originalReward, currentReward);
        }

        // 检查玩家是否拥有权限
        if (!player.hasPermission(permission)) {
            return originalReward == currentReward
                    ? RewardResult.normal(currentReward)
                    : RewardResult.replaced(originalReward, currentReward);
        }

        // 玩家拥有权限，检查行为
        PermissionAction action = currentReward.getPermissionAction();

        if (action == PermissionAction.SKIP) {
            // 这种情况不应该发生（应该在shouldSkipReward中被过滤）
            return null;
        }

        if (action == PermissionAction.ALTERNATIVE) {
            String alternativeId = currentReward.getAlternativeRewardId();
            if (alternativeId != null && !alternativeId.isEmpty()) {
                // 防止循环引用
                if (checkedRewardIds.contains(alternativeId)) {
                    return RewardResult.replaced(originalReward, currentReward);
                }

                // 查找替代奖励
                Reward alternativeReward = rewards.stream()
                        .filter(r -> r.getId().equals(alternativeId))
                        .findFirst()
                        .orElse(null);

                if (alternativeReward != null) {
                    // 递归检测替代奖励，但保持原始奖励用于显示
                    checkedRewardIds.add(currentReward.getId());
                    return processPermissionCheckResult(player, originalReward, alternativeReward, checkedRewardIds);
                }
            }
        }

        return originalReward == currentReward
                ? RewardResult.normal(currentReward)
                : RewardResult.replaced(originalReward, currentReward);
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
        PityTier triggeredTier = null;
        for (PityTier tier : sortedTiers) {
            if (currentCount > 0 && currentCount % tier.getCount() == 0) {
                triggeredTier = tier;
            }
        }
        return triggeredTier;
    }

    public PityTier getNextPityTier(int currentCount) {
        if (!pityEnabled || pityTiers.isEmpty()) return null;

        List<PityTier> sortedTiers = new ArrayList<>(pityTiers);
        sortedTiers.sort(Comparator.comparingInt(PityTier::getCount));

        for (PityTier tier : sortedTiers) {
            if (tier.getCount() > currentCount) {
                return tier;
            }
        }

        return null;
    }

    public int getRemainingToNextPity(int currentCount) {
        PityTier nextTier = getNextPityTier(currentCount);
        if (nextTier == null) {
            return 0;
        }

        return Math.max(0, nextTier.getCount() - currentCount);
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
     * 根据指定稀有度抽取保底奖励（带权限检测）
     */
    public Reward rollPityRewardWithPermissionCheck(Player player, String targetRarity) {
        RewardResult result = rollPityRewardWithPermissionCheckResult(player, targetRarity);
        return result != null ? result.getActualReward() : null;
    }

    /**
     * 根据指定稀有度抽取保底奖励（带权限检测），返回完整结果
     */
    public RewardResult rollPityRewardWithPermissionCheckResult(Player player, String targetRarity) {
        return rollPityRewardWithPermissionCheckResult(player, targetRarity, new HashSet<>());
    }

    /**
     * 根据指定稀有度抽取保底奖励（带权限检测，递归防循环）
     */
    private RewardResult rollPityRewardWithPermissionCheckResult(Player player, String targetRarity, Set<String> checkedRewardIds) {
        // 过滤出符合稀有度且不需要跳过的奖励
        List<Reward> pityRewards = rewards.stream()
                .filter(r -> r.getRarity().equalsIgnoreCase(targetRarity) ||
                        isRarityHigherOrEqual(r.getRarity(), targetRarity))
                .filter(r -> !shouldSkipReward(player, r, checkedRewardIds))
                .toList();

        if (pityRewards.isEmpty()) {
            // 如果保底奖励都被跳过，尝试普通抽取
            return rollRewardWithPermissionCheckResult(player);
        }

        double totalChance = pityRewards.stream().mapToDouble(Reward::getChance).sum();
        double roll = random.nextDouble() * totalChance;
        double cumulative = 0;

        Reward selectedReward = null;
        for (Reward reward : pityRewards) {
            cumulative += reward.getChance();
            if (roll < cumulative) {
                selectedReward = reward;
                break;
            }
        }

        if (selectedReward == null) {
            selectedReward = pityRewards.get(pityRewards.size() - 1);
        }

        // 检查是否需要替代奖励
        return processPermissionCheckResult(player, selectedReward, selectedReward, checkedRewardIds);
    }

    /**
     * 根据指定稀有度抽取保底奖励（带权限检测，递归防循环）- 旧方法保留兼容
     */
    private Reward rollPityRewardWithPermissionCheck(Player player, String targetRarity, Set<String> checkedRewardIds) {
        RewardResult result = rollPityRewardWithPermissionCheckResult(player, targetRarity, checkedRewardIds);
        return result != null ? result.getActualReward() : null;
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
        int rarityLevel = getRarityLevel(rarity);
        int targetLevel = getRarityLevel(target);
        return rarityLevel >= 0 && targetLevel >= 0 && rarityLevel >= targetLevel;
    }

    private int getRarityLevel(String rarity) {
        if (rarity == null) {
            return -1;
        }

        for (int i = 0; i < rarityOrder.size(); i++) {
            if (rarityOrder.get(i).equalsIgnoreCase(rarity)) {
                return i;
            }
        }
        return -1;
    }

    private String getHighestConfiguredRarity() {
        if (rarityOrder.isEmpty()) {
            return "legendary";
        }
        return rarityOrder.get(rarityOrder.size() - 1);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getBlockMaterial() { return blockMaterial; }
    public String getBlockItemName() { return blockItemName; }
    public List<String> getBlockItemLore() { return new ArrayList<>(blockItemLore); }
    public boolean isModelEngineEnabled() { return modelEngineEnabled; }
    public boolean isModelEnabled() { return modelEngineEnabled; }
    public String getModelProvider() { return modelProvider; }
    public boolean isBetterModelEnabled() { return modelEngineEnabled && "bettermodel".equals(modelProvider); }
    public boolean usesModelEngineProvider() { return modelEngineEnabled && "modelengine".equals(modelProvider); }
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
    public Map<ParticleStage, CrateParticleEffect> getParticleEffects() { return new EnumMap<>(particleEffects); }
    public CrateParticleEffect getParticleEffect(ParticleStage stage) {
        CrateParticleEffect effect = particleEffects.get(stage);
        return effect != null ? effect : CrateParticleEffect.defaultFor(stage);
    }
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
        if (pityTiers.isEmpty()) return getHighestConfiguredRarity();
        return pityTiers.stream()
                .max(Comparator.comparingInt(PityTier::getCount))
                .map(PityTier::getRarity)
                .orElse(getHighestConfiguredRarity());
    }
}
