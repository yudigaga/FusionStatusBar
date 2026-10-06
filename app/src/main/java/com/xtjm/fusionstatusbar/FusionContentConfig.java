package com.xtjm.fusionstatusbar;

import android.os.Bundle;

/** Three distinct content slots inside the single fused icon design space. */
final class FusionContentConfig {
    static final int BATTERY = 0;
    static final int NETWORK = 1;
    static final int SIGNAL = 2;
    static final int TOP = 0;
    static final int MIDDLE = 1;
    static final int BOTTOM = 2;
    static final String KEY_BATTERY_POSITION = "fusion_battery_position";
    static final String KEY_NETWORK_POSITION = "fusion_network_position";
    static final String KEY_SIGNAL_POSITION = "fusion_signal_position";
    static final String KEY_BATTERY_SCALE = "fusion_battery_content_scale";
    static final String KEY_NETWORK_SCALE = "fusion_network_content_scale";
    static final String KEY_SIGNAL_SCALE = "fusion_signal_content_scale";

    final int batteryPosition;
    final int networkPosition;
    final int signalPosition;
    final int batteryScale;
    final int networkScale;
    final int signalScale;

    private FusionContentConfig(int batteryPosition, int networkPosition, int signalPosition,
            int batteryScale, int networkScale, int signalScale) {
        this.batteryPosition = position(batteryPosition);
        int network = position(networkPosition);
        this.networkPosition = network == this.batteryPosition
                ? firstFree(this.batteryPosition) : network;
        int signal = position(signalPosition);
        this.signalPosition = signal != this.batteryPosition && signal != this.networkPosition
                ? signal : 3 - this.batteryPosition - this.networkPosition;
        this.batteryScale = FusionConfig.clampScale(batteryScale);
        this.networkScale = FusionConfig.clampScale(networkScale);
        this.signalScale = FusionConfig.clampScale(signalScale);
    }

    static FusionContentConfig defaults() {
        return new FusionContentConfig(TOP, MIDDLE, BOTTOM, 100, 100, 100);
    }

    static FusionContentConfig fromBundle(Bundle values) {
        FusionContentConfig defaults = defaults();
        return new FusionContentConfig(
                values.getInt(KEY_BATTERY_POSITION, defaults.batteryPosition),
                values.getInt(KEY_NETWORK_POSITION, defaults.networkPosition),
                values.getInt(KEY_SIGNAL_POSITION, defaults.signalPosition),
                values.getInt(KEY_BATTERY_SCALE, defaults.batteryScale),
                values.getInt(KEY_NETWORK_SCALE, defaults.networkScale),
                values.getInt(KEY_SIGNAL_SCALE, defaults.signalScale));
    }

    void writeTo(Bundle values) {
        values.putInt(KEY_BATTERY_POSITION, batteryPosition);
        values.putInt(KEY_NETWORK_POSITION, networkPosition);
        values.putInt(KEY_SIGNAL_POSITION, signalPosition);
        values.putInt(KEY_BATTERY_SCALE, batteryScale);
        values.putInt(KEY_NETWORK_SCALE, networkScale);
        values.putInt(KEY_SIGNAL_SCALE, signalScale);
    }

    int positionFor(int part) {
        return part == BATTERY ? batteryPosition : part == NETWORK ? networkPosition : signalPosition;
    }

    int scaleFor(int part) {
        return part == BATTERY ? batteryScale : part == NETWORK ? networkScale : signalScale;
    }

    FusionContentConfig withPosition(int part, int requestedPosition) {
        int next = position(requestedPosition);
        int[] positions = {batteryPosition, networkPosition, signalPosition};
        int previous = positions[part];
        for (int index = 0; index < positions.length; index++) {
            if (positions[index] == next) positions[index] = previous;
        }
        positions[part] = next;
        return new FusionContentConfig(positions[0], positions[1], positions[2],
                batteryScale, networkScale, signalScale);
    }

    FusionContentConfig withScale(int part, int value) {
        return new FusionContentConfig(batteryPosition, networkPosition, signalPosition,
                part == BATTERY ? value : batteryScale,
                part == NETWORK ? value : networkScale,
                part == SIGNAL ? value : signalScale);
    }

    boolean sameAs(FusionContentConfig other) {
        return other != null && batteryPosition == other.batteryPosition
                && networkPosition == other.networkPosition && signalPosition == other.signalPosition
                && batteryScale == other.batteryScale && networkScale == other.networkScale
                && signalScale == other.signalScale;
    }

    private static int position(int value) { return Math.max(TOP, Math.min(BOTTOM, value)); }
    private static int firstFree(int occupied) { return occupied == TOP ? MIDDLE : TOP; }
}
