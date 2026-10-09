package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class DesktopConfigCacheTest {
    @Test public void enabledModuleDoesNotCauseProviderReadsWhenLauncherResumes() {
        Context launcher = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return "com.miui.home"; }
            @Override public android.content.ContentResolver getContentResolver() {
                throw new AssertionError("runtime_must_not_start_settings_provider");
            }
        };
        DesktopConfigStore.write(launcher, DesktopConfig.defaults().withGesture(0,
                DesktopConfig.ACTION_SCREENSHOT));

        assertEquals(DesktopConfig.ACTION_SCREENSHOT,
                DesktopConfig.readForRuntime(launcher).gestureAction(0));
    }
    @Test public void launcherUsesItsPersistedConfigWhenTheProviderIsDisabled() {
        Context launcher = host("com.miui.home");
        DesktopConfig saved = DesktopConfig.defaults().withGesture(0,
                DesktopConfig.ACTION_NOTIFICATIONS);
        DesktopConfigStore.write(launcher, saved);

        DesktopConfig loaded = DesktopConfig.read(launcher, () -> {
            throw new IllegalStateException("package_disabled");
        });

        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS, loaded.gestureAction(0));
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS,
                DesktopConfig.readForRuntime(launcher, true).gestureAction(0));
    }

    @Test public void successfulProviderReadCopiesTheSnapshotIntoLauncherStorage() {
        Context launcher = host("com.miui.home");
        DesktopConfig saved = DesktopConfig.defaults().withGesture(2,
                DesktopConfig.ACTION_QUICK_SETTINGS);

        DesktopConfig loaded = DesktopConfig.read(launcher, saved::toBundle);

        assertEquals(DesktopConfig.ACTION_QUICK_SETTINGS, loaded.gestureAction(2));
        assertEquals(DesktopConfig.ACTION_QUICK_SETTINGS,
                DesktopConfigStore.read(launcher).gestureAction(2));
    }

    private static Context host(String packageName) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return packageName; }
        };
    }
}
