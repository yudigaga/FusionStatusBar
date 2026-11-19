package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
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
public class ControlCenterFallbackOpacityRegressionTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void nativeTileWithoutImageViewKeepsZeroAndManualOpacityWithoutInventingAnUnknownAutomaticBody() {
        Context context = RuntimeEnvironment.getApplication();
        for (ControlCenterSurface style : ControlCenterSurface.values()) {
            QSTileItemView tile = new QSTileItemView(context);
            ControlCenterRuntimeGrid.GridView grid = grid(context, tile);
            try {
                grid.applyMaterials(settings(0).withSurface(style));
                layout(grid);
                assertPixels(grid, tile, 0);
                for (int strength : new int[] {40, 100, 0}) {
                    ControlCenterConfig changed = settings(strength).withSurface(style);
                    ControlCenterMaterials.refresh(changed);
                    grid.updateDecorations(changed);
                    assertPixels(grid, tile, style == ControlCenterSurface.CLEAR || style == ControlCenterSurface.HIGHLIGHT_GLASS
                            ? 0 : Math.round(255f * strength / 100));
                }
            } finally { grid.releaseMaterials(); }
        }
    }

    @Test public void unknownAutomaticNoImageTileRestoresLatestOemRequestsWithoutInventingCanvasFill() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        QSTileItemView tile = new QSTileItemView(context);
        Drawable original = tile.getBackground();
        ControlCenterConfig nativeStyle = ControlCenterConfig.defaults().withEnabled(true);
        record(tile, "setMiViewBlurMode", 1, nativeStyle);
        record(tile, "setMiBackgroundBlurMode", 2, nativeStyle);
        record(tile, "setMiBackgroundBlurRadius", 180, nativeStyle);
        ControlCenterRuntimeGrid.GridView grid = grid(context, tile);
        ControlCenterConfig zero = settings(0).withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        try {
            grid.applyMaterials(zero); layout(grid); assertPixels(grid, tile, 0);
            assertEquals(0, tile.viewMode); assertEquals(0, tile.backgroundMode); assertEquals(0, tile.radius);
            record(tile, "setMiViewBlurMode", 2, zero);
            record(tile, "setMiBackgroundBlurMode", 3, zero);
            record(tile, "setMiBackgroundBlurRadius", 240, zero);
            for (int strength : new int[] {40, 100}) {
                ControlCenterConfig changed = settings(strength).withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
                ControlCenterMaterials.refresh(changed); grid.updateDecorations(changed); assertPixels(grid, tile, 0);
                assertSame("UNKNOWN returns ownership to the original transparent host background", original, tile.getBackground());
                assertEquals("The latest OEM view-mode request replays independently of Canvas pixels", 2, tile.viewMode);
                assertEquals(3, tile.backgroundMode); assertEquals(240, tile.radius);
            }
            ControlCenterConfig disabled = zero.withEnabled(false);
            ControlCenterMaterials.refresh(disabled); grid.updateDecorations(disabled); assertPixels(grid, tile, 0);
            assertSame(original, tile.getBackground());
            assertEquals(2, tile.viewMode); assertEquals(3, tile.backgroundMode); assertEquals(240, tile.radius);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void retainedCustomFallbackTracksMaterialOnlyChangesAndRestoresPositiveStrength() {
        Context context = RuntimeEnvironment.getApplication();
        CustomTileView tile = new CustomTileView(context);
        Drawable original = tile.getBackground();
        ControlCenterRuntimeGrid.GridView grid = grid(context, tile);
        try {
            grid.applyMaterials(settings(100));
            layout(grid);
            assertPixels(grid, tile, 255);
            for (int strength : new int[] {0, 40, 100, 0}) {
                ControlCenterConfig changed = settings(strength);
                ControlCenterMaterials.refresh(changed);
                grid.updateDecorations(changed);
                assertPixels(grid, tile, Math.round(255f * strength / 100));
            }
            Drawable fallback = tile.getBackground();
            Drawable.Callback callback = fallback.getCallback();
            Invalidations invalidations = new Invalidations();
            fallback.setCallback(invalidations);
            try {
                for (int frame = 0; frame < 4; frame++) assertPixels(grid, tile, 0);
                assertEquals("Stable frames must reuse the fallback without requesting redraws", 0,
                        invalidations.count);
            } finally { fallback.setCallback(callback); }

            ControlCenterConfig transparent = settings(100).withSurface(ControlCenterSurface.CLEAR);
            ControlCenterMaterials.refresh(transparent);
            grid.updateDecorations(transparent);
            assertPixels(grid, tile, 0);

            ControlCenterConfig disabled = settings(100).withEnabled(false);
            ControlCenterMaterials.refresh(disabled);
            grid.updateDecorations(disabled);
            assertPixels(grid, tile, 0);
            assertSame("Disabling the override must restore the original transparent background",
                    original, tile.getBackground());
        } finally { grid.releaseMaterials(); }
    }

    private static ControlCenterConfig settings(int strength) {
        return ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.LIGHT).withBlur(0, strength, strength);
    }

    private static void record(View owner, String operation, int requested, ControlCenterConfig config) throws Exception {
        Object[] arguments = ControlCenterMaterials.nativeArguments(owner, operation, new Object[] {requested}, config, false);
        ControlCenterMaterials.invoke(owner, operation, arguments);
    }

    private static ControlCenterRuntimeGrid.GridView grid(Context context, View tile) {
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 1);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("custom.fallback", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
        return grid;
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void assertPixels(ControlCenterRuntimeGrid.GridView grid, View tile, int expectedAlpha) {
        Bitmap pixels = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            grid.draw(new Canvas(pixels));
            int centerX = tile.getLeft() + tile.getWidth() / 2;
            int bodyY = tile.getTop() + Math.max(8, tile.getHeight() / 6);
            assertEquals("The fallback body must follow the configured tile strength", expectedAlpha,
                    Color.alpha(pixels.getPixel(centerX, bodyY)));
            assertEquals("Changing only the surface strength must preserve the custom glyph", Color.BLUE,
                    pixels.getPixel(centerX, tile.getTop() + tile.getHeight() / 2));
        } finally { pixels.recycle(); }
    }

    /** A tile with state and a directly drawn glyph, so no ImageView owns its surface. */
    public static class CustomTileView extends FrameLayout {
        public int state = 2;
        CustomTileView(Context context) {
            super(context);
            // This tile owns a transparent background; no full-slot backing is needed.
            setBackgroundColor(Color.TRANSPARENT);
            addView(new View(context) {
                private final Paint glyph = new Paint();
                @Override protected void onDraw(Canvas canvas) {
                    glyph.setColor(Color.BLUE);
                    canvas.drawRect(getWidth() * .4f, getHeight() * .4f,
                            getWidth() * .6f, getHeight() * .6f, glyph);
                }
            }, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    public static final class QSTileItemView extends CustomTileView {
        int viewMode, backgroundMode, radius;
        QSTileItemView(Context context) { super(context); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
    }

    private static final class Invalidations implements Drawable.Callback {
        int count;
        @Override public void invalidateDrawable(Drawable drawable) { count++; }
        @Override public void scheduleDrawable(Drawable drawable, Runnable task, long when) { }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable task) { }
    }
}
