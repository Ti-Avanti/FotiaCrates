package gg.fotia.crates.animation;

final class TripleReelSchedule {

    static final int REEL_COUNT = 3;

    private final int totalTicks;
    private final int stopGapTicks;

    TripleReelSchedule(int totalTicks, int stopGapTicks) {
        this.stopGapTicks = Math.max(1, stopGapTicks);
        this.totalTicks = Math.max(totalTicks, this.stopGapTicks * REEL_COUNT + 10);
    }

    int totalTicks() {
        return totalTicks;
    }

    int stopTick(int reelIndex) {
        int resolvedIndex = Math.max(0, Math.min(REEL_COUNT - 1, reelIndex));
        return totalTicks - (REEL_COUNT - 1 - resolvedIndex) * stopGapTicks;
    }

    boolean isSpinning(int reelIndex, int tick) {
        return tick < stopTick(reelIndex);
    }
}
