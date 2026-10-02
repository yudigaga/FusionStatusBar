package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 35}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TelemetryLifecycleTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @After public void cleanup() { TelemetrySampler.configure(context, TelemetryConfig.defaults()); }

    @Test public void pollingStopsAtScreenOffAndRestartsAtScreenOn() {
        PowerManager power = context.getSystemService(PowerManager.class);
        shadowOf(power).setIsInteractive(true);
        TelemetrySampler.configure(context, TelemetryConfig.defaults().withEnabled(TelemetryConfig.NET_SPEED, true));
        assertTrue(TelemetrySampler.isPolling());
        shadowOf(power).setIsInteractive(false);
        context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(TelemetrySampler.isPolling());
        shadowOf(power).setIsInteractive(true);
        context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(TelemetrySampler.isPolling());
        TelemetrySampler.configure(context, TelemetryConfig.defaults());
        assertFalse(TelemetrySampler.isPolling());
    }

    @Test public void enablingWhileScreenOffDoesNotCreatePollingTask() {
        shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
        TelemetrySampler.configure(context, TelemetryConfig.defaults().withEnabled(TelemetryConfig.NET_SPEED, true));
        assertFalse(TelemetrySampler.isPolling());
        assertFalse(TelemetrySampler.isInteractive());
    }
}
