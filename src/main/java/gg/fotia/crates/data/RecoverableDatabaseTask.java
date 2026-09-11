package gg.fotia.crates.data;

import java.util.concurrent.atomic.AtomicBoolean;

/** A queued write retains its recovery action until execution actually starts. */
final class RecoverableDatabaseTask implements Runnable {
    private final Runnable action;
    private final Runnable recovery;
    private final AtomicBoolean claimed = new AtomicBoolean();

    RecoverableDatabaseTask(Runnable action, Runnable recovery) {
        this.action = action;
        this.recovery = recovery;
    }

    @Override
    public void run() {
        if (claimed.compareAndSet(false, true)) {
            action.run();
        }
    }

    void discard() {
        if (claimed.compareAndSet(false, true) && recovery != null) {
            recovery.run();
        }
    }
}
