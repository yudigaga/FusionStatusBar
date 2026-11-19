package com.xtjm.fusionstatusbar;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import java.util.IdentityHashMap;

/** Module output, including output hidden in inactive framework children, is never a host original. */
final class HostDrawableGraph {
    private static final int MAX_NODES = 128;
    private static final int MAX_DEPTH = 32;
    enum Result { SAFE, UNSAFE, UNKNOWN }

    private HostDrawableGraph() { }

    static boolean isSafe(Drawable drawable) {
        return inspect(drawable) == Result.SAFE;
    }

    static Result inspect(Drawable drawable) {
        if (drawable == null) return Result.SAFE;
        if (module(drawable)) return Result.UNSAFE;
        if (!container(drawable)) return Result.SAFE;
        try {
            Scan scan = new Scan();
            if (!scan.visit(drawable, 0)) return Result.UNSAFE;
            return scan.unknown ? Result.UNKNOWN : Result.SAFE;
        } catch (RuntimeException ignored) { return Result.UNKNOWN; }
    }

    private static boolean module(Drawable drawable) {
        return drawable instanceof SystemSurfaceStyle.SurfaceDrawable
                || drawable instanceof NotificationSurfaceHooks.BoundedBackground;
    }

    private static boolean container(Drawable drawable) {
        return drawable instanceof DrawableWrapper || drawable instanceof LayerDrawable
                || drawable instanceof DrawableContainer;
    }

    private static final class Scan {
        final IdentityHashMap<Drawable, Boolean> visiting = new IdentityHashMap<>();
        int remaining = MAX_NODES;
        boolean unknown;

        boolean visit(Drawable drawable, int depth) {
            if (drawable == null) return true;
            if (module(drawable)) return false;
            if (depth > MAX_DEPTH) { unknown = true; return true; }
            Boolean onPath = visiting.get(drawable);
            if (onPath != null) return !onPath;
            if (--remaining < 0) { unknown = true; return true; }
            visiting.put(drawable, true);
            if (drawable instanceof DrawableWrapper wrapper) {
                if (!visit(wrapper.getDrawable(), depth + 1)) return false;
            } else if (drawable instanceof LayerDrawable layers) {
                int count = layers.getNumberOfLayers();
                if (count > MAX_NODES) return uninspected(drawable);
                for (int index = 0; index < count; index++)
                    if (!visit(layers.getDrawable(index), depth + 1)) return false;
            } else if (drawable instanceof StateListDrawable states) {
                int count = states.getStateCount();
                if (count > MAX_NODES) return uninspected(drawable);
                for (int index = 0; index < count; index++)
                    if (!visit(states.getStateDrawable(index), depth + 1)) return false;
            } else if (drawable instanceof AnimationDrawable animation) {
                int count = animation.getNumberOfFrames();
                if (count > MAX_NODES) return uninspected(drawable);
                for (int index = 0; index < count; index++)
                    if (!visit(animation.getFrame(index), depth + 1)) return false;
            } else if (drawable instanceof DrawableContainer) {
                // Some containers expose only their selected child without a constant state.
                // Their selected child can prove a back edge, but cannot prove all states safe.
                Drawable.ConstantState constant = drawable.getConstantState();
                if (!(constant instanceof DrawableContainer.DrawableContainerState children)) {
                    unknown = true;
                    boolean safe = visit(drawable.getCurrent(), depth + 1);
                    if (safe) visiting.put(drawable, false);
                    return safe;
                }
                int count = children.getChildCount();
                if (count > MAX_NODES) return uninspected(drawable);
                for (int index = 0; index < count; index++)
                    if (!visit(children.getChild(index), depth + 1)) return false;
            }
            visiting.put(drawable, false);
            return true;
        }

        private boolean uninspected(Drawable drawable) {
            unknown = true;
            visiting.put(drawable, false);
            return true;
        }
    }
}
