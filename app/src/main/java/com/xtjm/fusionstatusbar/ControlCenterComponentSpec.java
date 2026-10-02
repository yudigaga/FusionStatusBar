package com.xtjm.fusionstatusbar;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Stable identifiers for non-tile control-center components. */
final class ControlCenterComponentSpec {
    static final String MEDIA = "control:media";
    static final String BRIGHTNESS = "control:brightness";
    static final String VOLUME = "control:volume";
    static final String DEVICE_CENTER = "control:device-center";
    static final String DEVICE_CONTROLS = "control:device-controls";
    static final String QS_CARD = "control:qs-card";
    static final String SLIDER = "control:slider";
    static final String COMPONENT_PREFIX = "control:component:";

    private ControlCenterComponentSpec() {
    }

    static String fromViewName(String className) {
        String name = className == null ? "" : className.toLowerCase(Locale.ROOT);
        if (name.contains("mediaplayerpanel")) return MEDIA;
        if (name.contains("togglesliderview")) return SLIDER;
        if (name.contains("devicecenterentryframelayout")
                || name.contains("devicecenterentryrecyclerview")) {
            return DEVICE_CENTER;
        }
        if (name.contains("devicecontrolsentry")) return DEVICE_CONTROLS;
        if (name.contains("compactqscard")) return QS_CARD;
        return "";
    }

    static String fromControllerName(String className) {
        String name = className == null ? "" : className.toLowerCase(Locale.ROOT);
        if (name.contains("mediaplayercontroller")) return MEDIA;
        if (name.contains("brightnessslidercontroller")) return BRIGHTNESS;
        if (name.contains("volumeslidercontroller")) return VOLUME;
        if (name.contains("devicecenterentrycontroller")) return DEVICE_CENTER;
        if (name.contains("devicecontrolsentrycontroller")) return DEVICE_CONTROLS;
        if (name.contains("compactqscardcontroller")) return QS_CARD;
        return "";
    }

    static String fromType(int type) {
        switch (type) {
            case 63342:
                return MEDIA;
            case 274442:
                return BRIGHTNESS;
            case 865269:
                return VOLUME;
            case 338423:
                return DEVICE_CENTER;
            case 2668765:
                return DEVICE_CONTROLS;
            case 22273:
                return QS_CARD;
            default:
                return "";
        }
    }

    static String label(String spec) {
        if (MEDIA.equalsIgnoreCase(spec)) return "音乐";
        if (BRIGHTNESS.equalsIgnoreCase(spec)) return "亮度";
        if (VOLUME.equalsIgnoreCase(spec)) return "音量";
        if (DEVICE_CENTER.equalsIgnoreCase(spec)) return "融合设备中心";
        if (DEVICE_CONTROLS.equalsIgnoreCase(spec)) return "设备控制";
        if (QS_CARD.equalsIgnoreCase(spec)) return "控制中心卡片";
        if (spec != null && spec.toLowerCase(Locale.ROOT).startsWith(COMPONENT_PREFIX)) {
            return "控制中心组件";
        }
        return "";
    }

    static boolean isSpecial(String spec) {
        if (spec == null) return false;
        String value = spec.toLowerCase(Locale.ROOT);
        return value.startsWith("control:");
    }

    static int defaultSpan(String spec) {
        if (MEDIA.equalsIgnoreCase(spec)) return 2;
        if (DEVICE_CENTER.equalsIgnoreCase(spec)) return 4;
        if (DEVICE_CONTROLS.equalsIgnoreCase(spec)) return 4;
        return 1;
    }

    static int defaultRows(String spec) {
        if (MEDIA.equalsIgnoreCase(spec)
                || BRIGHTNESS.equalsIgnoreCase(spec)
                || VOLUME.equalsIgnoreCase(spec)) return 2;
        return 1;
    }

    static <T> void reorder(List<T> items, String order, Function<T, String> specOf) {
        if (items == null || order == null || order.isEmpty()) return;
        String[] desired = order.toLowerCase(Locale.ROOT).split(",");
        ArrayList<Integer> slots = new ArrayList<>();
        ArrayList<T> components = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String spec = specOf.apply(items.get(i));
            if (!isSpecial(spec)) continue;
            slots.add(i);
            components.add(items.get(i));
        }
        components.sort((left, right) -> Integer.compare(
                rank(desired, specOf.apply(left)), rank(desired, specOf.apply(right))));
        for (int i = 0; i < slots.size(); i++) {
            items.set(slots.get(i), components.get(i));
        }
    }

    private static int rank(String[] desired, String spec) {
        if (spec == null) return desired.length;
        for (int i = 0; i < desired.length; i++) {
            if (desired[i].equalsIgnoreCase(spec)) return i;
        }
        return desired.length;
    }
}
