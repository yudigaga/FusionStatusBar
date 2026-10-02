package com.xtjm.fusionstatusbar;

import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import java.lang.reflect.Field;

import static com.xtjm.fusionstatusbar.ControlCenterRuntimeGrid.callOptional;
import static com.xtjm.fusionstatusbar.ControlCenterRuntimeGrid.readField;

/** Adapts the OEM slider to the published grid bounds. */
final class ControlCenterSliderLayout {
    private final Object holder;
    private final SeekBar slider;
    private final View progress;
    private final View progressBg;
    private final View icon;
    private final View topText;
    private final Object gesture;
    private final Field verticalField;
    private final Object originalVertical;
    private final Object dragAnim;
    private final boolean originalDrag;
    private final boolean horizontal;
    private final float rotation;
    private final int layoutDirection;
    private final ViewOutlineProvider outline;
    private final FrameLayout.LayoutParams sliderParams;
    private final FrameLayout.LayoutParams iconParams;
    private final FrameLayout.LayoutParams textParams;
    private final View.OnAttachStateChangeListener attachListener;
    private boolean restored;

    static ControlCenterSliderLayout create(Object holder, ControlCenterLayoutPlan.Item item)
            throws ReflectiveOperationException {
        if (item.type != ControlCenterLayoutPlan.Type.COMPONENT
                || (!ControlCenterComponentSpec.BRIGHTNESS.equals(item.firstSpec)
                && !ControlCenterComponentSpec.VOLUME.equals(item.firstSpec))) return null;
        Object binding = callOptional(holder, "getBinding");
        Object slider = callOptional(holder, "getSlider");
        Object progress = readField(binding, "progress");
        if (!(slider instanceof SeekBar) || !(progress instanceof View)) return null;
        return new ControlCenterSliderLayout(holder, (SeekBar) slider, (View) progress,
                (View) readField(binding, "progressBg"), (View) readField(binding, "icon"),
                (View) readField(binding, "topText"), item.width > item.height);
    }

    private ControlCenterSliderLayout(Object holder, SeekBar slider, View progress,
            View progressBg, View icon, View topText, boolean horizontal)
            throws ReflectiveOperationException {
        this.holder = holder;
        this.slider = slider;
        this.progress = progress;
        this.progressBg = progressBg;
        this.icon = icon;
        this.topText = topText;
        this.horizontal = horizontal;
        rotation = slider.getRotation();
        layoutDirection = slider.getLayoutDirection();
        outline = progress.getOutlineProvider();
        sliderParams = params(slider);
        iconParams = params(icon);
        textParams = params(topText);
        gesture = readField(slider, "gestureHelper");
        verticalField = gesture == null ? null : verticalField(gesture.getClass());
        originalVertical = verticalField == null ? null : verticalField.get(gesture);
        dragAnim = callOptional(slider, "getDragAnim");
        originalDrag = Boolean.TRUE.equals(callOptional(dragAnim, "getDragEnabled"));
        if (horizontal) {
            applyAxis();
            progress.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline result) {
                    int range = slider.getMax() - slider.getMin();
                    float fraction = range > 0
                            ? (slider.getProgress() - slider.getMin()) / (float) range : 0f;
                    int end = Math.round(view.getWidth() * Math.max(0f, Math.min(1f, fraction)));
                    if (end == 0 || view.getHeight() <= 0) {
                        result.setEmpty();
                    } else {
                        result.setRoundRect(0, 0, end, view.getHeight(), progressRadius());
                    }
                }
            });
            placeSide(icon, iconParams, false);
            placeSide(topText, textParams, true);
        }
        attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                // VerticalSeekBar re-enables its vertical bounce on every attach.
                if (horizontal && !restored) {
                    try {
                        applyAxis();
                    } catch (ReflectiveOperationException error) {
                        Log.w("FusionControlGrid", "Slider axis update failed", error);
                    }
                }
            }
            @Override public void onViewDetachedFromWindow(View view) { }
        };
        slider.addOnAttachStateChangeListener(attachListener);
        progress.invalidateOutline();
    }

    private void applyAxis() throws ReflectiveOperationException {
        if (verticalField != null) verticalField.set(gesture, Boolean.FALSE);
        callOptional(dragAnim, "setDragEnabled", false);
        // VerticalSeekBar is an ordinary horizontal SeekBar rotated by the OEM XML.
        slider.setRotation(0f);
        slider.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    void measure(int width, int height) {
        if (restored || !(slider.getLayoutParams() instanceof FrameLayout.LayoutParams lp)) return;
        int trackWidth = Math.max(1, horizontal ? width : height);
        int trackHeight = Math.max(1, horizontal ? height : width);
        if (lp.width != trackWidth || lp.height != trackHeight || lp.gravity != Gravity.CENTER) {
            lp.width = trackWidth;
            lp.height = trackHeight;
            lp.gravity = Gravity.CENTER;
            slider.setLayoutParams(lp);
        }
    }

    private float progressRadius() {
        float radius = 0f;
        try {
            Object value = callOptional(holder, "getProgressRadius");
            if (value instanceof Number) radius = ((Number) value).floatValue();
        } catch (ReflectiveOperationException ignored) {
            // Theme backgrounds still supply their native radius below.
        }
        if (progressBg != null && progressBg.getBackground() instanceof GradientDrawable bg) {
            radius = radius > 0f ? Math.min(radius, bg.getCornerRadius()) : bg.getCornerRadius();
        }
        return Math.max(0f, radius);
    }

    private static FrameLayout.LayoutParams params(View view) {
        return view != null && view.getLayoutParams() instanceof FrameLayout.LayoutParams lp
                ? new FrameLayout.LayoutParams(lp) : null;
    }

    private static void placeSide(View view, FrameLayout.LayoutParams original, boolean end) {
        if (view == null || original == null) return;
        FrameLayout.LayoutParams next = new FrameLayout.LayoutParams(original.width, original.height,
                Gravity.CENTER_VERTICAL | (end ? Gravity.RIGHT : Gravity.LEFT));
        if (end) next.rightMargin = original.topMargin;
        else next.leftMargin = original.bottomMargin;
        view.setLayoutParams(next);
    }

    private static Field verticalField(Class<?> type) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("vertical");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // The slider's gesture helper is an anonymous subclass.
            }
        }
        throw new NoSuchFieldException(type.getName() + "#vertical");
    }

    void restore() throws ReflectiveOperationException {
        if (restored) return;
        restored = true;
        slider.removeOnAttachStateChangeListener(attachListener);
        slider.setRotation(rotation);
        slider.setLayoutDirection(layoutDirection);
        if (sliderParams != null) slider.setLayoutParams(new FrameLayout.LayoutParams(sliderParams));
        if (iconParams != null) icon.setLayoutParams(new FrameLayout.LayoutParams(iconParams));
        if (textParams != null) topText.setLayoutParams(new FrameLayout.LayoutParams(textParams));
        progress.setOutlineProvider(outline);
        progress.invalidateOutline();
        if (horizontal) {
            if (verticalField != null) verticalField.set(gesture, originalVertical);
            callOptional(dragAnim, "setDragEnabled", originalDrag);
        }
    }
}
