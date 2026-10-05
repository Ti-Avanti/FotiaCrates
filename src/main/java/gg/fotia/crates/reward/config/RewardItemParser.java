package gg.fotia.crates.reward.config;

import gg.fotia.crates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** 奖励、预览与附加物品共用的配置解析器，保留外部物品的组件。 */
public final class RewardItemParser {
    private static final String PREFIX = "craftengine-";
    private final ExternalRewardItems externalItems;
    private final Set<String> references = new LinkedHashSet<>();

    public RewardItemParser(ExternalRewardItems externalItems) {
        this.externalItems = externalItems;
    }

    public void begin() { references.clear(); }
    public Set<String> references() { return Set.copyOf(references); }
    public boolean available(Set<String> ids) { return ids.stream().allMatch(externalItems::exists); }

    public ItemStack parse(ConfigurationSection section, String defaultName) {
        String material = section.getString("material", "PAPER").trim();
        String id = section.getString("craftengine-id", section.getString("craft-engine-id", "")).trim();
        String shorthand = material.toLowerCase(Locale.ROOT).startsWith(PREFIX)
                ? material.substring(PREFIX.length()).trim() : "";
        if (!id.isEmpty() && !shorthand.isEmpty() && !id.equals(shorthand)) {
            throw error(section, "craftengine-id 与 material 中的物品 ID 不一致");
        }
        if (id.isEmpty()) id = shorthand;
        boolean external = !id.isEmpty();
        ItemStack base;
        if (external) {
            references.add(id);
            if (id.indexOf(':') < 1 || NamespacedKey.fromString(id) == null) {
                throw error(section, "CraftEngine ID 必须为 namespace:item_id: " + id);
            }
            try {
                base = externalItems.create(id);
            } catch (ExternalRewardItems.Pending exception) {
                throw new ExternalRewardItems.Pending(section.getCurrentPath() + ": [" + id + "] " + exception.getMessage());
            } catch (RuntimeException | LinkageError exception) {
                throw error(section, "[" + id + "] " + exception.getMessage());
            }
        } else {
            if (material.toLowerCase(Locale.ROOT).startsWith(PREFIX)
                    || section.isSet("craftengine-id") || section.isSet("craft-engine-id")) {
                throw error(section, "CraftEngine ID 不能为空");
            }
            Material type = Material.matchMaterial(material);
            if (type == null || (!type.isItem() && !type.isAir())) {
                throw error(section, "无效物品材质 '" + material
                        + "'；CraftEngine 物品请使用 craftengine-id: namespace:item_id");
            }
            base = new ItemStack(type);
        }
        ItemBuilder builder = new ItemBuilder(base);
        if (section.isSet("name")) builder.name(section.getString("name"));
        else if (!external) builder.name(defaultName);
        if (section.isSet("lore") || !external) builder.lore(section.getStringList("lore"));
        if (section.isSet("amount")) {
            int amount = section.getInt("amount");
            if (amount < 1) throw error(section, "amount 必须大于 0");
            builder.amount(amount);
        }
        if (section.isSet("custom-model-data")) builder.customModelData(section.getInt("custom-model-data"));
        if (section.isSet("glow")) builder.glow(section.getBoolean("glow"));
        var enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String name : enchantments.getKeys(false)) {
                NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
                var enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
                if (enchantment == null) throw error(section, "未知附魔: " + name);
                builder.enchant(enchantment, enchantments.getInt(name));
            }
        }
        return builder.build();
    }

    private IllegalArgumentException error(ConfigurationSection section, String detail) {
        return new IllegalArgumentException(section.getCurrentPath() + ": " + detail);
    }
}
