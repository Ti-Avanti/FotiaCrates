package gg.fotia.crates.gui;

import gg.fotia.crates.crate.Crate;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI持有者
 * 存储GUI相关数据
 */
public class CrateGuiHolder implements InventoryHolder {

    private final GuiType guiType;
    private final Crate crate;
    private final Map<String, Object> data;
    private int currentPage = 0;
    private Inventory inventory;

    public CrateGuiHolder(GuiType guiType, Crate crate) {
        this.guiType = guiType;
        this.crate = crate;
        this.data = new HashMap<>();
    }

    public GuiType getGuiType() {
        return guiType;
    }

    public Crate getCrate() {
        return crate;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = page;
    }

    public void setData(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    public void removeData(String key) {
        data.remove(key);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        // InventoryHolder 契约要求非 null；未绑定时兜底创建，避免第三方插件调用时 NPE
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 9);
        }
        return inventory;
    }
}
