package gg.fotia.crates.animation;

public enum AnimationType {
    ROULETTE,
    CSGO,
    PHYSICAL,
    TRIPLE_REEL,
    CARD_REVEAL,
    ORBITAL_CONVERGENCE,
    INSTANT;

    public AnimationType templateFamily() {
        return switch (this) {
            case CSGO, PHYSICAL -> ROULETTE;
            default -> this;
        };
    }
}
