package gg.fotia.crates.gui;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI配置类
 */
public class GuiConfig {

    private final String id;
    private final String title;
    private final int size;
    private final boolean fillEnabled;
    private final Material fillMaterial;
    private final String fillName;
    private final Map<Integer, GuiItem> items;
    private final List<Integer> contentSlots; // 用于放置动态内容的槽位
    private final List<Integer> animationSlots; // 动画滚动槽位
    private final int centerSlot; // 中心槽位（最终奖励显示位置）

    public GuiConfig(String id, String title, int size, boolean fillEnabled,
                     Material fillMaterial, String fillName,
                     Map<Integer, GuiItem> items, List<Integer> contentSlots,
                     List<Integer> animationSlots, int centerSlot) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.fillEnabled = fillEnabled;
        this.fillMaterial = fillMaterial;
        this.fillName = fillName;
        this.items = items != null ? items : new HashMap<>();
        this.contentSlots = contentSlots != null ? contentSlots : new ArrayList<>();
        this.animationSlots = animationSlots != null ? animationSlots : new ArrayList<>();
        this.centerSlot = centerSlot;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public boolean isFillEnabled() {
        return fillEnabled;
    }

    public Material getFillMaterial() {
        return fillMaterial;
    }

    public String getFillName() {
        return fillName;
    }

    public Map<Integer, GuiItem> getItems() {
        return new HashMap<>(items);
    }

    public GuiItem getItem(int slot) {
        return items.get(slot);
    }

    public GuiItem getItemByAction(String action) {
        if (action == null || action.isEmpty()) {
            return null;
        }
        for (GuiItem item : items.values()) {
            if (item.getAction() != null && item.getAction().equalsIgnoreCase(action)) {
                return item;
            }
        }
        return null;
    }

    public int getSlotByAction(String action, int fallback) {
        GuiItem item = getItemByAction(action);
        return item != null ? item.getSlot() : fallback;
    }

    public List<Integer> getSlotsByAction(String action) {
        List<Integer> slots = new ArrayList<>();
        if (action == null || action.isEmpty()) {
            return slots;
        }
        for (Map.Entry<Integer, GuiItem> entry : items.entrySet()) {
            GuiItem item = entry.getValue();
            if (item.getAction() != null && item.getAction().equalsIgnoreCase(action)) {
                slots.add(entry.getKey());
            }
        }
        slots.sort(Integer::compareTo);
        return slots;
    }

    public List<Integer> getContentSlots() {
        return new ArrayList<>(contentSlots);
    }

    public List<Integer> getAnimationSlots() {
        return new ArrayList<>(animationSlots);
    }

    public int getCenterSlot() {
        return centerSlot;
    }

    public boolean hasItem(int slot) {
        return items.containsKey(slot);
    }
}
