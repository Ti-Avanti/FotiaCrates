package gg.fotia.crates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemBuilder {

    /**
     * ItemMeta#setItemModel 仅 1.21.2+ 存在；编译目标为 1.20.6，运行期反射探测一次并缓存。
     */
    private static volatile Method setItemModelMethod;
    private static volatile boolean setItemModelChecked;

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;
    // 降级文本（setItemMeta 抛异常时使用）延迟到失败时才计算，正常路径零额外开销
    private String rawName;
    private Component componentName;
    private List<String> rawLore;
    private List<Component> componentLore;

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
            rawName = name;
            componentName = null;
        }
        return this;
    }

    public ItemBuilder name(Component name) {
        if (name != null && itemMeta != null) {
            itemMeta.displayName(name);
            componentName = name;
            rawName = null;
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (lore != null && itemMeta != null) {
            List<Component> components = new ArrayList<>(lore.size());
            for (String line : lore) {
                components.add(MessageUtil.parse(line));
            }
            itemMeta.lore(components);
            rawLore = new ArrayList<>(lore);
            componentLore = null;
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
            if (rawLore == null) {
                rawLore = new ArrayList<>();
            }
            rawLore.add(line);
        }
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lore) {
        if (lore != null && itemMeta != null) {
            itemMeta.lore(lore);
            componentLore = new ArrayList<>(lore);
            rawLore = null;
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

    /**
     * 设置 item_model（1.21.2+ 服务端生效，旧版本静默忽略）
     */
    public ItemBuilder itemModel(String model) {
        if (itemMeta == null || model == null || model.isBlank()) {
            return this;
        }
        NamespacedKey key = NamespacedKey.fromString(model.toLowerCase());
        if (key == null) {
            return this;
        }
        Method method = resolveSetItemModelMethod(itemMeta);
        if (method != null) {
            try {
                method.invoke(itemMeta, key);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 服务端不支持该组件时保持原样
            }
        }
        return this;
    }

    private static Method resolveSetItemModelMethod(ItemMeta meta) {
        if (!setItemModelChecked) {
            synchronized (ItemBuilder.class) {
                if (!setItemModelChecked) {
                    try {
                        setItemModelMethod = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
                    } catch (NoSuchMethodException ignored) {
                        setItemModelMethod = null;
                    }
                    setItemModelChecked = true;
                }
            }
        }
        return setItemModelMethod;
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
            try {
                itemStack.setItemMeta(itemMeta);
            } catch (RuntimeException | LinkageError e) {
                applyPlainTextFallback();
            }
        }
        return itemStack;
    }

    private void applyPlainTextFallback() {
        ItemMeta fallbackMeta = itemStack.getItemMeta();
        if (fallbackMeta == null) {
            return;
        }

        if (rawName != null) {
            fallbackMeta.displayName(Component.text(MessageUtil.stripColor(rawName)));
        } else if (componentName != null) {
            fallbackMeta.displayName(Component.text(
                    PlainTextComponentSerializer.plainText().serialize(componentName)));
        }
        if (rawLore != null) {
            fallbackMeta.lore(rawLore.stream()
                    .map(line -> (Component) Component.text(MessageUtil.stripColor(line)))
                    .toList());
        } else if (componentLore != null) {
            fallbackMeta.lore(componentLore.stream()
                    .map(component -> (Component) Component.text(
                            PlainTextComponentSerializer.plainText().serialize(component)))
                    .toList());
        }

        try {
            itemStack.setItemMeta(fallbackMeta);
        } catch (RuntimeException | LinkageError ignored) {
            // Leave the base item usable even if this server rejects all text metadata.
        }
    }
}
