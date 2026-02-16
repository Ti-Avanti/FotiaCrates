package gg.fotia.crates.hook;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.key.Key;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI 扩展
 * 提供宝箱钥匙数量相关的变量
 */
public class FotiaCratesExpansion extends PlaceholderExpansion {

    private final FotiaCrates plugin;

    public FotiaCratesExpansion(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fotiacrates";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return "0";
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return "0";
        }

        // 解析变量格式: keys_<type>_<crateId>
        // type: virtual, physical, total
        if (params.startsWith("keys_")) {
            String remaining = params.substring(5); // 移除 "keys_"

            // 查找类型和宝箱ID
            String type;
            String crateId;

            if (remaining.startsWith("virtual_")) {
                type = "virtual";
                crateId = remaining.substring(8);
            } else if (remaining.startsWith("physical_")) {
                type = "physical";
                crateId = remaining.substring(9);
            } else if (remaining.startsWith("total_")) {
                type = "total";
                crateId = remaining.substring(6);
            } else {
                return null;
            }

            // 验证宝箱是否存在
            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate == null) {
                return "0";
            }

            // 计算钥匙数量
            return String.valueOf(getKeysForCrate(player, crateId, type));
        }

        return null;
    }

    /**
     * 获取玩家对指定宝箱的钥匙数量
     *
     * @param player  玩家
     * @param crateId 宝箱ID
     * @param type    类型: virtual, physical, total
     * @return 钥匙数量
     */
    private int getKeysForCrate(Player player, String crateId, String type) {
        int count = 0;

        // 获取所有可以打开该宝箱的钥匙
        for (Key key : plugin.getKeyManager().getKeysForCrate(crateId)) {
            switch (type) {
                case "virtual":
                    count += plugin.getKeyManager().getVirtualKeys(player.getUniqueId(), key.getId());
                    break;
                case "physical":
                    count += plugin.getKeyManager().getPhysicalKeys(player, key.getId());
                    break;
                case "total":
                    count += plugin.getKeyManager().getTotalKeys(player, key.getId());
                    break;
            }
        }

        return count;
    }
}
