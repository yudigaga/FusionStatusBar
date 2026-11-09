package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ModuleStringsTest {
    @Test public void unformattedResourcePatternsRemainLiteralWhenNoArgumentsAreProvided() {
        Context app = RuntimeEnvironment.getApplication();
        assertEquals(app.getString(R.string.statusbar_height_value),
                ModuleStrings.get(app, R.string.statusbar_height_value));
    }
    @Test public void settingsUseTheirOwnResourcesAndFormatting() {
        Context app = RuntimeEnvironment.getApplication();
        assertEquals(app.getString(R.string.desktop_hidden_apps_count, 3),
                ModuleStrings.get(app, R.string.desktop_hidden_apps_count, 3));
    }

    @Test public void hostResourceIdCollisionsCannotReplaceTheModuleLabel() {
        Context app = RuntimeEnvironment.getApplication();
        Resources nativeResources = app.getResources();
        Resources collision = new Resources(app.getAssets(), nativeResources.getDisplayMetrics(),
                nativeResources.getConfiguration()) {
            @Override public String getString(int id, Object... arguments) { return "unrelated host text"; }
        };
        Context host = new ContextWrapper(app) {
            @Override public String getPackageName() { return "com.android.systemui"; }
            @Override public Resources getResources() { return collision; }
            @Override public Context createPackageContext(String name, int flags) {
                assertEquals(BuildConfig.APPLICATION_ID, name);
                return app;
            }
        };
        assertEquals(app.getString(R.string.app_name), ModuleStrings.get(host, R.string.app_name));
    }
}
