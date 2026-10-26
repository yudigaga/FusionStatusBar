package com.xtjm.fusionstatusbar;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherBackHoldRuntimeTest {
    private Context context;

    @Before public void reset() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("desktop_settings_v1", Context.MODE_PRIVATE).edit().clear().commit();
        context.createDeviceProtectedStorageContext().getSharedPreferences("desktop_settings_v1",
                Context.MODE_PRIVATE).edit().clear().commit();
        Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(true);
        Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
        setField("currentActivity", new WeakReference<>(null));
        setField("runtimeContext", new WeakReference<>(context));
        setField("launcherResumed", false);
        setField("config", DesktopConfig.defaults());
    }

    @Test public void foregroundApplicationActionWaitsForNativeCancellationToFinish() {
        cacheToken();
        Runnable prepared = LauncherHooks.prepareBackHoldAction(context, screenshot());
        assertNotNull(prepared);
        assertTrue(broadcasts().isEmpty());
        prepared.run();
        assertTrue(broadcasts().isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, broadcasts().size());
        Intent sent = broadcasts().get(0);
        assertEquals("com.android.systemui", sent.getPackage());
        assertEquals(DesktopActionQueue.ACTION_DISPATCH, sent.getAction());
        assertEquals(DesktopConfig.ACTION_SCREENSHOT,
                sent.getIntExtra(DesktopActionQueue.KEY_ACTION, -1));
        assertTrue(sent.hasExtra(DesktopActionQueue.KEY_SIGNATURE));
    }

    @Test public void unavailableCommandsLeaveNativeBackAvailable() {
        assertNull(LauncherHooks.prepareBackHoldAction(context, screenshot()));
        cacheToken();
        assertNull(LauncherHooks.prepareBackHoldAction(context, DesktopConfig.defaults()));
        assertNull(LauncherHooks.prepareBackHoldAction(context, DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_NONE)));
        assertNull(LauncherHooks.prepareBackHoldAction(context, DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_APP)));
        assertNull(LauncherHooks.prepareBackHoldAction(context, DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_SHORTCUT)
                .withGestureTarget(DesktopConfig.GESTURE_BACK_HOLD, "invalid-json", "Missing")));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(broadcasts().isEmpty());
    }

    @Test public void lockedOrNonInteractiveDeviceKeepsNativeBack() {
        cacheToken();
        Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setKeyguardLocked(true);
        assertNull(LauncherHooks.prepareBackHoldAction(context, screenshot()));
        Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
        Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
        assertNull(LauncherHooks.prepareBackHoldAction(context, screenshot()));
        assertTrue(broadcasts().isEmpty());
    }

    @Test public void liveConfigRefreshSurvivesLauncherActivityDestruction() throws Exception {
        DesktopConfig saved = screenshot();
        assertTrue(DesktopConfigStore.write(context, saved));
        Method refresh = LauncherHooks.class.getDeclaredMethod("refreshConfiguration");
        refresh.setAccessible(true);
        refresh.invoke(null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Field config = LauncherHooks.class.getDeclaredField("config");
        config.setAccessible(true);
        assertEquals(saved, config.get(null));
    }

    @Test public void existingDesktopGesturesStillRequireForegroundDesktop() throws Exception {
        cacheToken();
        setField("config", DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_SINGLE_DOWN,
                DesktopConfig.ACTION_SCREENSHOT));
        Method action = LauncherHooks.class.getDeclaredMethod("performGestureAction", Context.class, int.class);
        action.setAccessible(true);
        assertEquals(false, action.invoke(null, context, DesktopConfig.GESTURE_SINGLE_DOWN));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(broadcasts().isEmpty());
    }

    private DesktopConfig screenshot() {
        return DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                DesktopConfig.ACTION_SCREENSHOT);
    }

    private void cacheToken() {
        assertTrue(DesktopActionBridgeToken.cacheHostToken(context,
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY"));
    }

    private List<Intent> broadcasts() {
        return Shadows.shadowOf(RuntimeEnvironment.getApplication()).getBroadcastIntents();
    }

    private static void setField(String name, Object value) throws Exception {
        Field field = LauncherHooks.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
