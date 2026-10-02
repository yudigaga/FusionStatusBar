package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlCenterHolderPresentationTest {
    @Test
    public void wideWifiUsesNativeCardWhileSingleTilesAndPairsKeepTileViews() {
        var wide = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1);
        assertTrue(ControlCenterRuntimeGrid.usesCardView(java.util.List.of(wide), "wifi"));
        assertFalse(ControlCenterRuntimeGrid.usesCardView(
                java.util.List.of(wide.withSize(1, 1)), "wifi"));
        assertFalse(ControlCenterRuntimeGrid.usesCardView(java.util.List.of(
                wide.withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0)), "wifi"));
        var pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        assertFalse(ControlCenterRuntimeGrid.usesCardView(java.util.List.of(pair), "wifi"));
        assertFalse(ControlCenterRuntimeGrid.usesCardView(java.util.List.of(pair), "bt"));
    }

    @Test
    public void laterNativeFramesCannotShrinkOrOffsetEmbeddedGrid() throws Exception {
        NativeHolder holder = new NativeHolder();
        holder.frame();
        assertEquals(0.65f, holder.scale, 0f);
        assertEquals(180f, holder.translation, 0f);

        new ControlCenterRuntimeGrid.HolderPresentation(holder);
        holder.alpha = 1f;
        holder.scale = 1f;
        holder.translation = 0f;
        holder.frame();
        holder.frame();

        assertEquals(1f, holder.alpha, 0f);
        assertEquals(1f, holder.scale, 0f);
        assertEquals(0f, holder.translation, 0f);
    }

    @Test
    public void nativeFlagsAreRestoredWhenGridReleasesHolder() throws Exception {
        NativeHolder holder = new NativeHolder();
        holder.ignoreAlpha = true;
        ControlCenterRuntimeGrid.HolderPresentation presentation =
                new ControlCenterRuntimeGrid.HolderPresentation(holder);
        presentation.freeze();
        presentation.restore();

        assertTrue(holder.ignoreAlpha);
        assertFalse(holder.ignoreScale);
        assertFalse(holder.ignoreTranslation);
        holder.frame();
        assertEquals(0.65f, holder.scale, 0f);
        assertEquals(180f, holder.translation, 0f);
    }

    static final class NativeHolder {
        boolean ignoreAlpha;
        boolean ignoreScale;
        boolean ignoreTranslation;
        float alpha = 1f;
        float scale = 1f;
        float translation;

        public boolean getIgnoreHolderAlpha() { return ignoreAlpha; }
        public boolean getIgnoreHolderScale() { return ignoreScale; }
        public boolean getIgnoreHolderTranslation() { return ignoreTranslation; }
        public void setIgnoreHolderAlpha(boolean value) { ignoreAlpha = value; }
        public void setIgnoreHolderScale(boolean value) { ignoreScale = value; }
        public void setIgnoreHolderTranslation(boolean value) { ignoreTranslation = value; }

        // The native holder's collapsed state is reapplied on every frame.
        void frame() {
            if (!ignoreAlpha) alpha = 0f;
            if (!ignoreScale) scale = 0.65f;
            if (!ignoreTranslation) translation = 180f;
        }
    }
}
