package com.xtjm.fusionstatusbar;

import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** User-owned control-center tile composition and visual parameters. */
final class ControlCenterConfig {
    static final String KEY_ENABLED = "control_center_custom_enabled";
    static final String KEY_ORDER = "control_center_tile_order";
    static final String KEY_HIDDEN = "control_center_hidden_tiles";
    static final String KEY_COLUMNS = "control_center_columns";
    static final String KEY_TILE_SCALE = "control_center_tile_scale";
    static final String KEY_CORNER_RADIUS = "control_center_corner_radius";
    static final String KEY_SPACING = "control_center_spacing";
    static final String KEY_STYLE = "control_center_tile_style";
    static final String KEY_LAYOUT = "control_center_tile_layout";
    static final String KEY_LAYOUT_PLAN = "control_center_layout_plan";
    static final String KEY_COMPONENT_ORDER = "control_center_component_order";
    static final String KEY_COMPONENT_PLACEMENT = "control_center_component_placement";
    static final String KEY_BACKGROUND_BLUR = "control_center_background_blur";
    static final String KEY_CARD_BLUR = "control_center_card_blur";
    static final String KEY_TILE_BLUR = "control_center_tile_blur";

    static final String DEFAULT_ORDER =
            "cell,wifi,bt,airplane,mute,flashlight,screenshot,batterysaver,rotation,controls,"
                    + "custom(com.miui.screenrecorder/.service.QuickService),hotspot,satellite";

    final boolean enabled;
    final String order;
    final String hidden;
    final int columns;
    final int tileScale;
    final int cornerRadius;
    final int spacing;
    final int style;
    final String layout;
    final String layoutPlan;
    final String componentOrder;
    final String componentPlacement;
    final int backgroundBlur, cardBlur, tileBlur;

    private ControlCenterConfig(boolean enabled, String order, String hidden, int columns,
            int tileScale, int cornerRadius, int spacing, int style, String layout) {
        this(enabled, order, hidden, columns, tileScale, cornerRadius, spacing, style, layout,
                "", "", "");
    }

    private ControlCenterConfig(boolean enabled, String order, String hidden, int columns,
            int tileScale, int cornerRadius, int spacing, int style, String layout,
            String layoutPlan, String componentOrder, String componentPlacement) {
        this(enabled, order, hidden, columns, tileScale, cornerRadius, spacing, style, layout,
                layoutPlan, componentOrder, componentPlacement, 100, 100, 100);
    }

    private ControlCenterConfig(boolean enabled, String order, String hidden, int columns,
            int tileScale, int cornerRadius, int spacing, int style, String layout,
            String layoutPlan, String componentOrder, String componentPlacement,
            int backgroundBlur, int cardBlur, int tileBlur) {
        this.enabled = enabled;
        this.order = normalizeList(order, DEFAULT_ORDER);
        this.hidden = normalizeList(hidden, "");
        this.columns = Math.max(3, Math.min(6, columns));
        this.tileScale = Math.max(70, Math.min(130, tileScale));
        this.cornerRadius = Math.max(0, Math.min(32, cornerRadius));
        this.spacing = Math.max(0, Math.min(24, spacing));
        this.style = Math.max(0, Math.min(2, style));
        this.layout = ControlCenterTileLayout.normalize(layout);
        this.layoutPlan = ControlCenterLayoutPlan.normalize(layoutPlan);
        this.componentOrder = normalizeList(componentOrder, "");
        this.componentPlacement = normalizePlacement(componentPlacement);
        this.backgroundBlur = Math.max(0, Math.min(100, backgroundBlur));
        this.cardBlur = Math.max(0, Math.min(100, cardBlur));
        this.tileBlur = Math.max(0, Math.min(100, tileBlur));
    }

    static ControlCenterConfig defaults() {
        return new ControlCenterConfig(false, DEFAULT_ORDER, "", 4, 100, 18, 4, 0, "");
    }

    static ControlCenterConfig fromBundle(Bundle values) {
        if (values == null) return defaults();
        ControlCenterConfig fallback = defaults();
        return new ControlCenterConfig(values.getBoolean(KEY_ENABLED, fallback.enabled),
                values.getString(KEY_ORDER, fallback.order),
                values.getString(KEY_HIDDEN, fallback.hidden),
                values.getInt(KEY_COLUMNS, fallback.columns),
                values.getInt(KEY_TILE_SCALE, fallback.tileScale),
                values.getInt(KEY_CORNER_RADIUS, fallback.cornerRadius),
                values.getInt(KEY_SPACING, fallback.spacing),
                values.getInt(KEY_STYLE, fallback.style),
                values.getString(KEY_LAYOUT, fallback.layout),
                values.getString(KEY_LAYOUT_PLAN, fallback.layoutPlan),
                values.getString(KEY_COMPONENT_ORDER, fallback.componentOrder),
                values.getString(KEY_COMPONENT_PLACEMENT, fallback.componentPlacement),
                values.getInt(KEY_BACKGROUND_BLUR, 100), values.getInt(KEY_CARD_BLUR, 100), values.getInt(KEY_TILE_BLUR, 100));
    }

    static ControlCenterConfig read(SharedPreferences values) {
        ControlCenterConfig fallback = defaults();
        return new ControlCenterConfig(values.getBoolean(KEY_ENABLED, fallback.enabled),
                values.getString(KEY_ORDER, fallback.order),
                values.getString(KEY_HIDDEN, fallback.hidden),
                values.getInt(KEY_COLUMNS, fallback.columns),
                values.getInt(KEY_TILE_SCALE, fallback.tileScale),
                values.getInt(KEY_CORNER_RADIUS, fallback.cornerRadius),
                values.getInt(KEY_SPACING, fallback.spacing),
                values.getInt(KEY_STYLE, fallback.style),
                values.getString(KEY_LAYOUT, fallback.layout),
                values.getString(KEY_LAYOUT_PLAN, fallback.layoutPlan),
                values.getString(KEY_COMPONENT_ORDER, fallback.componentOrder),
                values.getString(KEY_COMPONENT_PLACEMENT, fallback.componentPlacement),
                values.getInt(KEY_BACKGROUND_BLUR, 100), values.getInt(KEY_CARD_BLUR, 100), values.getInt(KEY_TILE_BLUR, 100));
    }

    void writeTo(Bundle values) {
        values.putBoolean(KEY_ENABLED, enabled);
        values.putString(KEY_ORDER, order);
        values.putString(KEY_HIDDEN, hidden);
        values.putInt(KEY_COLUMNS, columns);
        values.putInt(KEY_TILE_SCALE, tileScale);
        values.putInt(KEY_CORNER_RADIUS, cornerRadius);
        values.putInt(KEY_SPACING, spacing);
        values.putInt(KEY_STYLE, style);
        values.putString(KEY_LAYOUT, layout);
        values.putString(KEY_LAYOUT_PLAN, layoutPlan);
        values.putString(KEY_COMPONENT_ORDER, componentOrder);
        values.putString(KEY_COMPONENT_PLACEMENT, componentPlacement);
        values.putInt(KEY_BACKGROUND_BLUR, backgroundBlur);
        values.putInt(KEY_CARD_BLUR, cardBlur);
        values.putInt(KEY_TILE_BLUR, tileBlur);
    }

    void writeTo(SharedPreferences.Editor editor) {
        editor.putBoolean(KEY_ENABLED, enabled);
        editor.putString(KEY_ORDER, order);
        editor.putString(KEY_HIDDEN, hidden);
        editor.putInt(KEY_COLUMNS, columns);
        editor.putInt(KEY_TILE_SCALE, tileScale);
        editor.putInt(KEY_CORNER_RADIUS, cornerRadius);
        editor.putInt(KEY_SPACING, spacing);
        editor.putInt(KEY_STYLE, style);
        editor.putString(KEY_LAYOUT, layout);
        editor.putString(KEY_LAYOUT_PLAN, layoutPlan);
        editor.putString(KEY_COMPONENT_ORDER, componentOrder);
        editor.putString(KEY_COMPONENT_PLACEMENT, componentPlacement);
        editor.putInt(KEY_BACKGROUND_BLUR, backgroundBlur);
        editor.putInt(KEY_CARD_BLUR, cardBlur);
        editor.putInt(KEY_TILE_BLUR, tileBlur);
    }

    ControlCenterConfig withEnabled(boolean value) {
        return copy(value, order, hidden, columns, tileScale, cornerRadius, spacing, style, layout);
    }

    ControlCenterConfig withOrder(String value) {
        return copy(enabled, value, hidden, columns, tileScale, cornerRadius, spacing, style, layout);
    }

    ControlCenterConfig withHidden(String value) {
        return copy(enabled, order, value, columns, tileScale, cornerRadius, spacing, style, layout);
    }

    ControlCenterConfig withColumns(int value) {
        ControlCenterConfig next = copy(enabled, order, hidden, value, tileScale, cornerRadius,
                spacing, style, layout);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(layoutPlan);
        if (plan == null) return next;
        return next.withLayoutPlan(plan.withMode(false,
                plan.regular.withColumns(next.columns)).encode());
    }

    ControlCenterConfig withTileScale(int value) {
        return copy(enabled, order, hidden, columns, value, cornerRadius, spacing, style, layout);
    }

    ControlCenterConfig withCornerRadius(int value) {
        return copy(enabled, order, hidden, columns, tileScale, value, spacing, style, layout);
    }

    ControlCenterConfig withSpacing(int value) {
        return copy(enabled, order, hidden, columns, tileScale, cornerRadius, value, style, layout);
    }

    ControlCenterConfig withStyle(int value) {
        return copy(enabled, order, hidden, columns, tileScale, cornerRadius, spacing, value, layout);
    }

    ControlCenterConfig withTileLayout(String value) {
        return copy(enabled, order, hidden, columns, tileScale, cornerRadius, spacing, style, value);
    }

    ControlCenterConfig withLayoutPlan(String value) {
        return new ControlCenterConfig(enabled, order, hidden, columns, tileScale, cornerRadius,
                spacing, style, layout, value, componentOrder, componentPlacement, backgroundBlur, cardBlur, tileBlur);
    }

    ControlCenterConfig withComponentOrder(String value) {
        return new ControlCenterConfig(enabled, order, hidden, columns, tileScale, cornerRadius,
                spacing, style, layout, layoutPlan, value, componentPlacement, backgroundBlur, cardBlur, tileBlur);
    }

    ControlCenterConfig withBlur(int background, int card, int tile) {
        return new ControlCenterConfig(enabled, order, hidden, columns, tileScale, cornerRadius,
                spacing, style, layout, layoutPlan, componentOrder, componentPlacement, background, card, tile);
    }

    ControlCenterConfig withComponentSide(String spec, String side) {
        LinkedHashMap<String, String> values = parsePlacement(componentPlacement);
        String normalizedSpec = normalizeSpec(spec);
        String normalizedSide = side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
        if (normalizedSpec.isEmpty() || (!"left".equals(normalizedSide)
                && !"right".equals(normalizedSide))) {
            values.remove(normalizedSpec);
        } else {
            values.put(normalizedSpec, normalizedSide);
        }
        return withComponentPlacement(serializePlacement(values));
    }

    String componentSide(String spec) {
        String normalizedSpec = normalizeSpec(spec);
        if (normalizedSpec.isEmpty()) return "";
        return parsePlacement(componentPlacement).getOrDefault(normalizedSpec, "");
    }

    ControlCenterConfig clearCustomization() {
        return new ControlCenterConfig(enabled, "", "", 4, 100, 0, 0, 0, "", "", "", "");
    }

    boolean hasTileOrderOverride() {
        String normalizedDefault = normalizeList(DEFAULT_ORDER, "");
        return !hidden.isEmpty() || (!order.isEmpty() && !normalizedDefault.equals(order));
    }

    boolean hasLegacyLayoutOverride() {
        return hasTileOrderOverride() || !layout.isEmpty() || !componentOrder.isEmpty()
                || !componentPlacement.isEmpty();
    }

    boolean hasShapeOverride() {
        return !layout.isEmpty();
    }

    boolean hasLayoutPlanOverride() {
        return !layoutPlan.isEmpty();
    }

    boolean hasGridOverride() {
        return columns != 4;
    }

    boolean hasVisualOverride() {
        return hasGridOverride() || tileScale != 100 || cornerRadius != 0 || spacing != 0
                || style != 0 || hasShapeOverride() || hasLayoutPlanOverride()
                || backgroundBlur != 100 || cardBlur != 100 || tileBlur != 100;
    }

    int tileWidth(String spec) {
        return ControlCenterTileLayout.width(layout, spec, columns);
    }

    int tileHeight(String spec) {
        return ControlCenterTileLayout.height(layout, spec);
    }

    ControlCenterConfig withTileShape(String spec, int width, int height) {
        ControlCenterConfig next = withTileLayout(ControlCenterTileLayout.set(layout, spec,
                width, height, columns, ControlCenterComponentSpec.isSpecial(spec)));
        return next.withPlanShape(spec, next.tileWidth(spec), next.tileHeight(spec), true);
    }

    ControlCenterConfig withoutTileShape(String spec) {
        ControlCenterConfig next = withTileLayout(ControlCenterTileLayout.remove(layout, spec));
        boolean component = ControlCenterComponentSpec.isSpecial(spec);
        return next.withPlanShape(spec,
                component ? ControlCenterComponentSpec.defaultSpan(spec) : 1,
                component ? ControlCenterComponentSpec.defaultRows(spec) : 1, false);
    }

    private ControlCenterConfig withPlanShape(String spec, int width, int height,
            boolean addMissing) {
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(layoutPlan);
        if (plan == null) return this;
        String normalized = normalizeSpec(spec);
        if (normalized.isEmpty() || normalized.length() > 128) return this;
        for (boolean compact : new boolean[] {false, true}) {
            ControlCenterLayoutPlan.Mode mode = plan.mode(compact);
            ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
            boolean found = false;
            for (ControlCenterLayoutPlan.Item item : mode.items) {
                if (item.containsSpec(normalized)) {
                    items.add(item.type == ControlCenterLayoutPlan.Type.GROUP ? item
                            : item.withSize(Math.min(mode.columns, width), height));
                    found = true;
                } else {
                    items.add(item);
                }
            }
            if (!found && addMissing) {
                int safeWidth = Math.min(mode.columns, width);
                items.add(ControlCenterComponentSpec.isSpecial(normalized)
                        ? ControlCenterLayoutPlan.Item.component(normalized,
                                ControlCenterLayoutPlan.UNPLACED,
                                ControlCenterLayoutPlan.UNPLACED, safeWidth, height)
                        : ControlCenterLayoutPlan.Item.tile(normalized,
                                ControlCenterLayoutPlan.UNPLACED,
                                ControlCenterLayoutPlan.UNPLACED, safeWidth, height));
            }
            plan = plan.withMode(compact, mode.withItems(items));
        }
        return withLayoutPlan(plan.encode());
    }

    boolean isHidden(String spec) {
        if (spec == null || spec.isEmpty()) return false;
        String normalized = canonicalSpec(spec.toLowerCase(Locale.ROOT));
        return new LinkedHashSet<>(Arrays.asList(hidden.split(","))).contains(normalized);
    }

    Set<String> orderedSpecs() {
        if (order.isEmpty()) return new LinkedHashSet<>();
        return new LinkedHashSet<>(Arrays.asList(order.split(",")));
    }

    private ControlCenterConfig copy(boolean nextEnabled, String nextOrder, String nextHidden,
            int nextColumns, int nextScale, int nextRadius, int nextSpacing, int nextStyle,
            String nextLayout) {
        return new ControlCenterConfig(nextEnabled, nextOrder, nextHidden, nextColumns,
                nextScale, nextRadius, nextSpacing, nextStyle, nextLayout, layoutPlan, componentOrder,
                componentPlacement, backgroundBlur, cardBlur, tileBlur);
    }

    private ControlCenterConfig withComponentPlacement(String value) {
        return new ControlCenterConfig(enabled, order, hidden, columns, tileScale, cornerRadius,
                spacing, style, layout, layoutPlan, componentOrder, value, backgroundBlur, cardBlur, tileBlur);
    }

    private static String normalizeList(String value, String fallback) {
        if (value == null) return fallback;
        if (value.trim().isEmpty()) return "";
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : value.toLowerCase(Locale.ROOT).split(",")) {
            String trimmed = item.trim();
            String canonical = canonicalSpec(trimmed);
            if (!canonical.isEmpty() && canonical.length() <= 128) result.add(canonical);
        }
        return String.join(",", result);
    }

    private static String normalizePlacement(String value) {
        return serializePlacement(parsePlacement(value));
    }

    private static LinkedHashMap<String, String> parsePlacement(String value) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (value == null || value.trim().isEmpty()) return result;
        for (String entry : value.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2) continue;
            String spec = normalizeSpec(pair[0]);
            String side = pair[1].trim().toLowerCase(Locale.ROOT);
            if (ControlCenterComponentSpec.isSpecial(spec)
                    && ("left".equals(side) || "right".equals(side))) {
                result.put(spec, side);
            }
        }
        return result;
    }

    private static String serializePlacement(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (result.length() > 0) result.append(',');
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    private static String normalizeSpec(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String canonicalSpec(String value) {
        value = normalizeSpec(value);
        if (value.startsWith("custom(") && value.endsWith(")")) {
            int slash = value.indexOf('/', 7);
            if (slash > 7) {
                String packageName = value.substring(7, slash);
                String className = value.substring(slash + 1, value.length() - 1);
                if (className.startsWith(packageName + ".")) {
                    return "custom(" + packageName + "/" + className.substring(packageName.length()) + ")";
                }
            }
        }
        switch (value) {
            case "silent":
                return "mute";
            case "dnd":
                return "quietmode";
            case "battery":
                return "batterysaver";
            case "location":
                return "gps";
            case "screenrecord":
                return "custom(com.miui.screenrecorder/.service.quickservice)";
            default:
                return value;
        }
    }
}
