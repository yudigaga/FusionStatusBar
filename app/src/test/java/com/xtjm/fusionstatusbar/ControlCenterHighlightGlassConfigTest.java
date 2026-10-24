package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterHighlightGlassConfigTest {
    @Test public void adaptiveChoiceAppendsToEveryPreviouslySavedOrdinal() {
        ControlCenterSurface[] previous = {ControlCenterSurface.DEFAULT, ControlCenterSurface.CLEAR,
                ControlCenterSurface.FROSTED, ControlCenterSurface.DARK, ControlCenterSurface.GLASS,
                ControlCenterSurface.LIGHT, ControlCenterSurface.GRAPHITE, ControlCenterSurface.ICE,
                ControlCenterSurface.MINT, ControlCenterSurface.SOFT_GLASS};
        for (int ordinal = 0; ordinal < previous.length; ordinal++) {
            assertEquals(previous[ordinal].name(), ordinal, previous[ordinal].ordinal());
            assertSame(previous[ordinal], ControlCenterSurface.fromOrdinal(ordinal));
        }
        assertEquals(previous.length, ControlCenterSurface.HIGHLIGHT_GLASS.ordinal());
        assertTrue(ControlCenterSurface.HIGHLIGHT_GLASS.hasSoftGlassReflection());
        assertTrue(ControlCenterSurface.SOFT_GLASS.hasSoftGlassReflection());
    }

    @Test public void systemModeResolvesOnlyAdaptiveChoiceAndKeepsContrast() {
        Context day = context(false), night = context(true);
        for (ControlCenterSurface surface : ControlCenterSurface.values()) {
            assertSame(surface, surface.resolve(day));
            assertSame(surface == ControlCenterSurface.HIGHLIGHT_GLASS
                    ? ControlCenterSurface.SOFT_GLASS : surface, surface.resolve(night));
        }
        assertSame(ControlCenterSurface.HIGHLIGHT_GLASS, ControlCenterSurface.HIGHLIGHT_GLASS.resolve(null));
        assertEquals(0xff202124, ControlCenterSurface.HIGHLIGHT_GLASS.resolve(day).neutralForeground());
        assertEquals(0xffffffff, ControlCenterSurface.HIGHLIGHT_GLASS.resolve(night).neutralForeground());
    }

    @Test public void preferencesBundleBackupAndPresetRetainAdaptiveChoiceAfterNightConsumption()
            throws Exception {
        ControlCenterConfig source = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(35, 60, 85)
                .withGlobalBorder(true, 2);
        assertSame(source, source.resolveSurface(context(false)));
        ControlCenterConfig resolved = source.resolveSurface(context(true));
        assertSame(ControlCenterSurface.SOFT_GLASS, resolved.surface);
        assertSame("Resolving a consuming view must not overwrite the saved automatic choice",
                ControlCenterSurface.HIGHLIGHT_GLASS, source.surface);

        Bundle bundle = new Bundle();
        source.writeTo(bundle);
        assertAdaptive(ControlCenterConfig.fromBundle(bundle));
        var preferences = RuntimeEnvironment.getApplication().getSharedPreferences("highlight-glass", 0);
        preferences.edit().clear().commit();
        var editor = preferences.edit();
        source.writeTo(editor);
        assertTrue(editor.commit());
        assertAdaptive(ControlCenterConfig.read(preferences));
        var restored = ConfigurationBackupCodec.decode(ConfigurationBackupCodec.encode(
                FusionConfig.defaults().withControlCenter(source)));
        assertAdaptive(restored.controlCenter);
        assertAdaptive(ConfigurationBackupCodec.controlCenterFromJson(ConfigurationBackupCodec.configJson(source)));
    }

    private static void assertAdaptive(ControlCenterConfig restored) {
        assertTrue(restored.enabled);
        assertSame(ControlCenterSurface.HIGHLIGHT_GLASS, restored.surface);
        assertEquals(35, restored.backgroundBlur);
        assertEquals(60, restored.cardBlur);
        assertEquals(85, restored.tileBlur);
        assertTrue(restored.borderEnabled);
        assertEquals(2, restored.borderWidthDp);
        assertSame(ControlCenterSurface.HIGHLIGHT_GLASS, restored.resolveSurface(context(false)).surface);
        assertSame(ControlCenterSurface.SOFT_GLASS, restored.resolveSurface(context(true)).surface);
    }

    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return app.createConfigurationContext(configuration);
    }
}
