package gg.fotia.crates.hook;

import cn.superiormc.enchantmentslots.managers.ConfigManager;
import cn.superiormc.enchantmentslots.methods.SlotUtil;
import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.reward.Reward;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/** 只准备附魔槽数据，显示的 lore 由 EnchantmentSlots 自己按玩家生成。 */
public final class EnchantmentSlotsHook {
    private final FotiaCrates plugin;
    private final Plugin dependency;
    private boolean incompatibleApi;
    private boolean reportedItemFailure;

    public EnchantmentSlotsHook(FotiaCrates plugin) {
        this.plugin = plugin;
        dependency = plugin.getServer().getPluginManager().getPlugin("EnchantmentSlots");
    }

    public ItemStack preview(Player viewer, Reward reward) {
        return prepare(viewer, reward.getDisplayItem(), true);
    }

    public Function<Reward, ItemStack> animationIcons(Player viewer) {
        if (!isAvailable()) return Reward::getDisplayItem;
        Map<Reward, ItemStack> icons = new IdentityHashMap<>();
        return reward -> icons.computeIfAbsent(reward, key -> preview(viewer, key)).clone();
    }

    public ItemStack inventoryCopy(Player player, ItemStack reward) {
        return prepare(player, reward, false);
    }

    private boolean isAvailable() {
        return dependency != null && dependency.isEnabled() && !incompatibleApi
                && plugin.getConfigManager().isEnchantmentSlotsEnabled();
    }

    private ItemStack prepare(Player player, ItemStack original, boolean preview) {
        if (isAvailable() && !original.getType().isAir()) {
            ItemStack copy = original.clone();
            try {
                Slots.prepare(copy, player, preview);
                return copy;
            } catch (LinkageError error) {
                incompatibleApi = true;
                plugin.getLogger().warning("Incompatible EnchantmentSlots API: " + error);
            } catch (RuntimeException error) {
                if (!reportedItemFailure) {
                    reportedItemFailure = true;
                    plugin.getLogger().log(Level.WARNING,
                            "EnchantmentSlots could not prepare reward item " + original.getType(), error);
                }
            }
        }
        return preview ? original : original.clone();
    }

    private static final class Slots {
        private static void prepare(ItemStack copy, Player player, boolean preview) {
            ConfigManager settings = ConfigManager.configManager;
            if (settings == null) return;
            if (preview && (!settings.canDisplay(copy) || (player.getGameMode() == GameMode.CREATIVE
                    && settings.getBoolean("settings.add-lore.black-creative", true)))) return;

            if (!setsSlotsOnReceipt(settings) && !(preview && settings.isAutoAddLore(copy, player, true))) return;

            // 与背包槽位事件使用相同的顺序，保留原有槽位值和插件定义的附魔显示标记。
            int slots = settings.getDefaultLimits(copy, player);
            if (slots > 0) SlotUtil.setSlot(copy, slots, false);
        }

        private static boolean setsSlotsOnReceipt(ConfigManager settings) {
            if (settings.getBoolean("settings.set-slot-trigger.PlayerInventorySlotChangeEvent.enabled", false)) return true;
            return settings.getBoolean("settings.set-slot-trigger.SetSlotPacket.enabled", true)
                    && "packetevents".equalsIgnoreCase(settings.getString("settings.add-lore.use-listener-plugin"));
        }
    }
}
