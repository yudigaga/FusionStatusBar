package com.xtjm.fusionstatusbar;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Stores per-tile grid shapes as spec=columnsxrows entries. */
final class ControlCenterTileLayout {
    static final int DEFAULT_WIDTH = 1;
    static final int DEFAULT_HEIGHT = 1;
    private static final int MAX_HEIGHT = 4;

    private ControlCenterTileLayout() {
    }

    static int width(String layout, String spec, int columns) {
        int[] shape = find(layout, spec);
        return clamp(shape[0], 1, Math.max(1, columns));
    }

    static int height(String layout, String spec) {
        return clamp(find(layout, spec)[1], 1, MAX_HEIGHT);
    }

    static boolean hasShape(String layout, String spec) {
        return parse(layout).containsKey(normalizeSpec(spec));
    }

    static String set(String layout, String spec, int width, int height, int columns) {
        return set(layout, spec, width, height, columns, false);
    }

    static String set(String layout, String spec, int width, int height, int columns,
            boolean keepOneByOne) {
        if (spec == null || spec.trim().isEmpty()) return normalize(layout);
        String normalizedSpec = normalizeSpec(spec);
        if (normalizedSpec.isEmpty()) return normalize(layout);
        LinkedHashMap<String, int[]> values = parse(layout);
        int safeWidth = clamp(width, 1, Math.max(1, columns));
        int safeHeight = clamp(height, 1, MAX_HEIGHT);
        if (safeWidth == DEFAULT_WIDTH && safeHeight == DEFAULT_HEIGHT && !keepOneByOne) {
            values.remove(normalizedSpec);
        } else {
            values.put(normalizedSpec, new int[] {safeWidth, safeHeight});
        }
        return serialize(values);
    }

    static String remove(String layout, String spec) {
        LinkedHashMap<String, int[]> values = parse(layout);
        values.remove(normalizeSpec(spec));
        return serialize(values);
    }

    static String normalize(String layout) {
        return serialize(parse(layout));
    }

    private static int[] find(String layout, String spec) {
        int[] shape = parse(layout).get(normalizeSpec(spec));
        return shape == null ? new int[] {DEFAULT_WIDTH, DEFAULT_HEIGHT} : shape;
    }

    private static LinkedHashMap<String, int[]> parse(String layout) {
        LinkedHashMap<String, int[]> values = new LinkedHashMap<>();
        if (layout == null || layout.trim().isEmpty()) return values;
        for (String entry : layout.split("\\|")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2) continue;
            String spec = normalizeSpec(pair[0]);
            String[] size = pair[1].toLowerCase(Locale.ROOT).split("x", 2);
            if (spec.isEmpty() || size.length != 2) continue;
            try {
                int width = Integer.parseInt(size[0].trim());
                int height = Integer.parseInt(size[1].trim());
                if (width >= 1 && height >= 1) {
                    values.put(spec, new int[] {clamp(width, 1, 6),
                            clamp(height, 1, MAX_HEIGHT)});
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed entries from older or manually edited settings.
            }
        }
        return values;
    }

    private static String serialize(Map<String, int[]> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, int[]> entry : values.entrySet()) {
            if (result.length() > 0) result.append('|');
            int[] shape = entry.getValue();
            result.append(entry.getKey()).append('=').append(shape[0]).append('x').append(shape[1]);
        }
        return result.toString();
    }

    private static String normalizeSpec(String spec) {
        if (spec == null) return "";
        return spec.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
