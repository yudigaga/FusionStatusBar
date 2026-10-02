package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Maps native pixel slots into editable grid coordinates without substituting configured order. */
final class ControlCenterCaptureImport {
    final ControlCenterLayoutPlan.Mode mode;
    final int spacingDp;

    private ControlCenterCaptureImport(ControlCenterLayoutPlan.Mode mode, int spacingDp) {
        this.mode = mode;
        this.spacingDp = spacingDp;
    }

    static ControlCenterCaptureImport from(ControlCenterActualEditor.LayoutData layout,
            int fallbackColumns, float density) {
        if (layout == null || layout.tiles.isEmpty()) throw new IllegalArgumentException("Missing native bounds");
        if (layout.editableMode != null) return new ControlCenterCaptureImport(layout.editableMode, layout.spacingDp);
        List<ControlCenterActualEditor.Tile> tiles = new ArrayList<>(layout.tiles);
        tiles.sort(Comparator.comparingInt((ControlCenterActualEditor.Tile t) -> t.top).thenComparingInt(t -> t.left));
        int left = Integer.MAX_VALUE, right = 0, top = Integer.MAX_VALUE;
        ArrayList<Integer> units = new ArrayList<>();
        ArrayList<Integer> sliderUnits = new ArrayList<>();
        for (ControlCenterActualEditor.Tile tile : tiles) {
            left = Math.min(left, tile.left);
            right = Math.max(right, tile.left + tile.width);
            top = Math.min(top, tile.top);
            if ((!ControlCenterComponentSpec.isSpecial(tile.spec) && tile.width < tile.height * 1.35f)
                    || tile.spec.equals(ControlCenterComponentSpec.BRIGHTNESS)
                    || tile.spec.equals(ControlCenterComponentSpec.VOLUME)) units.add(tile.width);
            if ((tile.spec.equals(ControlCenterComponentSpec.BRIGHTNESS)
                    || tile.spec.equals(ControlCenterComponentSpec.VOLUME)) && tile.width < tile.height
                    && tile.nativeSpan == 1) sliderUnits.add(tile.width);
        }
        // Older captures measured padded tile wrappers, but sliders already expose their surface.
        // Do not let a majority of those wrappers collapse a real, visible gap to zero.
        if (!sliderUnits.isEmpty()) units = sliderUnits;
        units.sort(Integer::compare);
        float unit = units.isEmpty() ? (right - left) / (float) fallbackColumns : units.get(units.size() / 2);
        float pitchX = Float.MAX_VALUE;
        for (ControlCenterActualEditor.Tile a : tiles) for (ControlCenterActualEditor.Tile b : tiles) {
            float distance = b.left - a.left;
            if (Math.abs(a.top - b.top) < unit * 0.25f && distance >= unit * 0.8f) pitchX = Math.min(pitchX, distance);
        }
        if (pitchX == Float.MAX_VALUE) pitchX = unit;
        // Adjacent wide cards can be two grid columns apart; use the narrow controls as unit evidence.
        if (pitchX > unit * 1.6f) pitchX /= Math.max(1, Math.round(pitchX / unit));
        float gap = Math.max(0, pitchX - unit);
        int columns = Math.max(ControlCenterLayoutPlan.MIN_COLUMNS, Math.min(ControlCenterLayoutPlan.MAX_COLUMNS,
                Math.round((right - left + gap) / pitchX)));
        float pitchY = Float.MAX_VALUE;
        for (ControlCenterActualEditor.Tile a : tiles) for (ControlCenterActualEditor.Tile b : tiles) {
            int distance = b.top - a.top;
            if (distance >= unit * 0.6f) pitchY = Math.min(pitchY, distance);
        }
        if (pitchY == Float.MAX_VALUE) pitchY = layout.rowHeight(1) > 0 ? layout.rowHeight(1) + gap : unit + gap;
        ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
        for (ControlCenterActualEditor.Tile tile : tiles) {
            int x = Math.max(0, Math.round((tile.left - left) / pitchX));
            int y = Math.max(0, Math.round((tile.top - top) / pitchY));
            int w = Math.max(1, Math.min(columns, Math.round((tile.width + gap) / pitchX)));
            int h = Math.max(1, Math.min(ControlCenterLayoutPlan.MAX_HEIGHT, Math.round((tile.height + gap) / pitchY)));
            x = Math.min(columns - w, x);
            boolean component = ControlCenterComponentSpec.isSpecial(tile.spec);
            ControlCenterLayoutPlan.Item item = component
                    ? ControlCenterLayoutPlan.Item.component(tile.spec, x, y, w, h)
                    : ControlCenterLayoutPlan.Item.tile(tile.spec, x, y, w, h);
            ControlCenterLayoutPlan.Shape shape = !component && w == 1 && h == 1
                    ? ControlCenterLayoutPlan.Shape.CIRCLE : ControlCenterLayoutPlan.Shape.RECTANGLE;
            ControlCenterCapturedStyle style = layout.styles.get(ControlCenterConfig.canonicalSpec(tile.spec));
            items.add(item.withShape(style == null ? shape : style.shape,
                    style == null ? shape == ControlCenterLayoutPlan.Shape.CIRCLE ? 0 : 24 : style.radiusDp));
        }
        ControlCenterLayoutPlan.Mode mode = new ControlCenterLayoutPlan.Mode(columns, items);
        if (!mode.validate().isEmpty()) throw new IllegalArgumentException("Captured bounds do not form a valid grid");
        float sourceDensity = layout.density > 0 && Float.isFinite(layout.density) ? layout.density : density;
        return new ControlCenterCaptureImport(mode, Math.max(0, Math.min(24, Math.round(gap / Math.max(0.1f, sourceDensity)))));
    }
}
