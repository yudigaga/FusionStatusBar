package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterTileHardwareTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void hardwareBitmapActuallyRendersRecordedPixels() throws Exception {
        RenderNode node = new RenderNode("hardware-rendering-probe");
        node.setPosition(0, 0, 24, 24);
        RecordingCanvas canvas = node.beginRecording(24, 24);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        canvas.drawRect(4, 4, 20, 20, paint);
        node.endRecording();
        Bitmap image = hardwareBitmap(node, 24, 24);
        assertEquals("The probe must execute recorded drawing, not return an empty bitmap",
                Color.BLUE, image.getPixel(12, 12));
        assertEquals(0, Color.alpha(image.getPixel(0, 0)));
        image.recycle();
    }

    @Test public void hardwareDecorationDoesNotOccludeNativeGlyphOrFillTileCorners() throws Exception {
        for (ControlCenterLayoutPlan.Shape shape : new ControlCenterLayoutPlan.Shape[] {
                ControlCenterLayoutPlan.Shape.CIRCLE,
                ControlCenterLayoutPlan.Shape.RECTANGLE,
                ControlCenterLayoutPlan.Shape.CAPSULE}) {
            Fixture fixture = new Fixture(shape);
            fixture.grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true)
                    .withGlobalBorder(true, 2));
            fixture.layout();
            View decoration = fixture.grid.getChildAt(fixture.grid.getChildCount() - 1);
            decoration.setVisibility(View.INVISIBLE);
            Bitmap nativeImage = fixture.drawHardware();
            int centerX = fixture.tile.getLeft() + fixture.frame.getLeft() + 37;
            int centerY = fixture.tile.getTop() + fixture.frame.getTop() + 37;
            assertEquals("The native fixture glyph must render for " + shape,
                    Color.BLUE, nativeImage.getPixel(centerX, centerY));
            decoration.setVisibility(View.VISIBLE);
            Bitmap decorated = fixture.drawHardware();
            assertEquals("Transparent decorations must leave the glyph unchanged for " + shape,
                    nativeImage.getPixel(centerX, centerY), decorated.getPixel(centerX, centerY));
            int cornerX = fixture.tile.getLeft() + fixture.frame.getLeft();
            int cornerY = fixture.tile.getTop() + fixture.frame.getTop();
            assertEquals("The transparent decoration must not fill its rectangular bounds for " + shape,
                    nativeImage.getPixel(cornerX, cornerY), decorated.getPixel(cornerX, cornerY));
            nativeImage.recycle();
            decorated.recycle();
        }
    }

    private static Bitmap hardwareBitmap(RenderNode node, int width, int height) throws Exception {
        Method method = HardwareRenderer.class.getDeclaredMethod("createHardwareBitmap",
                RenderNode.class, int.class, int.class);
        method.setAccessible(true);
        Bitmap hardware = (Bitmap) method.invoke(null, node, width, height);
        assertNotNull("HardwareRenderer must return a real bitmap", hardware);
        Bitmap readable = hardware.copy(Bitmap.Config.ARGB_8888, false);
        hardware.recycle();
        assertNotNull(readable);
        return readable;
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                activity, 4, 8, 12, 0, 2);
        final QSTileItemView tile = new QSTileItemView(activity);
        final FrameLayout frame = new FrameLayout(activity);

        Fixture(ControlCenterLayoutPlan.Shape shape) {
            tile.setClipChildren(false);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xff3a3f45);
            background.setSize(74, 74);
            LayerDrawable layers = new LayerDrawable(new Drawable[] {
                    background, new ColorDrawable(Color.BLUE)});
            layers.setLayerSize(1, 24, 24);
            layers.setLayerGravity(1, Gravity.CENTER);
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(layers);
            icon.setScaleType(ImageView.ScaleType.FIT_XY);
            frame.addView(icon, new FrameLayout.LayoutParams(74, 74));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    74, 74, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            params.topMargin = 12;
            tile.addView(frame, params);
            grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                    .withShape(shape, 0), 0);
            activity.setContentView(grid);
            layout();
        }

        void layout() {
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
            grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        }

        Bitmap drawHardware() throws Exception {
            RenderNode node = new RenderNode("control-center-grid");
            node.setPosition(0, 0, grid.getWidth(), grid.getHeight());
            Canvas canvas = node.beginRecording(grid.getWidth(), grid.getHeight());
            assertTrue(canvas.isHardwareAccelerated());
            grid.draw(canvas);
            node.endRecording();
            return hardwareBitmap(node, grid.getWidth(), grid.getHeight());
        }
    }

    static final class QSTileItemView extends FrameLayout {
        final TileState state = new TileState();
        QSTileItemView(Activity activity) { super(activity); }
    }
    static final class TileState { int state = 2; }
}
