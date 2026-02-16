package gg.fotia.crates.animation;

import gg.fotia.crates.FotiaCrates;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationManager {

    private final FotiaCrates plugin;
    private final Map<UUID, Animation> activeAnimations = new HashMap<>();
    private final Map<UUID, Animation> activePhysicalAnimations = new HashMap<>(); // 物理动画单独存储

    public AnimationManager(FotiaCrates plugin) {
        this.plugin = plugin;
    }

    public void playAnimation(Player player, Crate crate, Reward reward, Location crateLocation, Runnable onComplete) {
        if (hasActiveAnimation(player)) {
            return;
        }

        boolean guiAnimEnabled = crate.isAnimationEnabled();
        boolean physicalAnimEnabled = crate.isPhysicalAnimationEnabled();

        // 如果两者都启用，同时播放
        if (guiAnimEnabled && physicalAnimEnabled) {
            playBothAnimations(player, crate, reward, crateLocation, onComplete);
            return;
        }

        // 只启用物理动画
        if (physicalAnimEnabled) {
            Animation animation = new PhysicalAnimation(plugin);
            activeAnimations.put(player.getUniqueId(), animation);
            animation.start(player, crate, reward, crateLocation, () -> {
                activeAnimations.remove(player.getUniqueId());
                onComplete.run();
            });
            return;
        }

        // 只启用GUI动画或默认
        if (guiAnimEnabled) {
            Animation animation;
            switch (crate.getAnimationType()) {
                case CSGO:
                    animation = new RouletteAnimation(plugin);
                    break;
                case PHYSICAL:
                    // 如果类型是PHYSICAL但physicalAnimEnabled为false，使用轮盘
                    animation = new RouletteAnimation(plugin);
                    break;
                case INSTANT:
                    onComplete.run();
                    return;
                case ROULETTE:
                default:
                    animation = new RouletteAnimation(plugin);
                    break;
            }

            activeAnimations.put(player.getUniqueId(), animation);
            animation.start(player, crate, reward, crateLocation, () -> {
                activeAnimations.remove(player.getUniqueId());
                onComplete.run();
            });
            return;
        }

        // 都没启用，直接完成
        onComplete.run();
    }

    /**
     * 同时播放GUI动画和物理动画
     */
    private void playBothAnimations(Player player, Crate crate, Reward reward, Location crateLocation, Runnable onComplete) {
        // 创建GUI动画
        Animation guiAnimation;
        switch (crate.getAnimationType()) {
            case CSGO:
                guiAnimation = new RouletteAnimation(plugin);
                break;
            case INSTANT:
                guiAnimation = null;
                break;
            case ROULETTE:
            default:
                guiAnimation = new RouletteAnimation(plugin);
                break;
        }

        // 创建物理动画
        PhysicalAnimation physicalAnimation = new PhysicalAnimation(plugin);

        // 跟踪完成状态
        final boolean[] completed = {false, false}; // [gui, physical]
        final Runnable checkComplete = () -> {
            if (completed[0] && completed[1]) {
                activeAnimations.remove(player.getUniqueId());
                activePhysicalAnimations.remove(player.getUniqueId());
                onComplete.run();
            }
        };

        // 启动GUI动画
        if (guiAnimation != null) {
            activeAnimations.put(player.getUniqueId(), guiAnimation);
            guiAnimation.start(player, crate, reward, crateLocation, () -> {
                completed[0] = true;
                checkComplete.run();
            });
        } else {
            completed[0] = true;
        }

        // 启动物理动画
        activePhysicalAnimations.put(player.getUniqueId(), physicalAnimation);
        physicalAnimation.start(player, crate, reward, crateLocation, () -> {
            completed[1] = true;
            checkComplete.run();
        });
    }

    public boolean hasActiveAnimation(Player player) {
        Animation animation = activeAnimations.get(player.getUniqueId());
        Animation physicalAnimation = activePhysicalAnimations.get(player.getUniqueId());
        return (animation != null && animation.isRunning()) ||
               (physicalAnimation != null && physicalAnimation.isRunning());
    }

    public void cancelAnimation(Player player) {
        Animation animation = activeAnimations.remove(player.getUniqueId());
        if (animation != null) {
            animation.cancel();
        }
        Animation physicalAnimation = activePhysicalAnimations.remove(player.getUniqueId());
        if (physicalAnimation != null) {
            physicalAnimation.cancel();
        }
    }

    public void cancelAllAnimations() {
        for (Animation animation : activeAnimations.values()) {
            animation.cancel();
        }
        activeAnimations.clear();
        for (Animation animation : activePhysicalAnimations.values()) {
            animation.cancel();
        }
        activePhysicalAnimations.clear();
    }

    public Animation getAnimation(Player player) {
        return activeAnimations.get(player.getUniqueId());
    }
}
