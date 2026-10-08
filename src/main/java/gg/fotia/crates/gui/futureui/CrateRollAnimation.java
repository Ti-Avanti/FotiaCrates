package gg.fotia.crates.gui.futureui;

import gg.fotia.crates.animation.BatchAnimation;
import gg.fotia.crates.crate.Crate;
import gg.fotia.crates.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.List;

/** 动画只展示已提交的奖励；逐帧变化不会参与中奖计算。 */
final class CrateRollAnimation implements BatchAnimation {
    private final FutureCrateMenu owner;
    private BukkitTask task;
    private Player player;
    private CrateMenuSession session;
    private Runnable completion;
    private boolean finished;
    private CrateReelSequence reel;
    private int elapsed, duration;
    CrateRollAnimation(FutureCrateMenu owner) { this.owner = owner; }
    @Override public void startBatch(Player player, Crate crate, List<Reward> rewards, Location location, Runnable completion) {
        this.player = player; this.completion = completion;
        if (rewards.isEmpty()) { finish(true); return; }
        Reward winner = rewards.get(0);
        List<Reward> pool = crate.getAvailableRewardsFor(player);
        if (pool.isEmpty()) pool = List.of(winner);
        reel = new CrateReelSequence(pool, winner, owner.settings.travelSlots(),
                Math.max(owner.settings.standard().reelSlots(), owner.settings.compact().reelSlots()),
                owner.settings.slowdownPower(), owner.settings.landingSpread(), owner.settings.avoidAdjacentDuplicates());
        duration = Math.min(owner.settings.maxAnimationSeconds(), Math.max(1, crate.getAnimationDuration())) * 20;
        session = owner.startRoll(player, crate, this);
        if (session == null) { finish(true); return; }
        step();
    }
    private void step() {
        if (finished) return;
        if (!player.isOnline() || session.detached || !owner.visible(player, session)) { finish(true); return; }
        session.progress = Math.min(1, (double) elapsed / duration);
        boolean done = elapsed >= duration;
        List<Reward> next = frames(session.compact, session.progress);
        if (!done && !next.equals(session.frames)) {
            if (session.crate.getSpinSound() != null) player.playSound(player.getLocation(), session.crate.getSpinSound(),
                    session.crate.getSpinVolume(), session.crate.getSpinPitch());
        }
        session.frames = next;
        session.reelPosition = reel.offset(session.progress);
        session.selectedFrame = done ? reel.selectedIndex(owner.settings.profile(session.compact).reelSlots()) : -1;
        session.feedback = done ? "revealed" : session.progress >= 0.6 ? "braking" : "spinning";
        owner.refresh(player, session);
        if (done) task = owner.plugin.getServer().getScheduler().runTaskLater(owner.plugin, () -> finish(true), owner.settings.holdTicks());
        else {
            int delay = Math.min(owner.settings.frameTicks(), duration - elapsed);
            elapsed += delay;
            task = owner.plugin.getServer().getScheduler().runTaskLater(owner.plugin, this::step, delay);
        }
    }
    List<Reward> frames(boolean compact, double progress) {
        return reel.frame(owner.settings.profile(compact).reelSlots(), progress);
    }
    void finish(boolean notify) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        if (session != null) { session.feedback = "settling"; session.roll = null; }
        if (notify && completion != null) completion.run();
    }
    @Override public void cancel() { finish(false); }
    @Override public boolean isRunning() { return !finished; }
}
