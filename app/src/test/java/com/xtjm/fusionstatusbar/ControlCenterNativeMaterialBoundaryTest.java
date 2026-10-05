package com.xtjm.fusionstatusbar;

import android.graphics.Point;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
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
@Config(sdk = 34)
public class ControlCenterNativeMaterialBoundaryTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    private ControlCenterConfig glass() {
        return ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS).withBlur(70, 60, 50);
    }

    @Test public void cardLabelBlendMustNotBecomeARectangularBackground() {
        var card = new QSCardItemView();
        var label = new NativeLabel();
        card.addView(label);
        ArrayList<Point> colors = new ArrayList<>(List.of(new Point(0xccffffff, 3)));
        Object[] request = {colors};
        Object[] actual = ControlCenterMaterials.nativeArguments(label,
                "setMiBackgroundBlendColors", request, glass(), false);
        assertSame("Text blend describes glyphs, not the card surface", request, actual);
        label.setMiBackgroundBlendColors(colors);
        ControlCenterMaterials.nativeArguments(label, "setMiViewBlurMode",
                new Object[] {1}, glass(), false);
        ControlCenterMaterials.registerRoot(card, glass());
        ControlCenterMaterials.refresh(glass());
        assertEquals("A label must not acquire its own background blur", 0, label.backgroundMode);
        assertEquals(1, label.viewMode);
        assertEquals(0xccffffff, label.colors.get(0).x);
    }

    @Test public void cardIconContainerAndGlyphKeepNativeBlend() {
        var card = new QSCardItemView();
        var icon = new QSCardItemIconView();
        var glyph = new NativeView();
        icon.addView(glyph);
        card.addView(icon);
        Object[] request = {new ArrayList<>(List.of(new Point(0xff2288ff, 3)))};
        assertSame("QSCardItemIconView is content, not a second card", request,
                ControlCenterMaterials.nativeArguments(glyph,
                        "setMiBackgroundBlendColors", request, glass(), false));
        ControlCenterMaterials.nativeArguments(glyph, "setMiViewBlurMode",
                new Object[] {3}, glass(), false);
        ControlCenterMaterials.registerRoot(card, glass());
        assertEquals(0, glyph.backgroundMode);
    }

    @Test public void oemDisablingBlurMustNotReenableASurface() {
        var card = new QSCardItemView();
        ControlCenterMaterials.nativeArguments(card, "setMiViewBlurMode",
                new Object[] {0}, glass(), false);
        card.setMiViewBlurMode(0);
        ControlCenterMaterials.updateNativeSurface(card, glass(), false);
        assertEquals("Native mode 0 must remain off", 0, card.viewMode);
        assertEquals(0, card.backgroundMode);
        assertTrue("Do not synthesize tint on a disabled native surface", card.colors.isEmpty());
    }

    @Test public void nativeSurfaceCanTurnOffAfterBeingCustomized() {
        var card = new QSCardItemView();
        card.viewMode = 1;
        ControlCenterMaterials.nativeArguments(card, "setMiViewBlurMode",
                new Object[] {1}, glass(), false);
        ControlCenterMaterials.updateNativeSurface(card, glass(), false);
        assertEquals(1, card.backgroundMode);
        ControlCenterMaterials.nativeArguments(card, "setMiViewBlurMode",
                new Object[] {0}, glass(), false);
        card.setMiViewBlurMode(0);
        ControlCenterMaterials.updateNativeSurface(card, glass(), false);
        assertEquals(0, card.viewMode);
        assertEquals(0, card.backgroundMode);
        assertTrue(card.colors.isEmpty());
    }

    @Test public void clearingNativeBlendMustNotInstallAnotherTint() {
        var card = new QSCardItemView();
        Object[] clear = {new ArrayList<Point>()};
        assertSame("An OEM clear is a clear even while a global style is enabled", clear,
                ControlCenterMaterials.nativeArguments(card,
                        "setMiBackgroundBlendColors", clear, glass(), false));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void nativeTileBorderFollowsTheInnerIconWithoutOutliningItsGridCell() {
        var tile = new QSTileItemView();
        var icon = new ImageView(tile.getContext());
        icon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        tile.addView(icon, new FrameLayout.LayoutParams(60, 60, Gravity.CENTER));
        tile.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        tile.layout(0, 0, 100, 100);
        ControlCenterMaterials.registerRoot(tile, ControlCenterConfig.defaults()
                .withEnabled(true).withGlobalBorder(true, 2));
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        tile.draw(new Canvas(bitmap));
        assertEquals("The padded grid cell has no border", 0, Color.alpha(bitmap.getPixel(0, 50)));
        assertTrue("The real circular surface retains its border", Color.alpha(bitmap.getPixel(20, 50)) > 0);
        bitmap.eraseColor(Color.TRANSPARENT);
        ControlCenterMaterials.refresh(ControlCenterConfig.defaults());
        tile.draw(new Canvas(bitmap));
        assertEquals(0, Color.alpha(bitmap.getPixel(20, 50)));
        bitmap.recycle();
    }

    public static class NativeView extends FrameLayout {
        int viewMode, backgroundMode, radius;
        ArrayList<Point> colors = new ArrayList<>();
        NativeView() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; }
    }

    public static class QSCardItemView extends NativeView { }
    public static class QSCardItemIconView extends NativeView { }
    public static class QSTileItemView extends NativeView { }

    public static class NativeLabel extends TextView {
        int viewMode = 1, backgroundMode, radius;
        ArrayList<Point> colors = new ArrayList<>();
        NativeLabel() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; }
    }
}
