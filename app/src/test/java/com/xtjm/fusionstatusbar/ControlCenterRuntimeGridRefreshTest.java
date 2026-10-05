package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterRuntimeGridRefreshTest {
    @Test public void materialRefreshSurvivesTheNextNativeTileDraw() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity);
        QSTileItemView tile = new QSTileItemView(activity);
        ImageView icon = new ImageView(activity);
        LayerDrawable drawable = new LayerDrawable(new Drawable[] {
                new ColorDrawable(0xff606166), new ColorDrawable(Color.WHITE)
        });
        drawable.setLayerInset(1, 28, 28, 28, 28);
        icon.setImageDrawable(drawable);
        tile.addView(icon, new FrameLayout.LayoutParams(80, 80, Gravity.CENTER));
        grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
        ControlCenterConfig original = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.DARK);
        grid.applyMaterials(original);
        layout(grid);
        int dark = surfacePixel(grid, tile, icon);

        ControlCenterConfig updated = original.withSurface(ControlCenterSurface.LIGHT);
        ControlCenterMaterials.refresh(updated);
        grid.updateDecorations(updated);
        int light = surfacePixel(grid, tile, icon);
        assertNotEquals("The next grid frame must use the newly selected material", dark, light);

        ControlCenterMaterials.syncTileView(tile, updated);
        Bitmap expected = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(expected));
        assertEquals("The grid must not overwrite the current material with its mount-time snapshot",
                expected.getPixel(20, 40), light);
        expected.recycle();
    }

    @Test public void pairBackingRemainsVisibleBehindBothTransparentNativeHolders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterRuntimeGrid.GridView grid = grid(activity);
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0);
        grid.addTile(new View(activity), pair, 0);
        grid.addTile(new View(activity), pair, 1);
        layout(grid);

        View backing = grid.getChildAt(0);
        Bitmap image = draw(grid);
        assertTrue("The shared pair material must not be clipped away by an empty path",
                Color.alpha(image.getPixel(backing.getLeft() + backing.getWidth() / 2,
                        backing.getTop() + backing.getHeight() / 2)) > 0);
        assertEquals("The shared backing still has rounded corners", 0,
                Color.alpha(image.getPixel(backing.getLeft(), backing.getTop())));
        image.recycle();
    }

    private static ControlCenterRuntimeGrid.GridView grid(Activity activity) {
        return new ControlCenterRuntimeGrid.GridView(activity, 4, 8, 12, 0, 1);
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static int surfacePixel(View grid, View tile, View icon) {
        Bitmap image = draw(grid);
        int color = image.getPixel(tile.getLeft() + icon.getLeft() + 20,
                tile.getTop() + icon.getTop() + 40);
        image.recycle();
        return color;
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    static final class QSTileItemView extends FrameLayout {
        final TileState state = new TileState();
        QSTileItemView(Activity activity) { super(activity); }
    }

    static final class TileState { int state = 1; }
}
