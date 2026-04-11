package gg.fotia.crates.command.subcommand;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.crate.Crate;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 给予宝箱方块命令
 * /crate give <玩家> <宝箱> [数量]
 */
public class GiveCommand extends AbstractSubCommand {

    public GiveCommand(FotiaCrates plugin) {
        super(plugin, "fotiacrates.admin.give", "/crate give <玩家> <宝箱> [数量]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "usage",
                    LanguageManager.placeholders("usage", "/crate give <玩家> <宝箱> [数量]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sendMessage(sender, "invalid-player");
            return;
        }

        String crateId = args[1];
        Crate crate = plugin.getCrateManager().getCrate(crateId);
        if (crate == null) {
            sendMessage(sender, "invalid-crate");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }

        ItemStack crateBlock = plugin.getCrateManager().createCrateBlockItem(crate, amount);

        // 尝试添加到玩家背包
        var leftover = target.getInventory().addItem(crateBlock);
        if (!leftover.isEmpty()) {
            // 背包满了，掉落在地上
            for (ItemStack item : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
            plugin.getLanguageManager().send(target, "inventory-full");
        }

        // 发送消息给目标玩家
        plugin.getLanguageManager().send(target, "crate-block-received",
                LanguageManager.placeholders("amount", String.valueOf(amount), "crate", crate.getName()));

        // 发送消息给执行者
        if (sender instanceof Player p && !p.equals(target)) {
            plugin.getLanguageManager().send(p, "crate-block-given",
                    LanguageManager.placeholders("player", target.getName(), "amount", String.valueOf(amount), "crate", crate.getName()));
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("crate-block-given",
                    LanguageManager.placeholders("player", target.getName(), "amount", String.valueOf(amount), "crate", crate.getName())));
        }
    }

    private void sendMessage(CommandSender sender, String key) {
        if (sender instanceof Player p) {
            plugin.getLanguageManager().send(p, key);
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage(key));
        }
    }

    private void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player p) {
            plugin.getLanguageManager().send(p, key, placeholders);
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage(key, placeholders));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return filterCompletions(players, args[0]);
        }

        if (args.length == 2) {
            return filterCompletions(new ArrayList<>(plugin.getCrateManager().getCrateIds()), args[1]);
        }

        if (args.length == 3) {
            return filterCompletions(List.of("1", "5", "10", "32", "64"), args[2]);
        }

        return List.of();
    }
}
