package gg.fotia.crates.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ExperienceReward extends AbstractReward {

    private final int amount;
    private final boolean levels;

    public ExperienceReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                            ItemStack displayItem, int amount, boolean levels) {
        super(id, displayName, rarity, chance, broadcast, displayItem);
        this.amount = amount;
        this.levels = levels;
    }

    @Override
    public RewardType getType() { return RewardType.EXPERIENCE; }

    @Override
    public void give(Player player) {
        if (levels) {
            player.giveExpLevels(amount);
        } else {
            player.giveExp(amount);
        }
    }

    public int getAmount() { return amount; }
    public boolean isLevels() { return levels; }
}
