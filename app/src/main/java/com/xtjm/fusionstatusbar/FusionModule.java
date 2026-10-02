package com.xtjm.fusionstatusbar;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class FusionModule extends XposedModule {
    static final String TAG = "FusionStatusBar";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    public FusionModule() {
        super();
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }
        installSystemUi(param.getDefaultClassLoader());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if ("com.android.systemui".equals(param.getPackageName())) {
            installSystemUi(param.getClassLoader());
            return;
        }
        if (!"miui.systemui.plugin".equals(param.getPackageName())) {
            return;
        }
        // The plugin also has isolated processes (for example :flashlight). Their
        // class loaders do not contain SystemUI's QS interfaces; the main SystemUI
        // process already captures the shared plugin loader through PluginFactory.
        if (!STARTED.get()) {
            return;
        }
        try {
            SystemUiHooks.installPluginHooks(this, param.getClassLoader());
            log(Log.INFO, TAG, "systemui plugin hooks loaded");
        } catch (Throwable error) {
            Log.e(TAG, "systemui plugin hooks failed", error);
            log(Log.ERROR, TAG, "systemui plugin hooks failed", error);
        }
    }

    private void installSystemUi(ClassLoader classLoader) {
        try {
            log(Log.INFO, TAG, "systemui module version=" + BuildConfig.VERSION_NAME
                    + " versionCode=" + BuildConfig.VERSION_CODE);
            SystemUiHooks.install(this, classLoader);
            STARTED.set(classLoader != null);
            log(Log.INFO, TAG, "systemui module loaded");
        } catch (Throwable error) {
            Log.e(TAG, "systemui module load failed", error);
            log(Log.ERROR, TAG, "systemui module load failed", error);
        }
    }
}
