package gg.fotia.crates.gui;

/**
 * 根据分页状态选择GUI按钮显示。
 */
public final class GuiItemDisplayResolver {

    private GuiItemDisplayResolver() {
    }

    public static GuiItemDisplay resolve(GuiItem item, GuiPaginationState paginationState) {
        if (paginationState != null
                && !paginationState.isActionAvailable(item.getAction())
                && item.getUnavailableDisplay() != null) {
            return item.getUnavailableDisplay();
        }
        return item.getDisplay();
    }
}
