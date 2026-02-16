package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MoneyReward extends AbstractReward {

    private final double amount;

    public MoneyReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                       ItemStack displayItem, double amount) {
        super(id, displayName, rarity, chance, broadcast, displayItem);
        this.amount = amount;
    }

    @Override
    public RewardType getType() { return RewardType.MONEY; }

    @Override
    public void give(Player player) {
        FotiaCrates plugin = FotiaCrates.getInstance();
        if (plugin.hasEconomy()) {
            Economy economy = plugin.getEconomy();
            economy.depositPlayer(player, amount);
        } else {
            plugin.getLogger().warning("Vault economy not available! Cannot give money reward to " + player.getName());
        }
    }

    public double getAmount() { return amount; }
}
