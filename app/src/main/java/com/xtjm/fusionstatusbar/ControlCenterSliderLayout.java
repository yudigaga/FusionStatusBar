package com.xtjm.fusionstatusbar;

import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import static com.xtjm.fusionstatusbar.ControlCenterRuntimeGrid.callOptional;
import static com.xtjm.fusionstatusbar.ControlCenterRuntimeGrid.readField;

/** Adapts the OEM slider to the published grid bounds. */
final class ControlCenterSliderLayout {
    private final Object holder;
    private final SeekBar slider;
    private final View progress;
    private final View progressBg;
    private final View inner;
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
    private final ViewOutlineProvider innerOutline;
    private final ViewOutlineProvider surfaceOutline;
    private final boolean progressClips;
    private final boolean innerClips;
    private final boolean surfaceClips;
    private final ControlCenterLayoutPlan.Item placement;
    private final float defaultRadiusPx;
    private final ViewOutlineProvider trackOutline;
    private final ViewOutlineProvider fillOutline;
    private final ViewTreeObserver.OnPreDrawListener geometryListener;
    private ViewTreeObserver geometryObserver;
    private final Map<GradientDrawable, NativeCorners> nativeCorners = new IdentityHashMap<>();
    private final FrameLayout.LayoutParams sliderParams;
    private final FrameLayout.LayoutParams iconParams;
    private final FrameLayout.LayoutParams textParams;
    private final View.OnAttachStateChangeListener attachListener;
    private boolean restored;

    static ControlCenterSliderLayout create(Object holder, ControlCenterLayoutPlan.Item item)
            throws ReflectiveOperationException {
        return create(holder, item, 0f);
    }

    static ControlCenterSliderLayout create(Object holder, ControlCenterLayoutPlan.Item item,
            float defaultRadiusPx) throws ReflectiveOperationException {
        if (item.type != ControlCenterLayoutPlan.Type.COMPONENT
                || (!ControlCenterComponentSpec.BRIGHTNESS.equals(item.firstSpec)
                && !ControlCenterComponentSpec.VOLUME.equals(item.firstSpec))) return null;
        Object binding = callOptional(holder, "getBinding");
        Object slider = callOptional(holder, "getSlider");
        Object progress = readField(binding, "progress");
        if (!(slider instanceof SeekBar) || !(progress instanceof View)) return null;
        return new ControlCenterSliderLayout(holder, (SeekBar) slider, (View) progress,
                (View) readField(binding, "progressBg"), (View) readField(binding, "icon"),
                (View) readField(binding, "topText"), (View) readField(binding, "toggleSliderInner"),
                item, defaultRadiusPx);
    }

    private ControlCenterSliderLayout(Object holder, SeekBar slider, View progress,
            View progressBg, View icon, View topText, View inner,
            ControlCenterLayoutPlan.Item placement, float defaultRadiusPx)
            throws ReflectiveOperationException {
        this.holder = holder;
        this.slider = slider;
        this.progress = progress;
        this.progressBg = progressBg;
        this.icon = icon;
        this.topText = topText;
        this.inner = inner;
        this.placement = placement;
        this.defaultRadiusPx = Math.max(0f, defaultRadiusPx);
        this.horizontal = placement.width > placement.height;
        rotation = slider.getRotation();
        layoutDirection = slider.getLayoutDirection();
        outline = progress.getOutlineProvider();
        innerOutline = inner == null ? null : inner.getOutlineProvider();
        surfaceOutline = progressBg == null ? null : progressBg.getOutlineProvider();
        progressClips = progress.getClipToOutline();
        innerClips = inner != null && inner.getClipToOutline();
        surfaceClips = progressBg != null && progressBg.getClipToOutline();
        sliderParams = params(slider);
        iconParams = params(icon);
        textParams = params(topText);
        gesture = readField(slider, "gestureHelper");
        verticalField = gesture == null ? null : verticalField(gesture.getClass());
        originalVertical = verticalField == null ? null : verticalField.get(gesture);
        dragAnim = callOptional(slider, "getDragAnim");
        originalDrag = Boolean.TRUE.equals(callOptional(dragAnim, "getDragEnabled"));
        trackOutline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline result) {
                if (placement.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                    result.setOval(0, 0, view.getWidth(), view.getHeight());
                } else {
                    result.setRoundRect(0, 0, view.getWidth(), view.getHeight(), trackRadius(view));
                }
            }
        };
        fillOutline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline result) {
                int range = slider.getMax() - slider.getMin();
                float fraction = range > 0
                        ? (slider.getProgress() - slider.getMin()) / (float) range : 0f;
                fraction = Math.max(0f, Math.min(1f, fraction));
                int extent = horizontal ? view.getWidth() : view.getHeight();
                int filled = Math.round(extent * fraction);
                if (filled == 0 || view.getWidth() <= 0 || view.getHeight() <= 0) {
                    result.setEmpty();
                } else if (horizontal) {
                    result.setRoundRect(0, 0, filled, view.getHeight(), fillRadius());
                } else {
                    result.setRoundRect(0, view.getHeight() - filled,
                            view.getWidth(), view.getHeight(), fillRadius());
                }
            }
        };
        if (horizontal) {
            applyAxis();
            placeSide(icon, iconParams, false);
            placeSide(topText, textParams, true);
        }
        geometryListener = () -> {
            if (!restored) applyGeometry();
            return true;
        };
        registerGeometryListener();
        applyGeometry();
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
                if (!restored) {
                    registerGeometryListener();
                    applyGeometry();
                }
            }
            @Override public void onViewDetachedFromWindow(View view) {
                removeGeometryListener();
            }
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
        applyGeometry();
    }

    private View geometryHost() {
        return inner == null ? progress : inner;
    }

    private void registerGeometryListener() {
        ViewTreeObserver observer = geometryHost().getViewTreeObserver();
        if (geometryObserver == observer) return;
        removeGeometryListener();
        if (observer.isAlive()) {
            // An attached observer can inherit this listener from the detached one.
            observer.removeOnPreDrawListener(geometryListener);
            observer.addOnPreDrawListener(geometryListener);
            geometryObserver = observer;
        }
    }

    private void removeGeometryListener() {
        if (geometryObserver != null && geometryObserver.isAlive()) {
            geometryObserver.removeOnPreDrawListener(geometryListener);
        }
        ViewTreeObserver current = geometryHost().getViewTreeObserver();
        if (current != geometryObserver && current.isAlive()) {
            current.removeOnPreDrawListener(geometryListener);
        }
        geometryObserver = null;
    }

    private void applyGeometry() {
        if (inner != null) {
            setClip(inner, trackOutline);
            if (progressBg != null) setClip(progressBg, trackOutline);
            // The OEM drawable and inner outline otherwise keep a resource radius
            // independently of the grid's outer container and stroke.
            adaptCorners(progressBg, trackRadius(inner),
                    placement.shape == ControlCenterLayoutPlan.Shape.CIRCLE);
            adaptCorners(progress, fillRadius(), false);
        }
        if (inner != null || horizontal) setClip(progress, fillOutline);
    }

    private static void setClip(View view, ViewOutlineProvider provider) {
        if (view.getOutlineProvider() != provider) view.setOutlineProvider(provider);
        if (!view.getClipToOutline()) view.setClipToOutline(true);
    }

    private float trackRadius(View view) {
        if (placement.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                || placement.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
            return Math.min(view.getWidth(), view.getHeight()) / 2f;
        }
        return placement.cornerRadius > 0
                ? placement.cornerRadius * view.getResources().getDisplayMetrics().density
                : defaultRadiusPx;
    }

    private float fillRadius() {
        float nativeRadius = progressRadius();
        return inner == null ? nativeRadius : Math.min(nativeRadius, trackRadius(inner));
    }

    private void adaptCorners(View view, float radius, boolean oval) {
        if (view == null || !(view.getBackground() instanceof GradientDrawable drawable)) return;
        nativeCorners.computeIfAbsent(drawable, NativeCorners::new);
        int shape = oval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE;
        if (drawable.getShape() == shape && drawable.getCornerRadii() == null
                && drawable.getCornerRadius() == radius) return;
        drawable.mutate();
        drawable.setShape(shape);
        drawable.setCornerRadius(radius);
        view.invalidateOutline();
    }

    private float progressRadius() {
        float radius = 0f;
        try {
            Object value = callOptional(holder, "getProgressRadius");
            if (value instanceof Number) radius = ((Number) value).floatValue();
        } catch (ReflectiveOperationException ignored) {
            // Theme backgrounds still supply their native radius below.
        }
        if (inner == null && progressBg != null
                && progressBg.getBackground() instanceof GradientDrawable bg) {
            radius = radius > 0f ? Math.min(radius, bg.getCornerRadius()) : bg.getCornerRadius();
        }
        return Math.max(0f, radius);
    }

    private static final class NativeCorners {
        private final int shape;
        private final float radius;
        private final float[] radii;

        NativeCorners(GradientDrawable drawable) {
            shape = drawable.getShape();
            radius = drawable.getCornerRadius();
            radii = drawable.getCornerRadii();
        }

        void restore(GradientDrawable drawable) {
            drawable.setShape(shape);
            if (radii == null) drawable.setCornerRadius(radius);
            else drawable.setCornerRadii(radii);
        }
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
        removeGeometryListener();
        slider.removeOnAttachStateChangeListener(attachListener);
        slider.setRotation(rotation);
        slider.setLayoutDirection(layoutDirection);
        if (sliderParams != null) slider.setLayoutParams(new FrameLayout.LayoutParams(sliderParams));
        if (iconParams != null) icon.setLayoutParams(new FrameLayout.LayoutParams(iconParams));
        if (textParams != null) topText.setLayoutParams(new FrameLayout.LayoutParams(textParams));
        progress.setOutlineProvider(outline);
        progress.setClipToOutline(progressClips);
        progress.invalidateOutline();
        if (inner != null) {
            inner.setOutlineProvider(innerOutline);
            inner.setClipToOutline(innerClips);
            inner.invalidateOutline();
        }
        if (progressBg != null) {
            progressBg.setOutlineProvider(surfaceOutline);
            progressBg.setClipToOutline(surfaceClips);
            progressBg.invalidateOutline();
        }
        for (Map.Entry<GradientDrawable, NativeCorners> entry : nativeCorners.entrySet()) {
            entry.getValue().restore(entry.getKey());
        }
        nativeCorners.clear();
        if (horizontal) {
            if (verticalField != null) verticalField.set(gesture, originalVertical);
            callOptional(dragAnim, "setDragEnabled", originalDrag);
        }
    }
}
