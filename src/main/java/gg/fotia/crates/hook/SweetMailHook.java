package gg.fotia.crates.hook;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import top.mrxiaom.sweetmail.IMail;
import top.mrxiaom.sweetmail.attachments.AttachmentItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SweetMailHook {

    private final FotiaCrates plugin;

    public SweetMailHook(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("SweetMail");
    }

    public boolean isMailable(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }

        try {
            return AttachmentItem.build(item).isLegal();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to validate SweetMail attachment item: " + throwable.getMessage());
            return false;
        }
    }

    public boolean sendOverflowMail(Player player, Collection<ItemStack> items) {
        if (!isAvailable() || items.isEmpty()) {
            return false;
        }

        try {
            IMail.MailDraft draft = IMail.api()
                    .createSystemMail(toMailText(plugin.getConfigManager().getOverflowMailSenderDisplay()))
                    .setReceiver(player)
                    .setTitle(toMailText(plugin.getConfigManager().getOverflowMailTitle()))
                    .setIcon(plugin.getConfigManager().getOverflowMailIcon());

            List<String> content = plugin.getConfigManager().getOverflowMailContent();
            if (!content.isEmpty()) {
                List<String> processedContent = new ArrayList<>();
                for (String line : content) {
                    processedContent.add(toMailText(line));
                }
                draft.addContent(processedContent);
            }

            for (ItemStack item : items) {
                draft.addAttachments(AttachmentItem.build(item.clone()));
            }

            IMail.Status status = draft.send();
            if (!status.ok()) {
                plugin.getLogger().warning("Failed to send overflow mail to " + player.getName() + ": " + status.name());
            }
            return status.ok();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to send overflow mail to " + player.getName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private String toMailText(String input) {
        return MessageUtil.toLegacy(input == null ? "" : input);
    }
}
