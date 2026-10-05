package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterSurfaceConfigTest {
    @Test public void globalSurfaceSurvivesCopiesAndBothSettingsTransports() {
        ControlCenterConfig source = ControlCenterConfig.defaults().withSurface(ControlCenterSurface.DARK);
        ControlCenterConfig changed = source.withEnabled(true).withOrder("wifi").withHidden("bt")
                .withColumns(5).withTileScale(110).withCornerRadius(22).withSpacing(8).withStyle(1)
                .withTileLayout("").withLayoutPlan(ControlCenterLayoutPlan.blank(4).encode())
                .withComponentOrder("control:media").withComponentSide("control:media", "left")
                .withBlur(12, 34, 56).withTileShape("wifi", 2, 1).withoutTileShape("wifi");
        assertEquals(ControlCenterSurface.DARK, changed.surface);
        Bundle bundle = new Bundle(); changed.writeTo(bundle);
        assertEquals(ControlCenterSurface.DARK, ControlCenterConfig.fromBundle(bundle).surface);
        var preferences = RuntimeEnvironment.getApplication().getSharedPreferences("surface-config", 0);
        var editor = preferences.edit(); changed.writeTo(editor); editor.commit();
        assertEquals(ControlCenterSurface.DARK, ControlCenterConfig.read(preferences).surface);
        assertEquals(ControlCenterSurface.DEFAULT, changed.clearCustomization().surface);
        assertTrue(ControlCenterConfig.defaults().clearCustomization().withSurface(ControlCenterSurface.CLEAR).hasVisualOverride());
    }

    @Test public void olderAndInvalidSettingsUseTheOriginalSurface() {
        Bundle bundle = new Bundle();
        assertEquals(ControlCenterSurface.DEFAULT, ControlCenterConfig.fromBundle(bundle).surface);
        for (int invalid : new int[] {-1, ControlCenterSurface.values().length, Integer.MAX_VALUE}) {
            bundle.putInt(ControlCenterConfig.KEY_SURFACE, invalid);
            assertEquals(ControlCenterSurface.DEFAULT, ControlCenterConfig.fromBundle(bundle).surface);
        }
        assertEquals(ControlCenterSurface.DEFAULT, ControlCenterConfig.defaults().withSurface(null).surface);
    }

    @Test public void everyStyleKeepsItsSavedOrdinalAndRetainsBlurStrengths() {
        assertEquals(0, ControlCenterSurface.DEFAULT.ordinal());
        assertEquals(1, ControlCenterSurface.CLEAR.ordinal());
        assertEquals(2, ControlCenterSurface.FROSTED.ordinal());
        assertEquals(3, ControlCenterSurface.DARK.ordinal());
        assertEquals(9, ControlCenterSurface.SOFT_GLASS.ordinal());
        for (ControlCenterSurface style : ControlCenterSurface.values()) {
            var config = ControlCenterConfig.defaults().withEnabled(true).withBlur(35, 60, 85).withSurface(style);
            var bundle = new Bundle(); config.writeTo(bundle);
            var restored = ControlCenterConfig.fromBundle(bundle);
            assertEquals(style, restored.surface);
            assertEquals(35, restored.backgroundBlur);
            assertEquals(60, restored.cardBlur);
            assertEquals(85, restored.tileBlur);
            assertEquals(style != ControlCenterSurface.CLEAR, style.allowsContainerBlur());
        }
    }

    @Test public void borderCopiesAndTransportsWithTheStyleAndIsClamped() {
        var source = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.MINT).withGlobalBorder(true, 3);
        var copied = source.withOrder("wifi").withBlur(20, 40, 60).withLayoutPlan(
                ControlCenterLayoutPlan.blank(4).encode());
        assertTrue(copied.borderEnabled);
        assertEquals(3, copied.borderWidthDp);
        Bundle bundle = new Bundle(); copied.writeTo(bundle);
        var restored = ControlCenterConfig.fromBundle(bundle);
        assertEquals(ControlCenterSurface.MINT, restored.surface);
        assertTrue(restored.borderEnabled);
        assertEquals(3, restored.borderWidthDp);
        var preferences = RuntimeEnvironment.getApplication().getSharedPreferences("surface-border-config", 0);
        preferences.edit().clear().commit();
        var editor = preferences.edit(); copied.writeTo(editor); editor.commit();
        assertTrue(ControlCenterConfig.read(preferences).borderEnabled);
        assertEquals(3, ControlCenterConfig.read(preferences).borderWidthDp);
        assertEquals(1, copied.withGlobalBorder(true, -5).borderWidthDp);
        assertEquals(4, copied.withGlobalBorder(true, 99).borderWidthDp);
        assertFalse(copied.clearCustomization().borderEnabled);
        assertEquals(1, copied.clearCustomization().borderWidthDp);
    }
}
