package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Package and component discovery includes disabled entries without changing their state. */
final class DesktopAppCatalog {
    private static final int DISABLED_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS
            | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;

    private DesktopAppCatalog() { }

    static List<App> installed(Context context) {
        PackageManager manager = context.getPackageManager();
        Map<String, App> apps = new LinkedHashMap<>();
        for (ApplicationInfo application : manager.getInstalledApplications(DISABLED_FLAGS)) {
            if (application == null || application.packageName == null) continue;
            apps.put(application.packageName, app(manager, application));
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo resolved : manager.queryIntentActivities(launcher, DISABLED_FLAGS)) {
            ActivityInfo target = resolved.activityInfo;
            if (target == null || target.packageName == null || target.name == null
                    || !target.exported || target.applicationInfo == null) continue;
            App application = apps.computeIfAbsent(target.packageName,
                    key -> app(manager, target.applicationInfo));
            boolean enabled = componentEnabled(manager, target);
            if (application.launcherActivity == null || enabled && application.launcherDisabled) {
                application.launcherActivity = target.name;
                application.launcherDisabled = !enabled;
            }
        }
        ArrayList<App> result = new ArrayList<>(apps.values());
        result.sort(Comparator.comparing(value -> value.label, Collator.getInstance(Locale.CHINA)));
        return result;
    }

    static List<ActivityEntry> activities(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        ArrayList<ActivityEntry> result = new ArrayList<>();
        try {
            PackageInfo info = manager.getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES | DISABLED_FLAGS);
            if (info.activities == null) return result;
            for (ActivityInfo target : info.activities) {
                if (target == null || target.name == null || !target.exported) continue;
                ApplicationInfo application = target.applicationInfo == null
                        ? info.applicationInfo : target.applicationInfo;
                boolean disabled = application != null && !applicationEnabled(manager, application);
                boolean suspended = application != null
                        && (application.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
                result.add(new ActivityEntry(packageName, target.name,
                        label(target.loadLabel(manager), target.name),
                        disabled ? R.string.desktop_app_disabled
                                : suspended ? R.string.desktop_app_suspended
                                : !componentEnabled(manager, target)
                                ? R.string.desktop_activity_disabled : 0));
            }
            result.sort(Comparator.comparing(value -> value.label,
                    Collator.getInstance(Locale.CHINA)));
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        return result;
    }

    private static App app(PackageManager manager, ApplicationInfo application) {
        return new App(application.packageName,
                label(application.loadLabel(manager), application.packageName),
                !applicationEnabled(manager, application),
                (application.flags & ApplicationInfo.FLAG_SUSPENDED) != 0);
    }

    private static String label(CharSequence label, String fallback) {
        return label == null || label.length() == 0 ? fallback : label.toString();
    }

    private static boolean applicationEnabled(PackageManager manager, ApplicationInfo application) {
        try {
            return enabled(manager.getApplicationEnabledSetting(application.packageName),
                    application.enabled);
        } catch (IllegalArgumentException ignored) {
            return application.enabled;
        }
    }

    private static boolean componentEnabled(PackageManager manager, ActivityInfo activity) {
        try {
            return enabled(manager.getComponentEnabledSetting(
                    new ComponentName(activity.packageName, activity.name)), activity.enabled);
        } catch (IllegalArgumentException ignored) {
            return activity.enabled;
        }
    }

    private static boolean enabled(int setting, boolean manifestEnabled) {
        if (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
        if (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return manifestEnabled;
        return false;
    }

    static final class App {
        final String packageName;
        final String label;
        final boolean disabled;
        final boolean suspended;
        String launcherActivity;
        boolean launcherDisabled;

        App(String packageName, String label, boolean disabled, boolean suspended) {
            this.packageName = packageName;
            this.label = label;
            this.disabled = disabled;
            this.suspended = suspended;
        }

        int stateLabel() {
            return disabled ? R.string.desktop_app_disabled : suspended ? R.string.desktop_app_suspended
                    : launcherDisabled ? R.string.desktop_launcher_disabled : 0;
        }

        String displayLabel(Context context) {
            int state = stateLabel();
            return label + "\n" + packageName + (state == 0 ? "" : " · " + context.getString(state));
        }

        String component() { return packageName + "|" + (launcherActivity == null ? "" : launcherActivity); }
    }

    static final class ActivityEntry {
        final String packageName;
        final String className;
        final String label;
        final String component;
        final int stateLabel;

        ActivityEntry(String packageName, String className, String label, int stateLabel) {
            this.packageName = packageName;
            this.className = className;
            this.label = label;
            this.stateLabel = stateLabel;
            component = packageName + "|" + className;
        }

        String displayLabel(Context context) {
            return label + "\n" + className
                    + (stateLabel == 0 ? "" : " · " + context.getString(stateLabel));
        }
    }
}
