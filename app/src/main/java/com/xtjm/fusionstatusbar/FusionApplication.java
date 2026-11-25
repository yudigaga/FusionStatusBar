package com.xtjm.fusionstatusbar;

import android.app.Application;
import android.content.Context;

public final class FusionApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ModuleLog.configure(Boolean.TRUE.equals(ModuleLoggingConfig.read(base)));
    }
}
