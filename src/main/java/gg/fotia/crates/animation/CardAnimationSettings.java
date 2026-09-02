package gg.fotia.crates.animation;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record CardAnimationSettings(
        CardBackDisplay backDisplay,
        int cardCount,
        int flickerIntervalTicks,
        int flickerMinCycles,
        int flickerMinTicks,
        int coverDelayTicks,
        int revealIntervalTicks,
        int selectionTimeoutTicks,
        int pageTransitionTicks,
        int resultHoldTicks,
        int statusSlot,
        String flickerStatusName,
        String coverStatusName,
        String selectStatusName,
        String completeStatusName
) {

    private static final int DEFAULT_CARD_COUNT = 15;
    private static final int DEFAULT_FLICKER_INTERVAL = 3;
    private static final int DEFAULT_FLICKER_CYCLES = 1;
    private static final int DEFAULT_FLICKER_MIN_TICKS = 40;
    private static final int DEFAULT_COVER_DELAY = 10;
    private static final int DEFAULT_INTERVAL = 5;
    private static final int DEFAULT_SELECTION_TIMEOUT_TICKS = 300;
    private static final int DEFAULT_PAGE_TRANSITION_TICKS = 20;
    private static final int DEFAULT_RESULT_HOLD_TICKS = 40;
    private static final int DEFAULT_STATUS_SLOT = 4;
    private static final String DEFAULT_FLICKER_STATUS = "<!i><gold>奖池闪烁中...";
    private static final String DEFAULT_COVER_STATUS = "<!i><light_purple>秘匣封牌中...";
    private static final String DEFAULT_SELECT_STATUS =
            "<!i><green>请选择卡牌 <!i><gray>({revealed}/{total})";
    private static final String DEFAULT_COMPLETE_STATUS = "<!i><gold>全部奖励已揭晓";

    public Material backMaterial() {
        return backDisplay.material();
    }

    public String backName() {
        return backDisplay.name();
    }

    public static CardAnimationSettings from(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        int legacyCoverDelay = section.getInt("shuffle-ticks", DEFAULT_COVER_DELAY);
        int legacyFlickerTicks = section.getInt("showcase-page-ticks", 20)
                + section.getInt("shuffle-ticks", 20);
        String flickerStatus = section.getString("status.flicker-name",
                section.getString("status.showcase-name", DEFAULT_FLICKER_STATUS));
        String coverStatus = section.getString("status.cover-name",
                section.getString("status.shuffle-name", DEFAULT_COVER_STATUS));
        return new CardAnimationSettings(
                CardBackDisplay.from(section),
                clamp(section.getInt("card-count", DEFAULT_CARD_COUNT), 1, 54),
                clamp(section.getInt("flicker-interval-ticks", DEFAULT_FLICKER_INTERVAL), 1, 20),
                clamp(section.getInt("flicker-min-cycles", DEFAULT_FLICKER_CYCLES), 1, 10),
                clamp(section.getInt("flicker-min-ticks", legacyFlickerTicks), 10, 400),
                clamp(section.getInt("cover-delay-ticks", legacyCoverDelay), 1, 100),
                Math.max(1, Math.min(20,
                        section.getInt("reveal-interval-ticks", DEFAULT_INTERVAL))),
                clamp(section.getInt("selection-timeout-ticks", DEFAULT_SELECTION_TIMEOUT_TICKS),
                        100, 2400),
                clamp(section.getInt("page-transition-ticks", DEFAULT_PAGE_TRANSITION_TICKS),
                        5, 100),
                clamp(section.getInt("result-hold-ticks", DEFAULT_RESULT_HOLD_TICKS), 10, 200),
                clamp(section.getInt("status-slot", DEFAULT_STATUS_SLOT), 0, 53),
                flickerStatus,
                coverStatus,
                section.getString("status.select-name", DEFAULT_SELECT_STATUS),
                section.getString("status.complete-name", DEFAULT_COMPLETE_STATUS)
        );
    }

    private static CardAnimationSettings defaults() {
        return new CardAnimationSettings(
                CardBackDisplay.from(null),
                DEFAULT_CARD_COUNT,
                DEFAULT_FLICKER_INTERVAL,
                DEFAULT_FLICKER_CYCLES,
                DEFAULT_FLICKER_MIN_TICKS,
                DEFAULT_COVER_DELAY,
                DEFAULT_INTERVAL,
                DEFAULT_SELECTION_TIMEOUT_TICKS,
                DEFAULT_PAGE_TRANSITION_TICKS,
                DEFAULT_RESULT_HOLD_TICKS,
                DEFAULT_STATUS_SLOT,
                DEFAULT_FLICKER_STATUS,
                DEFAULT_COVER_STATUS,
                DEFAULT_SELECT_STATUS,
                DEFAULT_COMPLETE_STATUS
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
