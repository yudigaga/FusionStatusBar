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

    static class Battery { void onAttachedToWindow() { } }
}
