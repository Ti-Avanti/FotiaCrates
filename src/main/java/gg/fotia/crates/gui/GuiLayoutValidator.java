package gg.fotia.crates.gui;

import java.util.ArrayList;
import java.util.List;

public final class GuiLayoutValidator {

    private static final int INVENTORY_COLUMNS = 9;

    private GuiLayoutValidator() {
    }

    public static List<Integer> invalidRows(List<String> layout) {
        if (layout == null || layout.isEmpty()) {
            return List.of();
        }
        List<Integer> invalid = new ArrayList<>();
        for (int index = 0; index < layout.size(); index++) {
            String row = layout.get(index);
            if (row == null || row.length() != INVENTORY_COLUMNS) {
                invalid.add(index);
            }
        }
        return List.copyOf(invalid);
    }
}
