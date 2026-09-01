package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;

public final class AnimationFactory {

    private final FotiaCrates plugin;

    public AnimationFactory(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public Animation create(AnimationType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ROULETTE, CSGO, PHYSICAL -> new RouletteAnimation(plugin);
            case TRIPLE_REEL -> new TripleReelAnimation(plugin);
            case CARD_REVEAL -> new CardRevealAnimation(plugin);
            case ORBITAL_CONVERGENCE -> new OrbitalConvergenceAnimation(plugin);
            case INSTANT -> null;
        };
    }
}
