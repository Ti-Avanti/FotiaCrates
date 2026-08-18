package gg.fotia.crates.animation;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

public final class AnimationTemplateSelection {

    public static final String DEFAULT_TEMPLATE = "default";

    private AnimationTemplateSelection() {
    }

    public static String normalize(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return DEFAULT_TEMPLATE;
        }
        return templateId.trim().toLowerCase(Locale.ROOT);
    }

    public static String resolve(String requested, Collection<String> availableTemplates) {
        String normalized = normalize(requested);
        if (availableTemplates == null || availableTemplates.isEmpty()) {
            return DEFAULT_TEMPLATE;
        }
        for (String available : availableTemplates) {
            if (normalize(available).equals(normalized)) {
                return normalized;
            }
        }
        for (String available : availableTemplates) {
            if (normalize(available).equals(DEFAULT_TEMPLATE)) {
                return DEFAULT_TEMPLATE;
            }
        }
        return availableTemplates.stream()
                .map(AnimationTemplateSelection::normalize)
                .min(Comparator.naturalOrder())
                .orElse(DEFAULT_TEMPLATE);
    }
}
