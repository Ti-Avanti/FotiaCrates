package gg.fotia.crates.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 箱子盖子工具类 - 使用 PacketEvents 只对特定玩家显示箱子打开/关闭
 */
public class ChestLidUtil {

    /**
     * 检查 PacketEvents 是否可用
     */
    public static boolean isPacketEventsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("packetevents");
    }

    /**
     * 对特定玩家打开箱子盖子
     */
    public static void openChestLid(Player player, Location location) {
        if (location == null || player == null) return;
        sendBlockAction(player, location, 1, 1);
    }

    /**
     * 对特定玩家关闭箱子盖子
     */
    public static void closeChestLid(Player player, Location location) {
        if (location == null || player == null) return;
        sendBlockAction(player, location, 1, 0);
    }

    /**
     * 发送方块动作数据包
     * @param player 目标玩家
     * @param location 方块位置
     * @param actionId 动作ID (1 = 箱子盖子)
     * @param actionParam 动作参数 (0 = 关闭, 1 = 打开)
     */
    private static void sendBlockAction(Player player, Location location, int actionId, int actionParam) {
        if (!isPacketEventsAvailable()) return;

        try {
            Block block = location.getBlock();
            WrappedBlockState blockState = SpigotConversionUtil.fromBukkitBlockData(block.getBlockData());
            WrapperPlayServerBlockAction packet = new WrapperPlayServerBlockAction(
                new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                actionId,
                actionParam,
                blockState.getGlobalId()
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception e) {
            // 忽略错误，回退到无效果
        }
    }
}
