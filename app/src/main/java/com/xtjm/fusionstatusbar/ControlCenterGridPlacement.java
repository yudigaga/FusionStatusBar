package com.xtjm.fusionstatusbar;

import java.util.ArrayList;

/** Shared drop/property placement: preserve locked slots, reflow unlocked collisions. */
final class ControlCenterGridPlacement {
    private ControlCenterGridPlacement() { }

    static ControlCenterLayoutPlan.Mode place(ControlCenterLayoutPlan.Mode source,
            ControlCenterLayoutPlan.Item candidate) {
        if (candidate.x < 0 || candidate.y < 0 || candidate.width < 1 || candidate.height < 1
                || candidate.width + candidate.x > source.columns
                || candidate.height > ControlCenterLayoutPlan.MAX_HEIGHT
                || candidate.height + candidate.y > ControlCenterLayoutPlan.MAX_ROWS) return null;
        ArrayList<ControlCenterLayoutPlan.Item> ordered = new ArrayList<>();
        boolean found = false;
        for (ControlCenterLayoutPlan.Item item : source.items) {
            if (item.id.equals(candidate.id)) {
                found = true;
                if (item.locked) return null;
            } else if (item.locked) {
                if (overlaps(item, candidate)) return null;
                ordered.add(item);
            }
        }
        if (!found) return null;
        ordered.add(candidate);
        for (ControlCenterLayoutPlan.Item item : source.items) {
            if (!item.locked && !item.id.equals(candidate.id)) ordered.add(item);
        }
        ControlCenterLayoutPlan.Mode proposed = new ControlCenterLayoutPlan.Mode(source.columns, ordered);
        ControlCenterLayoutPlan.Mode result = ControlCenterLayoutPlan.of(proposed, proposed).resolve(false, null).mode;
        if (result.items.size() != source.items.size() || !result.validate().isEmpty()) return null;
        for (ControlCenterLayoutPlan.Item item : result.items) {
            if (item.id.equals(candidate.id) && (item.x != candidate.x || item.y != candidate.y
                    || item.width != candidate.width || item.height != candidate.height)) return null;
            if (item.locked) {
                for (ControlCenterLayoutPlan.Item original : source.items) {
                    if (original.id.equals(item.id) && (original.x != item.x || original.y != item.y)) return null;
                }
            }
        }
        return result;
    }

    private static boolean overlaps(ControlCenterLayoutPlan.Item a, ControlCenterLayoutPlan.Item b) {
        return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
    }
}
