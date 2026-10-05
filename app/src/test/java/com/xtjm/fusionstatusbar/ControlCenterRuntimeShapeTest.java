package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterRuntimeShapeTest {
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void nativeCircleMustNotAcquireASecondLargerHalo() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        QSTileItemView tile = new QSTileItemView(activity);
        View surface = new View(activity);
        GradientDrawable nativeSurface = new GradientDrawable();
        nativeSurface.setShape(GradientDrawable.OVAL);
        nativeSurface.setColor(android.graphics.Color.BLACK);
        surface.setBackground(nativeSurface);
        tile.addView(surface, new android.widget.FrameLayout.LayoutParams(48, 48, android.view.Gravity.CENTER));
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
        layout(grid);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(grid.getWidth(), grid.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        grid.draw(new Canvas(bitmap));
        assertEquals("No translucent ring outside the native circular surface", 0,
                android.graphics.Color.alpha(bitmap.getPixel(tile.getLeft() + 8, tile.getHeight() / 2)));
        assertEquals(1, grid.getChildCount());
        assertSame(nativeSurface, surface.getBackground());
        bitmap.recycle();
    }

    static final class QSTileItemView extends android.widget.FrameLayout {
        final TileState state = new TileState();
        QSTileItemView(Activity activity) { super(activity); }
    }

    static final class TileState { int state; }

    @Test public void nativeCardAndComponentsKeepTheirOwnStatefulSurface() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        QSCardItemView card = new QSCardItemView(activity);
        android.graphics.drawable.ColorDrawable material = new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE);
        card.setBackground(material);
        View media = new View(activity);
        grid.addTile(card, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1), 0);
        grid.addTile(media, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA, 0, 1, 2, 2), -1);
        layout(grid);
        assertEquals(2, grid.getChildCount());
        assertSame(material, card.getBackground());
        assertTrue(card.getClipToOutline());
        assertTrue(media.getClipToOutline());
    }

    static final class QSCardItemView extends android.widget.FrameLayout {
        QSCardItemView(Activity activity) { super(activity); }
    }

    @Test
    public void tileBoundAsFirstMemberStillUsesConfiguredRectangleAndRadius() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        View wifi = new View(activity);
        grid.addTile(wifi, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 7), 0);
        layout(grid);

        assertEquals(2, grid.getChildCount());
        GradientDrawable surface = (GradientDrawable) grid.getChildAt(0).getBackground();
        assertEquals(GradientDrawable.RECTANGLE, surface.getShape());
        assertEquals(7 * density(activity), surface.getCornerRadius(), 0.01f);
        assertTrue(wifi.getClipToOutline());
        assertEquals(surface.getCornerRadius(), outline(wifi).getRadius(), 0.01f);
    }

    @Test
    public void defaultRectangleRadiusUsesGlobalSettingForSurfaceAndClip() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        View wifi = new View(activity);
        grid.addTile(wifi, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1), 0);
        layout(grid);

        GradientDrawable surface = (GradientDrawable) grid.getChildAt(0).getBackground();
        assertEquals(12 * density(activity), surface.getCornerRadius(), 0.01f);
        assertEquals(surface.getCornerRadius(), outline(wifi).getRadius(), 0.01f);
    }

    @Test
    public void circleAndCapsuleHaveVisibleConfiguredSurfaces() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        View bluetooth = new View(activity);
        View capsule = new View(activity);
        grid.addTile(bluetooth, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
        grid.addTile(capsule, ControlCenterLayoutPlan.Item.tile("wifi", 1, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), 0);
        layout(grid);

        assertEquals(GradientDrawable.OVAL,
                ((GradientDrawable) grid.getChildAt(0).getBackground()).getShape());
        assertTrue(bluetooth.getClipToOutline());
        assertEquals(Math.min(capsule.getWidth(), capsule.getHeight()) / 2f,
                outline(capsule).getRadius(), 0.01f);
    }

    @Test
    public void capsuleBackingUsesTheSameFiniteRadiusAsItsNativeTileClip() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        QSTileItemView tile = new QSTileItemView(activity);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 2)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), 0);
        layout(grid);

        View backing = grid.getChildAt(0);
        float expected = Math.min(backing.getWidth(), backing.getHeight()) / 2f;
        GradientDrawable background = (GradientDrawable) backing.getBackground();
        assertEquals(expected, background.getCornerRadius(), 0.01f);
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(grid.getWidth(),
                grid.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        grid.draw(new Canvas(image));
        assertEquals(expected, outline(backing).getRadius(), 0.01f);
        assertEquals(expected, outline(tile).getRadius(), 0.01f);
        image.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void tileBorderStaysAboveNativeContentsAfterAnOemBackgroundUpdate() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                activity, 4, 8, 12, 0, 2);
        FrameLayout tile = new FrameLayout(activity);
        View nativeContent = new View(activity);
        nativeContent.setBackgroundColor(0xff353840);
        tile.addView(nativeContent, new FrameLayout.LayoutParams(138, 138, Gravity.CENTER));
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 2)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), 0);
        activity.setContentView(grid);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2);
        grid.applyMaterials(settings);
        layout(grid);
        tile.setClipToOutline(false);

        android.graphics.Bitmap bordered = draw(grid);
        int children = grid.getChildCount();
        ControlCenterConfig noBorder = settings.withGlobalBorder(false, 2);
        ControlCenterMaterials.refresh(noBorder);
        grid.updateDecorations(noBorder);
        android.graphics.Bitmap plain = draw(grid);
        assertEquals(children, grid.getChildCount());
        assertTrue("The border remains visible at the top",
                differentNear(bordered, plain, 72, 1));
        assertTrue("The border must continue through the rounded corner above native content",
                differentNear(bordered, plain, 21, 22));
        int[] taps = {0};
        tile.setOnClickListener(ignored -> taps[0]++);
        assertTrue(tile.dispatchTouchEvent(MotionEvent.obtain(0, 0,
                MotionEvent.ACTION_DOWN, 72, 89, 0)));
        tile.dispatchTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 72, 89, 0));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1, taps[0]);
        grid.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 72, 89, 0));
        grid.dispatchTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 72, 89, 0));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("The decoration must not intercept tile taps", 2, taps[0]);
        bordered.recycle();
        plain.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void tileBorderDrawsAfterAnElevatedNativeHolderWithoutAddingElevation() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                activity, 4, 8, 12, 0, 2);
        FrameLayout tile = new FrameLayout(activity);
        View nativeContent = new View(activity);
        nativeContent.setBackgroundColor(0xff353840);
        tile.addView(nativeContent, new FrameLayout.LayoutParams(138, 138, Gravity.CENTER));
        tile.setZ(24f);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("5g", 0, 0, 1, 2)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), 0);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2);
        grid.applyMaterials(settings);
        layout(grid);

        android.graphics.Bitmap image = draw(grid);
        View decoration = grid.getChildAt(grid.getChildCount() - 1);
        assertEquals("Ordering must not create an elevated transparent shadow plane",
                0f, decoration.getZ(), 0f);
        assertTrue("The rounded border remains visible where native content overlaps",
                Color.alpha(image.getPixel(72, 50)) > 0);
        decoration.setVisibility(View.INVISIBLE);
        android.graphics.Bitmap plain = draw(grid);
        assertTrue("The border is drawn over the elevated native content",
                differentNear(image, plain, 21, 22));
        image.recycle();
        plain.recycle();
    }

    @Test
    public void pairDecorationSpansBothNativeHoldersOnlyOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
        var pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 1, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0);
        grid.addTile(new View(activity), pair, 0);
        grid.addTile(new View(activity), pair, 1);
        grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2));
        layout(grid);

        assertEquals(4, grid.getChildCount());
        View backing = grid.getChildAt(0);
        View decoration = grid.getChildAt(3);
        assertEquals(backing.getLeft(), decoration.getLeft());
        assertEquals(backing.getTop(), decoration.getTop());
        assertEquals(backing.getRight(), decoration.getRight());
        assertEquals(backing.getBottom(), decoration.getBottom());
        assertFalse(decoration.getClipToOutline());
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void circleDecorationUsesACenteredSquareInANonSquareGridCell() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var grid = grid(activity, 12);
        grid.addTile(new FrameLayout(activity), ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
        grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(true, 2));
        layout(grid);
        View decoration = grid.getChildAt(grid.getChildCount() - 1);
        assertEquals("A circle must use a square display bounds", decoration.getWidth(), decoration.getHeight());
        var image = draw(grid);
        assertEquals("A circle must not acquire square corner frames", 0,
                Color.alpha(image.getPixel(1, 1)));
        assertTrue(Color.alpha(image.getPixel(decoration.getLeft() + decoration.getWidth() / 2,
                decoration.getTop() + decoration.getHeight() / 2)) > 0);
        image.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void nativeCardBackgroundIsClippedToItsRoundedSlot() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var grid = grid(activity, 12);
        QSCardItemView card = new QSCardItemView(activity);
        card.setBackgroundColor(0xff9fa1a4);
        grid.addTile(card, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1), 0);
        layout(grid);

        var image = draw(grid);
        assertEquals("OEM rectangular material must not leak through the rounded corner",
                0, Color.alpha(image.getPixel(card.getLeft(), card.getTop())));
        assertEquals(0xff9fa1a4, image.getPixel(card.getLeft() + card.getWidth() / 2,
                card.getTop() + card.getHeight() / 2));
        image.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void nativeCardBackgroundIsClippedEvenWhenOemDisablesOutlineClipping() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var grid = grid(activity, 12);
        QSCardItemView card = new QSCardItemView(activity);
        card.setBackgroundColor(0xff9fa1a4);
        grid.addTile(card, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1), 0);
        layout(grid);
        card.setClipToOutline(false);

        var image = draw(grid);
        assertEquals("Grid path clipping must contain an OEM rectangle at the rounded corner",
                0, Color.alpha(image.getPixel(card.getLeft(), card.getTop())));
        image.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void activeCircleTileGetsTheSameLightFillAsSliderTrack() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var grid = grid(activity, 12);
        QSTileItemView tile = new QSTileItemView(activity);
        tile.state.state = 2;
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
        grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2));
        layout(grid);

        var image = draw(grid);
        View decoration = grid.getChildAt(grid.getChildCount() - 1);
        int center = image.getPixel(decoration.getLeft() + decoration.getWidth() / 2,
                decoration.getTop() + decoration.getHeight() / 2);
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, center);
        image.recycle();

        tile.state.state = 1;
        tile.invalidate();
        var inactive = draw(grid);
        assertNotEquals("Disabling the tile restores the native surface",
                ControlCenterCardView.ACTIVE_SURFACE,
                inactive.getPixel(decoration.getLeft() + decoration.getWidth() / 2,
                        decoration.getTop() + decoration.getHeight() / 2));
        assertNull(tile.getBackground());
        inactive.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void activeOemLayerDrawableUsesCircleSurfaceBehindGlyph() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSTileItemView tile = new QSTileItemView(activity);
        tile.state.state = 2;
        ImageView image = new ImageView(activity);
        ColorDrawable nativeBackground = new ColorDrawable(0xff606166);
        ColorDrawable glyph = new ColorDrawable(0xffffffff);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[] {
                nativeBackground, glyph
        });
        layers.setLayerInset(1, 32, 32, 32, 32);
        image.setImageDrawable(layers);
        tile.addView(image, new FrameLayout.LayoutParams(100, 100));
        tile.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        tile.layout(0, 0, 100, 100);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
        ControlCenterMaterials.registerGridTileShape(tile, item);
        assertTrue(ControlCenterMaterials.syncTileView(tile,
                ControlCenterConfig.defaults().withEnabled(true)));
        android.graphics.Bitmap imagePixels = android.graphics.Bitmap.createBitmap(100, 100,
                android.graphics.Bitmap.Config.ARGB_8888);
        image.draw(new Canvas(imagePixels));
        assertEquals("The rectangular OEM layer must be clipped at the circle corner", 0,
                Color.alpha(imagePixels.getPixel(0, 0)));
        assertEquals("The active fill is painted below the fixed-size glyph",
                ControlCenterCardView.ACTIVE_SURFACE, imagePixels.getPixel(20, 50));
        assertTrue("The glyph remains visible after the surface replacement",
                Color.alpha(imagePixels.getPixel(50, 32)) > 0);
        imagePixels.recycle();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void gridDecorationOwnershipPreservesComponentBorders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 8, 12, 0, 2);
        var media = new ControlCenterGlobalBorderTest.QSCardItemView(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(12);
        media.setBackground(background);
        grid.addTile(media, ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.MEDIA, 0, 0, 2, 2), -1);
        grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(true, 2));
        layout(grid);
        var image = draw(grid);
        assertNotEquals(0xff353840, image.getPixel(1, media.getHeight() / 2));
        image.recycle();
    }

    @Test
    public void pairChildrenUseOneSharedClipAtTheWholePairPosition() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (ControlCenterLayoutPlan.Shape shape : ControlCenterLayoutPlan.Shape.values()) {
            ControlCenterRuntimeGrid.GridView grid = grid(activity, 12);
            var pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                    ControlCenterLayoutPlan.Direction.HORIZONTAL, 1, 0, 2, 1)
                    .withShape(shape, 18);
            View first = new View(activity);
            View second = new View(activity);
            grid.addTile(first, pair, 0);
            grid.addTile(second, pair, 1);
            layout(grid);
            assertEquals(3, grid.getChildCount()); // one shared surface, two holders
            View surface = grid.getChildAt(0);
            RecordingCanvas canvas = new RecordingCanvas();
            grid.drawChild(canvas, first, 0);
            assertEquals(1, canvas.clips);
            RectF firstClip = new RectF();
            canvas.path.computeBounds(firstClip, true);
            assertEquals(new RectF(surface.getLeft(), surface.getTop(),
                    surface.getRight(), surface.getBottom()), firstClip);
            assertTrue(firstClip.width() > first.getWidth());
            grid.drawChild(canvas, second, 0);
            assertEquals(2, canvas.clips);
            RectF secondClip = new RectF();
            canvas.path.computeBounds(secondClip, true);
            assertEquals(firstClip, secondClip);
        }
    }

    private static final class RecordingCanvas extends Canvas {
        int clips;
        Path path;

        @Override
        public boolean clipPath(Path value) {
            clips++;
            path = new Path(value);
            return true;
        }
    }

    private static ControlCenterRuntimeGrid.GridView grid(Activity activity, int radius) {
        return new ControlCenterRuntimeGrid.GridView(activity, 4, 8, radius, 0, 1);
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static Outline outline(View view) {
        Outline outline = new Outline();
        view.getOutlineProvider().getOutline(view, outline);
        return outline;
    }

    private static float density(Activity activity) {
        return activity.getResources().getDisplayMetrics().density;
    }

    private static android.graphics.Bitmap draw(View view) {
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(view.getWidth(),
                view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private static boolean differentNear(android.graphics.Bitmap first,
            android.graphics.Bitmap second, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (x + dx >= 0 && y + dy >= 0
                        && first.getPixel(x + dx, y + dy) != second.getPixel(x + dx, y + dy))
                    return true;
            }
        }
        return false;
    }
}
