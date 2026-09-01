package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnimationManager {

    private final FotiaCrates plugin;
    private final AnimationFactory animationFactory;
    private final Map<UUID, AnimationSession> activeSessions = new HashMap<>();

    public AnimationManager(FotiaCrates plugin) {
        this.plugin = plugin;
        this.animationFactory = new AnimationFactory(plugin);
    }

    public boolean playAnimation(Player player, Crate crate, Reward reward, Location crateLocation, Runnable onComplete) {
        return playAnimation(player, crate, List.of(reward), crateLocation, onComplete);
    }

    public boolean playAnimation(Player player, Crate crate, List<Reward> rewards,
                                 Location crateLocation, Runnable onComplete) {
        boolean guiAnimEnabled = crate.isAnimationEnabled();
        boolean physicalAnimEnabled = crate.isPhysicalAnimationEnabled();
        List<Animation> animations = new ArrayList<>(2);

        if (guiAnimEnabled) {
            Animation animation = animationFactory.create(crate.getAnimationType());
            if (animation != null) {
                animations.add(animation);
            }
        }

        if (physicalAnimEnabled) {
            animations.add(new PhysicalAnimation(plugin));
        }

        return startAnimations(player, crate,
                AnimationRewardBatchPolicy.forType(crate.getAnimationType(), rewards),
                crateLocation, onComplete, animations);
    }

    public boolean previewAnimation(Player player, Crate crate, Reward reward,
                                    Location crateLocation, Runnable onComplete) {
        List<Animation> animations = new ArrayList<>(2);
        Animation selected = animationFactory.create(crate.getAnimationType());
        if (selected != null) {
            animations.add(selected);
        }
        if (crate.isPhysicalAnimationEnabled()) {
            animations.add(new PhysicalAnimation(plugin));
        }
        return startAnimations(player, crate, List.of(reward), crateLocation, onComplete, animations);
    }

    private boolean startAnimations(Player player, Crate crate, List<Reward> rewards,
                                    Location crateLocation, Runnable onComplete,
                                    List<Animation> animations) {
        if (hasActiveAnimation(player)) {
            return false;
        }

        if (animations.isEmpty()) {
            onComplete.run();
            return true;
        }
        if (rewards == null || rewards.isEmpty()) {
            onComplete.run();
            return true;
        }

        UUID playerId = player.getUniqueId();
        AnimationSession session = new AnimationSession(playerId, animations, onComplete);
        activeSessions.put(playerId, session);
        try {
            for (Animation animation : animations) {
                if (animation instanceof BatchAnimation batchAnimation) {
                    batchAnimation.startBatch(player, crate, rewards,
                            crateLocation, session::completeOne);
                } else {
                    animation.start(player, crate, rewards.get(0),
                            crateLocation, session::completeOne);
                }
            }
            return true;
        } catch (RuntimeException exception) {
            session.cancelAndComplete();
            throw exception;
        }
    }

    public boolean hasActiveAnimation(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void cancelAnimation(Player player) {
        AnimationSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.cancelAndComplete();
        }
    }

    public void cancelAllAnimations() {
        for (AnimationSession session : List.copyOf(activeSessions.values())) {
            session.cancelAndComplete();
        }
    }

    public Animation getAnimation(Player player) {
        AnimationSession session = activeSessions.get(player.getUniqueId());
        return session == null || session.animations.isEmpty() ? null : session.animations.get(0);
    }

    public boolean handleInventoryClick(Player player, int slot) {
        AnimationSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        for (Animation animation : session.animations) {
            if (animation instanceof InteractiveAnimation interactive
                    && interactive.handleClick(player, slot)) {
                return true;
            }
        }
        return false;
    }

    private final class AnimationSession {
        private final UUID playerId;
        private final List<Animation> animations;
        private final Runnable onComplete;
        private int remaining;
        private boolean completed;

        private AnimationSession(UUID playerId, List<Animation> animations, Runnable onComplete) {
            this.playerId = playerId;
            this.animations = List.copyOf(animations);
            this.onComplete = onComplete;
            this.remaining = animations.size();
        }

        private void completeOne() {
            if (completed || --remaining > 0) {
                return;
            }
            complete();
        }

        private void cancelAndComplete() {
            if (completed) {
                return;
            }
            for (Animation animation : animations) {
                animation.cancel();
            }
            complete();
        }

        private void complete() {
            if (completed) {
                return;
            }
            completed = true;
            activeSessions.remove(playerId, this);
            onComplete.run();
        }
    }
}
