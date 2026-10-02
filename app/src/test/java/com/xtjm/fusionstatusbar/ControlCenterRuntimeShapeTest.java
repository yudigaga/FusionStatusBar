package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

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
        QSTileItemView(Activity activity) { super(activity); }
    }

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
}
