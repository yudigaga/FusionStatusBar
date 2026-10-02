package com.xtjm.fusionstatusbar;

import java.util.Locale;

/** Identifies GPU thermal zones and normalizes common sysfs temperature units. */
final class GpuThermalSensor {
    private GpuThermalSensor() {
    }

    static boolean isGpuType(String type) {
        return type != null && type.toLowerCase(Locale.ROOT).contains("gpu");
    }

    static Double celsius(String text) {
        if (text == null) return null;
        try {
            double raw = Double.parseDouble(text.trim());
            if (!Double.isFinite(raw)) return null;
            double celsius = Math.abs(raw) >= 10_000d ? raw / 1000d
                    : Math.abs(raw) >= 200d ? raw / 10d : raw;
            return celsius >= -20d && celsius <= 150d ? celsius : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static Double hottest(float[] values) {
        if (values == null) return null;
        Double hottest = null;
        for (float value : values) {
            if (Float.isFinite(value) && value >= -20f && value <= 150f
                    && (hottest == null || value > hottest)) {
                hottest = (double) value;
            }
        }
        return hottest;
    }
}
