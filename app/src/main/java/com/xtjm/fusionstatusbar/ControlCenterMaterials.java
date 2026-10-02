package com.xtjm.fusionstatusbar;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Background-only material adjustments; never changes a View's alpha or foreground RenderEffect. */
final class ControlCenterMaterials {
    enum Layer { NONE, CARD, TILE }
    private static final Map<Drawable, Surface> SURFACES = new WeakHashMap<>();
    private static final Map<View, NativeSurface> NATIVE = new WeakHashMap<>();
    private static final Map<Object, Float> WINDOW_RATIOS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);
    private ControlCenterMaterials() { }

    static int percent(ControlCenterConfig config, Layer layer, boolean editing) {
        if (!config.enabled || editing || layer == Layer.NONE) return 100;
        return layer == Layer.CARD ? config.cardBlur : config.tileBlur;
    }
    static float scale(float value, int strength) { return value * Math.max(0, Math.min(100, strength)) / 100f; }
    static int color(int color, int strength) {
        return (color & 0x00ffffff) | (Math.round(scale(color >>> 24, strength)) << 24);
    }
    static Layer layer(View source) {
        View current = source;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String name = current.getClass().getSimpleName();
            if (name.contains("QSTileItem")) return Layer.TILE;
            if (name.contains("QSCardItem") || current instanceof ControlCenterGroupView
                    || name.contains("MediaPlayerPanel") || name.contains("ToggleSliderView")
                    || name.contains("DeviceCenterEntry") || name.contains("DeviceControlsEntry")
                    || name.contains("CompactQsCard")) return Layer.CARD;
            current = current.getParent() instanceof View view ? view : null;
        }
        return Layer.NONE;
    }
    static void register(View owner, Drawable drawable, Layer layer, ControlCenterConfig config) {
        if (drawable == null || layer == Layer.NONE) return;
        Surface state = SURFACES.get(drawable);
        if (state == null) { state = new Surface(owner, layer, drawable.getAlpha()); SURFACES.put(drawable, state); }
        state.owner = new WeakReference<>(owner); state.layer = layer;
        applyAlpha(drawable, state, config);
    }
    static int drawableAlpha(Drawable drawable, int requested, ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) return requested;
        state.alpha = requested;
        View owner = state.owner.get();
        return owner == null ? requested : Math.round(scale(requested, percent(config, state.layer, ControlCenterNativeMode.isEditing(owner))));
    }
    private static void applyAlpha(Drawable drawable, Surface state, ControlCenterConfig config) {
        View owner = state.owner.get();
        int amount = owner == null ? 100 : percent(config, state.layer, ControlCenterNativeMode.isEditing(owner));
        int target = Math.round(scale(state.alpha, amount));
        if (drawable.getAlpha() != target) guarded(() -> drawable.setAlpha(target));
    }

    static float windowRatio(Object owner, float nativeRatio, ControlCenterConfig config, boolean editing) {
        if (APPLYING.get()) return nativeRatio;
        WINDOW_RATIOS.put(owner, nativeRatio);
        return scale(nativeRatio, config.enabled && !editing ? config.backgroundBlur : 100);
    }

    static Object[] nativeArguments(View view, String operation, Object[] arguments, ControlCenterConfig config, boolean editing) {
        if (APPLYING.get() || layer(view) == Layer.NONE) return arguments;
        NativeSurface state = NATIVE.computeIfAbsent(view, ignored -> new NativeSurface());
        state.requests.put(operation, copy(arguments));
        if (state.overridden && arguments.length == 1 && arguments[0] instanceof Integer requested) {
            if (operation.equals("setMiBackgroundBlurRadius")) state.radius = requested;
            if (operation.equals("setMiBackgroundBlurMode")) state.mode = requested;
        }
        return transform(operation, arguments, percent(config, layer(view), editing));
    }
    private static Object[] copy(Object[] args) {
        Object[] copy = args.clone();
        if (copy.length == 1 && copy[0] instanceof ArrayList<?> list) {
            ArrayList<Point> points = new ArrayList<>();
            for (Object value : list) if (value instanceof Point point) points.add(new Point(point));
            copy[0] = points;
        }
        return copy;
    }
    private static Object[] transform(String operation, Object[] args, int amount) {
        if (amount == 100 || args.length != 1) return args;
        Object[] next = args.clone();
        if (operation.equals("setMiBackgroundBlendColors") && args[0] instanceof ArrayList<?> list) {
            ArrayList<Point> colors = new ArrayList<>();
            for (Object value : list) if (value instanceof Point point) colors.add(new Point(color(point.x, amount), point.y));
            next[0] = colors;
        } else if (operation.equals("setMiBackgroundBlurRadius") && args[0] instanceof Integer radius) {
            next[0] = Math.round(scale(radius, amount));
        } else if (operation.equals("setMiBackgroundBlurScaleRatio") && args[0] instanceof Float ratio) {
            next[0] = scale(ratio, amount);
        } else if ((operation.equals("setMiViewBlurMode") || operation.equals("setMiBackgroundBlurMode")) && amount == 0) next[0] = 0;
        return next;
    }

    /** A native view-blur surface normally inherits the panel blur. Give partial strengths
     * their own background sampling radius where HyperOS exposes the reversible setters. */
    static void updateNativeSurface(View view, ControlCenterConfig config, boolean editing) {
        if (APPLYING.get() || layer(view) == Layer.NONE) return;
        NativeSurface state = NATIVE.get(view);
        if (state == null) return;
        Object[] modeRequest = state.requests.get("setMiViewBlurMode");
        if (modeRequest == null || !(modeRequest[0] instanceof Integer mode)) return;
        int amount = percent(config, layer(view), editing);
        try {
            if (mode <= 0 || !config.enabled || editing || amount == 100 && !state.ownedContainer) {
                if (state.overridden) {
                    guardedThrowing(() -> {
                        invoke(view, "setMiBackgroundBlurMode", state.mode);
                        invoke(view, "setMiBackgroundBlurRadius", state.radius);
                        if (state.ownedContainer) invoke(view, "setMiViewBlurMode", 0);
                    });
                    state.overridden = false;
                }
                return;
            }
            if (!state.overridden) {
                Object[] originalMode = state.requests.get("setMiBackgroundBlurMode");
                Object[] originalRadius = state.requests.get("setMiBackgroundBlurRadius");
                state.mode = originalMode != null && originalMode[0] instanceof Integer value
                        ? value : ((Number) invoke(view, "getMiBackgroundBlurMode")).intValue();
                state.radius = originalRadius != null && originalRadius[0] instanceof Integer value
                        ? value : ((Number) invoke(view, "getMiBackgroundBlurRadius")).intValue();
                state.overridden = true;
            }
            guardedThrowing(() -> {
                if (state.ownedContainer) invoke(view, "setMiViewBlurMode", amount == 0 ? 0 : 2);
                invoke(view, "setMiBackgroundBlurMode", amount == 0 ? 0 : 1);
                invoke(view, "setMiBackgroundBlurRadius", Math.round(275f * amount / 100));
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!state.failureLogged) {
                state.failureLogged = true;
                android.util.Log.w("FusionStatusBar", "Native per-surface blur unsupported; keeping available material controls", error);
            }
        }
    }

    static void refresh(ControlCenterConfig config) {
        for (Map.Entry<Drawable, Surface> entry : new ArrayList<>(SURFACES.entrySet())) applyAlpha(entry.getKey(), entry.getValue(), config);
        for (Map.Entry<View, NativeSurface> entry : new ArrayList<>(NATIVE.entrySet())) {
            View view = entry.getKey();
            int amount = percent(config, layer(view), ControlCenterNativeMode.isEditing(view));
            for (Map.Entry<String, Object[]> request : new ArrayList<>(entry.getValue().requests.entrySet())) {
                // Owned-container mode is synthetic, not an OEM value to replay on disable.
                if (entry.getValue().ownedContainer && request.getKey().equals("setMiViewBlurMode")) continue;
                try { guardedThrowing(() -> invoke(view, request.getKey(), transform(request.getKey(), request.getValue(), amount))); }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
        }
        for (Map.Entry<Object, Float> entry : new ArrayList<>(WINDOW_RATIOS.entrySet())) {
            try { guardedThrowing(() -> invoke(entry.getKey(), "setBlurRatio", scale(entry.getValue(),
                    config.enabled && !ControlCenterNativeMode.isEditing(entry.getKey()) ? config.backgroundBlur : 100))); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }

    static void registerRoot(View view, ControlCenterConfig config) {
        Layer layer = layer(view);
        register(view, view.getBackground(), layer, config);
        if (view instanceof ControlCenterGroupView) registerOwnedContainer(view, config);
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                String id = "";
                if (child.getId() != View.NO_ID) {
                    try { id = child.getResources().getResourceEntryName(child.getId()); }
                    catch (android.content.res.Resources.NotFoundException ignored) { }
                }
                if (id.equals("progress_bg")) register(child, child.getBackground(), Layer.CARD, config);
            }
        }
    }

    static void registerOwnedContainer(View view, ControlCenterConfig config) {
        NativeSurface state = NATIVE.computeIfAbsent(view, ignored -> new NativeSurface());
        state.ownedContainer = true;
        state.requests.put("setMiViewBlurMode", new Object[] {2});
        updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
    }
    static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Integer ? int.class : args[i] instanceof Float ? float.class : args[i].getClass();
        Method method = target.getClass().getMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private interface Action { void run() throws ReflectiveOperationException; }
    private static void guardedThrowing(Action action) throws ReflectiveOperationException {
        boolean was = APPLYING.get(); APPLYING.set(true);
        try { action.run(); } finally { APPLYING.set(was); }
    }
    private static void guarded(Runnable action) {
        boolean was = APPLYING.get(); APPLYING.set(true);
        try { action.run(); } finally { APPLYING.set(was); }
    }
    static void clearForTest() { SURFACES.clear(); NATIVE.clear(); WINDOW_RATIOS.clear(); }
    private static final class Surface {
        WeakReference<View> owner; Layer layer; int alpha;
        Surface(View owner, Layer layer, int alpha) { this.owner = new WeakReference<>(owner); this.layer = layer; this.alpha = alpha; }
    }
    private static final class NativeSurface {
        final Map<String, Object[]> requests = new java.util.LinkedHashMap<>();
        int mode, radius;
        boolean overridden, failureLogged, ownedContainer;
    }
}
