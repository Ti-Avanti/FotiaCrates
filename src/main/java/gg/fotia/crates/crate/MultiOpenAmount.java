package gg.fotia.crates.crate;

public final class MultiOpenAmount {

    private MultiOpenAmount() {
    }

    public static int forPreviewRightClick(boolean multiOpenEnabled, int configuredMax, int availableKeys) {
        if (!multiOpenEnabled || configuredMax <= 1 || availableKeys <= 1) {
            return 1;
        }
        return Math.max(1, Math.min(configuredMax, availableKeys));
    }
}
