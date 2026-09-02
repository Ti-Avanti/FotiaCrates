package gg.fotia.crates.animation;

import gg.fotia.crates.reward.Reward;
import gg.fotia.crates.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashSet;
import java.util.List;

final class CardRevealBoard {

    private final Inventory inventory;
    private final CardAnimationSettings settings;
    private List<Integer> cardSlots = List.of();

    CardRevealBoard(Inventory inventory, CardAnimationSettings settings) {
        this.inventory = inventory;
        this.settings = settings;
    }

    void useSlots(List<Integer> slots) {
        clearCards();
        cardSlots = slots == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(slots));
        clearCards();
    }

    void showFrame(List<Reward> rewards, List<Integer> rewardIndexes) {
        int count = Math.min(cardSlots.size(), rewardIndexes.size());
        for (int index = 0; index < count; index++) {
            int rewardIndex = rewardIndexes.get(index);
            if (rewardIndex >= 0 && rewardIndex < rewards.size()) {
                inventory.setItem(cardSlots.get(index),
                        rewards.get(rewardIndex).getDisplayItem());
            }
        }
    }

    void cover(boolean glow) {
        ItemStack back = settings.backDisplay().createItem(glow);
        for (int slot : cardSlots) {
            inventory.setItem(slot, back.clone());
        }
    }

    void reveal(int slot, Reward reward) {
        inventory.setItem(slot, reward.getDisplayItem());
    }

    void status(String configuredName, int revealed, int total) {
        int slot = settings.statusSlot();
        if (slot < 0 || slot >= inventory.getSize() || cardSlots.contains(slot)) {
            return;
        }
        String name = configuredName
                .replace("{revealed}", String.valueOf(revealed))
                .replace("{remaining}", String.valueOf(Math.max(0, total - revealed)))
                .replace("{total}", String.valueOf(total));
        ItemStack status = inventory.getItem(slot);
        if (status == null || status.getType().isAir()) {
            status = new ItemStack(Material.NETHER_STAR);
        } else {
            status = status.clone();
        }
        ItemMeta meta = status.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse(name));
            status.setItemMeta(meta);
        }
        inventory.setItem(slot, status);
    }

    private void clearCards() {
        for (int slot : cardSlots) {
            inventory.clear(slot);
        }
    }
}
