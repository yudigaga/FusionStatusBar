package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterPublishedMaterialTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    private ControlCenterConfig glass() throws Exception {
        return ControlCenterImportedConfigRegressionTest.loadedSettings();
    }

    @Test public void blurOnlyCapsuleKeepsNativeRectBlendAfterRestart() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var card = new QSCardItemView(activity);
        var grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 14, 28, 0, 1);
        var item = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0);
        var colors = new ArrayList<>(List.of(new Point(0xff9fa1a4, 3)));
        for (ControlCenterSurface style : new ControlCenterSurface[] {
                ControlCenterSurface.DEFAULT, ControlCenterSurface.SOFT_GLASS}) {
            var settings = glass().withSurface(style);
            Object[] mode = ControlCenterMaterials.nativeArguments(card, "setMiViewBlurMode",
                    new Object[] {1}, settings, false);
            card.setMiViewBlurMode((Integer) mode[0]);
            ControlCenterMaterials.updateNativeSurface(card, settings, false);
            Object[] blend = ControlCenterMaterials.nativeArguments(card, "setMiBackgroundBlendColors",
                    new Object[] {colors}, settings, false);
            card.setMiBackgroundBlendColors((ArrayList<Point>) blend[0]);
        }
        grid.addTile(card, item, 0);
        grid.applyMaterials(glass());
        layout(grid, 400, 200);
        draw(grid).recycle();
        assertNull("OEM blur cards deliberately have no custom-drawn background", card.getBackground());
        assertEquals("DRAW_RECT_AND_BLEND must not turn into DRAW_CUSTOM_AND_BLEND without a mask",
                1, card.viewMode);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, card.colors.get(0).x);
    }

    @Test public void componentBackgroundRecreatedAfterAttachReceivesCurrentMaterial() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 14, 28, 0, 2);
        var panel = new MediaPlayerPanel(activity);
        panel.setBackgroundColor(0xff999999);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.MEDIA, 0, 0, 2, 2), -1);
        grid.applyMaterials(glass());
        layout(grid, 400, 300);
        ColorDrawable afterAttach = new ColorDrawable(0xffbbbbbb);
        panel.setBackground(afterAttach);
        Bitmap pixels = draw(grid);
        assertEquals("OEM attachment may replace the background after the grid was bound",
                ControlCenterSurface.SOFT_GLASS.color, pixels.getPixel(panel.getLeft() + panel.getWidth() / 2,
                        panel.getTop() + panel.getHeight() / 2));
        assertEquals("Retain the original fill for native restoration", 0xffbbbbbb, afterAttach.getColor());
        pixels.recycle();
    }

    @Test public void globalCircleTintDoesNotExposeTheOemPolygonUnderneath() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var tile = new QSTileItemView(activity);
        var image = new ImageView(activity);
        var layers = new LayerDrawable(new Drawable[] {new PolygonBackground(), new ColorDrawable(Color.BLUE)});
        layers.setLayerInset(1, 42, 42, 42, 42);
        image.setImageDrawable(layers);
        tile.addView(image, new FrameLayout.LayoutParams(100, 100));
        layout(tile, 100, 100);
        ControlCenterMaterials.registerGridTileShape(tile,
                ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 1)
                        .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        ControlCenterMaterials.syncTileView(tile, glass());
        Bitmap pixels = draw(image);
        assertEquals("One translucent material must have the same opacity inside and outside the OEM polygon",
                Color.alpha(pixels.getPixel(50, 4)), Color.alpha(pixels.getPixel(50, 20)));
        assertEquals(192, Color.alpha(pixels.getPixel(50, 20)));
        pixels.recycle();
    }

    @Test public void nativeCustomForegroundModeKeepsGlyphColorsAndDoesNotCreateBackdrop() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        MediaPlayerPanel panel = new MediaPlayerPanel(activity);
        NativeGlyph glyph = new NativeGlyph(activity);
        panel.addView(glyph);
        Object[] mode = ControlCenterMaterials.nativeArguments(glyph, "setMiViewBlurMode",
                new Object[] {3}, glass(), false);
        glyph.setMiViewBlurMode((Integer) mode[0]);
        ControlCenterMaterials.updateNativeSurface(glyph, glass(), false);
        ArrayList<Point> colors = new ArrayList<>(List.of(new Point(Color.WHITE, 3)));
        Object[] original = {colors};
        Object[] adjusted = ControlCenterMaterials.nativeArguments(glyph,
                "setMiBackgroundBlendColors", original, glass(), false);
        assertSame("DRAW_CUSTOM_INSTEAD is a foreground blend, not a panel background", original, adjusted);
        assertEquals(3, glyph.viewMode);
        assertEquals(0, glyph.backgroundMode);
    }

    @Test public void aReusedCardWithWrappedBackgroundReceivesMaterialOnRebind() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView card = new QSCardItemView(activity);
        ColorDrawable original = new ColorDrawable(Color.WHITE);
        card.setBackground(original);
        ControlCenterMaterials.syncCardView(card, ControlCenterLayoutPlan.Item.tile("cell", 2, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0));
        ControlCenterMaterials.registerRoot(card, glass());
        assertEquals("The clip wrapper must not hide the real background from registration",
                ControlCenterSurface.SOFT_GLASS.color, original.getColor());
    }

    @Test public void twoAnimatedOemLayersProduceOnlyOneTranslucentSurface() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSTileItemView tile = new QSTileItemView(activity);
        ImageView image = new ImageView(activity);
        ColorDrawable inactive = new ColorDrawable(Color.WHITE);
        ColorDrawable active = new ColorDrawable(Color.GRAY);
        inactive.setAlpha(0);
        active.setAlpha(255);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {inactive, active, new ColorDrawable(Color.BLUE)});
        layers.setLayerInset(2, 42, 42, 42, 42);
        image.setImageDrawable(layers);
        tile.addView(image, new FrameLayout.LayoutParams(100, 100));
        layout(tile, 100, 100);
        ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("night", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
        ControlCenterMaterials.syncTileView(tile, glass());
        Bitmap bitmap = draw(image);
        assertEquals("Native animation alpha must not remove or double the selected material", 192,
                Color.alpha(bitmap.getPixel(50, 20)));
        bitmap.recycle();
        ControlCenterMaterials.syncTileView(tile, glass().withSurface(ControlCenterSurface.CLEAR));
        bitmap = draw(image);
        assertEquals(0, Color.alpha(bitmap.getPixel(50, 20)));
        assertEquals(Color.BLUE, bitmap.getPixel(50, 50));
        bitmap.recycle();
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    public static class QSCardItemView extends FrameLayout {
        int viewMode = 1, backgroundMode = 0, radius;
        ArrayList<Point> colors = new ArrayList<>();
        QSCardItemView(Activity activity) { super(activity); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; }
    }

    public static class MediaPlayerPanel extends FrameLayout {
        MediaPlayerPanel(Activity activity) { super(activity); }
    }
    public static class NativeGlyph extends QSCardItemView {
        NativeGlyph(Activity activity) { super(activity); }
    }
    public static class QSTileItemView extends FrameLayout {
        final State state = new State();
        QSTileItemView(Activity activity) { super(activity); }
    }
    public static class State { int state = 1; }

    private static final class PolygonBackground extends Drawable {
        final Paint paint = new Paint();
        final Path path = new Path();
        PolygonBackground() { paint.setColor(Color.WHITE); }
        @Override public void draw(Canvas canvas) {
            path.reset();
            path.moveTo(30, 10); path.lineTo(70, 10); path.lineTo(90, 30); path.lineTo(90, 70);
            path.lineTo(70, 90); path.lineTo(30, 90); path.lineTo(10, 70); path.lineTo(10, 30); path.close();
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public int getAlpha() { return paint.getAlpha(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
