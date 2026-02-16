package gg.fotia.crates.key;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 钥匙类
 * 一个钥匙可以对应多个宝箱
 */
public class Key {

    private final String id;
    private String name;
    private Material material;
    private String displayName;
    private List<String> lore;
    private int customModelData;
    private boolean glow;
    private List<String> allowedCrates; // 可以打开的宝箱列表
    private ItemStack customItem; // 自定义物品

    // 新增字段
    private String itemModel; // item_model (1.21.4+)
    private String tooltipStyle; // tooltip_style (1.21.4+)
    private String craftEngineId; // CraftEngine物品ID
    private String itemsAdderId; // ItemsAdder物品ID
    private String oraxenId; // Oraxen物品ID

    public Key(String id, String name, Material material, String displayName,
               List<String> lore, int customModelData, boolean glow, List<String> allowedCrates) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
        this.customModelData = customModelData;
        this.glow = glow;
        this.allowedCrates = allowedCrates != null ? new ArrayList<>(allowedCrates) : new ArrayList<>();
    }

    /**
     * 简单构造函数，用于创建新钥匙
     */
    public Key(String id, String name) {
        this.id = id;
        this.name = name;
        this.material = Material.TRIPWIRE_HOOK;
        this.displayName = name;
        this.lore = new ArrayList<>();
        this.customModelData = 0;
        this.glow = true;
        this.allowedCrates = new ArrayList<>();
    }

    /**
     * 检查此钥匙是否可以打开指定宝箱
     */
    public boolean canOpenCrate(String crateId) {
        // 如果allowedCrates为空或包含"*"，则可以打开所有宝箱
        if (allowedCrates.isEmpty() || allowedCrates.contains("*")) {
            return true;
        }
        return allowedCrates.contains(crateId);
    }

    /**
     * 添加可开启的宝箱
     */
    public void addCrate(String crateId) {
        if (!allowedCrates.contains(crateId)) {
            allowedCrates.add(crateId);
        }
    }

    /**
     * 移除可开启的宝箱
     */
    public void removeCrate(String crateId) {
        allowedCrates.remove(crateId);
    }

    /**
     * 获取可开启的宝箱ID列表
     */
    public List<String> getCrateIds() {
        return new ArrayList<>(allowedCrates);
    }

    /**
     * 获取物品
     */
    public ItemStack getItem() {
        if (customItem != null) {
            return customItem.clone();
        }
        return new ItemStack(material);
    }

    /**
     * 设置自定义物品
     */
    public void setItem(ItemStack item) {
        this.customItem = item != null ? item.clone() : null;
        if (item != null) {
            this.material = item.getType();
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.displayName = name;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getLore() {
        return new ArrayList<>(lore);
    }

    public void setLore(List<String> lore) {
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public boolean isGlow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public List<String> getAllowedCrates() {
        return new ArrayList<>(allowedCrates);
    }

    public void setAllowedCrates(List<String> allowedCrates) {
        this.allowedCrates = allowedCrates != null ? new ArrayList<>(allowedCrates) : new ArrayList<>();
    }

    public ItemStack getCustomItem() {
        return customItem != null ? customItem.clone() : null;
    }

    // ===== 新增字段的getter/setter =====

    public String getItemModel() {
        return itemModel;
    }

    public void setItemModel(String itemModel) {
        this.itemModel = itemModel;
    }

    public String getTooltipStyle() {
        return tooltipStyle;
    }

    public void setTooltipStyle(String tooltipStyle) {
        this.tooltipStyle = tooltipStyle;
    }

    public String getCraftEngineId() {
        return craftEngineId;
    }

    public void setCraftEngineId(String craftEngineId) {
        this.craftEngineId = craftEngineId;
    }

    public String getItemsAdderId() {
        return itemsAdderId;
    }

    public void setItemsAdderId(String itemsAdderId) {
        this.itemsAdderId = itemsAdderId;
    }

    public String getOraxenId() {
        return oraxenId;
    }

    public void setOraxenId(String oraxenId) {
        this.oraxenId = oraxenId;
    }

    /**
     * 检查是否使用第三方插件物品
     */
    public boolean hasExternalItem() {
        return (craftEngineId != null && !craftEngineId.isEmpty()) ||
               (itemsAdderId != null && !itemsAdderId.isEmpty()) ||
               (oraxenId != null && !oraxenId.isEmpty());
    }
}
