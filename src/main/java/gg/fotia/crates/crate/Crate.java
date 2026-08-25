package gg.fotia.crates.crate;

import gg.fotia.crates.animation.AnimationType;
import gg.fotia.crates.animation.AnimationTemplateSelection;
import gg.fotia.crates.particle.CrateParticleEffect;
import gg.fotia.crates.particle.ParticleStage;
import gg.fotia.crates.reward.PermissionAction;
import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.reward.RewardProbability;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Crate {

    private final String id;
    private final String name;
    private final Material blockMaterial;
    private final ItemStack blockItemTemplate;
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
    private final PreviewChanceDisplayMode previewChanceDisplayMode;
    private final PreviewSortMode previewSortMode;
    private final String previewTitle;
    private final boolean animationEnabled;
    private final AnimationType animationType;
    private final String animationTemplate;
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
    private final List<PityTier> pityTiers; // 多级保底，构造时按 count 升序排序的不可变列表
    private final String minimumPityRarity; // 构造时缓存，避免每次开箱重算
    private final boolean resetPityOnEarlyQualifyingReward;
    private final boolean multiOpenEnabled;
    private final int multiOpenMax;
    private final boolean multiOpenAnimationEnabled;
    private final UniqueDrawSettings uniqueDrawSettings;
    private final String permission; // 开箱权限节点
    private final List<String> rarityOrder; // 不可变

    /**
     * 保底等级
     */
    public static class PityTier {
        private final String id;
        private final int count;
        private final String rarity;

        public PityTier(int count, String rarity) {
            this("legacy_" + count, count, rarity);
        }

        public PityTier(String id, int count, String rarity) {
            this.id = id == null || id.isBlank() ? "legacy_" + count : id;
            this.count = count;
            this.rarity = rarity;
        }

        public String getId() { return id; }
        public int getCount() { return count; }
        public String getRarity() { return rarity; }
    }

    public Crate(String id, String name, Material blockMaterial, ItemStack blockItemTemplate,
                 String blockItemName, List<String> blockItemLore,
                 String modelProvider, boolean modelEngineEnabled, String modelEngineId,
                 String modelEngineIdleAnimation, String modelEngineOpenAnimation,
                 int modelEngineOpenDelay, int modelEngineViewRange, double physicalAnimationHeight,
                 double hologramHeight, List<String> hologramLines,
                 List<Reward> rewards, boolean previewEnabled,
                 PreviewChanceDisplayMode previewChanceDisplayMode, PreviewSortMode previewSortMode,
                 String previewTitle,
                 boolean animationEnabled, AnimationType animationType, String animationTemplate,
                 int animationDuration,
                 String animationTitle, boolean physicalAnimationEnabled,
                 boolean particlesEnabled, Particle particleType, int particleCount,
                 Map<ParticleStage, CrateParticleEffect> particleEffects,
                 Sound spinSound, float spinVolume, float spinPitch,
                 Sound winSound, float winVolume, float winPitch,
                 boolean pityEnabled, List<PityTier> pityTiers, boolean resetPityOnEarlyQualifyingReward,
                 boolean multiOpenEnabled, int multiOpenMax, boolean multiOpenAnimationEnabled,
                 UniqueDrawSettings uniqueDrawSettings, String permission,
                 List<String> rarityOrder) {
        this.id = id;
        this.name = name;
        this.blockMaterial = blockMaterial;
        this.blockItemTemplate = CrateBlockItemTemplate.copyForStorage(blockItemTemplate);
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
        this.previewChanceDisplayMode = previewChanceDisplayMode != null
                ? previewChanceDisplayMode
                : PreviewChanceDisplayMode.PERCENTAGE;
        this.previewSortMode = previewSortMode != null
                ? previewSortMode
                : PreviewSortMode.CONFIG_ORDER;
        this.previewTitle = previewTitle;
        this.animationEnabled = animationEnabled;
        this.animationType = animationType;
        this.animationTemplate = AnimationTemplateSelection.normalize(animationTemplate);
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
        this.pityTiers = sortedPityTiers(pityTiers);
        this.resetPityOnEarlyQualifyingReward = resetPityOnEarlyQualifyingReward;
        this.multiOpenEnabled = multiOpenEnabled;
        this.multiOpenMax = multiOpenMax;
        this.multiOpenAnimationEnabled = multiOpenAnimationEnabled;
        this.uniqueDrawSettings = uniqueDrawSettings != null
                ? uniqueDrawSettings
                : UniqueDrawSettings.defaults();
        this.permission = permission;
        this.rarityOrder = rarityOrder != null ? List.copyOf(rarityOrder) : List.of();
        this.minimumPityRarity = computeMinimumPityRarity();
    }

    /**
     * 按 count 升序排序并剔除 count<=0 的非法等级（防止取余除零）。
     */
    private static List<PityTier> sortedPityTiers(List<PityTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }
        List<PityTier> sorted = new ArrayList<>(tiers.size());
        for (PityTier tier : tiers) {
            if (tier != null && tier.getCount() > 0) {
                sorted.add(tier);
            }
        }
        sorted.sort(Comparator.comparingInt(PityTier::getCount));
        return List.copyOf(sorted);
    }

    private String normalizeModelProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "modelengine";
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.equals("bettermodel") ? "bettermodel" : "modelengine";
    }

    public Reward rollReward() {
        return RewardProbability.select(rewards, ThreadLocalRandom.current());
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

    public List<Reward> getAvailableRewardsFor(Player player) {
        if (player == null) {
            return getRewards();
        }

        MultiOpenPermissionContext permissionContext = new MultiOpenPermissionContext(player::hasPermission);
        return rewards.stream()
                .filter(reward -> !shouldSkipReward(permissionContext, reward))
                .toList();
    }

    /**
     * 抽取奖励（带权限检测），返回完整结果
     * @param player 玩家
     * @return 抽奖结果（包含显示奖励和实际奖励），如果所有奖励都被跳过则返回null
     */
    public RewardResult rollRewardWithPermissionCheckResult(Player player) {
        return rollRewardWithPermissionCheckResult(new MultiOpenPermissionContext(player::hasPermission));
    }

    RewardResult rollRewardWithPermissionCheckResult(MultiOpenPermissionContext permissionContext) {
        return rollRewardWithPermissionCheckResult(permissionContext, new HashSet<>());
    }

    /**
     * 抽取奖励（带权限检测，递归防循环）
     * @param player 玩家
     * @param checkedRewardIds 已检测过的奖励ID集合（防止循环引用）
     * @return 抽奖结果，如果所有奖励都被跳过则返回null
     */
    private RewardResult rollRewardWithPermissionCheckResult(MultiOpenPermissionContext permissionContext,
                                                             Set<String> checkedRewardIds) {
        // 过滤掉需要跳过的奖励
        List<Reward> availableRewards = new ArrayList<>();
        for (Reward reward : rewards) {
            if (shouldSkipReward(permissionContext, reward)) {
                continue;
            }
            availableRewards.add(reward);
        }

        // 如果没有可用奖励，返回null
        if (availableRewards.isEmpty()) {
            return null;
        }

        // 从可用奖励中抽取（统一走 RewardProbability，含正权重钳制，与预览显示概率一致）
        Reward selectedReward = RewardProbability.select(availableRewards, ThreadLocalRandom.current());
        if (selectedReward == null) {
            return null;
        }

        // 检查是否需要替代奖励
        return processPermissionCheckResult(
                permissionContext, selectedReward, selectedReward, checkedRewardIds);
    }

    /**
     * 抽取奖励（带权限检测，递归防循环）- 旧方法保留兼容
     */
    private Reward rollRewardWithPermissionCheck(Player player, Set<String> checkedRewardIds) {
        RewardResult result = rollRewardWithPermissionCheckResult(
                new MultiOpenPermissionContext(player::hasPermission), checkedRewardIds);
        return result != null ? result.getActualReward() : null;
    }

    /**
     * 检查是否应该跳过该奖励
     */
    private boolean shouldSkipReward(MultiOpenPermissionContext permissionContext, Reward reward) {
        return permissionContext.shouldSkip(reward);
    }

    /**
     * 处理权限检测，返回最终奖励
     */
    private Reward processPermissionCheck(Player player, Reward reward, Set<String> checkedRewardIds) {
        RewardResult result = processPermissionCheckResult(
                new MultiOpenPermissionContext(player::hasPermission), reward, reward, checkedRewardIds);
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
    private RewardResult processPermissionCheckResult(MultiOpenPermissionContext permissionContext,
                                                      Reward originalReward, Reward currentReward,
                                                      Set<String> checkedRewardIds) {
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
        if (!permissionContext.hasPermission(permission)) {
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
                    return processPermissionCheckResult(
                            permissionContext, originalReward, alternativeReward, checkedRewardIds);
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

        // pityTiers 已在构造时按次数升序排序且 count>0
        PityTier triggeredTier = null;
        for (PityTier tier : pityTiers) {
            if (currentCount > 0 && currentCount % tier.getCount() == 0) {
                triggeredTier = tier;
            }
        }
        return triggeredTier;
    }

    /**
     * 分层计数模式下选出本次触发的保底档位：
     * 各档独立计数达到阈值即候选，多档同时到达时取稀有度最高者（同稀有度取更高档位）
     * @param currentCountFunction 返回某档"本次抽取后"的计数（即已 +1）
     */
    public PityTier selectTriggeredTier(java.util.function.ToIntFunction<PityTier> currentCountFunction) {
        if (!pityEnabled || pityTiers.isEmpty()) return null;

        PityTier best = null;
        for (PityTier tier : pityTiers) {
            if (currentCountFunction.applyAsInt(tier) < tier.getCount()) {
                continue;
            }
            if (best == null || getRarityLevel(tier.getRarity()) >= getRarityLevel(best.getRarity())) {
                best = tier;
            }
        }
        return best;
    }

    public PityTier getNextPityTier(int currentCount) {
        if (!pityEnabled || pityTiers.isEmpty()) return null;

        for (PityTier tier : pityTiers) {
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
        // pityTiers 已按 count 升序排序
        return pityTiers.isEmpty() ? 0 : pityTiers.get(pityTiers.size() - 1).getCount();
    }

    public String getMinimumPityRarity() {
        return minimumPityRarity;
    }

    private String computeMinimumPityRarity() {
        String minimumRarity = null;
        int minimumLevel = Integer.MAX_VALUE;
        for (PityTier tier : pityTiers) {
            int level = getRarityLevel(tier.getRarity());
            if (level >= 0 && level < minimumLevel) {
                minimumLevel = level;
                minimumRarity = tier.getRarity();
            }
        }
        return minimumRarity != null
                ? minimumRarity
                : (pityTiers.isEmpty() ? null : pityTiers.get(0).getRarity());
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

        return RewardProbability.select(pityRewards, ThreadLocalRandom.current());
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
        return rollPityRewardWithPermissionCheckResult(
                new MultiOpenPermissionContext(player::hasPermission), targetRarity);
    }

    RewardResult rollPityRewardWithPermissionCheckResult(MultiOpenPermissionContext permissionContext,
                                                         String targetRarity) {
        return rollPityRewardWithPermissionCheckResult(permissionContext, targetRarity, new HashSet<>());
    }

    /**
     * 根据指定稀有度抽取保底奖励（带权限检测，递归防循环）
     */
    private RewardResult rollPityRewardWithPermissionCheckResult(MultiOpenPermissionContext permissionContext,
                                                                 String targetRarity,
                                                                 Set<String> checkedRewardIds) {
        // 过滤出符合稀有度且不需要跳过的奖励
        List<Reward> pityRewards = rewards.stream()
                .filter(r -> r.getRarity().equalsIgnoreCase(targetRarity) ||
                        isRarityHigherOrEqual(r.getRarity(), targetRarity))
                .filter(r -> !shouldSkipReward(permissionContext, r))
                .toList();

        if (pityRewards.isEmpty()) {
            // 如果保底奖励都被跳过，尝试普通抽取
            return rollRewardWithPermissionCheckResult(permissionContext);
        }

        Reward selectedReward = RewardProbability.select(pityRewards, ThreadLocalRandom.current());
        if (selectedReward == null) {
            return rollRewardWithPermissionCheckResult(permissionContext);
        }

        // 检查是否需要替代奖励
        return processPermissionCheckResult(
                permissionContext, selectedReward, selectedReward, checkedRewardIds);
    }

    /**
     * 根据指定稀有度抽取保底奖励（带权限检测，递归防循环）- 旧方法保留兼容
     */
    private Reward rollPityRewardWithPermissionCheck(Player player, String targetRarity, Set<String> checkedRewardIds) {
        RewardResult result = rollPityRewardWithPermissionCheckResult(
                new MultiOpenPermissionContext(player::hasPermission), targetRarity, checkedRewardIds);
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

    public boolean isRarityHigherOrEqual(String rarity, String target) {
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
    public ItemStack getBlockItemTemplate() { return CrateBlockItemTemplate.copyForStorage(blockItemTemplate); }
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
    public boolean isShowChance() { return previewChanceDisplayMode != PreviewChanceDisplayMode.HIDDEN; }
    public PreviewChanceDisplayMode getPreviewChanceDisplayMode() { return previewChanceDisplayMode; }
    public PreviewSortMode getPreviewSortMode() { return previewSortMode; }
    public String getPreviewTitle() { return previewTitle; }
    public boolean isAnimationEnabled() { return animationEnabled; }
    public AnimationType getAnimationType() { return animationType; }
    public String getAnimationTemplate() { return animationTemplate; }
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
    public boolean hasPityTiers() { return !pityTiers.isEmpty(); }
    public List<PityTier> getPityTiers() { return pityTiers; }
    public boolean isResetPityOnEarlyQualifyingReward() { return resetPityOnEarlyQualifyingReward; }
    public boolean isMultiOpenEnabled() { return multiOpenEnabled; }
    public int getMultiOpenMax() { return multiOpenMax; }
    public boolean isMultiOpenAnimationEnabled() { return multiOpenAnimationEnabled; }
    public UniqueDrawSettings getUniqueDrawSettings() { return uniqueDrawSettings; }
    public boolean isUniqueDrawEnabled() { return uniqueDrawSettings.enabled(); }
    public boolean isReplaceObtainedInPreview() { return uniqueDrawSettings.replaceObtainedInPreview(); }
    public UniqueDrawSettings.ObtainedIcon getObtainedRewardIcon() { return uniqueDrawSettings.obtainedIcon(); }
    public List<String> getRarityOrder() { return rarityOrder; }
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
