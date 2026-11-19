package com.xtjm.fusionstatusbar;

import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeApplicationPolicyTest {
    @Test public void failedHookCannotReportStatusBarAppliedEvenWithAttachedViews() throws Throwable {
        RuntimeHookRegistry<Object> registry = new RuntimeHookRegistry<>();
        Method method = Battery.class.getDeclaredMethod("onAttachedToWindow");
        registry.install(method, "battery-attach", () -> null);
        assertEquals("battery_adapter_unavailable", RuntimeApplicationPolicy.statusBar(
                registry.hasHook(Battery.class, "onAttachedToWindow"), true, true, true).reason());
        registry.install(method, "battery-attach", Object::new);
        assertEquals(FusionActivationStatus.APPLIED, RuntimeApplicationPolicy.statusBar(
                registry.hasHook(Battery.class, "onAttachedToWindow"), true, true, true).state());
    }

    @Test public void customClockNeedsItsOwnAttachedView() {
        assertEquals(FusionActivationStatus.WAITING,
                RuntimeApplicationPolicy.statusBar(true, true, true, false).state());
        assertEquals(FusionActivationStatus.APPLIED,
                RuntimeApplicationPolicy.statusBar(true, true, false, false).state());
        assertEquals(FusionActivationStatus.WAITING,
                RuntimeApplicationPolicy.statusBar(true, false, false, true).state());
    }

    @Test public void notificationAndControlCenterKeepDisabledAndMissingAdapterSemantics() {
        assertEquals(FusionActivationStatus.APPLIED,
                RuntimeApplicationPolicy.notificationClock(false, false, false).state());
        assertEquals(FusionActivationStatus.DEGRADED,
                RuntimeApplicationPolicy.notificationClock(true, false, false).state());
        assertEquals(FusionActivationStatus.WAITING,
                RuntimeApplicationPolicy.notificationClock(true, true, false).state());
        assertEquals(FusionActivationStatus.APPLIED,
                RuntimeApplicationPolicy.notificationClock(true, false, true).state());
        assertEquals("native_restored",
                RuntimeApplicationPolicy.controlCenterAvailability(false, false).reason());
        assertEquals(FusionActivationStatus.DEGRADED,
                RuntimeApplicationPolicy.controlCenterAvailability(true, false).state());
        assertEquals(FusionActivationStatus.WAITING,
                RuntimeApplicationPolicy.controlCenterAvailability(true, true).state());
    }

    @Test public void mountedAutomaticGlassWithUnknownOemBlurReportsItsFallback() {
        ControlCenterConfig automatic = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(0, 30, 0);
        RuntimeApplicationPolicy.Result fallback = RuntimeApplicationPolicy.controlCenterMaterial(
                automatic, FusionActivationStatus.APPLIED, "control_center_mounted");
        assertEquals(FusionActivationStatus.DEGRADED, fallback.state());
        assertEquals("control_center_mounted_canvas_compatibility_private_blur_unverified",
                fallback.reason());
        for (ControlCenterConfig unchanged : new ControlCenterConfig[] {
                automatic.withEnabled(false), automatic.withBlur(0, 0, 0),
                automatic.withSurface(ControlCenterSurface.CLEAR),
                automatic.withSurface(ControlCenterSurface.SOFT_GLASS)}) {
            RuntimeApplicationPolicy.Result result = RuntimeApplicationPolicy.controlCenterMaterial(
                    unchanged, FusionActivationStatus.APPLIED, "control_center_mounted");
            assertEquals(FusionActivationStatus.APPLIED, result.state());
            assertEquals("control_center_mounted", result.reason());
        }
    }

    @Test public void missingHooksAndPendingMountAreNotOverwrittenByMaterialFallback() {
        ControlCenterConfig automatic = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        assertEquals("required_control_center_hooks_unavailable", RuntimeApplicationPolicy.controlCenterMaterial(
                automatic, FusionActivationStatus.DEGRADED, "required_control_center_hooks_unavailable").reason());
        assertEquals(FusionActivationStatus.WAITING, RuntimeApplicationPolicy.controlCenterMaterial(
                automatic, FusionActivationStatus.WAITING, "control_center_mount_pending").state());
    }

    static class Battery { void onAttachedToWindow() { } }
}
