package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterTileSurfaceRegressionTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void replacingNativeSurfaceKeepsLayerIntrinsicSizeAndGlyphScale() {
        var tile = new QSTileItemView();
        var background = new GradientDrawable();
        background.setSize(100, 100);
        background.setColor(Color.GRAY);
        var layers = new LayerDrawable(new Drawable[] {background, new ColorDrawable(Color.BLUE)});
        layers.setLayerSize(1, 32, 32);
        layers.setLayerGravity(1, Gravity.CENTER);
        var icon = image(tile, layers);
        synchronize(tile);
        assertEquals("The OEM background defines image scale, not the fixed-size glyph",
                100, layers.getIntrinsicWidth());
        var pixels = draw(icon);
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, pixels.getPixel(20, 50));
        assertEquals(Color.BLUE, pixels.getPixel(50, 50));
        pixels.recycle();
    }

    @Test public void activeSurfaceIgnoresOemPolygonAndDrawsOneUniformCircle() {
        var tile = new QSTileItemView();
        var layers = new LayerDrawable(new Drawable[] {
                new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLUE)});
        layers.setLayerInset(1, 35, 35, 35, 35);
        var icon = image(tile, layers);
        synchronize(tile);
        var pixels = draw(icon);
        assertEquals("The replacement must not blend with stale OEM fill underneath",
                ControlCenterCardView.ACTIVE_SURFACE, pixels.getPixel(20, 50));
        pixels.recycle();
    }

    @Test public void replacingCardBackgroundKeepsItsPaddingAndIntrinsicSize() {
        var card = new QSCardItemView();
        var background = new GradientDrawable();
        background.setSize(200, 100);
        background.setColor(Color.GRAY);
        card.setBackground(background);
        ControlCenterMaterials.syncCardView(card,
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1));
        assertEquals(200, card.getBackground().getIntrinsicWidth());
        assertEquals(100, card.getBackground().getIntrinsicHeight());
    }

    @Test public void glyphOnlyNativeBlurTileOwnsItsFillInsideTheImage() {
        var tile = new QSTileItemView();
        var layers = new LayerDrawable(new Drawable[] {new ColorDrawable(Color.BLUE)});
        layers.setLayerSize(0, 32, 32);
        layers.setLayerGravity(0, Gravity.CENTER);
        var icon = image(tile, layers);
        synchronize(tile);
        assertNull("A padded tile holder must not receive a second backing plate", tile.getBackground());
        assertNotNull("Native-blur glyph-only icons still own their active fill", icon.getBackground());
        assertEquals("The glyph-only image composition must stay intact", 1, layers.getNumberOfLayers());
        var pixels = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        icon.getBackground().setBounds(0, 0, 100, 100);
        icon.getBackground().draw(new Canvas(pixels));
        assertEquals(0, Color.alpha(pixels.getPixel(0, 0)));
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, pixels.getPixel(20, 50));
        pixels.recycle();
        tile.state.state = 1;
        synchronize(tile);
        assertNull("An inactive native-blur icon restores its original background", icon.getBackground());
    }

    @Test public void nativeTileAnimationStillInvalidatesAndSchedulesAfterWrapping() {
        var tile = new QSTileItemView();
        var nativeSurface = new ColorDrawable(Color.GRAY);
        var layers = new LayerDrawable(new Drawable[] {nativeSurface, new ColorDrawable(Color.BLUE)});
        layers.setLayerInset(1, 35, 35, 35, 35);
        image(tile, layers);
        synchronize(tile);
        assertNativeAnimation(layers, nativeSurface);
    }

    @Test public void nativeCardAnimationStillInvalidatesAndSchedulesAfterWrapping() {
        var card = new QSCardItemView();
        var nativeSurface = new ColorDrawable(Color.GRAY);
        card.setBackground(nativeSurface);
        ControlCenterMaterials.syncCardView(card,
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1));
        assertNativeAnimation(card.getBackground(), nativeSurface);
    }

    private void assertNativeAnimation(Drawable installed, Drawable nativeSurface) {
        var callback = new AnimationCallback();
        installed.setCallback(callback);
        nativeSurface.invalidateSelf();
        assertEquals("The original OEM drawable must still invalidate its view", 1, callback.invalidations);
        Runnable frame = () -> { };
        nativeSurface.scheduleSelf(frame, 123L);
        assertSame(frame, callback.frame);
        nativeSurface.unscheduleSelf(frame);
        assertNull(callback.frame);
    }

    private static final class AnimationCallback implements Drawable.Callback {
        int invalidations;
        Runnable frame;
        @Override public void invalidateDrawable(Drawable drawable) { invalidations++; }
        @Override public void scheduleDrawable(Drawable drawable, Runnable runnable, long when) { frame = runnable; }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable runnable) { frame = null; }
    }

    private void synchronize(QSTileItemView tile) {
        ControlCenterMaterials.registerGridTileShape(tile,
                ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                        .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        assertTrue(ControlCenterMaterials.syncTileView(tile,
                ControlCenterConfig.defaults().withEnabled(true)));
    }

    private ImageView image(QSTileItemView tile, LayerDrawable layers) {
        var icon = new ImageView(tile.getContext());
        icon.setScaleType(ImageView.ScaleType.FIT_XY);
        icon.setImageDrawable(layers);
        tile.addView(icon, new FrameLayout.LayoutParams(100, 100));
        tile.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        tile.layout(0, 0, 100, 100);
        return icon;
    }

    private Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    public static class QSTileItemView extends FrameLayout {
        final TileState state = new TileState();
        QSTileItemView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class TileState { int state = 2; }
    public static class QSCardItemView extends FrameLayout {
        QSCardItemView() { super(RuntimeEnvironment.getApplication()); }
    }
}
