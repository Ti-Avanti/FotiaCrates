package gg.fotia.crates.animation;

import java.util.Optional;

public final class AnimationEditorAction {

    private AnimationEditorAction() {
    }

    public static Optional<AnimationType> selectedType(String action) {
        if (action == null) {
            return Optional.empty();
        }
        return switch (action.toLowerCase()) {
            case "select_roulette" -> Optional.of(AnimationType.ROULETTE);
            case "select_card_reveal" -> Optional.of(AnimationType.CARD_REVEAL);
            case "select_orbital_convergence" -> Optional.of(AnimationType.ORBITAL_CONVERGENCE);
            case "select_void_rift" -> Optional.of(AnimationType.VOID_RIFT);
            case "select_meteor_judgment" -> Optional.of(AnimationType.METEOR_JUDGMENT);
            case "select_instant" -> Optional.of(AnimationType.INSTANT);
            default -> Optional.empty();
        };
    }
}
