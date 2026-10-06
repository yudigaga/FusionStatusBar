package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterSliderFillPixelTest {
    @Test public void capsuleFillNeverCrossesTheInsetTrackAtAnyLevelOrAxis() throws Exception {
        for (boolean horizontal : new boolean[] {false, true}) {
            checkShape(horizontal, ControlCenterLayoutPlan.Shape.CAPSULE, 0);
        }
    }

    @Test public void rectangleFillFollowsTheConfiguredCornersAfterNativeRefresh() throws Exception {
        for (boolean horizontal : new boolean[] {false, true}) {
            checkShape(horizontal, ControlCenterLayoutPlan.Shape.RECTANGLE, 28);
        }
    }

    @Test public void offsetNativeFillUsesTrackCoordinatesInsteadOfItsOwnSmallerCapsule() throws Exception {
        ControlCenterSliderMaskTest.Holder holder = new ControlCenterSliderMaskTest.Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:volume", 0, 0, 1, 3)
                        .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0), 0f, 6f);
        layout.measure(200, 600);
        holder.layout(200, 600);
        holder.binding.progress.layout(8, 12, 192, 588);
        holder.slider.setProgress(100);
        Bitmap actual = rasterOutline(holder.binding.progress);
        assertEquals(0, Color.alpha(actual.getPixel(1, 1)));
        assertEquals(255, Color.alpha(actual.getPixel(92, 1)));
        Outline outline = new Outline();
        holder.binding.progress.getOutlineProvider().getOutline(holder.binding.progress, outline);
        java.lang.reflect.Field field = Outline.class.getDeclaredField("mPath");
        field.setAccessible(true);
        RectF bounds = new RectF();
        ((Path) field.get(outline)).computeBounds(bounds, true);
        assertEquals(new RectF(-2, -6, 186, 582), bounds);
        actual.recycle();
        layout.restore();
    }

    private void checkShape(boolean horizontal, ControlCenterLayoutPlan.Shape shape, int radius)
            throws Exception {
        ControlCenterSliderMaskTest.Holder holder = new ControlCenterSliderMaskTest.Holder();
        int width = horizontal ? 600 : 200, height = horizontal ? 200 : 600;
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.component(
                "control:brightness", 0, 0, horizontal ? 3 : 1, horizontal ? 1 : 3)
                .withShape(shape, radius);
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder, item, 0f, 6f);
        layout.measure(width, height);
        holder.layout(width, height);
        for (int level : new int[] {0, 5, 25, 50, 100}) {
            holder.slider.setProgress(level);
            holder.binding.progress.setOutlineProvider(holder.nativeProgressOutline);
            holder.binding.toggleSliderInner.getViewTreeObserver().dispatchOnPreDraw();
            Bitmap actual = rasterOutline(holder.binding.progress);
            Bitmap allowed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(allowed);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            float outerRadius = shape == ControlCenterLayoutPlan.Shape.CAPSULE ? 100f : radius;
            canvas.drawRoundRect(new RectF(6, 6, width - 6, height - 6),
                    outerRadius - 6, outerRadius - 6, paint);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (Color.alpha(allowed.getPixel(x, y)) == 0) {
                        // Path intersections and analytic round rects differ slightly at subpixel edges.
                        assertTrue("fill outside track at " + x + "," + y + " level=" + level
                                + " horizontal=" + horizontal, Color.alpha(actual.getPixel(x, y)) <= 16);
                    }
                }
            }
            int px = horizontal ? 10 : width / 2;
            int py = horizontal ? height / 2 : height - 10;
            assertEquals(level > 0 ? 255 : 0, Color.alpha(actual.getPixel(px, py)));
            actual.recycle();
            allowed.recycle();
        }
        layout.restore();
    }

    static Bitmap rasterOutline(View view) throws Exception {
        Outline outline = new Outline();
        view.getOutlineProvider().getOutline(view, outline);
        assertTrue("The configured outline must be hardware-clippable", outline.canClip());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        if (outline.isEmpty()) return bitmap;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        Canvas canvas = new Canvas(bitmap);
        Rect rect = new Rect();
        if (outline.getRect(rect)) {
            canvas.drawRoundRect(new RectF(rect), outline.getRadius(), outline.getRadius(), paint);
        } else {
            java.lang.reflect.Field field = Outline.class.getDeclaredField("mPath");
            field.setAccessible(true);
            canvas.drawPath((Path) field.get(outline), paint);
        }
        return bitmap;
    }
}
