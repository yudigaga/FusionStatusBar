package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterHighlightGlassNativePathTest {
    @Test public void smoothOemPathSeparatesTheShadeFromTheOuterHighlight() {
        View surface = new View(RuntimeEnvironment.getApplication());
        surface.setBackground(new SmoothRoundDrawable());
        surface.layout(0, 0, 160, 100);
        surface.getBackground().setBounds(0, 0, 160, 100);
        assertInsetBevel(surface);
    }

    @Test public void nativeOutlinePathSeparatesTheShadeWithoutReplacingItsCorners() {
        View surface = new View(RuntimeEnvironment.getApplication());
        surface.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setPath(nativeShape(new RectF(0, 0, view.getWidth(), view.getHeight())));
            }
        });
        surface.layout(0, 0, 160, 100);
        assertInsetBevel(surface);
    }

    private static void assertInsetBevel(View surface) {
        Paint rim = stroke(Color.WHITE, 2f), inner = stroke(0xff222529, 1f);
        ControlCenterSurfaceShape shape = new ControlCenterSurfaceShape();
        Bitmap withoutShade = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888);
        Bitmap withShade = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888);
        try {
            assertTrue(shape.resolve(surface, rim.getStrokeWidth() / 2f));
            shape.draw(new Canvas(withoutShade), rim);
            Canvas canvas = new Canvas(withShade);
            shape.draw(canvas, rim);
            assertTrue(shape.resolve(surface, rim.getStrokeWidth() + inner.getStrokeWidth() / 2f));
            shape.draw(canvas, inner, rim.getStrokeWidth());

            assertEquals("The legacy native rim still starts at the exact OEM edge",
                    Color.WHITE, withoutShade.getPixel(80, 0));
            assertEquals("The legacy rim retains its original thickness",
                    Color.TRANSPARENT, withoutShade.getPixel(80, 2));
            assertEquals("The inset shade must preserve the bright outer edge",
                    withoutShade.getPixel(80, 0), withShade.getPixel(80, 0));
            assertEquals("The shade must preserve the full outer highlight band",
                    withoutShade.getPixel(80, 1), withShade.getPixel(80, 1));
            assertEquals("The shade occupies a separate inner band", 0xff222529, withShade.getPixel(80, 2));
            assertEquals("The bevel does not fill the surface body", Color.TRANSPARENT,
                    withShade.getPixel(80, 50));
            assertEquals("The exact native rounded corner remains clear", Color.TRANSPARENT,
                    withShade.getPixel(1, 1));
            assertEquals("The native square bottom corner must remain square", Color.WHITE,
                    withShade.getPixel(0, 99));
            assertEquals("Drawing must restore the caller's rim width", 2f, rim.getStrokeWidth(), 0f);
            assertEquals("Drawing must restore the caller's inner width", 1f, inner.getStrokeWidth(), 0f);
        } finally {
            withoutShade.recycle();
            withShade.recycle();
        }
    }

    private static Paint stroke(int color, float width) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        return paint;
    }

    private static Path nativeShape(RectF bounds) {
        Path path = new Path();
        path.addRoundRect(bounds, new float[] {24, 24, 24, 24, 0, 0, 0, 0}, Path.Direction.CW);
        return path;
    }

    private static final class SmoothRoundDrawable extends Drawable {
        private final Path path = new Path();
        @Override protected void onBoundsChange(Rect bounds) { path.set(nativeShape(new RectF(bounds))); }
        @Override public void draw(Canvas canvas) { }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
