package gg.fotia.crates.gui;

import java.util.Locale;

/**
 * 使用从零开始的页码描述GUI分页状态。
 */
public record GuiPaginationState(int currentPage, int totalPages) {

    public GuiPaginationState {
        totalPages = Math.max(1, totalPages);
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
    }

    public boolean isActionAvailable(String action) {
        if (action == null || action.isBlank()) {
            return true;
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "prev_page", "previous_page" -> currentPage > 0;
            case "next_page" -> currentPage + 1 < totalPages;
            default -> true;
        };
    }
}
