package gg.fotia.crates.animation;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    public static String resolve(String requested, Map<String, AnimationType> templateTypes,
                                 AnimationType animationType) {
        if (templateTypes == null || templateTypes.isEmpty() || animationType == null) {
            return DEFAULT_TEMPLATE;
        }
        AnimationType family = animationType.templateFamily();
        Set<String> compatible = new LinkedHashSet<>();
        for (Map.Entry<String, AnimationType> entry : templateTypes.entrySet()) {
            if (entry.getValue() != null && entry.getValue().templateFamily() == family) {
                compatible.add(entry.getKey());
            }
        }
        return resolve(requested, compatible);
    }
}
