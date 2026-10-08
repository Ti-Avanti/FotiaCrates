package gg.fotia.crates.gui.futureui;

import gg.fotia.crates.command.subcommand.OpenCommand;
import gg.fotia.futureui.api.ActionResult;
import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** 按钮仅改变展示状态或调用原开箱入口；不拼接发奖命令。 */
final class CrateMenuActions {
    private final FutureCrateMenu owner;
    private final OpenCommand open;
    CrateMenuActions(FutureCrateMenu owner) { this.owner = owner; open = new OpenCommand(owner.plugin); }
    CompletableFuture<ActionResult> run(MenuContext ctx, Node node) {
        FutureCrateMenu.mainThread();
        var s = owner.current(ctx); var p = ctx.player(); String operation = node.text("operation", "");
        if (operation.equals("closed")) { if (s != null) owner.dismiss(p, s); return ok(); }
        if (s == null || !owner.allowed(p, s)) return denied();
        if (operation.equals("opened")) return ok();
        if (operation.equals("close")) { owner.dismiss(p, s); owner.closeScreen(p); return ok(); }
        if (s.busy || owner.plugin.getOpenSessionManager().isActive(p.getUniqueId())) return denied();
        String id = argument(ctx, node, "id");
        switch (operation) {
            case "tab" -> {
                if (!Set.of("preview", "history", "results").contains(id)) return denied();
                if (id.equals("history")) owner.showHistory(p, s);
                else {
                    if (id.equals("preview") && (s.crate == null || !s.crate.isPreviewEnabled() || !p.hasPermission("fotiacrates.preview"))) return denied();
                    if (id.equals("results") && s.results.isEmpty()) return denied();
                    s.view = id; s.page = 0; s.loading = false; s.historyRevision++;
                }
            }
            case "previous" -> s.page = Math.max(0, s.page - 1);
            case "next" -> s.page = Math.min(owner.data.pages(s) - 1, s.page + 1);
            case "less" -> s.amount = Math.max(1, s.amount - 1);
            case "more" -> s.amount = Math.min(owner.data.maxAmount(p, s), s.amount + 1);
            case "maximum" -> s.amount = owner.data.maxAmount(p, s);
            case "layout" -> {
                if (s.loading || !Set.of("standard", "compact").contains(id)) return denied();
                owner.layout(p, s, id.equals("compact"));
            }
            case "draw" -> {
                if (!open.hasPermission(p) || s.crate == null || !owner.data.canDraw(p, s)) return denied();
                int amount = id.equals("single") ? 1 : Math.max(1, Math.min(s.amount, owner.data.maxAmount(p, s)));
                s.busy = true; s.feedback = "preparing"; s.results = java.util.List.of();
                open.execute(p, new String[]{s.crate.getId(), Integer.toString(amount)});
                owner.watchCompletion(p, s);
            }
            default -> { return denied(); }
        }
        return ok();
    }
    private static String argument(MenuContext ctx, Node node, String key) {
        String value = node.text(key, "");
        return value.startsWith("{") && value.endsWith("}") ? Objects.toString(ctx.variables().get(value.substring(1, value.length() - 1)), "") : value;
    }
    private static CompletableFuture<ActionResult> ok() { return CompletableFuture.completedFuture(ActionResult.ok()); }
    private static CompletableFuture<ActionResult> denied() { return CompletableFuture.completedFuture(ActionResult.fail("messages.requirement-failed")); }
}
