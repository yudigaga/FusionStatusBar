package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;

/** Resolves module text without interpreting module resource IDs in another package. */
final class ModuleStrings {
    private ModuleStrings() { }
    static String get(Context context, int resource, Object... arguments) {
        if (context == null) return "";
        try {
            Context owner = context;
            if (!BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
                owner = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
                        .createConfigurationContext(context.getResources().getConfiguration());
            }
            return arguments == null || arguments.length == 0
                    ? owner.getString(resource) : owner.getString(resource, arguments);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException unavailable) {
            android.util.Log.w(FusionModule.TAG, "module language resource unavailable", unavailable);
            return "";
        }
    }
}
