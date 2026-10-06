package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterSliderMaskTest {
    @Test
    public void verticalTrackUsesPlacementMaskAndPreservesProgressAmount() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                item(1, 3).withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 24));
        layout.measure(200, 600);
        holder.layout(200, 600);
        holder.slider.setProgress(25);

        assertRoundRect(holder.binding.toggleSliderInner, new Rect(0, 0, 200, 600), 24f);
        assertRoundRect(holder.binding.progressBg, new Rect(0, 0, 200, 600), 24f);
        assertEquals(24f, background(holder.binding.progressBg).getCornerRadius(), 0f);
        assertFillBounds(holder.binding.progress, new Rect(0, 450, 200, 600));
        assertEquals(25, holder.slider.getProgress());
        assertEquals(270f, holder.slider.getRotation(), 0f);
        layout.restore();
    }

    @Test
    public void capsuleTrackRecomputesMaskAfterResizeWithoutRoundingTheFillBoundary() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                item(1, 3).withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 0));
        layout.measure(160, 600);
        holder.layout(160, 600);
        holder.slider.setProgress(50);
        assertRoundRect(holder.binding.toggleSliderInner, new Rect(0, 0, 160, 600), 80f);
        assertFillBounds(holder.binding.progress, new Rect(0, 300, 160, 600));

        layout.measure(220, 620);
        holder.layout(220, 620);
        assertRoundRect(holder.binding.toggleSliderInner, new Rect(0, 0, 220, 620), 110f);
        assertEquals(110f, background(holder.binding.progressBg).getCornerRadius(), 0f);
        assertFillBounds(holder.binding.progress, new Rect(0, 310, 220, 620));
        layout.restore();
    }

    @Test
    public void smallCustomCornersDoNotLeaveNativeProgressCutoutsAtTheBottom() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                item(1, 3).withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 4));
        layout.measure(200, 600);
        holder.layout(200, 600);
        holder.slider.setProgress(100);
        assertRoundRect(holder.binding.toggleSliderInner, new Rect(0, 0, 200, 600), 4f);
        assertFillBounds(holder.binding.progress, new Rect(0, 0, 200, 600));
        assertEquals(4f, background(holder.binding.progress).getCornerRadius(), 0f);
        layout.restore();
    }

    @Test
    public void nativeResourceRefreshCannotReintroduceDifferentTrackMask() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                item(3, 1).withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 18));
        layout.measure(600, 200);
        holder.layout(600, 200);
        GradientDrawable nextTrack = shape(72f);
        holder.binding.progressBg.setBackground(nextTrack);
        holder.binding.toggleSliderInner.setOutlineProvider(holder.nativeTrackOutline);
        holder.binding.progressBg.setOutlineProvider(holder.nativeSurfaceOutline);
        holder.binding.progress.setOutlineProvider(holder.nativeProgressOutline);
        holder.slider.setMin(10);
        holder.slider.setMax(110);
        holder.slider.setProgress(60);
        holder.binding.toggleSliderInner.getViewTreeObserver().dispatchOnPreDraw();

        assertRoundRect(holder.binding.toggleSliderInner, new Rect(0, 0, 600, 200), 18f);
        assertRoundRect(holder.binding.progressBg, new Rect(0, 0, 600, 200), 18f);
        assertEquals(18f, nextTrack.getCornerRadius(), 0f);
        assertFillBounds(holder.binding.progress, new Rect(0, 0, 300, 200));
        layout.restore();
        assertEquals(72f, nextTrack.getCornerRadius(), 0f);
    }

    @Test
    public void releaseRestoresNativeMasksAndDrawableCorners() throws Exception {
        Holder holder = new Holder();
        GradientDrawable nativeTrack = background(holder.binding.progressBg);
        GradientDrawable nativeProgress = background(holder.binding.progress);
        float[] corners = new float[] { 30f, 31f, 32f, 33f, 34f, 35f, 36f, 37f };
        nativeTrack.setCornerRadii(corners);
        holder.binding.toggleSliderInner.setClipToOutline(false);
        holder.binding.progressBg.setClipToOutline(false);
        holder.binding.progress.setClipToOutline(false);
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                item(1, 3).withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 4));
        layout.measure(200, 600);
        holder.layout(200, 600);
        assertTrue(holder.binding.toggleSliderInner.getClipToOutline());
        assertTrue(holder.binding.progressBg.getClipToOutline());
        assertTrue(holder.binding.progress.getClipToOutline());
        layout.restore();
        layout.restore();
        assertSame(holder.nativeTrackOutline, holder.binding.toggleSliderInner.getOutlineProvider());
        assertSame(holder.nativeSurfaceOutline, holder.binding.progressBg.getOutlineProvider());
        assertSame(holder.nativeProgressOutline, holder.binding.progress.getOutlineProvider());
        assertFalse(holder.binding.toggleSliderInner.getClipToOutline());
        assertFalse(holder.binding.progressBg.getClipToOutline());
        assertFalse(holder.binding.progress.getClipToOutline());
        assertArrayEquals(corners, nativeTrack.getCornerRadii(), 0f);
        assertEquals(8f, nativeProgress.getCornerRadius(), 0f);
        holder.binding.toggleSliderInner.getViewTreeObserver().dispatchOnPreDraw();
        assertFalse(holder.binding.toggleSliderInner.getClipToOutline());
    }

    @Test
    public void inheritedGlobalCornerRadiusMatchesTheTrackOutline() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder, item(1, 3), 36f);
        layout.measure(200, 600);
        holder.layout(200, 600);
        assertRoundRect(holder.binding.toggleSliderInner, new Rect(0, 0, 200, 600), 36f);
        assertRoundRect(holder.binding.progressBg, new Rect(0, 0, 200, 600), 36f);
        layout.restore();
    }

    @Test
    public void fillInsetsFromTheOutlineForVerticalAndHorizontalTracks() throws Exception {
        Holder vertical = new Holder();
        ControlCenterSliderLayout verticalLayout = ControlCenterSliderLayout.create(vertical,
                item(1, 3), 24f, 5f);
        verticalLayout.measure(200, 600);
        vertical.layout(200, 600);
        vertical.slider.setProgress(25);
        assertFillBounds(vertical.binding.progress, new Rect(5, 447, 195, 595));
        vertical.slider.setProgress(100);
        assertFillBounds(vertical.binding.progress, new Rect(5, 5, 195, 595));
        verticalLayout.restore();

        Holder horizontal = new Holder();
        ControlCenterSliderLayout horizontalLayout = ControlCenterSliderLayout.create(horizontal,
                item(3, 1), 24f, 5f);
        horizontalLayout.measure(600, 200);
        horizontal.layout(600, 200);
        horizontal.slider.setProgress(50);
        assertFillBounds(horizontal.binding.progress, new Rect(5, 5, 300, 195));
        horizontal.slider.setProgress(100);
        assertFillBounds(horizontal.binding.progress, new Rect(5, 5, 595, 195));
        horizontalLayout.restore();
    }

    @Test
    public void reattachKeepsTrackingTheNewViewTreeObserver() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                item(1, 3).withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 24));
        FrameLayout content = new FrameLayout(holder.activity);
        holder.activity.setContentView(content);
        content.addView(holder.itemView);
        layout.measure(200, 600);
        holder.layout(200, 600);
        content.removeView(holder.itemView);
        content.addView(holder.itemView);
        holder.binding.progressBg.setBackground(shape(72f));
        holder.binding.progressBg.setOutlineProvider(holder.nativeSurfaceOutline);
        holder.binding.toggleSliderInner.getViewTreeObserver().dispatchOnPreDraw();
        assertRoundRect(holder.binding.progressBg, new Rect(0, 0, 200, 600), 24f);
        assertEquals(24f, background(holder.binding.progressBg).getCornerRadius(), 0f);
        layout.restore();
    }

    private static ControlCenterLayoutPlan.Item item(int width, int height) {
        return ControlCenterLayoutPlan.Item.component("control:brightness", 0, 0, width, height);
    }

    private static void assertRoundRect(View view, Rect expected, float radius) {
        Outline outline = new Outline();
        view.getOutlineProvider().getOutline(view, outline);
        Rect rect = new Rect();
        assertTrue(outline.getRect(rect));
        assertEquals(expected, rect);
        assertEquals(radius, outline.getRadius(), 0f);
    }

    private static GradientDrawable background(View view) {
        return (GradientDrawable) view.getBackground();
    }

    private static void assertFillBounds(View view, Rect expected) throws Exception {
        Outline outline = new Outline();
        view.getOutlineProvider().getOutline(view, outline);
        java.lang.reflect.Field field = Outline.class.getDeclaredField("mPath");
        field.setAccessible(true);
        RectF bounds = new RectF();
        ((Path) field.get(outline)).computeBounds(bounds, true);
        Rect actual = new Rect();
        bounds.roundOut(actual);
        assertEquals(expected, actual);
    }

    private static GradientDrawable shape(float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xffeeeeee);
        shape.setCornerRadius(radius);
        return shape;
    }

    static final class Binding {
        final FrameLayout toggleSliderInner;
        final View progressBg;
        final View progress;
        final View icon;
        final View topText;

        Binding(Activity activity) {
            toggleSliderInner = new FrameLayout(activity);
            progressBg = new View(activity);
            progress = new View(activity);
            icon = new View(activity);
            topText = new View(activity);
        }
    }

    static final class Holder {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout itemView = new FrameLayout(activity);
        final SeekBar slider = new SeekBar(activity);
        final Binding binding = new Binding(activity);
        final ViewOutlineProvider nativeTrackOutline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        background(binding.progressBg).getCornerRadius());
            }
        };
        final ViewOutlineProvider nativeProgressOutline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                int top = Math.round(view.getHeight() * (1f - slider.getProgress() / 100f));
                outline.setRoundRect(0, top, view.getWidth(), view.getHeight(), 8f);
            }
        };
        final ViewOutlineProvider nativeSurfaceOutline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 20f);
            }
        };

        Holder() {
            slider.setMax(100);
            slider.setRotation(270f);
            binding.progressBg.setBackground(shape(96f));
            binding.progressBg.setOutlineProvider(nativeSurfaceOutline);
            binding.progress.setBackground(shape(8f));
            binding.toggleSliderInner.setOutlineProvider(nativeTrackOutline);
            binding.toggleSliderInner.setClipToOutline(true);
            binding.progress.setOutlineProvider(nativeProgressOutline);
            binding.progress.setClipToOutline(true);
            itemView.addView(binding.toggleSliderInner, new FrameLayout.LayoutParams(-1, -1));
            binding.toggleSliderInner.addView(binding.progressBg, new FrameLayout.LayoutParams(-1, -1));
            binding.toggleSliderInner.addView(slider, new FrameLayout.LayoutParams(200, 200, Gravity.CENTER));
            binding.toggleSliderInner.addView(binding.progress, new FrameLayout.LayoutParams(-1, -1));
            binding.toggleSliderInner.addView(binding.icon, new FrameLayout.LayoutParams(40, 40));
            binding.toggleSliderInner.addView(binding.topText, new FrameLayout.LayoutParams(40, 20));
        }

        public SeekBar getSlider() { return slider; }
        public Binding getBinding() { return binding; }
        public float getProgressRadius() { return 8f; }
        public float getOutlineRadius() { return 96f; }

        void layout(int width, int height) {
            itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            itemView.layout(0, 0, width, height);
            binding.toggleSliderInner.getViewTreeObserver().dispatchOnPreDraw();
        }
    }
}
