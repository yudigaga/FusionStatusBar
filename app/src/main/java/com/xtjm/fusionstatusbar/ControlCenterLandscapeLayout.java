package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** Projects a saved canvas into the native landscape panels without changing the plan. */
final class ControlCenterLandscapeLayout {
    private static final Set<String> FALLBACK_RIGHT_TILES = Set.of("wifi", "cell");

    private ControlCenterLandscapeLayout() {
    }

    static ControlCenterLayoutPlan.Mode forPanel(ControlCenterConfig settings,
            ControlCenterLayoutPlan.Mode source, boolean rightPanel,
            Set<String> nativeRightTiles, Map<String, Boolean> nativeComponentSides) {
        ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
        for (ControlCenterLayoutPlan.Item item : source.items) {
            if (item != null && !item.hidden
                    && rightSide(settings, item, nativeRightTiles, nativeComponentSides) == rightPanel) {
                items.add(item);
            }
        }
        return compactRows(source.withItems(items));
    }

    static boolean rightSide(ControlCenterConfig settings, ControlCenterLayoutPlan.Item item,
            Set<String> nativeRightTiles, Map<String, Boolean> nativeComponentSides) {
        if (item.type == ControlCenterLayoutPlan.Type.COMPONENT) {
            String side = settings == null ? "" : settings.componentSide(item.firstSpec);
            if (!side.isEmpty()) return "right".equals(side);
            Boolean nativeSide = nativeComponentSides == null ? null
                    : nativeComponentSides.get(item.firstSpec);
            if (nativeSide != null) return nativeSide;
            return ControlCenterComponentSpec.MEDIA.equals(item.firstSpec)
                    || ControlCenterComponentSpec.BRIGHTNESS.equals(item.firstSpec)
                    || ControlCenterComponentSpec.VOLUME.equals(item.firstSpec)
                    || ControlCenterComponentSpec.QS_CARD.equals(item.firstSpec);
        }
        Set<String> rightTiles = nativeRightTiles == null || nativeRightTiles.isEmpty()
                ? FALLBACK_RIGHT_TILES : nativeRightTiles;
        return !item.specs().isEmpty() && rightTiles.containsAll(item.specs());
    }

    static ControlCenterLayoutPlan.Mode compactRows(ControlCenterLayoutPlan.Mode source) {
        boolean[] occupied = new boolean[ControlCenterLayoutPlan.MAX_ROWS];
        for (ControlCenterLayoutPlan.Item item : source.items) {
            if (!positioned(item)) continue;
            for (int row = item.y; row < item.y + item.height; row++) occupied[row] = true;
        }
        int[] emptyBefore = new int[occupied.length];
        int emptyRows = 0;
        for (int row = 0; row < occupied.length; row++) {
            emptyBefore[row] = emptyRows;
            if (!occupied[row]) emptyRows++;
        }
        ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
        for (ControlCenterLayoutPlan.Item item : source.items) {
            if (item == null || item.hidden) continue;
            items.add(positioned(item)
                    ? item.withPosition(item.x, item.y - emptyBefore[item.y]) : item);
        }
        return source.withItems(items);
    }

    private static boolean positioned(ControlCenterLayoutPlan.Item item) {
        return item != null && !item.hidden && item.x >= 0 && item.y >= 0
                && item.height > 0 && item.height <= ControlCenterLayoutPlan.MAX_ROWS
                && item.y <= ControlCenterLayoutPlan.MAX_ROWS - item.height;
    }
}
