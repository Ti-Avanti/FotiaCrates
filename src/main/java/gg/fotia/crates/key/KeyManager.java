package gg.fotia.crates.key;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.config.AsyncConfigurationWriter;
import gg.fotia.crates.hook.CraftEngineKeyItemProvider;
import gg.fotia.crates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.util.*;

/**
 * 钥匙管理器
 * 支持一个钥匙对应多个宝箱
 */
public class KeyManager {

    private final FotiaCrates plugin;
    private final NamespacedKey keyIdentifier;
    private final Map<String, Key> keys = new HashMap<>();
    private final KeyItemFactory keyItemFactory;
    private final AsyncConfigurationWriter configurationWriter;

    public KeyManager(FotiaCrates plugin) {
        this.plugin = plugin;
        this.configurationWriter = new AsyncConfigurationWriter(plugin);
        this.keyIdentifier = new NamespacedKey(plugin, "key_id");
        this.keyItemFactory = new KeyItemFactory(createCraftEngineProvider());
        loadKeys();
    }

    private KeyItemProvider createCraftEngineProvider() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("CraftEngine")) {
            return itemId -> Optional.empty();
        }

        try {
            plugin.getLogger().info("CraftEngine key item integration enabled.");
            return new CraftEngineKeyItemProvider(plugin.getLogger());
        } catch (LinkageError error) {
            plugin.getLogger().warning("CraftEngine key item API is incompatible: " + error.getMessage());
            return itemId -> Optional.empty();
        }
    }

    /**
     * 加载所有钥匙配置
     */
    public void loadKeys() {
        if (configurationWriter.isPending()) throw new IllegalStateException("Key configuration writes are still pending");
        configurationWriter.reload();
        keys.clear();

        File keysFolder = new File(plugin.getDataFolder(), "keys");
        if (!keysFolder.exists()) {
            keysFolder.mkdirs();
            // 保存默认钥匙配置
            saveDefaultKey("common_key.yml");
            saveDefaultKey("rare_key.yml");
        }

        File[] files = keysFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                Key key = loadKey(id, file);
                if (key != null) {
                    keys.put(id, key);
                    plugin.getLogger().info("Loaded key: " + id);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load key " + id + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + keys.size() + " keys.");
    }

    /**
     * 保存默认钥匙配置
     */
    private void saveDefaultKey(String fileName) {
        File file = new File(plugin.getDataFolder(), "keys/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("keys/" + fileName, false);
        }
    }

    /**
     * 从文件加载钥匙
     */
    private Key loadKey(String id, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name", id);

        ConfigurationSection itemSection = config.getConfigurationSection("item");
        Material material = Material.TRIPWIRE_HOOK;
        String displayName = name;
        List<String> lore = new ArrayList<>();
        int customModelData = 0;
        boolean glow = true;

        if (itemSection != null) {
            material = Material.valueOf(itemSection.getString("material", "TRIPWIRE_HOOK"));
            displayName = itemSection.getString("name", name);
            lore = itemSection.getStringList("lore");
            customModelData = itemSection.getInt("custom-model-data", 0);
            glow = itemSection.getBoolean("glow", true);
        }

        List<String> allowedCrates = config.getStringList("allowed-crates");

        Key key = new Key(id, name, material, displayName, lore, customModelData, glow, allowedCrates);

        // 加载新字段
        if (itemSection != null) {
            key.setItemModel(itemSection.getString("item-model"));
            key.setTooltipStyle(itemSection.getString("tooltip-style"));
            key.setCraftEngineId(KeyItemConfig.readCraftEngineId(itemSection));
            key.setItemsAdderId(itemSection.getString("itemsadder-id"));
            key.setOraxenId(itemSection.getString("oraxen-id"));

            // 加载自定义物品
            if (itemSection.contains("custom-item")) {
                ItemStack customItem = itemSection.getItemStack("custom-item");
                if (customItem != null) {
                    key.setItem(customItem);
                }
            }
        }

        return key;
    }

    /**
     * 获取钥匙
     */
    public Key getKey(String id) {
        return keys.get(id);
    }

    /**
     * 获取所有钥匙
     */
    public Collection<Key> getAllKeys() {
        return keys.values();
    }

    /**
     * 获取所有钥匙ID
     */
    public Set<String> getKeyIds() {
        return keys.keySet();
    }

    /**
     * 获取可以打开指定宝箱的所有钥匙
     */
    public List<Key> getKeysForCrate(String crateId) {
        List<Key> result = new ArrayList<>();
        for (Key key : keys.values()) {
            if (key.canOpenCrate(crateId)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * 获取玩家的虚拟钥匙数量
     */
    public int getVirtualKeys(UUID uuid, String keyId) {
        return plugin.getAsyncPlayerDataManager().getVirtualKeys(uuid, keyId);
    }

    /**
     * 设置玩家的虚拟钥匙数量
     */
    public boolean setVirtualKeys(UUID uuid, String keyId, int amount) {
        return plugin.getAsyncPlayerDataManager().setVirtualKeys(uuid, keyId, amount);
    }

    /**
     * 添加虚拟钥匙
     */
    public boolean addVirtualKeys(UUID uuid, String keyId, int amount) {
        return plugin.getAsyncPlayerDataManager().addVirtualKeys(uuid, keyId, amount);
    }

    /**
     * 移除虚拟钥匙
     */
    public boolean removeVirtualKeys(UUID uuid, String keyId, int amount) {
        return plugin.getAsyncPlayerDataManager().removeVirtualKeys(uuid, keyId, amount);
    }

    /**
     * 创建物理钥匙物品
     */
    public ItemStack createPhysicalKey(Key key, int amount) {
        KeyItemFactory.Resolution resolution = keyItemFactory.resolve(key);
        ItemBuilder builder = new ItemBuilder(resolution.item())
                .name(key.getDisplayName())
                .lore(key.getLore())
                .glow(key.isGlow());
        if (!resolution.configuredItem()) {
            builder.customModelData(key.getCustomModelData());
        }
        ItemStack item = builder.build();

        item.setAmount(amount);
        applyKeyIdentifier(item, key);

        return item;
    }

    private void applyKeyIdentifier(ItemStack item, Key key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyIdentifier, PersistentDataType.STRING, key.getId());
            item.setItemMeta(meta);
        }
    }

    /**
     * 创建物理钥匙物品（通过ID）
     */
    public ItemStack createPhysicalKey(String keyId, int amount) {
        Key key = getKey(keyId);
        if (key == null) return null;
        return createPhysicalKey(key, amount);
    }

    /**
     * 检查物品是否是钥匙
     */
    public boolean isPhysicalKey(ItemStack item) {
        return readKeyId(item) != null;
    }

    /**
     * 获取物品的钥匙ID
     */
    public String getKeyId(ItemStack item) {
        return readKeyId(item);
    }

    /**
     * 单次 getItemMeta 读取钥匙ID（getItemMeta 每次调用都会克隆整份 meta，禁止重复调用）
     */
    private String readKeyId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyIdentifier, PersistentDataType.STRING);
    }

    /**
     * 获取玩家拥有的指定钥匙的物理数量
     */
    public int getPhysicalKeys(Player player, String keyId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && keyId.equals(readKeyId(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 给予玩家物理钥匙
     */
    public void givePhysicalKeys(Player player, String keyId, int amount) {
        Key key = getKey(keyId);
        if (key == null) return;

        ItemStack keyItem = createPhysicalKey(key, amount);
        player.getInventory().addItem(keyItem).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /**
     * 移除玩家的物理钥匙
     */
    public boolean removePhysicalKeys(Player player, String keyId, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && keyId.equals(readKeyId(item))) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    player.getInventory().setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }

        return remaining == 0;
    }

    /**
     * 获取玩家拥有的指定钥匙的总数量
     */
    public int getTotalKeys(Player player, String keyId) {
        return getVirtualKeys(player.getUniqueId(), keyId) + getPhysicalKeys(player, keyId);
    }

    /**
     * 获取玩家可以打开指定宝箱的钥匙总数
     */
    public int getTotalKeysForCrate(Player player, String crateId) {
        int total = 0;
        for (Key key : getKeysForCrate(crateId)) {
            total += getTotalKeys(player, key.getId());
        }
        return total;
    }

    /**
     * 消耗一把可以打开指定宝箱的钥匙
     */
    public boolean consumeKeyForCrate(Player player, String crateId, KeyType preferredType) {
        return consumeKeyForCrateDetailed(player, crateId, preferredType) != null;
    }

    /**
     * 消耗一把可以打开指定宝箱的钥匙，返回消耗明细（用于开箱失败时精确退还）
     * @return 消耗明细，没有可消耗的钥匙时返回 null
     */
    public ConsumedKey consumeKeyForCrateDetailed(Player player, String crateId, KeyType preferredType) {
        for (Key key : getKeysForCrate(crateId)) {
            ConsumedKey consumed = consumeKeyDetailed(player, key.getId(), preferredType);
            if (consumed != null) {
                return consumed;
            }
        }
        return null;
    }

    /**
     * 消耗指定钥匙
     */
    public boolean consumeKey(Player player, String keyId, KeyType preferredType) {
        return consumeKeyDetailed(player, keyId, preferredType) != null;
    }

    /**
     * 消耗指定钥匙并返回明细；物理数量仅在需要时才扫描背包
     */
    public ConsumedKey consumeKeyDetailed(Player player, String keyId, KeyType preferredType) {
        if (preferredType == KeyType.VIRTUAL || preferredType == KeyType.ALL) {
            if (getVirtualKeys(player.getUniqueId(), keyId) > 0
                    && removeVirtualKeys(player.getUniqueId(), keyId, 1)) {
                return new ConsumedKey(keyId, false);
            }
        }

        if (preferredType == KeyType.PHYSICAL || preferredType == KeyType.ALL) {
            if (removePhysicalKeys(player, keyId, 1)) {
                return new ConsumedKey(keyId, true);
            }
        }

        // 回退逻辑
        if (preferredType == KeyType.VIRTUAL && removePhysicalKeys(player, keyId, 1)) {
            return new ConsumedKey(keyId, true);
        }
        if (preferredType == KeyType.PHYSICAL
                && getVirtualKeys(player.getUniqueId(), keyId) > 0
                && removeVirtualKeys(player.getUniqueId(), keyId, 1)) {
            return new ConsumedKey(keyId, false);
        }

        return null;
    }

    /**
     * 单次钥匙消耗明细
     * @param keyId 钥匙ID
     * @param physical true 表示消耗的是背包中的物理钥匙
     */
    public record ConsumedKey(String keyId, boolean physical) {
    }

    /**
     * 检查玩家是否有可以打开指定宝箱的钥匙
     */
    public boolean hasKeyForCrate(Player player, String crateId) {
        return getTotalKeysForCrate(player, crateId) > 0;
    }

    /**
     * 获取玩家可以打开指定宝箱的钥匙数量（别名方法）
     */
    public int getKeyCountForCrate(Player player, String crateId) {
        return getTotalKeysForCrate(player, crateId);
    }

    /**
     * 检查玩家是否有指定钥匙
     */
    public boolean hasKey(Player player, String keyId) {
        return getTotalKeys(player, keyId) > 0;
    }

    /**
     * 重新加载钥匙配置
     */
    public boolean isConfigSavePending() { return configurationWriter.isPending(); }
    public void shutdownConfigurationWriter() { configurationWriter.shutdown(); }

    public void reload() {
        loadKeys();
    }

    /**
     * 保存钥匙到文件
     */
    public void saveKey(Key key) {
        File file = new File(plugin.getDataFolder(), "keys/" + key.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("name", key.getName());
        config.set("item.material", key.getMaterial().name());
        config.set("item.name", key.getDisplayName());
        config.set("item.lore", key.getLore());
        config.set("item.custom-model-data", key.getCustomModelData());
        config.set("item.glow", key.isGlow());
        config.set("allowed-crates", key.getAllowedCrates());

        // 保存新字段
        if (key.getItemModel() != null && !key.getItemModel().isEmpty()) {
            config.set("item.item-model", key.getItemModel());
        }
        if (key.getTooltipStyle() != null && !key.getTooltipStyle().isEmpty()) {
            config.set("item.tooltip-style", key.getTooltipStyle());
        }
        if (key.getCraftEngineId() != null && !key.getCraftEngineId().isEmpty()) {
            config.set("item.craftengine-id", key.getCraftEngineId());
        }
        if (key.getItemsAdderId() != null && !key.getItemsAdderId().isEmpty()) {
            config.set("item.itemsadder-id", key.getItemsAdderId());
        }
        if (key.getOraxenId() != null && !key.getOraxenId().isEmpty()) {
            config.set("item.oraxen-id", key.getOraxenId());
        }

        // 保存自定义物品
        if (key.getCustomItem() != null) {
            config.set("item.custom-item", key.getCustomItem());
        }

        try {
            configurationWriter.save(file.toPath(), config.saveToString());
            keys.put(key.getId(), key);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save key " + key.getId() + ": " + e.getMessage());
        }
    }

    /**
     * 删除钥匙
     */
    public void deleteKey(String keyId) {
        File file = new File(plugin.getDataFolder(), "keys/" + keyId + ".yml");
        try {
            configurationWriter.delete(file.toPath());
            keys.remove(keyId);
        } catch (java.io.IOException exception) {
            plugin.getLogger().severe("Failed to delete key " + keyId + ": " + exception.getMessage());
        }
    }
}
