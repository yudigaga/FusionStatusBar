package com.xtjm.fusionstatusbar;

import android.app.Activity;
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
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterTileShapeIntegrationTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void presetRoundedTileHasOneStatefulSurfaceAcrossTheWholePlacement() {
        for (boolean glyphOnly : new boolean[] {false, true}) {
            Fixture f = new Fixture(ControlCenterCardStyle.ROUNDED, glyphOnly);
            for (int state : new int[] {2, 1, 2}) {
                f.tile.state.state = state;
                Bitmap pixels = f.draw();
                int edge = pixels.getPixel(f.tile.getLeft() + f.tile.getWidth() / 2, 5);
                int inner = pixels.getPixel(f.tile.getLeft() + f.tile.getWidth() / 2, 20);
                assertEquals("The placement and inner OEM surface must share one state color", edge, inner);
                assertEquals(state == 2 ? 255 : 192, Color.alpha(edge));
                assertEquals("Glyph size and color are unchanged", Color.BLUE,
                        pixels.getPixel(f.tile.getLeft() + f.image.getLeft() + 34,
                                f.image.getTop() + 34));
                pixels.recycle();
            }
        }
    }

    @Test public void everyNonCircularPresetOwnsTheInnerBackgroundAndRestoresItOnRelease() {
        for (ControlCenterCardStyle style : new ControlCenterCardStyle[] {
                ControlCenterCardStyle.DEFAULT, ControlCenterCardStyle.SUBTLE,
                ControlCenterCardStyle.CLASSIC, ControlCenterCardStyle.SOFT,
                ControlCenterCardStyle.ROUNDED, ControlCenterCardStyle.CAPSULE}) {
            Fixture f = new Fixture(style, false);
            f.draw().recycle();
            Bitmap content = Bitmap.createBitmap(68, 68, Bitmap.Config.ARGB_8888);
            f.image.draw(new Canvas(content));
            assertEquals("The OEM image must not keep an independent backing circle for " + style.name(),
                    0, Color.alpha(content.getPixel(34, 10)));
            assertEquals(Color.BLUE, content.getPixel(34, 34));
            f.grid.releaseMaterials();
            assertSame(f.nativeSurface, f.layers.getDrawable(0));
            content.recycle();
        }
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 14, 28, 0, 1);
        final QSTileItemView tile = new QSTileItemView(activity);
        final ImageView image = new ImageView(activity);
        final GradientDrawable nativeSurface = new GradientDrawable();
        final LayerDrawable layers;

        Fixture(ControlCenterCardStyle style, boolean glyphOnly) {
            nativeSurface.setShape(GradientDrawable.OVAL);
            nativeSurface.setColor(0xffbcbec2);
            nativeSurface.setSize(68, 68);
            layers = new LayerDrawable(glyphOnly ? new Drawable[] {new ColorDrawable(Color.BLUE)}
                    : new Drawable[] {nativeSurface, new ColorDrawable(Color.BLUE)});
            int glyph = layers.getNumberOfLayers() - 1;
            layers.setLayerInset(glyph, 24, 24, 24, 24);
            image.setImageDrawable(layers);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(68, 68, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            iconParams.topMargin = 9;
            tile.addView(image, iconParams);
            grid.addTile(tile, style.apply(ControlCenterLayoutPlan.Item.tile("custom_5g", 0, 0, 1, 1)), 0);
            grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.SOFT_GLASS));
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
            grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        }

        Bitmap draw() {
            Bitmap bitmap = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
            grid.draw(new Canvas(bitmap));
            return bitmap;
        }
    }
    static final class QSTileItemView extends FrameLayout {
        final State state = new State();
        QSTileItemView(Activity activity) { super(activity); }
    }
    static final class State { int state = 2; }
}
