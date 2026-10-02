package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Applies the user order without mutating the SystemUI-owned tile list. */
final class ControlCenterTileOrder {
    private ControlCenterTileOrder() {
    }

    static <T> List<T> forDisplay(List<T> source, ControlCenterConfig settings,
            Function<T, String> specReader) {
        return reorder(source, settings, specReader);
    }

    private static <T> List<T> reorder(List<T> source, ControlCenterConfig settings,
            Function<T, String> specReader) {
        ArrayList<T> result = new ArrayList<>();
        if (source == null) return result;
        for (T item : source) {
            String spec = specReader.apply(item);
            if (!settings.isHidden(spec)) {
                result.add(item);
            }
        }
        ArrayList<String> order = new ArrayList<>(settings.orderedSpecs());
        result.sort(Comparator.comparingInt(item -> {
            String spec = specReader.apply(item);
            int index = order.indexOf(spec == null ? "" : spec.toLowerCase(java.util.Locale.ROOT));
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        return result;
    }
}
