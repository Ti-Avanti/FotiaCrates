package gg.fotia.crates.animation;

record CardRevealSchedule(
        int startDelayTicks,
        int revealIntervalTicks,
        int finalHoldTicks,
        int revealCount
) {

    static CardRevealSchedule create(int durationTicks, int revealCount,
                                     int configuredIntervalTicks, int finalHoldTicks) {
        int count = Math.max(1, revealCount);
        int hold = Math.max(1, finalHoldTicks);
        int duration = Math.max(count + hold + 5, durationTicks);
        int available = Math.max(count, duration - hold);
        int interval = Math.max(1, Math.min(configuredIntervalTicks, available / count));
        int delay = Math.max(5, duration - hold - interval * count);
        return new CardRevealSchedule(delay, interval, hold, count);
    }

    int completionTick() {
        return startDelayTicks + revealIntervalTicks * revealCount + finalHoldTicks;
    }
}
