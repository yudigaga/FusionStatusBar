package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h800dp-mdpi")
public class LauncherIconVisibilityTest {
    private Context context;
    private ActivityController<MainActivity> controller;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        LauncherIconVisibility.setHidden(context, false);
    }

    @After public void cleanup() {
        if (controller != null) controller.pause().stop().destroy();
        LauncherIconVisibility.setHidden(context, false);
    }

    @Test public void hiddenLauncherLeavesLsposedSettingsActivityEnabled() throws Exception {
        LauncherIconVisibility.setHidden(context, true);
        assertTrue(LauncherIconVisibility.isHidden(context));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                context.getPackageManager().getComponentEnabledSetting(new ComponentName(
                        context.getPackageName(), "com.xtjm.fusionstatusbar.LauncherActivity")));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                context.getPackageManager().getComponentEnabledSetting(new ComponentName(
                        context, MainActivity.class)));
        Intent settings = new Intent(Intent.ACTION_MAIN)
                .addCategory("de.robv.android.xposed.category.MODULE_SETTINGS")
                .setPackage(context.getPackageName());
        assertEquals(MainActivity.class.getName(), context.getPackageManager()
                .resolveActivity(settings, 0).activityInfo.name);
    }

    @Test public void showingIconRestoresManifestDefault() {
        LauncherIconVisibility.setHidden(context, true);
        LauncherIconVisibility.setHidden(context, false);
        assertFalse(LauncherIconVisibility.isHidden(context));
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.getPackageName());
        assertEquals("com.xtjm.fusionstatusbar.LauncherActivity", context.getPackageManager()
                .resolveActivity(launcher, 0).activityInfo.name);
    }

    @Test public void overviewSwitchReflectsAndChangesPackageManagerState() throws Exception {
        LauncherIconVisibility.setHidden(context, true);
        Field repository = FusionConfigRepository.class.getDeclaredField("application");
        repository.setAccessible(true);
        repository.set(null, null);
        FusionConfigStore.write(context, FusionConfig.defaults());
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        MainActivity activity = controller.get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        assertTrue(activity.settingsLoaded());
        Switch toggle = findSwitch(activity.getWindow().getDecorView(),
                context.getString(R.string.hide_launcher_icon));
        assertNotNull(toggle);
        assertTrue(toggle.isChecked());
        toggle.performClick();
        assertFalse(LauncherIconVisibility.isHidden(context));
        toggle.performClick();
        assertTrue(LauncherIconVisibility.isHidden(context));
        assertFalse(activity.isFinishing());
    }

    private static Switch findSwitch(View view, String label) {
        if (view instanceof Switch && view.getContentDescription() != null
                && label.contentEquals(view.getContentDescription()))
            return (Switch) view;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                Switch found = findSwitch(group.getChildAt(index), label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
