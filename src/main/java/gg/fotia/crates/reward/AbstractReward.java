package gg.fotia.crates.reward;

import org.bukkit.inventory.ItemStack;

public abstract class AbstractReward implements Reward {

    protected final String id;
    protected final String displayName;
    protected final String rarity;
    protected final double chance;
    protected final boolean broadcast;
    protected final ItemStack displayItem;

    // 权限检测相关字段
    protected final boolean permissionCheckEnabled;
    protected final String checkPermission;
    protected final PermissionAction permissionAction;
    protected final String alternativeRewardId;
    protected final boolean autoDisplayIcon;
    protected final boolean autoDisplayName;

    public AbstractReward(String id, String displayName, String rarity, double chance, boolean broadcast, ItemStack displayItem) {
        this(id, displayName, rarity, chance, broadcast, displayItem, false, null, PermissionAction.SKIP, null);
    }

    public AbstractReward(String id, String displayName, String rarity, double chance, boolean broadcast, ItemStack displayItem,
                          boolean permissionCheckEnabled, String checkPermission, PermissionAction permissionAction, String alternativeRewardId) {
        this(id, displayName, rarity, chance, broadcast, displayItem,
                permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId, true, true);
    }

    public AbstractReward(String id, String displayName, String rarity, double chance, boolean broadcast, ItemStack displayItem,
                          boolean permissionCheckEnabled, String checkPermission, PermissionAction permissionAction, String alternativeRewardId,
                          boolean autoDisplayIcon, boolean autoDisplayName) {
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.chance = chance;
        this.broadcast = broadcast;
        this.displayItem = displayItem;
        this.permissionCheckEnabled = permissionCheckEnabled;
        this.checkPermission = checkPermission;
        this.permissionAction = permissionAction != null ? permissionAction : PermissionAction.SKIP;
        this.alternativeRewardId = alternativeRewardId;
        this.autoDisplayIcon = autoDisplayIcon;
        this.autoDisplayName = autoDisplayName;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getDisplayName() { return displayName; }

    @Override
    public String getRarity() { return rarity; }

    @Override
    public double getChance() { return chance; }

    @Override
    public boolean shouldBroadcast() { return broadcast; }

    @Override
    public ItemStack getDisplayItem() { return displayItem.clone(); }

    @Override
    public boolean isPermissionCheckEnabled() { return permissionCheckEnabled; }

    @Override
    public String getCheckPermission() { return checkPermission; }

    @Override
    public PermissionAction getPermissionAction() { return permissionAction; }

    @Override
    public String getAlternativeRewardId() { return alternativeRewardId; }

    @Override
    public boolean isAutoDisplayIcon() { return autoDisplayIcon; }

    @Override
    public boolean isAutoDisplayName() { return autoDisplayName; }
}
