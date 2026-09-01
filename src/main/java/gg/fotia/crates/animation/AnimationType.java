package gg.fotia.crates.animation;

public enum AnimationType {
    ROULETTE,
    CSGO,
    PHYSICAL,
    // 仅用于读取旧配置，运行时迁移为 CARD_REVEAL。
    TRIPLE_REEL,
    CARD_REVEAL,
    ORBITAL_CONVERGENCE,
    VOID_RIFT,
    METEOR_JUDGMENT,
    INSTANT;

    public AnimationType templateFamily() {
        return switch (this) {
            case CSGO, PHYSICAL -> ROULETTE;
            case TRIPLE_REEL -> CARD_REVEAL;
            default -> this;
        };
    }
}
