package com.xtjm.fusionstatusbar;

import java.util.LinkedHashSet;
import java.util.Set;

/** Composes the module's category switches with SystemUI's native blocked-slot list. */
final class SystemIconVisibilityPolicy {
    private SystemIconVisibilityPolicy() { }

    static String apply(String nativeSlots, int visibilityMask) {
        Set<String> blocked = new LinkedHashSet<>();
        if (nativeSlots != null && !nativeSlots.isEmpty()) {
            for (String slot : nativeSlots.split(",")) {
                if (!slot.isEmpty()) blocked.add(slot);
            }
        }
        int mask = visibilityMask & FusionConfig.SYSTEM_ICON_ALL;
        category(blocked, mask, FusionConfig.ICON_NETWORK_SPEED, "network_speed");
        category(blocked, mask, FusionConfig.ICON_BLUETOOTH, "bluetooth");
        category(blocked, mask, FusionConfig.ICON_LOCATION, "location");
        category(blocked, mask, FusionConfig.ICON_PRIVACY, "privacy");
        category(blocked, mask, FusionConfig.ICON_VPN, "vpn");
        category(blocked, mask, FusionConfig.ICON_HOTSPOT, "hotspot");
        category(blocked, mask, FusionConfig.ICON_VOLUME, "volume");
        category(blocked, mask, FusionConfig.ICON_ZEN, "zen");
        category(blocked, mask, FusionConfig.ICON_ALARM, "alarm_clock");
        category(blocked, mask, FusionConfig.ICON_ROAMING, "roaming");
        category(blocked, mask, FusionConfig.ICON_HEADSET, "headset", "wireless_headset");
        category(blocked, mask, FusionConfig.ICON_PHONE, "phone");
        category(blocked, mask, FusionConfig.ICON_TABLET, "pad");
        category(blocked, mask, FusionConfig.ICON_PC, "pc");
        category(blocked, mask, FusionConfig.ICON_TV, "tv");
        category(blocked, mask, FusionConfig.ICON_CAR, "car");
        category(blocked, mask, FusionConfig.ICON_SPEAKER,
                "sound_box", "sound_box_screen", "stereo", "sound_box_group");
        category(blocked, mask, FusionConfig.ICON_GLASSES, "glasses");
        category(blocked, mask, FusionConfig.ICON_CAMERA, "camera");
        return String.join(",", blocked);
    }

    static boolean isHidden(String slot, int visibilityMask) {
        if (slot == null || slot.isEmpty()) return false;
        for (String hidden : apply("", visibilityMask).split(",")) {
            if (hidden.equals(slot)) return true;
        }
        if ("alarm".equals(slot) && (visibilityMask & FusionConfig.ICON_ALARM) == 0) return true;
        if ("tablet".equals(slot) && (visibilityMask & FusionConfig.ICON_TABLET) == 0) return true;
        if ("speaker".equals(slot) && (visibilityMask & FusionConfig.ICON_SPEAKER) == 0) return true;
        return false;
    }

    private static void category(Set<String> blocked, int mask, int icon, String... slots) {
        for (String slot : slots) {
            if ((mask & icon) != 0) blocked.remove(slot);
            else blocked.add(slot);
        }
    }
}
