package gg.fotia.crates.reward;

import org.bukkit.inventory.ItemStack;

public abstract class AbstractReward implements Reward {

    protected final String id;
    protected final String displayName;
    protected final String rarity;
    protected final double chance;
    protected final boolean broadcast;
    protected final ItemStack displayItem;

    public AbstractReward(String id, String displayName, String rarity, double chance, boolean broadcast, ItemStack displayItem) {
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.chance = chance;
        this.broadcast = broadcast;
        this.displayItem = displayItem;
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
}
