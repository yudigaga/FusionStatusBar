package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.app.UiModeManager;
import android.media.AudioManager;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DesktopSystemActionReceiverTest {
    @Test public void screenshotUsesTheRegisteredSystemUiAction() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        execute(context, DesktopConfig.ACTION_SCREENSHOT, "", 0);
        List<Intent> broadcasts = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getBroadcastIntents();
        Intent action = broadcasts.get(broadcasts.size() - 1);
        assertEquals("SYSTEM_ACTION_TAKE_SCREENSHOT", action.getAction());
        assertEquals("com.android.systemui", action.getPackage());
    }

    @Test public void recentsUsesTheRegisteredSystemUiAction() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        execute(context, DesktopConfig.ACTION_RECENTS, "", 0);
        List<Intent> broadcasts = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getBroadcastIntents();
        Intent action = broadcasts.get(broadcasts.size() - 1);
        assertEquals("SYSTEM_ACTION_RECENTS", action.getAction());
        assertEquals("com.android.systemui", action.getPackage());
    }

    @Test public void soundCyclesThroughSilentVibrateAndNormal() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        AudioManager audio = context.getSystemService(AudioManager.class);
        audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        execute(context, DesktopConfig.ACTION_TOGGLE, "", 5);
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.getRingerMode());
        execute(context, DesktopConfig.ACTION_TOGGLE, "", 5);
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.getRingerMode());
        execute(context, DesktopConfig.ACTION_TOGGLE, "", 5);
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.getRingerMode());
    }

    @Test public void legacySystemSettingsTargetRetainsTheSettingsIntent() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        execute(context, DesktopConfig.ACTION_LAUNCH_APP, "com.android.settings", 0);
        Intent activity = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull(activity);
        assertEquals(Settings.ACTION_SETTINGS, activity.getAction());
        assertEquals("com.android.settings", activity.getPackage());
    }

    @Test public void automaticNightModeStillTogglesTheCurrentlyDarkInterfaceOff() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        UiModeManager uiMode = context.getSystemService(UiModeManager.class);
        uiMode.setNightMode(UiModeManager.MODE_NIGHT_AUTO);
        Configuration configuration = context.getResources().getConfiguration();
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_YES;
        context.getResources().updateConfiguration(configuration, null);
        execute(context, DesktopConfig.ACTION_TOGGLE, "", 12);
        assertEquals(UiModeManager.MODE_NIGHT_NO, uiMode.getNightMode());
    }

    private static void execute(Context context, int action, String target, int toggle)
            throws Exception {
        Method method = DesktopSystemActionReceiver.class.getDeclaredMethod("execute",
                Context.class, int.class, String.class, int.class);
        method.setAccessible(true);
        method.invoke(null, context, action, target, toggle);
    }
}
