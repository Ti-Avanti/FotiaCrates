package gg.fotia.crates.gui;

public record PreviewMultiOpenHintConfig(String available, String unavailable) {

    public static PreviewMultiOpenHintConfig defaults() {
        return new PreviewMultiOpenHintConfig(
                "<!i><yellow>右键 <!i><gray>- 多连抽 {amount} 次",
                "<!i><dark_gray>右键多连抽不可用"
        );
    }

    public String render(int amount) {
        String template = amount > 1 ? available : unavailable;
        return (template == null ? "" : template).replace("{amount}", String.valueOf(amount));
    }
}
