package gg.fotia.crates.gui;

import gg.fotia.crates.crate.Crate;
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

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
