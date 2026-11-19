package com.xtjm.fusionstatusbar;

/** OEM private blur has no verified availability signal; public window blur is a separate domain. */
final class ControlCenterMaterialPolicy {
    private ControlCenterMaterialPolicy() { }

    static boolean nativeFallback(ControlCenterConfig rawConfig, int rawAmount, boolean editing) {
        return rawConfig.enabled && !editing && rawConfig.surface == ControlCenterSurface.HIGHLIGHT_GLASS
                && rawAmount > 0;
    }

    static int amount(ControlCenterConfig rawConfig, int rawAmount, boolean editing) {
        return !rawConfig.enabled || editing ? 100 : rawAmount;
    }

    static ControlCenterSurface surface(ControlCenterConfig rawConfig, int rawAmount, boolean editing) {
        return !rawConfig.enabled || editing ? ControlCenterSurface.DEFAULT : rawConfig.surface;
    }
}
