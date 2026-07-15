package gg.fotia.crates.animation;

final class PhysicalAnimationSchedule {

    private static final int MIN_DURATION_SECONDS = 1;
    private static final int MAX_DURATION_SECONDS = 30;

    private final boolean[] scrollTicks;
    private final int[] remainingScrolls;

    PhysicalAnimationSchedule(int totalTicks) {
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("totalTicks must be positive");
        }

        scrollTicks = new boolean[totalTicks];
        remainingScrolls = new int[totalTicks + 1];
        int lastScrollTick = 0;
        int scrollEnd = Math.max(0, totalTicks - 10);

        for (int tick = 0; tick < scrollEnd; tick++) {
            int interval = intervalAt(tick, totalTicks);
            if (tick - lastScrollTick >= interval) {
                scrollTicks[tick] = true;
                lastScrollTick = tick;
            }
        }

        for (int tick = totalTicks - 1; tick >= 0; tick--) {
            remainingScrolls[tick] = remainingScrolls[tick + 1] + (scrollTicks[tick] ? 1 : 0);
        }
    }

    boolean shouldScroll(int tick) {
        return tick >= 0 && tick < scrollTicks.length && scrollTicks[tick];
    }

    int remainingScrolls(int tick) {
        int boundedTick = Math.max(0, Math.min(tick, scrollTicks.length));
        return remainingScrolls[boundedTick];
    }

    static int clampDurationSeconds(int durationSeconds) {
        return Math.max(MIN_DURATION_SECONDS, Math.min(durationSeconds, MAX_DURATION_SECONDS));
    }

    private static int intervalAt(int tick, int totalTicks) {
        float progress = (float) tick / totalTicks;
        if (progress < 0.3f) return 1;
        if (progress < 0.5f) return 2;
        if (progress < 0.65f) return 3;
        if (progress < 0.75f) return 5;
        if (progress < 0.85f) return 8;
        if (progress < 0.95f) return 12;
        return 20;
    }
}
