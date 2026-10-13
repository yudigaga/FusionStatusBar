package com.xtjm.fusionstatusbar;

import android.content.Context;

final class AppAppearanceSettings {
    private static final String FILE = "app_appearance";
    private static final String FLOATING_NAVIGATION = "floating_navigation";

    private AppAppearanceSettings() { }

    static boolean floatingNavigation(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(FLOATING_NAVIGATION, true);
    }

    static boolean setFloatingNavigation(Context context, boolean enabled) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(FLOATING_NAVIGATION, enabled).commit();
    }
}
