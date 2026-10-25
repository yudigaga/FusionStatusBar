package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterGlassCompatibilityPolicyTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void legacyBlurAndBloomApisAloneDoNotAuthorizeReplacingTheOemBackgroundMaterial() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff78818c);
        background.setCornerRadius(12);
        panel.setBackground(background);
        View glyph = new View(context);
        glyph.setBackgroundColor(Color.BLUE);
        panel.addView(glyph, new FrameLayout.LayoutParams(24, 24, Gravity.CENTER));
        ControlCenterConfig original = ControlCenterConfig.defaults().withEnabled(true);
        record(panel, "setMiViewBlurMode", 3, original);
        record(panel, "setMiBackgroundBlurMode", 2, original);
        record(panel, "setMiBackgroundBlurRadius", 180, original);
        record(panel, "setMiBackgroundBlendColors", new ArrayList<>(List.of(new Point(Color.WHITE, 3))), original);
        record(panel, "setMixEffectEnabled", false, original);
        record(panel, "setMiBloomStroke", new float[21], original);
        assertEquals("The fixture records the observed OEM material mode", 3, panel.viewMode);
        try {
            panel.getClass().getMethod("setMiGlass", float[].class);
            fail("This fixture intentionally has only the old blur/mix/bloom API, without the native glass ABI");
        } catch (NoSuchMethodException expected) { }

        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 2);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        ControlCenterConfig highlight = original.withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
                .withBlur(100, 100, 100).withGlobalBorder(false, 1);
        try {
            grid.applyMaterials(highlight);
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
            grid.layout(0, 0, 400, 240);
            Bitmap image = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
            try { grid.draw(new Canvas(image)); }
            finally { image.recycle(); }
            assertEquals("Legacy API discovery must not force the unverified native view mode1", 0,
                    panel.forcedMode1Calls);
            assertTrue("The compatible path may preserve OEM mode3 or let the established Canvas surface own the fill",
                    panel.viewMode == 3 || panel.viewMode == 0);
            assertFalse("An unverified native glass path must not force the OEM mix effect on", panel.mix);
            assertArrayEquals("An unverified native glass path must not invent a native bloom profile",
                    new float[21], panel.bloom, 0f);
            Bitmap fallback = Bitmap.createBitmap(panel.getWidth(), panel.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                panel.draw(new Canvas(fallback));
                assertTrue("The compatible fallback must retain a visible body at full strength",
                        Color.alpha(fallback.getPixel(panel.getWidth() / 2, 10)) > 0);
                assertEquals("Foreground glyphs remain visible through the fallback", Color.BLUE,
                        fallback.getPixel(panel.getWidth() / 2, panel.getHeight() / 2));
            } finally { fallback.recycle(); }
        } finally { grid.releaseMaterials(); }
    }

    private static void record(View view, String operation, Object argument, ControlCenterConfig config)
            throws Exception {
        Object[] actual = ControlCenterMaterials.nativeArguments(view, operation, new Object[] {argument}, config, false);
        ControlCenterMaterials.invoke(view, operation, actual);
    }

    public static final class MediaPlayerPanel extends FrameLayout {
        int viewMode, backgroundMode, radius, forcedMode1Calls;
        boolean mix;
        float[] bloom = new float[21];
        ArrayList<Point> colors = new ArrayList<>();
        MediaPlayerPanel(Context context) { super(context); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; if (value == 1) forcedMode1Calls++; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = new ArrayList<>(value); }
        public void clearMiBackgroundBlendColor() { colors.clear(); }
        public void setMixEffectEnabled(boolean value) { mix = value; }
        public void setMiBloomStroke(float[] value) { bloom = value.clone(); }
    }
}
