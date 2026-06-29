package kr.toxicity.model.api.tracker;

import kr.toxicity.model.api.animation.AnimationModifier;

/**
 * Minimal compile-time stub for BetterModel 3.x. Excluded from the plugin jar.
 */
public abstract class Tracker {

    public abstract boolean animate(String animationName, AnimationModifier modifier);

    public abstract boolean stopAnimation(String animationName);
}
