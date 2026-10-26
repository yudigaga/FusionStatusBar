package com.xtjm.fusionstatusbar;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.ColorFilter;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Background-only material adjustments; never changes a View's alpha or foreground RenderEffect. */
final class ControlCenterMaterials {
    enum Layer { NONE, CARD, TILE }
    private static final Map<Drawable, Surface> SURFACES = new WeakHashMap<>();
    private static final Map<Drawable, TintState> PENDING_TINTS = new WeakHashMap<>();
    private static final Map<View, NativeSurface> NATIVE = new WeakHashMap<>();
    private static final Map<TextView, ColorStateList> TEXT = new WeakHashMap<>();
    private static final Map<Object, Float> WINDOW_RATIOS = new WeakHashMap<>();
    private static final Map<View, Boolean> BORDER_VIEWS = new WeakHashMap<>();
    private static final Map<View, Layer> GLASS_VIEWS = new WeakHashMap<>();
    private static final Map<View, WeakReference<View>> DECORATION_TARGETS = new WeakHashMap<>();
    private static final Map<View, Boolean> GRID_CARRIERS = new WeakHashMap<>();
    private static final Map<View, TileShape> TILE_SHAPES = new WeakHashMap<>();
    private static final Map<View, TileSurfaceDrawable> ICON_BACKGROUNDS = new WeakHashMap<>();
    private static final Map<View, ControlCenterMaterialSession> MATERIAL_SESSIONS = new WeakHashMap<>();
    private static final Map<View, Boolean> NATIVE_TILES = new WeakHashMap<>();
    private static final Map<View, NativeTileBackground> NATIVE_TILE_BACKGROUNDS = new WeakHashMap<>();
    /** Only caches a layer derived from the view's own class; parent-derived layers stay dynamic. */
    private static final Map<View, Layer> DIRECT_LAYERS = new WeakHashMap<>();
    private static final Map<View, ThemeObserver> THEME_ROOTS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);
    private ControlCenterMaterials() { }

    private static ControlCenterMaterialSession materialSession(View view) {
        ControlCenterMaterialSession session = MATERIAL_SESSIONS.get(view);
        if (session == null) {
            session = new ControlCenterMaterialSession(view);
            MATERIAL_SESSIONS.put(view, session);
        }
        return session;
    }

    private static void trimMaterialSession(View view, ControlCenterMaterialSession session) {
        if (session.empty()) MATERIAL_SESSIONS.remove(view);
    }

    static int percent(ControlCenterConfig config, Layer layer, boolean editing) {
        if (!config.enabled || editing || layer == Layer.NONE) return 100;
        return layer == Layer.CARD ? config.cardBlur : config.tileBlur;
    }
    private static int opacityPercent(int blurPercent) {
        return Math.max(0, Math.min(100, blurPercent));
    }
    static float scale(float value, int strength) { return value * Math.max(0, Math.min(100, strength)) / 100f; }
    static int color(int color, int strength) {
        return (color & 0x00ffffff) | (Math.round(scale(color >>> 24, strength)) << 24);
    }
    static ControlCenterSurface surface(ControlCenterConfig config, boolean editing) {
        return config.enabled && !editing ? config.surface : ControlCenterSurface.DEFAULT;
    }
    private static ControlCenterSurface nativeSurface(View view, ControlCenterConfig config, boolean editing) {
        return preservesNativeMaterial(view) ? ControlCenterSurface.DEFAULT
                : surface(config, editing).resolve(view.getContext());
    }
    static Layer layer(View source) {
        if (source == null) return Layer.NONE;
        if (source instanceof ControlCenter5GTileView) return Layer.TILE;
        String ownName = source.getClass().getSimpleName();
        Layer direct = directLayer(ownName);
        if (direct != Layer.NONE) {
            synchronized (DIRECT_LAYERS) {
                Layer cached = DIRECT_LAYERS.get(source);
                if (cached != null) return cached;
                DIRECT_LAYERS.put(source, direct);
            }
            return direct;
        }
        View current = source;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String name = current.getClass().getSimpleName();
            Layer resolved = directLayer(name);
            if (resolved != Layer.NONE || current instanceof ControlCenterGroupView) {
                return current instanceof ControlCenterGroupView ? Layer.CARD : resolved;
            }
            current = current.getParent() instanceof View view ? view : null;
        }
        return Layer.NONE;
    }

    private static Layer directLayer(String name) {
        if (name == null) return Layer.NONE;
        if (name.contains("QSTileItem")) return Layer.TILE;
        if (name.contains("QSCardItem") || name.contains("MediaPlayerPanel")
                || name.contains("ToggleSliderView") || name.contains("DeviceCenterEntry")
                || name.contains("DeviceControlsEntry") || name.contains("CompactQsCard")) {
            return Layer.CARD;
        }
        return Layer.NONE;
    }
    static void register(View owner, Drawable drawable, Layer layer, ControlCenterConfig config) {
        if (owner == null || drawable == null || layer == Layer.NONE) return;
        observeTheme(owner, config);
        ControlCenterMaterialSession session = MATERIAL_SESSIONS.get(owner);
        if (session != null && session.componentBackground == drawable) return;
        if (drawable instanceof ClippedBackgroundDrawable clipped) {
            register(owner, clipped.nativeDrawable, layer, config);
            return;
        }
        if (isSliderForeground(owner)) {
            Surface existing = SURFACES.get(drawable);
            if (existing != null) applyDrawable(drawable, existing, config);
            return;
        }
        if (drawable instanceof LayerDrawable layers) {
            Drawable background = layers.findDrawableByLayerId(android.R.id.background);
            if (background != null) {
                register(owner, background, layer, config);
            } else if (isUnlabelledPanelBackground(owner)) {
                // HyperOS QS cards use two full-size, unlabelled GradientDrawables.
                // Restrict this fallback to known panel roots: slider and icon layers
                // are deliberately left untouched when their OEM ids are absent.
                for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                    int id = layers.getId(i);
                    if (id == android.R.id.progress || id == android.R.id.secondaryProgress) continue;
                    Drawable child = layers.getDrawable(i);
                    if (child instanceof GradientDrawable || child instanceof ColorDrawable)
                        register(owner, child, layer, config);
                }
            }
            return;
        }
        Surface state = SURFACES.get(drawable);
        if (state == null) { state = new Surface(owner, layer, drawable); SURFACES.put(drawable, state); }
        state.owner = new WeakReference<>(owner); state.layer = layer;
        applyDrawable(drawable, state, config);
    }
    static int drawableAlpha(Drawable drawable, int requested, ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) return requested;
        state.alpha = requested;
        if (drawable instanceof ColorDrawable) state.colorAlphaFactor = requested / 255f;
        View owner = state.owner.get();
        return owner == null ? requested : alphaArgument(drawable, state, config);
    }
    static int drawableColor(Drawable drawable, int requested, ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null || !state.colorSupported) return requested;
        state.colors = null;
        state.color = ColorStateList.valueOf(requested);
        if (drawable instanceof ColorDrawable) {
            state.alpha = requested >>> 24;
            state.colorAlphaFactor = 1f;
        }
        ControlCenterSurface style = drawableSurface(state, config);
        return style == ControlCenterSurface.DEFAULT ? requested : containerColor(state.owner.get(), style);
    }
    static ColorStateList drawableColor(Drawable drawable, ColorStateList requested, ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null || !state.colorSupported) return requested;
        state.colors = null;
        state.color = requested;
        ControlCenterSurface style = drawableSurface(state, config);
        return style == ControlCenterSurface.DEFAULT ? requested : ColorStateList.valueOf(containerColor(state.owner.get(), style));
    }
    static int[] drawableColors(GradientDrawable drawable, int[] requested, ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) return requested;
        state.colors = requested == null ? null : requested.clone();
        state.color = null;
        ControlCenterSurface style = drawableSurface(state, config);
        if (style == ControlCenterSurface.DEFAULT || requested == null) return requested;
        int[] colors = new int[requested.length];
        java.util.Arrays.fill(colors, containerColor(state.owner.get(), style));
        return colors;
    }
    private static ControlCenterSurface drawableSurface(Surface state, ControlCenterConfig config) {
        View owner = state.owner.get();
        return owner == null ? ControlCenterSurface.DEFAULT
                : nativeSurface(owner, config, ControlCenterNativeMode.isEditing(owner));
    }
    private static void applyDrawable(Drawable drawable, Surface state, ControlCenterConfig config) {
        if (state.colorSupported) {
            ControlCenterSurface style = drawableSurface(state, config);
            guarded(() -> {
                if (drawable instanceof GradientDrawable gradient) {
                    if (style != ControlCenterSurface.DEFAULT && state.colors != null) {
                        int[] tint = new int[state.colors.length];
                        java.util.Arrays.fill(tint, containerColor(state.owner.get(), style));
                        if (!java.util.Arrays.equals(gradient.getColors(), tint)) gradient.setColors(tint);
                    } else if (style != ControlCenterSurface.DEFAULT) {
                        if (gradient.getColors() != null) gradient.setColors(null);
                        ColorStateList selected = ColorStateList.valueOf(containerColor(state.owner.get(), style));
                        if (!java.util.Objects.equals(gradient.getColor(), selected)) gradient.setColor(selected);
                    }
                    else if (state.colors != null) {
                        if (!java.util.Arrays.equals(gradient.getColors(), state.colors)) gradient.setColors(state.colors.clone());
                    }
                    else {
                        if (gradient.getColors() != null) gradient.setColors(null);
                        if (!java.util.Objects.equals(gradient.getColor(), state.color)) gradient.setColor(state.color);
                    }
                } else if (drawable instanceof ColorDrawable color) {
                    int selected = style == ControlCenterSurface.DEFAULT ? state.color.getDefaultColor()
                            : containerColor(state.owner.get(), style);
                    if (color.getColor() != selected) color.setColor(selected);
                }
            });
        }
        applyFilters(drawable, state, config);
        applyAlpha(drawable, state, config);
    }

    private static void applyFilters(Drawable drawable, Surface state, ControlCenterConfig config) {
        if (!filterSurface(state)) return;
        ControlCenterSurface style = drawableSurface(state, config);
        ColorFilter filter = style == ControlCenterSurface.DEFAULT ? state.colorFilter : null;
        ColorStateList tint = style == ControlCenterSurface.DEFAULT ? state.tintList : null;
        boolean tintChanged = state.tintCaptured && (state.filtersDirty
                || !java.util.Objects.equals(state.appliedTint, tint)
                || style == ControlCenterSurface.DEFAULT && state.appliedTintMode != state.tintMode);
        guarded(() -> {
            if (drawable.getColorFilter() != filter) drawable.setColorFilter(filter);
            if (tintChanged) {
                drawable.setTintList(tint);
                if (style == ControlCenterSurface.DEFAULT) drawable.setTintMode(state.tintMode);
            }
        });
        state.appliedTint = tint;
        if (style == ControlCenterSurface.DEFAULT) state.appliedTintMode = state.tintMode;
        state.filtersDirty = false;
    }

    private static boolean filterSurface(Surface state) {
        View owner = state.owner.get();
        if (owner == null || preservesNativeMaterial(owner)) return false;
        String name = owner.getClass().getSimpleName();
        if (name.contains("Icon")) return false;
        return panelContainer(owner, name) || name.contains("QSTileItemView");
    }
    private static void applyAlpha(Drawable drawable, Surface state, ControlCenterConfig config) {
        int target = targetAlpha(drawable, state, config);
        if (drawable.getAlpha() != target) guarded(() -> drawable.setAlpha(alphaArgument(drawable, state, config)));
    }
    private static int targetAlpha(Drawable drawable, Surface state, ControlCenterConfig config) {
        View owner = state.owner.get();
        int amount = owner == null || preservesNativeMaterial(owner) ? 100 : opacityPercent(
                percent(config, state.layer, ControlCenterNativeMode.isEditing(owner)));
        float alpha = state.alpha;
        ControlCenterSurface style = drawableSurface(state, config);
        if (drawable instanceof ColorDrawable) {
            int selected = style == ControlCenterSurface.DEFAULT ? state.color.getDefaultColor() : containerColor(owner, style);
            alpha = (selected >>> 24) * state.colorAlphaFactor;
        }
        return Math.round(scale(alpha, amount));
    }
    private static int alphaArgument(Drawable drawable, Surface state, ControlCenterConfig config) {
        if (!(drawable instanceof ColorDrawable)) return targetAlpha(drawable, state, config);
        View owner = state.owner.get();
        int amount = owner == null || preservesNativeMaterial(owner) ? 100 : opacityPercent(
                percent(config, state.layer, ControlCenterNativeMode.isEditing(owner)));
        return Math.round(scale(255f * state.colorAlphaFactor, amount));
    }
    static void drawableUpdated(Drawable drawable, ControlCenterConfig config) {
        Surface state = SURFACES.get(drawable);
        if (state != null && !APPLYING.get()) {
            View owner = state.owner.get();
            if (owner != null) ControlCenterRuntimeGrid.markMaterialsDirty(owner);
            applyDrawable(drawable, state, config);
        }
    }

    /** Keep OEM filters out of registered panel surfaces while a global material is active. */
    static ColorFilter drawableColorFilter(Drawable drawable, ColorFilter requested,
            ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) {
            PENDING_TINTS.computeIfAbsent(drawable, ignored -> new TintState()).colorFilter = requested;
            return requested;
        }
        if (!filterSurface(state)) return requested;
        state.colorFilter = requested;
        return drawableSurface(state, config) == ControlCenterSurface.DEFAULT ? requested : null;
    }

    static ColorStateList drawableTint(Drawable drawable, ColorStateList requested,
            ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) {
            TintState pending = PENDING_TINTS.computeIfAbsent(drawable, ignored -> new TintState());
            pending.tintList = requested;
            pending.tintCaptured = true;
            return requested;
        }
        if (!filterSurface(state)) return requested;
        state.tintList = requested;
        state.tintCaptured = true;
        state.filtersDirty = true;
        return requested;
    }

    static int drawableTint(Drawable drawable, int requested, ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) {
            TintState pending = PENDING_TINTS.computeIfAbsent(drawable, ignored -> new TintState());
            pending.tintList = ColorStateList.valueOf(requested);
            pending.tintCaptured = true;
            return requested;
        }
        if (!filterSurface(state)) return requested;
        state.tintList = ColorStateList.valueOf(requested);
        state.tintCaptured = true;
        state.filtersDirty = true;
        return requested;
    }

    static PorterDuff.Mode drawableTintMode(Drawable drawable, PorterDuff.Mode requested,
            ControlCenterConfig config) {
        if (APPLYING.get()) return requested;
        Surface state = SURFACES.get(drawable);
        if (state == null) {
            TintState pending = PENDING_TINTS.computeIfAbsent(drawable, ignored -> new TintState());
            pending.tintMode = requested;
            pending.tintCaptured = true;
            return requested;
        }
        if (!filterSurface(state)) return requested;
        state.tintMode = requested;
        state.tintCaptured = true;
        state.filtersDirty = true;
        return requested;
    }

    static float windowRatio(Object owner, float nativeRatio, ControlCenterConfig config, boolean editing) {
        if (APPLYING.get()) return nativeRatio;
        WINDOW_RATIOS.put(owner, nativeRatio);
        return scale(nativeRatio, config.enabled && !editing ? config.backgroundBlur : 100);
    }

    static Object[] nativeArguments(View view, String operation, Object[] arguments, ControlCenterConfig config, boolean editing) {
        if (APPLYING.get() || layer(view) == Layer.NONE) return arguments;
        // Blur animation changes scalar values, not the component's background tree.
        // Keep recording native requests for restoration without rebuilding its slot.
        if (!operation.equals("setMiBackgroundBlurRadius")
                && !operation.equals("setMiBackgroundBlurScaleRatio")) {
            ControlCenterRuntimeGrid.markMaterialsDirty(view);
        }
        syncComponentSurfaceOwnership(view, config, editing);
        NativeSurface state = NATIVE.computeIfAbsent(view, ignored -> new NativeSurface());
        if (operation.equals("clearMiBackgroundBlendColor")) {
            state.requests.put("setMiBackgroundBlendColors", new Object[] {new ArrayList<Point>()});
            state.blendAdjusted = false;
            return arguments;
        }
        state.requests.put(operation, copy(arguments));
        if (operation.equals("setMiBackgroundBlendColors") && softwareSurface(view, config, editing))
            clearNativeBlend(view);
        if (preservesNativeMaterial(view)) {
            if (operation.equals("setMiBackgroundBlendColors")) state.blendAdjusted = false;
            return effectArguments(view, operation, arguments, config, editing);
        }
        if (state.overridden && arguments.length == 1 && arguments[0] instanceof Integer requested) {
            if (operation.equals("setMiBackgroundBlurRadius")) state.radius = requested;
            if (operation.equals("setMiBackgroundBlurMode")) state.mode = requested;
        }
        Object[] transformed = transform(view, operation, arguments, percent(config, layer(view), editing),
                nativeSurface(view, config, editing));
        if (operation.equals("setMiBackgroundBlendColors")) state.blendAdjusted = transformed != arguments;
        return effectArguments(view, operation, transformed, config, editing);
    }
    private static Object[] copy(Object[] args) {
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof float[] values) copy[i] = values.clone();
        }
        if (copy.length == 1 && copy[0] instanceof ArrayList<?> list) {
            ArrayList<Point> points = new ArrayList<>();
            for (Object value : list) if (value instanceof Point point) points.add(new Point(point));
            copy[0] = points;
        }
        return copy;
    }

    private static void clearNativeBlend(View view) {
        try {
            guardedThrowing(() -> invoke(view, "clearMiBackgroundBlendColor"));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            try {
                guardedThrowing(() -> invoke(view, "setMiBackgroundBlendColors", new ArrayList<Point>()));
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }
    private static Object[] transform(View view, String operation, Object[] args, int amount, ControlCenterSurface surface) {
        if (surface == ControlCenterSurface.CLEAR) amount = 0;
        if ((amount == 100 && surface == ControlCenterSurface.DEFAULT) || args.length != 1) return args;
        Object[] next = args.clone();
        if (operation.equals("setMiBackgroundBlendColors") && args[0] instanceof ArrayList<?> list) {
            if (list.isEmpty()) return args;
            ArrayList<Point> colors = new ArrayList<>();
            if (surface != ControlCenterSurface.DEFAULT) {
                // Use the same translucent tint as the Canvas surface. Overlay/color-dodge
                // brightening washes out the wallpaper behind the daytime glass.
                colors.add(new Point(color(containerColor(view, surface), opacityPercent(amount)), 3));
            } else for (Object value : list) if (value instanceof Point point) {
                colors.add(new Point(color(point.x, amount), point.y));
            }
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
        syncComponentSurfaceOwnership(view, config, editing);
        NativeSurface state = NATIVE.get(view);
        if (state == null) return;
        syncNativeEffects(view, state, config, editing);
        if (preservesNativeMaterial(view) && state.blendAdjusted) {
            Object[] blend = state.requests.get("setMiBackgroundBlendColors");
            if (blend != null) {
                try {
                    guardedThrowing(() -> invoke(view, "setMiBackgroundBlendColors", blend[0]));
                    state.blendAdjusted = false;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        Object[] modeRequest = state.requests.get("setMiViewBlurMode");
        if (modeRequest == null || !(modeRequest[0] instanceof Integer mode)) return;
        boolean software = softwareSurface(view, config, editing);
        int amount = preservesNativeMaterial(view) ? 100 : percent(config, layer(view), editing);
        ControlCenterSurface surface = nativeSurface(view, config, editing);
        try {
            if (!config.enabled || editing || mode <= 0 && !state.ownedContainer && !software
                    || !software && surface == ControlCenterSurface.DEFAULT && amount == 100
                    && !state.ownedContainer) {
                if (state.overridden) {
                    guardedThrowing(() -> {
                        invoke(view, "setMiBackgroundBlurMode", state.mode);
                        invoke(view, "setMiBackgroundBlurRadius", state.radius);
                        invoke(view, "setMiViewBlurMode", state.ownedContainer ? 0 : mode);
                    });
                    state.overridden = false;
                }
                if (state.syntheticTint) {
                    Object[] blend = state.requests.get("setMiBackgroundBlendColors");
                    guardedThrowing(() -> invoke(view, "setMiBackgroundBlendColors",
                            blend == null ? new ArrayList<Point>() : blend[0]));
                    state.syntheticTint = false;
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
            int blur = software || surface == ControlCenterSurface.CLEAR ? 0 : amount;
            guardedThrowing(() -> {
                // Mode 1 draws the native surface; mode 2 requires custom-drawn
                // content. Blur-only cards have no Drawable to use as that mask.
                invoke(view, "setMiViewBlurMode", blur == 0
                        ? 0 : state.ownedContainer ? 2 : mode);
                invoke(view, "setMiBackgroundBlurMode", blur == 0 ? 0 : 1);
                invoke(view, "setMiBackgroundBlurRadius", Math.round(275f * blur / 100));
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!state.failureLogged) {
                state.failureLogged = true;
                android.util.Log.w("FusionStatusBar", "Native per-surface blur unsupported; keeping available material controls", error);
            }
        }
        if (surface != ControlCenterSurface.DEFAULT && view.getBackground() == null
                && !softwareSurface(view, config, editing)) {
            try {
                Object[] blend = state.requests.get("setMiBackgroundBlendColors");
                Object[] tint = blend == null
                        ? new Object[] {new ArrayList<>(java.util.List.of(new Point(surface.color, 3)))} : blend;
                guardedThrowing(() -> invoke(view, "setMiBackgroundBlendColors",
                        transform(view, "setMiBackgroundBlendColors", tint, amount, surface)));
                state.syntheticTint = blend == null;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }

    static void refresh(ControlCenterConfig config) {
        for (ThemeObserver observer : new ArrayList<>(THEME_ROOTS.values())) observer.configure(config);
        if (config.enabled && config.surface == ControlCenterSurface.HIGHLIGHT_GLASS) {
            // A material-only settings update can enable this style without rebinding the panel.
            for (View view : new ArrayList<>(GLASS_VIEWS.keySet())) observeTheme(view, config);
            for (View view : new ArrayList<>(NATIVE.keySet())) observeTheme(view, config);
            for (Surface state : new ArrayList<>(SURFACES.values())) {
                View view = state.owner.get();
                if (view != null) observeTheme(view, config);
            }
        }
        for (ControlCenterMaterialSession session : new ArrayList<>(MATERIAL_SESSIONS.values())) {
            View view = session.owner();
            if (view == null) continue;
            session.refresh(config);
            trimMaterialSession(view, session);
        }
        for (View view : new ArrayList<>(NATIVE.keySet()))
            syncComponentSurfaceOwnership(view, config, ControlCenterNativeMode.isEditing(view));
        for (View tile : new ArrayList<>(NATIVE_TILES.keySet())) syncNativeTile(tile, config);
        for (Map.Entry<Drawable, Surface> entry : new ArrayList<>(SURFACES.entrySet())) applyDrawable(entry.getKey(), entry.getValue(), config);
        for (Map.Entry<View, Layer> entry : new ArrayList<>(GLASS_VIEWS.entrySet())) {
            applySoftGlass(entry.getKey(), config, entry.getValue());
        }
        for (View view : new ArrayList<>(BORDER_VIEWS.keySet())) applyGlobalBorder(view, config);
        for (Map.Entry<View, NativeSurface> entry : new ArrayList<>(NATIVE.entrySet())) {
            View view = entry.getKey();
            int amount = preservesNativeMaterial(view) ? 100
                    : percent(config, layer(view), ControlCenterNativeMode.isEditing(view));
            for (Map.Entry<String, Object[]> request : new ArrayList<>(entry.getValue().requests.entrySet())) {
                // Owned-container mode is synthetic, not an OEM value to replay on disable.
                if (entry.getValue().ownedContainer && request.getKey().equals("setMiViewBlurMode")) continue;
                try {
                    Object[] replay = transform(view, request.getKey(), request.getValue(), amount,
                            nativeSurface(view, config, ControlCenterNativeMode.isEditing(view)));
                    replay = effectArguments(view, request.getKey(), replay, config, ControlCenterNativeMode.isEditing(view));
                    Object[] effective = replay;
                    guardedThrowing(() -> invoke(view, request.getKey(), effective));
                    if (request.getKey().equals("setMiBackgroundBlendColors"))
                        entry.getValue().blendAdjusted = replay != request.getValue();
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (softwareSurface(view, config, ControlCenterNativeMode.isEditing(view))
                    && entry.getValue().requests.containsKey("setMiBackgroundBlendColors"))
                clearNativeBlend(view);
            updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
        }
        for (Map.Entry<Object, Float> entry : new ArrayList<>(WINDOW_RATIOS.entrySet())) {
            try { guardedThrowing(() -> invoke(entry.getKey(), "setBlurRatio", scale(entry.getValue(),
                    config.enabled && !ControlCenterNativeMode.isEditing(entry.getKey()) ? config.backgroundBlur : 100))); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        for (Map.Entry<TextView, ColorStateList> entry : new ArrayList<>(TEXT.entrySet()))
            guarded(() -> entry.getKey().setTextColor(contrast(entry.getValue(), textSurface(entry.getKey(), config))));
        // Grid decorations own glass overlays outside GLASS_VIEWS. Update and
        // invalidate their shaders before HWUI can reuse a recorded display list.
        ControlCenterSoftGlass.refreshTheme();
    }

    static void registerRoot(View view, ControlCenterConfig config) {
        if (view != null && config.enabled && config.surface == ControlCenterSurface.HIGHLIGHT_GLASS
                && layer(view) != Layer.NONE && !(view instanceof TextView) && !isSliderForeground(view))
            ControlCenterGlassCapabilities.reportCompatibility(view);
        observeTheme(view, config);
        if (TILE_SHAPES.containsKey(view) && !isGridDecorationManaged(view))
            releaseGridSurface(view, config);
        registerTree(view, config, 0, new int[] {0});
        if (view.getClass().getSimpleName().contains("QSTileItemView")) {
            NATIVE_TILES.put(view, Boolean.TRUE);
            syncNativeTile(view, config);
        }
    }

    static void observeTheme(View view, ControlCenterConfig config) {
        if (view == null || config == null || !config.enabled
                || config.surface != ControlCenterSurface.HIGHLIGHT_GLASS) return;
        View root = view.getRootView();
        ThemeObserver observer = THEME_ROOTS.get(root);
        if (observer == null) {
            observer = new ThemeObserver(root);
            THEME_ROOTS.put(root, observer);
            observer.track(view.getResources());
            observer.configure(config);
        } else {
            observer.config = config;
            observer.track(root.getResources());
            observer.track(view.getResources());
        }
    }

    /** Check distinct resource groups, since plugin resources can update after their window. */
    private static final class ThemeObserver implements android.view.ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        private final WeakReference<View> owner;
        private WeakReference<android.view.ViewTreeObserver> tree = new WeakReference<>(null);
        private final ArrayList<ResourceStamp> resources = new ArrayList<>();
        private ControlCenterConfig config;

        ThemeObserver(View root) {
            owner = new WeakReference<>(root);
            track(root.getResources());
            root.addOnAttachStateChangeListener(this);
            if (root.isAttachedToWindow()) onViewAttachedToWindow(root);
        }

        void track(Resources value) {
            if (value == null) return;
            for (int index = resources.size() - 1; index >= 0; index--) {
                Resources existing = resources.get(index).owner.get();
                // Re-registering the same resource group must not accept a pending
                // mode change as already applied before the material refresh.
                if (existing == value) return;
                if (existing == null) resources.remove(index);
            }
            resources.add(new ResourceStamp(value));
        }

        boolean hasPendingChanges() {
            boolean changed = false;
            for (int index = resources.size() - 1; index >= 0; index--) {
                ResourceStamp stamp = resources.get(index);
                Resources value = stamp.owner.get();
                if (value == null) resources.remove(index);
                else if (stamp.changed(value)) changed = true;
            }
            return changed;
        }

        void configure(ControlCenterConfig next) {
            config = next;
            View root = owner.get();
            if (root == null) return;
            if (!next.enabled || next.surface != ControlCenterSurface.HIGHLIGHT_GLASS) {
                dispose(root);
                return;
            }
            for (int index = resources.size() - 1; index >= 0; index--) {
                ResourceStamp stamp = resources.get(index);
                Resources value = stamp.owner.get();
                if (value == null) resources.remove(index);
                else stamp.capture(value);
            }
        }

        @Override public boolean onPreDraw() {
            View root = owner.get();
            if (root == null || config == null || !config.enabled
                    || config.surface != ControlCenterSurface.HIGHLIGHT_GLASS) return true;
            // A surface registered before attachment may now share a window with other surfaces.
            if (root.getRootView() != root) {
                boolean pending = hasPendingChanges();
                dispose(root);
                observeTheme(root, config);
                ThemeObserver merged = THEME_ROOTS.get(root.getRootView());
                if (merged != null) {
                    for (int index = 0; index < resources.size(); index++)
                        merged.track(resources.get(index).owner.get());
                }
                // Ordinary attachment only merges observers. A change that happened
                // while detached still needs application after ownership has moved.
                if (pending) refresh(config);
                return true;
            }
            if (hasPendingChanges()) refresh(config);
            return true;
        }

        @Override public void onViewAttachedToWindow(View root) {
            onViewDetachedFromWindow(root);
            android.view.ViewTreeObserver observer = root.getViewTreeObserver();
            tree = new WeakReference<>(observer);
            observer.addOnPreDrawListener(this);
        }
        @Override public void onViewDetachedFromWindow(View root) {
            android.view.ViewTreeObserver observer = tree.get();
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            tree.clear();
        }
        void dispose(View root) {
            onViewDetachedFromWindow(root);
            root.removeOnAttachStateChangeListener(this);
            THEME_ROOTS.remove(root);
        }
    }

    private static final class ResourceStamp {
        final WeakReference<Resources> owner;
        private int nightMode, densityDpi;
        private float density;

        ResourceStamp(Resources resources) {
            owner = new WeakReference<>(resources);
            capture(resources);
        }

        boolean changed(Resources resources) {
            Configuration configuration = resources.getConfiguration();
            return nightMode != (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    || densityDpi != configuration.densityDpi
                    || Float.compare(density, resources.getDisplayMetrics().density) != 0;
        }

        void capture(Resources resources) {
            Configuration configuration = resources.getConfiguration();
            nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            densityDpi = configuration.densityDpi;
            density = resources.getDisplayMetrics().density;
        }
    }

    static void nativeTileUpdated(View iconContainer, ControlCenterConfig config) {
        if (APPLYING.get()) return;
        for (View current = iconContainer; current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getClass().getSimpleName().contains("QSTileItemView")) {
                ControlCenterRuntimeGrid.markMaterialsDirty(current);
                registerRoot(current, config);
                return;
            }
        }
    }

    private static void syncNativeTile(View tile, ControlCenterConfig config) {
        if (isGridDecorationManaged(tile)) return;
        View surfaceView = tileSurface(tile);
        if (!(surfaceView instanceof android.widget.ImageView image)) return;
        ControlCenterSurface style = surface(config, ControlCenterNativeMode.isEditing(tile)).resolve(tile.getContext());
        Drawable content = image.getDrawable();
        if (content instanceof LayerDrawable layers && layers.getNumberOfLayers() > 1) {
            int glyph = layers.getNumberOfLayers() - 1;
            for (int i = 0; i < glyph; i++) register(tile, layers.getDrawable(i), Layer.TILE, config);
            restoreNativeTileBackground(image);
            return;
        }
        if (style == ControlCenterSurface.DEFAULT) {
            restoreNativeTileBackground(image);
            return;
        }
        NativeTileBackground current = NATIVE_TILE_BACKGROUNDS.get(image);
        if (current == null || image.getBackground() != current) {
            current = new NativeTileBackground(image, image.getBackground());
            NATIVE_TILE_BACKGROUNDS.put(image, current);
            image.setBackground(current);
            if (current.getDrawable() != null) current.getDrawable().setCallback(current);
        }
        current.setSurfaceColor(color(containerColor(tile, style), opacityPercent(config.tileBlur)));
    }

    private static void restoreNativeTileBackground(View image) {
        NativeTileBackground background = NATIVE_TILE_BACKGROUNDS.remove(image);
        if (background != null && image.getBackground() == background)
            image.setBackground(background.getDrawable());
    }

    /** Records the geometry owned by the custom grid for the OEM icon surface. */
    static void registerGridTileShape(View tile, ControlCenterLayoutPlan.Item item) {
        if (tile == null || item == null) return;
        TileShape current = TILE_SHAPES.get(tile);
        if (current != null && current.shape == item.shape && current.radiusDp == item.cornerRadius) return;
        TILE_SHAPES.put(tile, new TileShape(item.shape, item.cornerRadius));
    }

    /** Returns whether the surface is managed, including an unchanged or restored surface. */
    static boolean syncTileView(View tile, ControlCenterConfig config) {
        return syncTileView(tile, config, false);
    }

    static boolean syncTileView(View tile, ControlCenterConfig config, boolean externalSurface) {
        if (tile == null) return false;
        if (!config.enabled || ControlCenterNativeMode.isEditing(tile)) {
            releaseGridSurface(tile, config);
            return true;
        }
        View icon = findImage(tile, 0);
        TileShape shape = TILE_SHAPES.get(tile);
        if (shape == null) shape = new TileShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
        if (!(icon instanceof android.widget.ImageView image)) return false;
        restoreNativeTileBackground(image);
        if (!externalSurface) applyShapeClip(image, shape.shape, shape.radiusDp);
        Object state = field(tile, "state");
        if (state == null) state = field(tile, "customizeState");
        if (state == null) state = field(tile, "tileState");
        Object stateValue = state instanceof Number ? state : field(state, "state");
        boolean active = config.enabled && !ControlCenterNativeMode.isEditing(tile)
                && stateValue instanceof Number && ((Number) stateValue).intValue() == 2;
        NativeSurface nativeState = NATIVE.computeIfAbsent(image, ignored -> new NativeSurface());
        nativeState.softwareSurface = externalSurface || active || surface(config, ControlCenterNativeMode.isEditing(tile))
                != ControlCenterSurface.DEFAULT;
        updateNativeSurface(image, config, ControlCenterNativeMode.isEditing(tile));
        if (!(image.getDrawable() instanceof LayerDrawable layers)) {
            syncIconBackground(image, shape, active, config, externalSurface);
            return true;
        }
        int glyph = -1;
        for (int i = layers.getNumberOfLayers() - 1; i >= 0; i--) {
            if (layers.getLayerWidth(i) > 0 && layers.getLayerHeight(i) > 0) {
                glyph = i;
                break;
            }
        }
        // Some OEM revisions do not expose LayerDrawable child sizes. Their
        // composition still keeps the last child as the glyph.
        if (glyph < 0 && layers.getNumberOfLayers() > 1) glyph = layers.getNumberOfLayers() - 1;
        if (glyph <= 0) {
            // In native blur mode the LayerDrawable contains only the glyph.
            // Its ImageView, not the padded holder, owns the visible surface.
            syncIconBackground(image, shape, active, config, externalSurface);
            return true;
        }
        restoreIconBackground(image);
        boolean changed = false;
        for (int i = 0; i < glyph; i++) {
            Drawable current = layers.getDrawable(i);
            TileSurfaceDrawable surface = current instanceof TileSurfaceDrawable wrapped
                    ? wrapped : new TileSurfaceDrawable(current);
            changed |= surface.configure(shape, active, config.resolveSurface(image.getContext()), i == 0 && !externalSurface, externalSurface);
            if (surface != current) {
                layers.setDrawable(i, surface);
                // Replacing a drawable clears the outgoing child's callback.
                current.setCallback(surface);
                changed = true;
            }
        }
        if (changed) image.invalidate();
        return true;
    }

    private static void syncIconBackground(View image, TileShape shape, boolean active,
            ControlCenterConfig config, boolean externalSurface) {
        ControlCenterSurface style = surface(config, ControlCenterNativeMode.isEditing(image)).resolve(image.getContext());
        if (!externalSurface && !active && (style == ControlCenterSurface.DEFAULT || style == ControlCenterSurface.CLEAR)) {
            restoreIconBackground(image);
            return;
        }
        TileSurfaceDrawable current = ICON_BACKGROUNDS.get(image);
        if (current == null || image.getBackground() != current) {
            current = new TileSurfaceDrawable(image.getBackground());
            ICON_BACKGROUNDS.put(image, current);
            image.setBackground(current);
            if (current.nativeDrawable != null) current.nativeDrawable.setCallback(current);
        }
        current.configure(shape, active, config.resolveSurface(image.getContext()), !externalSurface, externalSurface);
    }

    static void applyTileBacking(View backing, View tile, ControlCenterConfig config) {
        if (!(backing.getBackground() instanceof GradientDrawable background)) return;
        SURFACES.remove(background);
        ControlCenterSurface style = surface(config, ControlCenterNativeMode.isEditing(tile)).resolve(tile.getContext());
        int fill = config.enabled && !ControlCenterNativeMode.isEditing(tile)
                ? color(style.stateColor(activeTile(tile)), opacityPercent(config.tileBlur)) : 0;
        if (background.getColor() == null || background.getColor().getDefaultColor() != fill)
            guarded(() -> background.setColor(fill));
    }

    private static void restoreIconBackground(View image) {
        TileSurfaceDrawable current = ICON_BACKGROUNDS.remove(image);
        if (current != null && image.getBackground() == current)
            image.setBackground(current.nativeDrawable);
    }

    static boolean syncComponentView(View view, ControlCenterLayoutPlan.Item item, ControlCenterConfig config) {
        if (!inactiveComponentContainer(view)) return false;
        if (!config.enabled || ControlCenterNativeMode.isEditing(view)) {
            releaseGridSurface(view, config);
            registerRoot(view, config);
            return true;
        }
        applyShapeClip(view, item.shape, item.cornerRadius);
        if (surface(config, ControlCenterNativeMode.isEditing(view)) == ControlCenterSurface.DEFAULT) {
            restoreComponentBackground(view);
            updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
            return true;
        }
        ControlCenterMaterialSession session = materialSession(view);
        if (session.needsComponentBackground(view)) {
            Drawable nativeBackground = view.getBackground();
            Surface registered = SURFACES.remove(nativeBackground);
            if (registered != null) applyDrawable(nativeBackground, registered, config.withEnabled(false));
        }
        session.applyComponentBackground(item, config);
        updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
        return true;
    }

    private static void restoreComponentBackground(View view) {
        ControlCenterMaterialSession session = MATERIAL_SESSIONS.get(view);
        if (session == null) return;
        session.restoreComponentBackground();
        trimMaterialSession(view, session);
    }

    static boolean syncCardView(View card, ControlCenterLayoutPlan.Item item) {
        if (card == null || item == null) return false;
        // QSCardItemView clears its background when native blur is enabled;
        // clipping must still be installed for that hardware-rendered layer.
        applyShapeClip(card, item.shape, item.cornerRadius);
        if (card.getBackground() == null) return true;
        materialSession(card).applyCardBackground(item);
        return true;
    }

    static void applyShapeClip(View view, ControlCenterLayoutPlan.Shape shape,
            int radiusDp) {
        materialSession(view).applyShapeClip(shape, radiusDp);
    }

    static void releaseGridSurface(View view, ControlCenterConfig config) {
        TILE_SHAPES.remove(view);
        synchronized (DIRECT_LAYERS) { DIRECT_LAYERS.remove(view); }
        if (view instanceof android.widget.ImageView image && image.getDrawable() instanceof LayerDrawable layers) {
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (layers.getDrawable(i) instanceof TileSurfaceDrawable surface)
                    layers.setDrawable(i, surface.nativeDrawable);
            }
        }
        restoreIconBackground(view);
        ControlCenterMaterialSession materialSession = MATERIAL_SESSIONS.get(view);
        if (materialSession != null) {
            materialSession.release();
            trimMaterialSession(view, materialSession);
        }
        NativeSurface nativeState = NATIVE.get(view);
        if (nativeState != null && nativeState.softwareSurface) {
            nativeState.softwareSurface = false;
            updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) releaseGridSurface(group.getChildAt(i), config);
        }
    }

    static View tileSurface(View tile) {
        return findImage(tile, 0);
    }

    static String describeTileSurface(View tile) {
        StringBuilder result = new StringBuilder();
        appendSurfaceTree(result, tile, 0, new int[] {0});
        return result.length() <= 4096 ? result.toString() : result.substring(0, 4096);
    }

    private static void appendSurfaceTree(StringBuilder out, View view, int depth, int[] count) {
        if (depth > 8 || count[0]++ >= 20) return;
        out.append('[').append(depth).append(':').append(view.getClass().getSimpleName())
                .append('#').append(resourceName(view)).append(' ')
                .append(view.getLeft()).append(',').append(view.getTop()).append('+')
                .append(view.getWidth()).append('x').append(view.getHeight())
                .append(" clip=").append(view.getClipToOutline()).append(" z=").append(view.getZ())
                .append(" alpha=").append(view.getAlpha());
        ViewOutlineProvider provider = view.getOutlineProvider();
        if (provider != null) {
            Outline outline = new Outline();
            outline.setAlpha(1f);
            try {
                provider.getOutline(view, outline);
                out.append(" outlineAlpha=").append(outline.getAlpha());
            } catch (RuntimeException unavailable) { out.append(" outlineAlpha=unavailable"); }
        }
        out.append(" bg=");
        appendSurfaceDrawable(out, view.getBackground(), 0);
        out.append(" fg=");
        appendSurfaceDrawable(out, view.getForeground(), 0);
        if (view instanceof android.widget.ImageView image) {
            out.append(" image=");
            appendSurfaceDrawable(out, image.getDrawable(), 0);
            out.append(" matrix=").append(image.getImageMatrix().toShortString());
        }
        NativeSurface state = NATIVE.get(view);
        if (state != null) {
            out.append(" software=").append(state.softwareSurface)
                    .append(" effectPolicy=").append(state.effectPolicy).append(" requests=").append(state.requests.keySet());
            try {
                out.append(" nativeBgBlur=").append(invoke(view, "getMiBackgroundBlurMode"))
                        .append('/').append(invoke(view, "getMiBackgroundBlurRadius"));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                out.append(" nativeBgBlur=unavailable");
            }
        }
        out.append(']');
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) appendSurfaceTree(out, group.getChildAt(i), depth + 1, count);
        }
    }

    private static void appendSurfaceDrawable(StringBuilder out, Drawable drawable, int depth) {
        if (drawable == null) { out.append("null"); return; }
        out.append(drawable.getClass().getSimpleName()).append(drawable.getBounds().toShortString())
                .append("/a").append(drawable.getAlpha());
        if (drawable instanceof GradientDrawable gradient && gradient.getColor() != null)
            out.append("/color=").append(Integer.toHexString(gradient.getColor().getDefaultColor()));
        if (drawable instanceof TileSurfaceDrawable surface)
            out.append("/fill=").append(surface.fillLayer).append("/replace=").append(surface.replaceSurface)
                    .append("/color=").append(Integer.toHexString(surface.fillColor));
        if (depth >= 3) return;
        if (drawable instanceof DrawableWrapper wrapper) {
            out.append('{'); appendSurfaceDrawable(out, wrapper.getDrawable(), depth + 1); out.append('}');
        } else if (drawable instanceof LayerDrawable layers) {
            out.append('{');
            for (int i = 0; i < Math.min(6, layers.getNumberOfLayers()); i++) {
                out.append(i).append(':').append(layers.getLayerWidth(i)).append('x').append(layers.getLayerHeight(i)).append('=');
                appendSurfaceDrawable(out, layers.getDrawable(i), depth + 1);
                out.append(';');
            }
            out.append('}');
        }
    }

    private static View findImage(View view, int depth) {
        if (view == null || depth > 10 || view.getVisibility() != View.VISIBLE) return null;
        // Binding often precedes the asynchronous OEM icon update after restart.
        // The surface owner exists even while its glyph is still empty.
        if (view instanceof android.widget.ImageView image) return image;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findImage(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Object field(Object object, String name) {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field value = type.getDeclaredField(name);
                value.setAccessible(true);
                return value.get(object);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }
    private static void registerTree(View view, ControlCenterConfig config, int depth, int[] visited) {
        if (depth > 16 || visited[0]++ >= 512) return;
        String id = resourceName(view), name = view.getClass().getSimpleName();
        if (isSliderForeground(view)) {
            for (Map.Entry<Drawable, Surface> entry : new ArrayList<>(SURFACES.entrySet())) {
                if (entry.getValue().owner.get() == view)
                    applyDrawable(entry.getKey(), entry.getValue(), config);
            }
            updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
            return;
        }
        Layer layer = layer(view);
        boolean ownSurface = name.contains("QSTileItem") || panelContainer(view, name);
        if (((ownSurface || depth == 0) && !insidePanelSurface(view)) || id.equals("progress_bg"))
            register(view, view.getBackground(), id.equals("progress_bg") ? Layer.CARD : layer, config);
        if (isGridDecorationManaged(view)) {
            removeDecorations(view);
        } else if ((borderContainer(view, id, name) || depth == 0 && view.getBackground() != null)
                && !insideBorderContainer(view)) {
            applySoftGlass(view, config, id.equals("progress_bg") ? Layer.CARD : layer);
            applyGlobalBorder(view, config);
        }
        if (view instanceof ControlCenterGroupView) registerOwnedContainer(view, config);
        else updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
        if (view instanceof TextView text && layer != Layer.NONE && !insidePreservedIcon(view)) {
            ColorStateList original = TEXT.get(text);
            if (original == null) { original = text.getTextColors(); TEXT.put(text, original); }
            ColorStateList adjusted = contrast(original, textSurface(text, config));
            if (!java.util.Objects.equals(text.getTextColors(), adjusted)) guarded(() -> text.setTextColor(adjusted));
        }
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                registerTree(group.getChildAt(i), config, depth + 1, visited);
            }
        }
    }
    private static String resourceName(View view) {
        if (view.getId() != View.NO_ID) {
            try { return view.getResources().getResourceEntryName(view.getId()); }
            catch (android.content.res.Resources.NotFoundException ignored) { }
        }
        return "";
    }
    private static boolean isSliderForeground(View view) {
        View current = view;
        for (int depth = 0; current != null && depth < 8; depth++) {
            String id = resourceName(current);
            if (id.equals("progress") || id.equals("progress_fill")
                    || id.equals("progress_foreground") || id.equals("slider_progress")) return true;
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }
    private static boolean preservesNativeMaterial(View view) {
        // MIUI also uses the blur API to color text and standalone glyphs. Those
        // calls must not promote the content's rectangular bounds to a surface.
        NativeSurface nativeState = NATIVE.get(view);
        if (nativeState != null && nativeState.softwareSurface && inactiveComponentContainer(view)) return false;
        Object[] mode = nativeState == null ? null : nativeState.requests.get("setMiViewBlurMode");
        boolean foregroundBlend = mode != null && mode.length == 1 && Integer.valueOf(3).equals(mode[0]);
        return foregroundBlend || view instanceof TextView || isSliderForeground(view) || insidePreservedIcon(view);
    }

    private static boolean inactiveComponentContainer(View view) {
        String name = view.getClass().getSimpleName();
        return name.equals("MediaPlayerPanel") || name.equals("DeviceCenterEntryFrameLayout");
    }

    private static void syncComponentSurfaceOwnership(View view, ControlCenterConfig config, boolean editing) {
        if (!inactiveComponentContainer(view)) return;
        Drawable background = view.getBackground();
        NativeSurface state = NATIVE.computeIfAbsent(view, ignored -> new NativeSurface());
        // These components already paint their inactive surface. Native composition
        // would tint that same fill again, unlike the software-backed tile surface.
        state.softwareSurface = surface(config, editing) != ControlCenterSurface.DEFAULT
                && (background instanceof GradientDrawable || background instanceof ColorDrawable
                || background != null && MATERIAL_SESSIONS.get(view) != null
                && MATERIAL_SESSIONS.get(view).componentBackground == background);
    }

    private static boolean softwareSurface(View view, ControlCenterConfig config, boolean editing) {
        NativeSurface state = NATIVE.get(view);
        return state != null && state.softwareSurface && config.enabled && !editing;
    }

    private static Object[] effectArguments(View view, String operation, Object[] args,
            ControlCenterConfig config, boolean editing) {
        boolean software = softwareSurface(view, config, editing);
        boolean decoration = config.enabled && !editing
                && (software || config.borderEnabled || config.surface.hasSoftGlassReflection());
        return ControlCenterNativeEffects.arguments(operation, args, decoration, software);
    }

    private static void syncNativeEffects(View view, NativeSurface state,
            ControlCenterConfig config, boolean editing) {
        boolean software = softwareSurface(view, config, editing);
        boolean decoration = config.enabled && !editing
                && (software || config.borderEnabled || config.surface.hasSoftGlassReflection());
        // Bloom is a legacy outline API, not the 42-parameter MiGlass material.
        // Compatibility rendering must not synthesize a native glass profile or turn mix on.
        int policy = (decoration ? 1 : 0) | (software ? 2 : 0);
        if (state.effectPolicy == policy) return;
        boolean wasSoftware = state.effectPolicy >= 0 && (state.effectPolicy & 2) != 0;
        for (Map.Entry<String, Object[]> entry : state.requests.entrySet()) {
            String operation = entry.getKey();
            if (!ControlCenterNativeEffects.decoration(operation)
                    && !((software || wasSoftware) && ControlCenterNativeEffects.composition(operation))) continue;
            if (software && operation.equals("setMiBackgroundBlendColors")) {
                clearNativeBlend(view);
                continue;
            }
            Object[] effective = effectArguments(view, operation, entry.getValue(), config, editing);
            try { guardedThrowing(() -> invoke(view, operation, effective)); }
            catch (ReflectiveOperationException | RuntimeException error) {
                if (!state.effectsFailureLogged) {
                    state.effectsFailureLogged = true;
                    android.util.Log.w("FusionStatusBar", "Native material effect update failed: " + operation, error);
                }
            }
        }
        state.effectPolicy = policy;
    }
    private static boolean borderContainer(View view, String id, String name) {
        return panelContainer(view, name) || name.contains("QSTileItemView")
                || id.equals("progress_bg");
    }
    private static boolean panelContainer(View view, String name) {
        return view instanceof ControlCenterGroupView || name.contains("QSCardItemView")
                || name.contains("MediaPlayerPanel") || name.contains("ToggleSliderView")
                || name.contains("DeviceCenterEntry") || name.contains("DeviceControlsEntry")
                || name.contains("CompactQsCard");
    }
    private static boolean isUnlabelledPanelBackground(View view) {
        if (view == null || isSliderForeground(view)) return false;
        String name = view.getClass().getSimpleName();
        return name.contains("QSCardItemView") || name.contains("MediaPlayerPanel")
                || name.contains("DeviceCenterEntry") || name.contains("DeviceControlsEntry")
                || name.contains("CompactQsCard") || view instanceof ControlCenterGroupView;
    }
    private static boolean insideBorderContainer(View view) {
        Object parent = view.getParent();
        for (int depth = 0; parent instanceof View ancestor && depth < 16; depth++) {
            if (borderContainer(ancestor, resourceName(ancestor), ancestor.getClass().getSimpleName()))
                return true;
            parent = ancestor.getParent();
        }
        return false;
    }
    private static boolean insidePanelSurface(View view) {
        Object parent = view.getParent();
        for (int depth = 0; parent instanceof View ancestor && depth < 16; depth++) {
            if (panelContainer(ancestor, ancestor.getClass().getSimpleName())) return true;
            parent = ancestor.getParent();
        }
        return false;
    }
    private static boolean insidePreservedIcon(View view) {
        Object parent = view.getParent();
        String ownName = view.getClass().getSimpleName();
        if (ownName.contains("QSCardItemIconView")) return true;
        boolean icon = ownName.contains("QSTileItemIconView");
        for (int depth = 0; parent instanceof View ancestor && depth < 16; depth++) {
            if (ancestor instanceof ControlCenter5GTileView) return true;
            String name = ancestor.getClass().getSimpleName();
            if (name.contains("QSCardItemIconView")) return true;
            if (name.contains("QSTileItemIconView")) icon = true;
            if (icon && panelContainer(ancestor, name)) return true;
            parent = ancestor.getParent();
        }
        return false;
    }
    private static boolean isGridDecorationManaged(View view) {
        if (GRID_CARRIERS.containsKey(view)) return true;
        Object parent = view.getParent();
        for (int depth = 0; parent instanceof View ancestor && depth < 16; depth++) {
            if (ancestor instanceof ControlCenterRuntimeGrid.GridView grid)
                return grid.managesDecoration(view);
            parent = ancestor.getParent();
        }
        return false;
    }
    private static void removeDecorations(View view) {
        BORDER_VIEWS.remove(view);
        GLASS_VIEWS.remove(view);
        WeakReference<View> previous = DECORATION_TARGETS.remove(view);
        View target = previous == null ? null : previous.get();
        if (target != null && target != view) {
            ControlCenterGlobalBorder.apply(target, false, 1, false);
            ControlCenterSoftGlass.apply(target, false, 0);
        }
        ControlCenterGlobalBorder.apply(view, false, 1, false);
        ControlCenterSoftGlass.apply(view, false, 0);
    }

    private static View decorationTarget(View owner) {
        View target = owner;
        String name = owner.getClass().getSimpleName();
        if (name.contains("QSTileItemView")) {
            View image = findImage(owner, 0);
            if (image != null) target = image;
        } else if (name.contains("ToggleSliderView")) {
            View track = findSliderTrack(owner, 0);
            if (track != null) target = track;
        }
        WeakReference<View> previous = DECORATION_TARGETS.get(owner);
        View old = previous == null ? owner : previous.get();
        if (old != null && old != target) {
            ControlCenterGlobalBorder.apply(old, false, 1, false);
            ControlCenterSoftGlass.apply(old, false, 0);
            restoreNativeTileBackground(old);
        }
        DECORATION_TARGETS.put(owner, new WeakReference<>(target));
        return target;
    }

    private static View findSliderTrack(View view, int depth) {
        if (depth > 10) return null;
        if (resourceName(view).equals("progress_bg")) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View track = findSliderTrack(group.getChildAt(i), depth + 1);
                if (track != null) return track;
            }
        }
        return null;
    }
    static void setGridCarrier(View view, boolean active) {
        if (active) {
            GRID_CARRIERS.put(view, Boolean.TRUE);
            removeDecorations(view);
        } else GRID_CARRIERS.remove(view);
    }
    static boolean isGridCarrier(View view) { return view != null && GRID_CARRIERS.containsKey(view); }
    static void applyGlobalBorder(View view, ControlCenterConfig config) {
        if (view == null) return;
        if (isGridDecorationManaged(view)) {
            removeDecorations(view);
            return;
        }
        BORDER_VIEWS.put(view, Boolean.TRUE);
        String name = view.getClass().getSimpleName();
        boolean hasSurface = view.getBackground() != null
                || view instanceof ControlCenterGroupView
                || borderContainer(view, resourceName(view), name);
        boolean active = hasSurface && !insideBorderContainer(view)
                && config.enabled && config.borderEnabled
                && !ControlCenterNativeMode.isEditing(view);
        ControlCenterGlobalBorder.apply(decorationTarget(view), active, config.borderWidthDp,
                config.surface.resolve(view.getContext()).neutralForeground() == 0xff202124);
    }

    static void applySoftGlass(View view, ControlCenterConfig config, Layer layer) {
        if (view == null || layer == Layer.NONE) return;
        if (isGridDecorationManaged(view)) {
            removeDecorations(view);
            return;
        }
        GLASS_VIEWS.put(view, layer);
        int strength = percent(config, layer, ControlCenterNativeMode.isEditing(view));
        ControlCenterSoftGlass.apply(decorationTarget(view), config.surface, config.enabled && config.surface.hasSoftGlassReflection()
                && !ControlCenterNativeMode.isEditing(view) && !insideBorderContainer(view), strength);
    }
    static int textColor(TextView text, int requested, ControlCenterConfig config) {
        if (APPLYING.get() || !TEXT.containsKey(text)) return requested;
        TEXT.put(text, ColorStateList.valueOf(requested));
        return contrast(ColorStateList.valueOf(requested),
                textSurface(text, config)).getDefaultColor();
    }
    static ColorStateList textColor(TextView text, ColorStateList requested, ControlCenterConfig config) {
        if (APPLYING.get() || !TEXT.containsKey(text) || requested == null) return requested;
        TEXT.put(text, requested);
        return contrast(requested, textSurface(text, config));
    }

    private static int containerColor(View view, ControlCenterSurface style) {
        return style.resolve(view == null ? null : view.getContext()).stateColor(activeTile(view));
    }

    private static ControlCenterSurface textSurface(View view, ControlCenterConfig config) {
        ControlCenterSurface style = surface(config, ControlCenterNativeMode.isEditing(view)).resolve(view.getContext());
        return style != ControlCenterSurface.DEFAULT && style != ControlCenterSurface.CLEAR && activeTile(view)
                ? ControlCenterSurface.LIGHT : style;
    }

    private static boolean activeTile(View view) {
        for (View current = view; current != null && !(current instanceof ControlCenterRuntimeGrid.GridView);
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof ControlCenter5GTileView tile) return tile.networkEnabled();
            String name = current.getClass().getSimpleName();
            if (!name.contains("QSTileItem") && !name.contains("QSCardItemView")) continue;
            Object state = field(current, "state");
            if (state == null) state = field(current, "customizeState");
            if (state == null) state = field(current, "tileState");
            Object value = state instanceof Number ? state : field(state, "state");
            if (value instanceof Number number) return number.intValue() == 2;
        }
        return false;
    }
    private static ColorStateList contrast(ColorStateList original, ControlCenterSurface surface) {
        if (surface == ControlCenterSurface.DEFAULT) return original;
        int color = original.getDefaultColor();
        int r = android.graphics.Color.red(color), g = android.graphics.Color.green(color), b = android.graphics.Color.blue(color);
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 32) return original;
        if (surface.neutralForeground() == 0xff202124) return original.withLStar(13f);
        if (r + g + b > 480) return original;
        return original.withLStar(100f);
    }

    static void registerOwnedContainer(View view, ControlCenterConfig config) {
        NativeSurface state = NATIVE.computeIfAbsent(view, ignored -> new NativeSurface());
        state.ownedContainer = true;
        state.requests.put("setMiViewBlurMode", new Object[] {2});
        updateNativeSurface(view, config, ControlCenterNativeMode.isEditing(view));
    }
    static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Integer ? int.class
                : args[i] instanceof Float ? float.class : args[i] instanceof Boolean ? boolean.class : args[i].getClass();
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
    interface HookAction { Object run() throws Throwable; }
    static Object applyHook(HookAction action) throws Throwable {
        boolean was = APPLYING.get(); APPLYING.set(true);
        try { return action.run(); } finally { APPLYING.set(was); }
    }
    static void clearForTest() {
        for (ThemeObserver observer : new ArrayList<>(THEME_ROOTS.values())) {
            View root = observer.owner.get();
            if (root != null) observer.dispose(root);
        }
        THEME_ROOTS.clear();
        for (View view : new ArrayList<>(GLASS_VIEWS.keySet())) {
            removeDecorations(view);
        }
        for (View view : new ArrayList<>(BORDER_VIEWS.keySet())) {
            removeDecorations(view);
        }
        GLASS_VIEWS.clear(); BORDER_VIEWS.clear(); DECORATION_TARGETS.clear();
        for (View image : new ArrayList<>(ICON_BACKGROUNDS.keySet())) restoreIconBackground(image);
        for (View view : new ArrayList<>(MATERIAL_SESSIONS.keySet())) releaseGridSurface(view,
                ControlCenterConfig.defaults());
        for (View image : new ArrayList<>(NATIVE_TILE_BACKGROUNDS.keySet())) restoreNativeTileBackground(image);
        NATIVE_TILES.clear();
        GRID_CARRIERS.clear(); TILE_SHAPES.clear(); MATERIAL_SESSIONS.clear();
        SURFACES.clear(); NATIVE.clear();
        synchronized (DIRECT_LAYERS) { DIRECT_LAYERS.clear(); }
        TEXT.clear(); WINDOW_RATIOS.clear(); PENDING_TINTS.clear();
    }

    private static final class TileShape {
        final ControlCenterLayoutPlan.Shape shape;
        final int radiusDp;

        TileShape(ControlCenterLayoutPlan.Shape shape, int radiusDp) {
            this.shape = shape;
            this.radiusDp = radiusDp;
        }
    }

    static final class ShapeClip {
        final ViewOutlineProvider original;
        final boolean originalClipping;
        ViewOutlineProvider applied;
        ControlCenterLayoutPlan.Shape shape;
        int radiusDp;
        ShapeClip(View view) {
            original = view.getOutlineProvider();
            originalClipping = view.getClipToOutline();
        }
    }

    /** Native blur tiles keep only a glyph in the image; retain their OEM outline. */
    private static final class NativeTileBackground extends DrawableWrapper {
        private final WeakReference<View> owner;
        private final ControlCenterSurfaceShape shape = new ControlCenterSurfaceShape();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NativeTileBackground(View view, Drawable nativeBackground) {
            super(nativeBackground);
            owner = new WeakReference<>(view);
        }

        void setSurfaceColor(int color) {
            if (paint.getColor() == color) return;
            paint.setColor(color);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            View view = owner.get();
            if (view != null && shape.resolve(view, 0f)) shape.draw(canvas, paint);
        }

        @Override public void setAlpha(int alpha) {
            if (paint.getAlpha() == alpha) return;
            paint.setAlpha(alpha);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Paints a stable custom shape over the OEM background, underneath its glyph. */
    static final class TileSurfaceDrawable extends DrawableWrapper {
        private final Drawable nativeDrawable;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private TileShape shape = new TileShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
        private boolean replaceSurface;
        private boolean fillLayer;
        private int fillColor = 0x00000000;

        TileSurfaceDrawable(Drawable nativeDrawable) {
            this(nativeDrawable, nativeDrawable == null ? new android.graphics.Rect()
                    : new android.graphics.Rect(nativeDrawable.getBounds()));
        }

        private TileSurfaceDrawable(Drawable nativeDrawable, android.graphics.Rect bounds) {
            this(nativeDrawable, bounds, nativeDrawable == null ? null : nativeDrawable.getCallback());
        }

        private TileSurfaceDrawable(Drawable nativeDrawable, android.graphics.Rect bounds,
                Drawable.Callback nativeCallback) {
            super(nativeDrawable);
            this.nativeDrawable = nativeDrawable;
            this.nativeCallback = nativeCallback;
            // DrawableWrapper initially resets child bounds; preserve them before
            // LayerDrawable.setDrawable reads the outgoing child's geometry.
            setBounds(bounds);
            fill.setStyle(Paint.Style.FILL);
        }

        Drawable nativeDrawable() { return nativeDrawable; }

        private final Drawable.Callback nativeCallback;

        void restoreNativeCallback() {
            if (nativeDrawable != null && nativeDrawable.getCallback() == this)
                nativeDrawable.setCallback(nativeCallback);
        }

        boolean configure(ControlCenterLayoutPlan.Shape shape, int radiusDp, boolean active,
                ControlCenterConfig config, boolean fillLayer, boolean externalSurface, int blurPercent) {
            return configure(new TileShape(shape, radiusDp), active, config, fillLayer, externalSurface, blurPercent);
        }

        void refresh(ControlCenterConfig config) {
            configure(shape, false, config, true, false, config.cardBlur);
        }

        boolean configure(TileShape shape, boolean active, ControlCenterConfig config, boolean fillLayer,
                boolean externalSurface) {
            return configure(shape, active, config, fillLayer, externalSurface, config.tileBlur);
        }

        boolean configure(TileShape shape, boolean active, ControlCenterConfig config, boolean fillLayer,
                boolean externalSurface, int blurPercent) {
            ControlCenterSurface surface = config.enabled ? config.surface : ControlCenterSurface.DEFAULT;
            boolean replace = externalSurface || active || surface != ControlCenterSurface.DEFAULT;
            int selected = surface == ControlCenterSurface.DEFAULT && !active ? 0 : surface.stateColor(active);
            int color = color(selected, opacityPercent(blurPercent));
            if (this.shape.shape == shape.shape && this.shape.radiusDp == shape.radiusDp
                    && this.fillLayer == fillLayer && replaceSurface == replace && fillColor == color) return false;
            this.shape = shape;
            this.fillLayer = fillLayer;
            replaceSurface = replace;
            fillColor = color;
            invalidateSelf();
            return true;
        }

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            if (nativeDrawable != null) nativeDrawable.setBounds(bounds);
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            rect.set(bounds);
            path.reset();
            if (shape.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                float size = Math.min(rect.width(), rect.height());
                float dx = (rect.width() - size) / 2f, dy = (rect.height() - size) / 2f;
                rect.set(rect.left + dx, rect.top + dy, rect.left + dx + size, rect.top + dy + size);
                path.addOval(rect, Path.Direction.CW);
            } else {
                float radius = shape.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                        ? Math.min(rect.width(), rect.height()) / 2f
                        : shape.radiusDp > 0 ? shape.radiusDp *  getDensity() : Math.min(rect.width(), rect.height()) * .18f;
                path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            }
            int save = canvas.save();
            canvas.clipPath(path);
            if (nativeDrawable != null && !replaceSurface) {
                nativeDrawable.setBounds(bounds);
                nativeDrawable.draw(canvas);
            }
            if (fillLayer && fillColor != 0) {
                fill.setColor(fillColor);
                canvas.drawPath(path, fill);
            }
            canvas.restoreToCount(save);
        }

        private float getDensity() {
            return getCallback() instanceof View view
                    ? view.getResources().getDisplayMetrics().density : 1f;
        }

        @Override public void setAlpha(int alpha) {
            if (getAlpha() == alpha) return;
            super.setAlpha(alpha);
            invalidateSelf();
        }
        @Override public void setColorFilter(ColorFilter filter) {
            if (fill.getColorFilter() == filter && getColorFilter() == filter) return;
            fill.setColorFilter(filter);
            super.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static final class ClippedBackgroundDrawable extends DrawableWrapper {
        final Drawable nativeDrawable;
        private ControlCenterLayoutPlan.Item item;
        private final Path path = new Path();
        private final RectF rect = new RectF();

        ClippedBackgroundDrawable(Drawable nativeDrawable, ControlCenterLayoutPlan.Item item) {
            this(nativeDrawable, item, nativeDrawable == null ? null : nativeDrawable.getCallback());
        }

        private ClippedBackgroundDrawable(Drawable nativeDrawable, ControlCenterLayoutPlan.Item item,
                Drawable.Callback nativeCallback) {
            super(nativeDrawable);
            this.nativeDrawable = nativeDrawable;
            this.nativeCallback = nativeCallback;
            this.item = item;
        }

        private final Drawable.Callback nativeCallback;

        void restoreNativeCallback() {
            if (nativeDrawable != null && nativeDrawable.getCallback() == this)
                nativeDrawable.setCallback(nativeCallback);
        }

        boolean configure(ControlCenterLayoutPlan.Item item) {
            if (this.item.shape == item.shape && this.item.cornerRadius == item.cornerRadius) return false;
            this.item = item;
            invalidateSelf();
            return true;
        }

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            if (nativeDrawable != null) nativeDrawable.setBounds(bounds);
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            rect.set(bounds);
            path.reset();
            float density = getCallback() instanceof View view
                    ? view.getResources().getDisplayMetrics().density : 1f;
            float radius = item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                    ? Math.min(rect.width(), rect.height()) / 2f
                    : item.cornerRadius * density;
            if (item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                float size = Math.min(rect.width(), rect.height());
                float dx = (rect.width() - size) / 2f, dy = (rect.height() - size) / 2f;
                rect.set(rect.left + dx, rect.top + dy, rect.left + dx + size, rect.top + dy + size);
                path.addOval(rect, Path.Direction.CW);
            } else path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            if (nativeDrawable != null) {
                nativeDrawable.setBounds(bounds);
                nativeDrawable.draw(canvas);
            }
            canvas.restoreToCount(save);
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class TintState {
        ColorFilter colorFilter;
        ColorStateList tintList;
        PorterDuff.Mode tintMode;
        boolean tintCaptured;
    }
    private static final class Surface {
        WeakReference<View> owner; Layer layer; int alpha;
        final boolean colorSupported;
        ColorStateList color;
        int[] colors;
        ColorFilter colorFilter;
        ColorStateList tintList;
        PorterDuff.Mode tintMode;
        boolean tintCaptured;
        boolean filtersDirty = true;
        ColorStateList appliedTint;
        PorterDuff.Mode appliedTintMode;
        float colorAlphaFactor = 1f;
        Surface(View owner, Layer layer, Drawable drawable) {
            this.owner = new WeakReference<>(owner); this.layer = layer; this.alpha = drawable.getAlpha();
            colorFilter = drawable.getColorFilter();
            TintState pending = PENDING_TINTS.remove(drawable);
            if (pending != null) {
                if (pending.colorFilter != null) colorFilter = pending.colorFilter;
                tintList = pending.tintList;
                tintMode = pending.tintMode;
                tintCaptured = pending.tintCaptured;
            }
            colorSupported = drawable instanceof GradientDrawable || drawable instanceof ColorDrawable;
            if (drawable instanceof GradientDrawable gradient) {
                color = gradient.getColor();
                int[] source = gradient.getColors();
                colors = source == null ? null : source.clone();
            } else if (drawable instanceof ColorDrawable background) color = ColorStateList.valueOf(background.getColor());
        }
    }
    private static final class NativeSurface {
        final Map<String, Object[]> requests = new java.util.LinkedHashMap<>();
        int mode, radius;
        boolean overridden, failureLogged, ownedContainer, syntheticTint, blendAdjusted;
        boolean softwareSurface, effectsFailureLogged;
        int effectPolicy = -1;
    }
}
