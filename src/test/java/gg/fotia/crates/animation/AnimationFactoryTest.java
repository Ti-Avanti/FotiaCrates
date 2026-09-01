package gg.fotia.crates.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationFactoryTest {

    @Test
    void createsDedicatedAnimationForEachConfiguredType() {
        AnimationFactory factory = new AnimationFactory(null);

        assertInstanceOf(RouletteAnimation.class, factory.create(AnimationType.ROULETTE));
        assertInstanceOf(RouletteAnimation.class, factory.create(AnimationType.CSGO));
        assertInstanceOf(TripleReelAnimation.class, factory.create(AnimationType.TRIPLE_REEL));
        assertInstanceOf(CardRevealAnimation.class, factory.create(AnimationType.CARD_REVEAL));
        assertInstanceOf(OrbitalConvergenceAnimation.class,
                factory.create(AnimationType.ORBITAL_CONVERGENCE));
        assertNull(factory.create(AnimationType.INSTANT));
    }
}
