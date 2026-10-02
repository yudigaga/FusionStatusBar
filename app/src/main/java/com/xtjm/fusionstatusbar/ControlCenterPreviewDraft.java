package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Local edit order rooted in one captured control-center layout. */
final class ControlCenterPreviewDraft {
    private ControlCenterPreviewDraft() {
    }

    static List<String> visibleSpecs(List<String> captured, List<String> added,
            ControlCenterConfig settings) {
        LinkedHashSet<String> source = new LinkedHashSet<>();
        if (captured != null) {
            for (String spec : captured) addSpec(source, spec);
        }
        if (added != null) {
            for (String spec : added) addSpec(source, spec);
        }
        return ControlCenterTileOrder.forDisplay(new ArrayList<>(source), settings,
                spec -> spec);
    }

    private static void addSpec(LinkedHashSet<String> source, String spec) {
        if (spec != null && !spec.trim().isEmpty()) {
            source.add(ControlCenterConfig.canonicalSpec(spec));
        }
    }
}
