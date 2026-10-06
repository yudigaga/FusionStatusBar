package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** The launcher alias can be disabled while the LSPosed settings activity stays available. */
final class LauncherIconVisibility {
    private static final String ALIAS = "com.xtjm.fusionstatusbar.LauncherActivity";

    private LauncherIconVisibility() { }

    static boolean isHidden(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(component(context));
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    static void setHidden(Context context, boolean hidden) {
        if (isHidden(context) == hidden) return;
        context.getPackageManager().setComponentEnabledSetting(component(context), hidden
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
    }

    private static ComponentName component(Context context) {
        return new ComponentName(context.getPackageName(), ALIAS);
    }
}
