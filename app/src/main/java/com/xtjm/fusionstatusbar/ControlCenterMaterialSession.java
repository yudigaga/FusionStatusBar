package com.xtjm.fusionstatusbar;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import java.lang.ref.WeakReference;

/** Owns the reversible component/card background and outline state for one view. */
final class ControlCenterMaterialSession {
    private final WeakReference<View> owner;
    ControlCenterMaterials.TileSurfaceDrawable componentBackground;
    ControlCenterMaterials.ClippedBackgroundDrawable cardBackground;
    ControlCenterMaterials.ShapeClip shapeClip;

    ControlCenterMaterialSession(View owner) {
        this.owner = new WeakReference<>(owner);
    }

    View owner() { return owner.get(); }

    boolean needsComponentBackground(View view) {
        return componentBackground == null || view.getBackground() != componentBackground;
    }

    void applyComponentBackground(ControlCenterLayoutPlan.Item item, ControlCenterConfig config) {
        View view = owner();
        if (view == null) return;
        if (needsComponentBackground(view)) {
            if (componentBackground != null) componentBackground.restoreNativeCallback();
            Drawable nativeBackground = view.getBackground();
            componentBackground = new ControlCenterMaterials.TileSurfaceDrawable(nativeBackground);
            view.setBackground(componentBackground);
            if (nativeBackground != null) nativeBackground.setCallback(componentBackground);
        }
        componentBackground.configure(item.shape, item.cornerRadius, false, config, true, false, config.cardBlur);
    }

    void applyCardBackground(ControlCenterLayoutPlan.Item item) {
        View view = owner();
        if (view == null || view.getBackground() == null) return;
        ControlCenterMaterials.ClippedBackgroundDrawable current = cardBackground;
        Drawable nativeBackground = current != null && view.getBackground() == current
                ? current.nativeDrawable : view.getBackground();
        if (current == null || current.nativeDrawable != nativeBackground) {
            if (current != null) current.restoreNativeCallback();
            current = new ControlCenterMaterials.ClippedBackgroundDrawable(nativeBackground, item);
            cardBackground = current;
            view.setBackground(current);
            nativeBackground.setCallback(current);
        } else current.configure(item);
    }

    void applyShapeClip(ControlCenterLayoutPlan.Shape shape, int radiusDp) {
        View view = owner();
        if (view == null) return;
        ControlCenterMaterials.ShapeClip saved = shapeClip;
        if (saved == null) {
            saved = new ControlCenterMaterials.ShapeClip(view);
            shapeClip = saved;
        }
        if (!view.getClipToOutline()) view.setClipToOutline(true);
        if (view.getOutlineProvider() == saved.applied && saved.shape == shape && saved.radiusDp == radiusDp) return;
        saved.shape = shape;
        saved.radiusDp = radiusDp;
        saved.applied = new ViewOutlineProvider() {
            @Override public void getOutline(View target, android.graphics.Outline outline) {
                // This outline clips geometry; it does not promise opaque content to HWUI.
                outline.setAlpha(0f);
                int width = target.getWidth();
                int height = target.getHeight();
                if (width <= 0 || height <= 0) return;
                if (shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                    int size = Math.min(width, height);
                    int left = (width - size) / 2;
                    int top = (height - size) / 2;
                    outline.setOval(left, top, left + size, top + size);
                    return;
                }
                float density = target.getResources().getDisplayMetrics().density;
                float radius = shape == ControlCenterLayoutPlan.Shape.CAPSULE
                        ? Math.min(width, height) / 2f : radiusDp * density;
                outline.setRoundRect(0, 0, width, height, Math.max(0f, radius));
            }
        };
        view.setOutlineProvider(saved.applied);
        view.invalidateOutline();
    }

    void restoreComponentBackground() {
        View view = owner();
        ControlCenterMaterials.TileSurfaceDrawable current = componentBackground;
        if (current != null) {
            if (view != null && view.getBackground() == current) view.setBackground(current.nativeDrawable());
            else current.restoreNativeCallback();
        }
        componentBackground = null;
    }

    void restoreCardBackground() {
        View view = owner();
        ControlCenterMaterials.ClippedBackgroundDrawable current = cardBackground;
        if (current != null) {
            if (view != null && view.getBackground() == current) view.setBackground(current.nativeDrawable);
            else current.restoreNativeCallback();
        }
        cardBackground = null;
    }

    void restoreShapeClip() {
        View view = owner();
        ControlCenterMaterials.ShapeClip clip = shapeClip;
        if (view != null && clip != null && view.getOutlineProvider() == clip.applied) {
            view.setOutlineProvider(clip.original);
            view.setClipToOutline(clip.originalClipping);
        }
        shapeClip = null;
    }

    void refresh(ControlCenterConfig config) {
        View view = owner();
        ControlCenterMaterials.TileSurfaceDrawable current = componentBackground;
        if (view == null || current == null) return;
        if (ControlCenterMaterials.surface(config, ControlCenterNativeMode.isEditing(view))
                == ControlCenterSurface.DEFAULT) {
            restoreComponentBackground();
        } else current.refresh(config);
    }

    void release() {
        restoreComponentBackground();
        restoreCardBackground();
        restoreShapeClip();
    }

    boolean empty() {
        return componentBackground == null && cardBackground == null && shapeClip == null;
    }
}
