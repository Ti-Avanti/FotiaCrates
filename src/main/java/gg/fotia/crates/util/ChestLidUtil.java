package gg.fotia.crates.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 箱子盖子工具类 - 使用 ProtocolLib 只对特定玩家显示箱子打开/关闭
 */
public class ChestLidUtil {

    private static boolean protocolLibAvailable = false;

    static {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            protocolLibAvailable = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
        } catch (ClassNotFoundException e) {
            protocolLibAvailable = false;
        }
    }

    /**
     * 检查 ProtocolLib 是否可用
     */
    public static boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
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
        if (!protocolLibAvailable) return;

        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_ACTION);

            Block block = location.getBlock();

            packet.getBlockPositionModifier().write(0,
                new com.comphenix.protocol.wrappers.BlockPosition(
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
                ));
            packet.getIntegers().write(0, actionId);
            packet.getIntegers().write(1, actionParam);
            packet.getBlocks().write(0, block.getType());

            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            // 忽略错误，回退到无效果
        }
    }
}
