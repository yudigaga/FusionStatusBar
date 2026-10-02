package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterSliderLayoutTest {
    @Test
    public void wideVolumeChangesInputAxisFillAndKeepsNativeCallbacks() throws Exception {
        Holder holder = new Holder();
        List<String> calls = new ArrayList<>();
        holder.slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) calls.add("change");
                holder.binding.progress.invalidateOutline();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { calls.add("start"); }
            @Override public void onStopTrackingTouch(SeekBar bar) { calls.add("stop"); }
        });
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:volume", 0, 0, 2, 1));
        layout.measure(600, 200);
        holder.layout(600, 200);

        assertEquals(0f, holder.slider.getRotation(), 0f);
        assertEquals(Boolean.FALSE, holder.slider.gestureHelper.vertical);
        assertFalse(holder.slider.dragAnim.enabled);
        assertEquals(600, holder.slider.getWidth());
        assertEquals(200, holder.slider.getHeight());
        FrameLayout.LayoutParams iconParams = (FrameLayout.LayoutParams)
                holder.binding.icon.getLayoutParams();
        assertEquals(Gravity.LEFT | Gravity.CENTER_VERTICAL, iconParams.gravity);
        assertEquals(20, iconParams.leftMargin);
        assertEquals(0f, holder.binding.icon.getRotation(), 0f);
        holder.slider.setProgress(25);
        assertEquals(new Rect(0, 0, 150, 200), holder.fill());

        long start = SystemClock.uptimeMillis();
        holder.touch(start, start, MotionEvent.ACTION_DOWN, 100, 100);
        int before = holder.slider.getProgress();
        holder.touch(start, start + 50, MotionEvent.ACTION_MOVE, 500, 100);
        holder.touch(start, start + 100, MotionEvent.ACTION_UP, 500, 100);
        assertTrue(holder.slider.getProgress() > before);
        assertTrue(calls.contains("start"));
        assertTrue(calls.contains("change"));
        assertEquals("stop", calls.get(calls.size() - 1));
        layout.restore();
    }

    @Test
    public void cancelEndsTrackingAndRebindingVerticalRestoresUpwardMotion() throws Exception {
        Holder holder = new Holder();
        List<String> calls = new ArrayList<>();
        holder.slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { calls.add("start"); }
            @Override public void onStopTrackingTouch(SeekBar bar) { calls.add("stop"); }
        });
        ControlCenterSliderLayout horizontal = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:volume", 0, 0, 2, 1));
        horizontal.measure(600, 200);
        holder.layout(600, 200);
        long down = SystemClock.uptimeMillis();
        holder.touch(down, down, MotionEvent.ACTION_DOWN, 100, 100);
        holder.touch(down, down + 50, MotionEvent.ACTION_MOVE, 400, 100);
        holder.touch(down, down + 80, MotionEvent.ACTION_CANCEL, 400, 100);
        assertEquals(List.of("start", "stop"), calls);
        assertFalse(holder.slider.isPressed());
        horizontal.restore();

        ControlCenterSliderLayout vertical = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:volume", 0, 0, 1, 2));
        vertical.measure(200, 600);
        holder.layout(200, 600);
        holder.touch(down + 100, down + 100, MotionEvent.ACTION_DOWN, 100, 500);
        int low = holder.slider.getProgress();
        holder.touch(down + 100, down + 150, MotionEvent.ACTION_MOVE, 100, 100);
        holder.touch(down + 100, down + 180, MotionEvent.ACTION_UP, 100, 100);
        assertTrue(holder.slider.getProgress() > low);
        assertTrue(holder.slider.gestureHelper.vertical);
        vertical.restore();
    }

    @Test
    public void brightnessFillHandlesBoundsAndTrackResizesWithGrid() throws Exception {
        Holder holder = new Holder();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:brightness", 0, 0, 3, 1));
        layout.measure(600, 200);
        holder.layout(600, 200);
        holder.slider.setMin(10);
        holder.slider.setProgress(10);
        assertTrue(holder.fill().isEmpty());
        holder.slider.setProgress(55);
        assertEquals(new Rect(0, 0, 300, 200), holder.fill());
        holder.slider.setProgress(100);
        assertEquals(new Rect(0, 0, 600, 200), holder.fill());
        layout.measure(800, 220);
        holder.layout(800, 220);
        assertEquals(800, holder.slider.getWidth());
        assertEquals(new Rect(0, 0, 800, 220), holder.fill());
        holder.slider.setMin(0);
        holder.slider.setMax(0);
        assertTrue(holder.fill().isEmpty());
        layout.restore();
    }

    @Test
    public void reopenDoesNotRestoreVerticalDragAndReleaseRestoresNativeState() throws Exception {
        Holder holder = new Holder();
        float rotation = holder.slider.getRotation();
        ViewOutlineProvider outline = holder.binding.progress.getOutlineProvider();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:brightness", 0, 0, 2, 1));
        FrameLayout content = new FrameLayout(holder.activity);
        holder.activity.setContentView(content);
        content.addView(holder.itemView);
        layout.measure(600, 200);
        holder.layout(600, 200);
        assertFalse(holder.slider.dragAnim.enabled);
        content.removeView(holder.itemView);
        content.addView(holder.itemView);
        holder.layout(600, 200);
        assertFalse(holder.slider.dragAnim.enabled);
        assertEquals(Boolean.FALSE, holder.slider.gestureHelper.vertical);

        layout.restore();
        assertEquals(rotation, holder.slider.getRotation(), 0f);
        assertEquals(Boolean.TRUE, holder.slider.gestureHelper.vertical);
        assertTrue(holder.slider.dragAnim.enabled);
        assertSame(outline, holder.binding.progress.getOutlineProvider());
        FrameLayout.LayoutParams icon = (FrameLayout.LayoutParams) holder.binding.icon.getLayoutParams();
        assertEquals(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, icon.gravity);
        assertEquals(20, icon.bottomMargin);
        assertEquals(200, holder.slider.getLayoutParams().width);
    }

    @Test
    public void tallSliderRetainsUpwardInputAndNonSlidersAreIgnored() throws Exception {
        Holder holder = new Holder();
        ViewOutlineProvider outline = holder.binding.progress.getOutlineProvider();
        ControlCenterSliderLayout layout = ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:volume", 0, 0, 1, 3));
        layout.measure(200, 600);
        holder.layout(200, 600);
        assertEquals(270f, holder.slider.getRotation(), 0f);
        assertEquals(600, holder.slider.getWidth());
        assertEquals(200, holder.slider.getHeight());
        assertSame(outline, holder.binding.progress.getOutlineProvider());
        assertTrue(holder.slider.gestureHelper.vertical);
        assertTrue(holder.slider.dragAnim.enabled);
        layout.restore();
        assertNull(ControlCenterSliderLayout.create(holder,
                ControlCenterLayoutPlan.Item.component("control:media", 0, 0, 2, 1)));
    }

    static class GestureHelper {
        private final Boolean vertical = Boolean.TRUE;
    }

    static class DragAnim {
        boolean enabled = true;
        public boolean getDragEnabled() { return enabled; }
        public void setDragEnabled(boolean value) { enabled = value; }
    }

    static class Slider extends SeekBar {
        final GestureHelper gestureHelper = new GestureHelper();
        final DragAnim dragAnim = new DragAnim();
        Slider(Activity activity) { super(activity); }
        public DragAnim getDragAnim() { return dragAnim; }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            dragAnim.enabled = true;
        }
    }

    static class Binding {
        final View progress;
        final View progressBg;
        final View icon;
        final View topText;
        Binding(Activity activity) {
            progress = new View(activity);
            progressBg = new View(activity);
            icon = new View(activity);
            topText = new View(activity);
        }
    }

    static class Holder {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout itemView = new FrameLayout(activity);
        final Slider slider = new Slider(activity);
        final Binding binding = new Binding(activity);

        Holder() {
            slider.setMax(100);
            slider.setPadding(0, 0, 0, 0);
            slider.setRotation(270f);
            itemView.addView(slider, new FrameLayout.LayoutParams(200, 200, Gravity.CENTER));
            itemView.addView(binding.progress, new FrameLayout.LayoutParams(-1, -1));
            binding.progress.setClipToOutline(true);
            binding.progress.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRect(0, view.getHeight() / 2, view.getWidth(), view.getHeight());
                }
            });
            FrameLayout.LayoutParams icon = new FrameLayout.LayoutParams(40, 40,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            icon.bottomMargin = 20;
            itemView.addView(binding.icon, icon);
            itemView.addView(binding.topText, new FrameLayout.LayoutParams(40, 20,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        }
        public Slider getSlider() { return slider; }
        public Binding getBinding() { return binding; }
        public float getProgressRadius() { return 8f; }
        void layout(int width, int height) {
            itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            itemView.layout(0, 0, width, height);
        }
        Rect fill() {
            Outline outline = new Outline();
            binding.progress.getOutlineProvider().getOutline(binding.progress, outline);
            Rect rect = new Rect();
            outline.getRect(rect);
            return rect;
        }
        void touch(long down, long time, int action, float x, float y) {
            MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
            try { itemView.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }
}
