package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DesktopAppCatalogTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void disabledUntilUsedPackageAndDisabledLauncherStayDiscoverable() {
        install("test.frozen", true, true);
        PackageManager manager = context.getPackageManager();
        manager.setApplicationEnabledSetting("test.frozen",
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, 0);
        ComponentName main = new ComponentName("test.frozen", "test.frozen.Main");
        manager.setComponentEnabledSetting(main, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        DesktopAppCatalog.App app = find("test.frozen");
        assertEquals("test.frozen.Main", app.launcherActivity);
        assertTrue(app.disabled);
        assertTrue(app.launcherDisabled);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                manager.getApplicationEnabledSetting("test.frozen"));
    }

    @Test public void multipleAliasesChooseTheEnabledEntryWithoutDuplicatingThePackage() throws Exception {
        install("test.alias", true, false);
        var manager = context.getPackageManager();
        ActivityInfo alias = new ActivityInfo();
        alias.packageName = "test.alias";
        alias.name = "test.alias.Enabled";
        alias.applicationInfo = manager.getApplicationInfo("test.alias", 0);
        alias.enabled = true;
        alias.exported = true;
        Shadows.shadowOf(manager).addOrUpdateActivity(alias);
        launcher(alias);
        var apps = DesktopAppCatalog.installed(context);
        assertEquals(1, apps.stream().filter(app -> app.packageName.equals("test.alias")).count());
        assertEquals(alias.name, find("test.alias").launcherActivity);
        assertFalse(find("test.alias").launcherDisabled);
    }

    @Test public void suspendedApplicationRetainsItsStatus() {
        install("test.suspended", true, true);
        Shadows.shadowOf(context.getPackageManager()).getInternalMutablePackageInfo("test.suspended")
                .applicationInfo.flags |= ApplicationInfo.FLAG_SUSPENDED;
        assertTrue(find("test.suspended").suspended);
        assertEquals(R.string.desktop_app_suspended, find("test.suspended").stateLabel());
    }

    @Test public void activityCatalogIncludesDisabledExportedTargetsWithoutLauncher() {
        install("test.activities", false, false);
        assertNull(find("test.activities").launcherActivity);
        var entries = DesktopAppCatalog.activities(context, "test.activities");
        assertEquals(1, entries.size());
        assertEquals("test.activities.Main", entries.get(0).className);
        assertEquals(R.string.desktop_activity_disabled, entries.get(0).stateLabel);
    }

    private DesktopAppCatalog.App find(String packageName) {
        return DesktopAppCatalog.installed(context).stream()
                .filter(app -> packageName.equals(app.packageName)).findFirst().orElseThrow();
    }

    private void install(String packageName, boolean hasLauncher, boolean enabled) {
        ApplicationInfo app = new ApplicationInfo();
        app.packageName = packageName;
        app.enabled = true;
        app.flags = ApplicationInfo.FLAG_INSTALLED;
        app.nonLocalizedLabel = packageName;
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = app;
        var manager = Shadows.shadowOf(context.getPackageManager());
        manager.installPackage(info);
        ActivityInfo main = new ActivityInfo();
        main.packageName = packageName;
        main.name = packageName + ".Main";
        main.applicationInfo = app;
        main.enabled = enabled;
        main.exported = true;
        manager.addOrUpdateActivity(main);
        ActivityInfo internal = new ActivityInfo(main);
        internal.name = packageName + ".Internal";
        internal.exported = false;
        manager.addOrUpdateActivity(internal);
        if (hasLauncher) launcher(main);
    }

    private void launcher(ActivityInfo activity) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        Shadows.shadowOf(context.getPackageManager()).addIntentFilterForActivity(
                new ComponentName(activity.packageName, activity.name), filter);
    }
}
