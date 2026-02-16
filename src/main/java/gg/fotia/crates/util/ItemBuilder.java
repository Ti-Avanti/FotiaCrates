package gg.fotia.crates.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemMeta = this.itemStack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (name != null && itemMeta != null) {
            itemMeta.displayName(MessageUtil.parse(name));
        }
        return this;
    }

    public ItemBuilder name(Component name) {
        if (name != null && itemMeta != null) {
            itemMeta.displayName(name);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (lore != null && itemMeta != null) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(MessageUtil.parse(line));
            }
            itemMeta.lore(components);
        }
        return this;
    }

    public ItemBuilder addLore(String line) {
        if (line != null && itemMeta != null) {
            List<Component> currentLore = itemMeta.lore();
            if (currentLore == null) {
                currentLore = new ArrayList<>();
            } else {
                currentLore = new ArrayList<>(currentLore);
            }
            currentLore.add(MessageUtil.parse(line));
            itemMeta.lore(currentLore);
        }
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lore) {
        if (lore != null && itemMeta != null) {
            itemMeta.lore(lore);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (itemMeta != null && enchantment != null) {
            itemMeta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    public ItemBuilder enchantments(Map<String, Integer> enchantments) {
        if (enchantments != null && itemMeta != null) {
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey().toLowerCase()));
                if (enchantment != null) {
                    itemMeta.addEnchant(enchantment, entry.getValue(), true);
                }
            }
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow && itemMeta != null) {
            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null) {
                itemMeta.addEnchant(unbreaking, 1, true);
            }
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (itemMeta != null && data > 0) {
            itemMeta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (itemMeta != null) {
            itemMeta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        if (itemMeta != null) {
            itemMeta.setUnbreakable(unbreakable);
        }
        return this;
    }

    public ItemStack build() {
        if (itemMeta != null) {
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }
}
