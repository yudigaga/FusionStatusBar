package com.xtjm.fusionstatusbar;

import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;

/** Installs only the explicitly versioned phone-manager extension set. */
final class SecurityCenterHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private SecurityCenterHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return 0;
        int config = SecurityCenterConfigRuntime.install(module, loader);
        int privacy = SecurityCenterPrivacyHooks.install(module, loader);
        int battery = SecurityCenterBatteryHooks.install(module, loader);
        int gameVideo = SecurityCenterGameVideoHooks.install(module, loader);
        module.log(Log.INFO, FusionModule.TAG, "phone manager hooks host=40001380 config=" + config
                + " privacy=" + privacy + " battery=" + battery + " gameVideo=" + gameVideo);
        return config + privacy + battery + gameVideo;
    }
}
