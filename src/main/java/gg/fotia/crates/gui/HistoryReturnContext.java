package gg.fotia.crates.gui;

public record HistoryReturnContext(ReturnTarget target, String crateId, int page) {

    public HistoryReturnContext {
        target = target == null ? ReturnTarget.CLOSE : target;
        page = Math.max(0, page);
        if (target != ReturnTarget.PREVIEW || crateId == null || crateId.isBlank()) {
            target = ReturnTarget.CLOSE;
            crateId = null;
            page = 0;
        }
    }

    public static HistoryReturnContext close() {
        return new HistoryReturnContext(ReturnTarget.CLOSE, null, 0);
    }

    public static HistoryReturnContext preview(String crateId, int page) {
        return new HistoryReturnContext(ReturnTarget.PREVIEW, crateId, page);
    }

    public boolean returnsToPreview() {
        return target == ReturnTarget.PREVIEW;
    }

    public enum ReturnTarget {
        CLOSE,
        PREVIEW
    }
}
