package com.xtjm.fusionstatusbar;

import java.util.Locale;
import java.util.Properties;

/** Values and unit conversion shared by the SystemUI readouts and their tests. */
final class TelemetryReadings {
    static final TelemetryReadings EMPTY = new TelemetryReadings(
            null, null, null, null, 0, 0, false);
    static final TelemetryReadings SAMPLE = new TelemetryReadings(
            35.6d, 42.3d, 3.6d, -900d, 2048, 1024, true);

    final Double batteryCelsius;
    final Double gpuCelsius;
    final Double powerWatts;
    final Double currentMilliamps;
    final long txBytesPerSecond;
    final long rxBytesPerSecond;
    final boolean speedValid;

    TelemetryReadings(Double batteryCelsius, Double gpuCelsius, Double powerWatts,
            Double currentMilliamps, long txBytesPerSecond, long rxBytesPerSecond,
            boolean speedValid) {
        this.batteryCelsius = batteryCelsius;
        this.gpuCelsius = gpuCelsius;
        this.powerWatts = powerWatts;
        this.currentMilliamps = currentMilliamps;
        this.txBytesPerSecond = Math.max(0, txBytesPerSecond);
        this.rxBytesPerSecond = Math.max(0, rxBytesPerSecond);
        this.speedValid = speedValid;
    }

    boolean sameValues(TelemetryReadings other) {
        return other != null
                && equal(batteryCelsius, other.batteryCelsius)
                && equal(gpuCelsius, other.gpuCelsius)
                && equal(powerWatts, other.powerWatts)
                && equal(currentMilliamps, other.currentMilliamps)
                && txBytesPerSecond == other.txBytesPerSecond
                && rxBytesPerSecond == other.rxBytesPerSecond
                && speedValid == other.speedValid;
    }

    private static boolean equal(Double left, Double right) {
        return left == null ? right == null : left.equals(right);
    }

    static TelemetryReadings fromBattery(Properties battery, boolean currentIsMilliamps,
            boolean currentPositive, Double gpuCelsius, long txBytesPerSecond, long rxBytesPerSecond,
            boolean speedValid) {
        Double temperature = parse(battery, "POWER_SUPPLY_TEMP");
        if (temperature != null) {
            temperature /= 10d;
            if (temperature < -30d || temperature > 100d) temperature = null;
        }
        Double current = parse(battery, "POWER_SUPPLY_CURRENT_NOW");
        if (current != null) {
            Double apiCurrent = parse(battery, "FUSION_API_CURRENT_UA");
            if (apiCurrent != null && Math.abs(current) >= 1d
                    && Math.abs(apiCurrent) >= 1d) {
                double ratio = Math.abs(apiCurrent / current);
                if (ratio >= 100d && ratio <= 10_000d) currentIsMilliamps = true;
                else if (ratio >= 0.1d && ratio <= 10d
                        && Math.abs(current) >= 10_000d) currentIsMilliamps = false;
            } else if (currentIsMilliamps && Math.abs(current) >= 10_000d) {
                currentIsMilliamps = false;
            }
            if (!currentIsMilliamps) current /= 1000d;
            current = -current;
            if (currentPositive) current = Math.abs(current);
            if (Math.abs(current) > 100_000d) current = null;
        }
        Double voltage = parse(battery, "POWER_SUPPLY_VOLTAGE_NOW");
        if (voltage != null) {
            voltage = voltage > 100_000d ? voltage / 1_000_000d
                    : voltage > 100d ? voltage / 1000d : voltage;
            if (voltage < 2d || voltage > 6d) voltage = null;
        }
        Double power = voltage == null || current == null
                ? null : Math.abs(voltage * current) / 1000d;
        return new TelemetryReadings(temperature, gpuCelsius, power, current,
                txBytesPerSecond, rxBytesPerSecond, speedValid);
    }

    static long speed(long currentBytes, long previousBytes, long elapsedMillis) {
        if (currentBytes < 0 || previousBytes < 0 || currentBytes < previousBytes
                || elapsedMillis <= 0) {
            return 0;
        }
        return Math.max(0, Math.round((currentBytes - previousBytes) * 1000d / elapsedMillis));
    }

    String textFor(int metric, TelemetryConfig config, Locale locale) {
        return switch (metric) {
            case TelemetryConfig.TEMPERATURES ->
                    temperatureText(batteryCelsius, locale)
                            + "\n" + temperatureText(gpuCelsius, locale);
            case TelemetryConfig.POWER_CURRENT ->
                    (powerWatts == null ? "--W"
                            : String.format(locale, "%.2fW", powerWatts))
                            + "\n" + formatCurrent(locale);
            case TelemetryConfig.NET_SPEED -> formatSpeed(config.dualSpeed, locale);
            default -> "";
        };
    }

    private static String temperatureText(Double value, Locale locale) {
        if (value == null) return "--";
        double rounded = Math.round(value * 10d) / 10d;
        return String.format(locale, rounded == Math.rint(rounded) ? "%.0f℃" : "%.1f℃",
                rounded);
    }

    private String formatCurrent(Locale locale) {
        if (currentMilliamps == null) return "--mA";
        if (Math.abs(currentMilliamps) >= 1000d) {
            return String.format(locale, "%.2fA", currentMilliamps / 1000d);
        }
        return String.format(locale, "%.0fmA", currentMilliamps);
    }

    private String formatSpeed(boolean dual, Locale locale) {
        String upload = speedValid ? humanSpeed(txBytesPerSecond, locale) : "--";
        String download = speedValid ? humanSpeed(rxBytesPerSecond, locale) : "--";
        return "↑" + upload + (dual ? "\n" : " ") + "↓" + download;
    }

    private static String humanSpeed(long bytesPerSecond, Locale locale) {
        if (bytesPerSecond < 1024) return bytesPerSecond + "B/s";
        double value = bytesPerSecond / 1024d;
        String unit = "KB/s";
        if (value >= 1024d) {
            value /= 1024d;
            unit = "MB/s";
        }
        return String.format(locale, value < 100d ? "%.1f%s" : "%.0f%s", value, unit);
    }

    private static Double parse(Properties values, String key) {
        try {
            String value = values.getProperty(key);
            return value == null ? null : Double.parseDouble(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
