package com.xtjm.fusionstatusbar;

import java.util.Locale;

/** Decides which status-bar slots are replaced by the fused icon. */
public final class SlotFilter {
    private SlotFilter() {
    }

    public static boolean shouldHide(String slot) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }
        String name = slot.toLowerCase(Locale.ROOT);
        if (containsAny(name, "vowifi", "hotspot", "airplane", "calling")) {
            return false;
        }
        return containsAny(name, "wifi", "mobile", "cell", "ethernet");
    }

    public static boolean shouldForceShow(String slot) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }
        String name = slot.toLowerCase(Locale.ROOT);
        return containsAny(name, "bluetooth_handsfree_battery",
                "bluetooth_battery", "headset", "wireless_headset",
                "location", "privacy", "vpn", "hotspot", "mute", "zen",
                "alarm", "roaming", "nfc", "phone", "tablet", "pc",
                "tv", "car", "speaker", "glasses", "camera");
    }

    private static boolean containsAny(String name, String... parts) {
        for (String part : parts) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
