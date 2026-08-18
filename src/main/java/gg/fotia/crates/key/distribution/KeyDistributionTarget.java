package gg.fotia.crates.key.distribution;

public record KeyDistributionTarget(Kind kind, String playerName) {

    public static KeyDistributionTarget parse(String value) {
        if ("@online".equalsIgnoreCase(value)) {
            return new KeyDistributionTarget(Kind.ONLINE, null);
        }
        if ("@all".equalsIgnoreCase(value)) {
            return new KeyDistributionTarget(Kind.ALL, null);
        }
        return new KeyDistributionTarget(Kind.PLAYER, value);
    }

    public enum Kind {
        PLAYER,
        ONLINE,
        ALL
    }
}
