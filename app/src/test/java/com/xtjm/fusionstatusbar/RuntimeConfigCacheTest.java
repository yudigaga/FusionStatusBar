package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class RuntimeConfigCacheTest {
    @Test public void runtimeReadNeverTouchesProviderWhenTheModulePackageIsEnabled() {
        Context systemUi = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return "com.android.systemui"; }
            @Override public android.content.ContentResolver getContentResolver() {
                throw new AssertionError("runtime_must_not_start_settings_provider");
            }
        };
        assertTrue(RuntimeConfigCache.write(systemUi,
                FusionConfig.defaults().withIconScale(127).withRevision(14)));

        assertEquals(127, FusionConfig.read(systemUi).iconScale);
        assertEquals(14L, FusionConfig.read(systemUi).revision);
    }

    @Test public void authoritativeSnapshotCanReplaceAConfigWhoseSourceWasReset() {
        Context systemUi = host("com.android.systemui");
        RuntimeConfigCache.write(systemUi, FusionConfig.defaults().withIconScale(120).withRevision(50));
        RuntimeConfigCache.write(systemUi, FusionConfig.defaults().withIconScale(90).withRevision(1));

        assertEquals(90, RuntimeConfigCache.read(systemUi).iconScale);
        assertEquals(1L, RuntimeConfigCache.read(systemUi).revision);
    }
    @Test public void disabledPackageWithoutASnapshotNeverStartsTheProvider() {
        Context systemUi = host("com.android.systemui");
        systemUi.createDeviceProtectedStorageContext().getSharedPreferences("fusion_statusbar_runtime",
                Context.MODE_PRIVATE).edit().clear().commit();
        boolean[] providerCalled = {false};

        FusionConfig loaded = FusionConfig.readFromProvider(systemUi, () -> {
            providerCalled[0] = true;
            throw new IllegalStateException("package_disabled");
        }, true);

        assertFalse(providerCalled[0]);
        assertNull(loaded);
    }
    @Test public void initialHostReadUsesItsSnapshotWithoutStartingDisabledProvider() {
        Context systemUi = host("com.android.systemui");
        FusionConfig saved = FusionConfig.defaults().withIconScale(123).withRevision(7);
        assertTrue(RuntimeConfigCache.write(systemUi, saved));
        boolean[] providerCalled = {false};

        FusionConfig loaded = FusionConfig.readFromProvider(systemUi, () -> {
            providerCalled[0] = true;
            throw new IllegalStateException("package_disabled");
        }, true);

        assertFalse(providerCalled[0]);
        assertEquals(123, loaded.iconScale);
        assertEquals(7L, loaded.revision);
    }

    @Test public void providerFailureFallsBackToTheHostSnapshot() {
        Context systemUi = host("com.android.systemui");
        FusionConfig saved = FusionConfig.defaults().withStatusBarHeight(72).withRevision(11);
        RuntimeConfigCache.write(systemUi, saved);

        FusionConfig loaded = FusionConfig.readFromProvider(systemUi, () -> {
            throw new IllegalStateException("package_disabled");
        }, false);

        assertEquals(72, loaded.statusBarHeight);
        assertEquals(11L, loaded.revision);
    }

    @Test public void successfulProviderReadRefreshesTheHostSnapshot() {
        Context launcher = host("com.miui.home");
        FusionConfig saved = FusionConfig.defaults().withIconScale(118).withRevision(13);

        FusionConfig loaded = FusionConfig.readFromProvider(launcher, saved::toBundle, false);

        assertEquals(118, loaded.iconScale);
        assertEquals(13L, RuntimeConfigCache.read(launcher).revision);
    }

    @Test public void packageManagerDisableStateSelectsTheHostSnapshot() {
        Context app = RuntimeEnvironment.getApplication();
        try {
            app.getPackageManager().setApplicationEnabledSetting(BuildConfig.APPLICATION_ID,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
            assertTrue(RuntimeConfigCache.settingsPackageDisabled(host("com.android.systemui")));
        } finally {
            app.getPackageManager().setApplicationEnabledSetting(BuildConfig.APPLICATION_ID,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
        }
    }

    private static Context host(String packageName) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return packageName; }
        };
    }
}
