package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Chooses the one pair supported by the OEM compact card and removes duplicate tiles. */
final class ControlCenterCompactPairPolicy {
    private ControlCenterCompactPairPolicy() {
    }

    static ControlCenterLayoutPlan.Item firstAvailablePair(ControlCenterConfig config,
            Collection<String> availableSpecs) {
        if (config == null || !config.enabled || availableSpecs == null
                || !config.hasLayoutPlanOverride()) return null;
        ControlCenterLayoutPlan.Item first = firstConfiguredPair(config);
        if (first == null || config.isHidden(first.firstSpec)
                || config.isHidden(first.secondSpec)) return null;
        Set<String> available = new HashSet<>();
        for (String spec : availableSpecs) available.add(normalize(spec));
        return available.contains(first.firstSpec) && available.contains(first.secondSpec)
                ? first : null;
    }

    static ControlCenterLayoutPlan.Item firstConfiguredPair(ControlCenterConfig config) {
        if (config == null || !config.enabled || !config.hasLayoutPlanOverride()) return null;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(config.layoutPlan);
        if (plan == null) return null;
        for (ControlCenterLayoutPlan.Item item : plan.runtimeMode(true).items) {
            if (item.type == ControlCenterLayoutPlan.Type.PAIR) return item;
        }
        return null;
    }

    static boolean needsRebuild(ControlCenterConfig config,
            ControlCenterLayoutPlan.Item createdPair) {
        return createdPair == null ? firstConfiguredPair(config) != null
                : activePair(config, createdPair) == null;
    }

    static boolean hasMembers(ControlCenterLayoutPlan.Item pair, String first, String second) {
        return pair != null && pair.firstSpec.equals(normalize(first))
                && pair.secondSpec.equals(normalize(second));
    }

    static ControlCenterLayoutPlan.Item activePair(ControlCenterConfig config,
            ControlCenterLayoutPlan.Item createdPair) {
        if (config == null || !config.enabled || createdPair == null
                || !config.hasLayoutPlanOverride()) return null;
        ControlCenterLayoutPlan.Item first = firstConfiguredPair(config);
        return hasMembers(first, createdPair.firstSpec, createdPair.secondSpec)
                ? first : null;
    }

    static <T> List<T> withoutPair(List<T> source, ControlCenterLayoutPlan.Item pair,
            Function<T, String> specOf) {
        ArrayList<T> visible = new ArrayList<>();
        if (source == null) return visible;
        for (T item : source) {
            String spec = normalize(specOf.apply(item));
            if (pair == null || (!pair.firstSpec.equals(spec) && !pair.secondSpec.equals(spec))) {
                visible.add(item);
            }
        }
        return visible;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
