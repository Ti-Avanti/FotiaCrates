package gg.fotia.crates.reward;

import gg.fotia.crates.FotiaCrates;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MoneyReward extends AbstractReward {

    private final double amount;

    public MoneyReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                       ItemStack displayItem, double amount) {
        this(id, displayName, rarity, chance, broadcast, displayItem, amount,
                false, null, PermissionAction.SKIP, null);
    }

    public MoneyReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                       ItemStack displayItem, double amount,
                       boolean permissionCheckEnabled, String checkPermission,
                       PermissionAction permissionAction, String alternativeRewardId) {
        this(id, displayName, rarity, chance, broadcast, displayItem, amount,
                permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId, true, true);
    }

    public MoneyReward(String id, String displayName, String rarity, double chance, boolean broadcast,
                       ItemStack displayItem, double amount,
                       boolean permissionCheckEnabled, String checkPermission,
                       PermissionAction permissionAction, String alternativeRewardId,
                       boolean autoDisplayIcon, boolean autoDisplayName) {
        super(id, displayName, rarity, chance, broadcast, displayItem,
                permissionCheckEnabled, checkPermission, permissionAction, alternativeRewardId,
                autoDisplayIcon, autoDisplayName);
        this.amount = amount;
    }

    @Override
    public RewardType getType() { return RewardType.MONEY; }

    @Override
    public void give(Player player) {
        FotiaCrates plugin = FotiaCrates.getInstance();
        if (plugin.hasEconomy()) {
            Economy economy = plugin.getEconomy();
            var response = economy.depositPlayer(player, amount);
            if (response == null || !response.transactionSuccess()) {
                throw new IllegalStateException("Economy rejected the reward: "
                        + (response == null ? "no response" : response.errorMessage));
            }
        } else {
            throw new IllegalStateException("Vault economy is not available for " + player.getName());
        }
    }

    public double getAmount() { return amount; }
}
