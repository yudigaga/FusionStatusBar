package com.xtjm.fusionstatusbar;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Reversible material ownership for menus and notifications, independent of the QS layout. */
final class SystemSurfaceStyle {
    private static final Map<View, State> SURFACES = new WeakHashMap<>();
    private static final Map<TextView, TextState> TEXT = new WeakHashMap<>();
    private static final Map<Window, WindowState> WINDOWS = new WeakHashMap<>();
    private static final Map<View, BackdropState> BACKDROPS = new WeakHashMap<>();
    private static final Map<View, ThemeState> THEMES = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private SystemSurfaceStyle() { }

    static void apply(View view, ControlCenterConfig config, boolean enabled) {
        apply(view, config, enabled, false);
    }

    static void apply(View view, ControlCenterConfig config, boolean enabled, boolean track) {
        if (view == null) return;
        if (!enabled) { restore(view); return; }
        State state = SURFACES.get(view);
        if (state == null) {
            state = new State(view);
            SURFACES.put(view, state);
        } else if (view.getBackground() != state.material.get()) {
            state.background = view.getBackground();
        }
        state.config = config;
        state.track = track;
        SurfaceDrawable drawable = state.drawable();
        drawable.configure(state.background == null ? nativeBackground(view) : state.background,
                config, strength(state), view);
        if (view.getBackground() != drawable) {
            int left = view.getPaddingLeft(), top = view.getPaddingTop();
            int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
            view.setBackground(drawable);
            view.setPadding(left, top, right, bottom);
        }
        ControlCenterSoftGlass.apply(view, config.surface,
                config.surface.resolve(view.getContext()) == ControlCenterSurface.HIGHLIGHT_GLASS,
                strength(state));
        if (config.surface == ControlCenterSurface.HIGHLIGHT_GLASS) watchTheme(view);
        else releaseTheme(view);
        updateNative(view, state);
        view.invalidate();
    }

    static void applyTrack(View view, ControlCenterConfig config, boolean enabled) {
        apply(view, config, enabled, true);
    }

    static boolean isApplied(View view) { return SURFACES.containsKey(view); }

    static boolean usesNativeBackground(Drawable drawable) {
        return drawable instanceof SurfaceDrawable surface && surface.nativeContent();
    }

    static void restore(View view) {
        if (view == null) return;
        State state = SURFACES.remove(view);
        if (state == null) return;
        ControlCenterSoftGlass.apply(view, false, 0);
        releaseTheme(view);
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setBackground(state.background);
        view.setPadding(left, top, right, bottom);
        guarded(() -> {
            for (Map.Entry<String, Object[]> entry : state.nativeRequests.entrySet())
                invoke(view, entry.getKey(), entry.getValue());
        });
        view.invalidate();
    }

    static void applyContent(View root, ControlCenterConfig config, boolean enabled) {
        if (root == null) return;
        if (enabled && config.surface == ControlCenterSurface.HIGHLIGHT_GLASS)
            watchTheme(root).contentConfig = config;
        else {
            ThemeState theme = THEMES.get(root);
            if (theme != null) theme.contentConfig = null;
            releaseTheme(root);
        }
        content(root, config, enabled, 0, new int[1]);
    }

    private static void content(View view, ControlCenterConfig config, boolean enabled,
            int depth, int[] visited) {
        if (view == null || depth > 16 || visited[0]++ >= 256) return;
        if (view instanceof TextView text) {
            TextState state = TEXT.get(text);
            if (enabled) {
                if (state == null) {
                    state = new TextState(text.getTextColors(), text.getTextSize());
                    TEXT.put(text, state);
                }
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.size * config.tileScale / 100f);
                ControlCenterSurface surface = config.surface.resolve(text.getContext());
                text.setTextColor(surface == ControlCenterSurface.DEFAULT
                        ? state.colors : ColorStateList.valueOf(surface.neutralForeground()));
            } else if (state != null) {
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.size);
                text.setTextColor(state.colors);
                TEXT.remove(text);
            }
        }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                content(group.getChildAt(i), config, enabled, depth + 1, visited);
    }

    static void applyBackdrop(View root, Window window, ControlCenterConfig config, boolean enabled) {
        if (root != null) {
            BackdropState state = BACKDROPS.get(root);
            if (enabled) {
                if (state == null) {
                    state = new BackdropState(root);
                    BACKDROPS.put(root, state);
                }
                state.amount = config.backgroundBlur;
            }
            if (state != null) {
                BackdropState current = state;
                guarded(() -> {
                    if (current.radius != null) invoke(root, "setMiBackgroundBlurRadius",
                            enabled ? Math.round(current.radius * config.backgroundBlur / 100f) : current.radius);
                    if (current.mode != null) invoke(root, "setMiBackgroundBlurMode",
                            enabled && config.backgroundBlur == 0 ? 0 : current.mode);
                });
                if (!enabled) BACKDROPS.remove(root);
            }
        }
        applyWindow(window, config, enabled);
    }

    static void applyWindow(Window window, ControlCenterConfig config, boolean enabled) {
        if (window == null) return;
        WindowState state = WINDOWS.get(window);
        if (!enabled) {
            if (state != null) {
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.setBlurBehindRadius(state.radius);
                attributes.flags = (attributes.flags & ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        | state.blurFlag;
                window.setAttributes(attributes);
                WindowState original = state;
                if (original.backgroundRadius != null) guarded(() -> window.setBackgroundBlurRadius(original.backgroundRadius));
                WINDOWS.remove(window);
            }
            return;
        }
        if (state == null) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            state = new WindowState(attributes.getBlurBehindRadius(),
                    attributes.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WINDOWS.put(window, state);
        }
        WindowManager.LayoutParams attributes = window.getAttributes();
        state.amount = config.backgroundBlur;
        int radius = Math.round(state.radius * config.backgroundBlur / 100f);
        attributes.setBlurBehindRadius(radius);
        if (radius == 0) attributes.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        else attributes.flags |= state.blurFlag;
        window.setAttributes(attributes);
        WindowState current = state;
        if (current.backgroundRadius != null) guarded(() -> window.setBackgroundBlurRadius(
                Math.round(current.backgroundRadius * config.backgroundBlur / 100f)));
    }

    static int windowRadius(Window window, int requested) {
        WindowState state = WINDOWS.get(window);
        if (state == null || APPLYING.get()) return requested;
        state.backgroundRadius = requested;
        return Math.round(requested * state.amount / 100f);
    }

    /** Invoked by the existing OEM View effect hooks, before the platform receives the request. */
    static Object[] nativeArguments(View view, String operation, Object[] arguments) {
        if (APPLYING.get()) return arguments;
        BackdropState backdrop = BACKDROPS.get(view);
        if (backdrop != null && arguments.length == 1 && arguments[0] instanceof Integer requested) {
            if (operation.equals("setMiBackgroundBlurRadius")) {
                backdrop.radius = requested;
                return new Object[] {Math.round(requested * backdrop.amount / 100f)};
            }
            if (operation.equals("setMiBackgroundBlurMode")) {
                backdrop.mode = requested;
                return new Object[] {backdrop.amount == 0 ? 0 : requested};
            }
        }
        State state = SURFACES.get(view);
        if (state == null) return arguments;
        state.nativeRequests.put(operation, copy(arguments));
        return transform(state, operation, arguments);
    }

    private static Object[] transform(State state, String operation, Object[] arguments) {
        int amount = strength(state);
        boolean custom = state.config.surface != ControlCenterSurface.DEFAULT;
        if (state.config.surface == ControlCenterSurface.CLEAR) amount = 0;
        if (arguments.length == 1) {
            if (operation.equals("setMiViewBlurMode") && (custom || amount == 0))
                return new Object[] {0};
            if (operation.equals("setMiBackgroundBlurMode") && amount == 0)
                return new Object[] {0};
            if (operation.equals("setMiBackgroundBlurRadius") && arguments[0] instanceof Number value)
                return new Object[] {Math.round(value.intValue() * amount / 100f)};
            if (operation.equals("setMiBackgroundBlurScaleRatio") && arguments[0] instanceof Number value)
                return new Object[] {value.floatValue() * amount / 100f};
        }
        return ControlCenterNativeEffects.arguments(operation, arguments,
                custom || state.config.borderEnabled, custom && amount == 0);
    }

    private static void updateNative(View view, State state) {
        guarded(() -> {
            for (Map.Entry<String, Object[]> entry : state.nativeRequests.entrySet())
                invoke(view, entry.getKey(), transform(state, entry.getKey(), entry.getValue()));
        });
    }

    private static int strength(State state) {
        return state.track ? state.config.tileBlur : state.config.cardBlur;
    }

    private static Object[] copy(Object[] arguments) {
        Object[] result = arguments.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] instanceof ArrayList<?> list) result[i] = new ArrayList<>(list);
            else if (result[i] instanceof float[] array) result[i] = array.clone();
        }
        return result;
    }

    private static Drawable nativeBackground(View view) {
        try {
            java.lang.reflect.Field field = RuntimeReflection.field(view.getClass(), "mBackground");
            Object value = field == null || field.getDeclaringClass() == View.class ? null : field.get(view);
            return value instanceof Drawable drawable ? drawable : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Object invoke(Object owner, String name, Object... arguments) {
        try {
            Method method = RuntimeReflection.compatible(owner.getClass(), name, arguments);
            return method == null ? null : method.invoke(owner, arguments);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static void guarded(Runnable action) {
        boolean was = APPLYING.get();
        APPLYING.set(true);
        try { action.run(); } finally { APPLYING.set(was); }
    }

    private static ThemeState watchTheme(View view) {
        ThemeState theme = THEMES.get(view);
        if (theme == null) {
            theme = new ThemeState(view);
            THEMES.put(view, theme);
            view.addOnAttachStateChangeListener(theme);
            theme.observe(view);
        }
        return theme;
    }

    private static void releaseTheme(View view) {
        ThemeState theme = THEMES.get(view);
        State surface = SURFACES.get(view);
        if (theme == null || surface != null && surface.config.surface == ControlCenterSurface.HIGHLIGHT_GLASS
                || theme.contentConfig != null) return;
        theme.stopObserving();
        view.removeOnAttachStateChangeListener(theme);
        THEMES.remove(view);
    }

    /** OEM notification/menu views survive theme changes without rebinding their content. */
    private static final class ThemeState implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> owner;
        ControlCenterConfig contentConfig;
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        int nightMode, densityDpi;

        ThemeState(View view) {
            owner = new WeakReference<>(view);
            nightMode = view.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            densityDpi = view.getResources().getConfiguration().densityDpi;
        }

        void observe(View view) {
            stopObserving();
            ViewTreeObserver current = view.getViewTreeObserver();
            observer = new WeakReference<>(current);
            current.addOnPreDrawListener(this);
        }

        void stopObserving() {
            ViewTreeObserver current = observer.get();
            if (current != null && current.isAlive()) current.removeOnPreDrawListener(this);
            observer.clear();
        }

        @Override public boolean onPreDraw() {
            View view = owner.get();
            if (view == null) { stopObserving(); return true; }
            android.content.res.Configuration configuration = view.getResources().getConfiguration();
            int nextMode = configuration.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (nightMode == nextMode && densityDpi == configuration.densityDpi) return true;
            nightMode = nextMode;
            densityDpi = configuration.densityDpi;
            State state = SURFACES.get(view);
            if (state != null) apply(view, state.config, true, state.track);
            if (contentConfig != null) content(view, contentConfig, true, 0, new int[1]);
            return true;
        }

        @Override public void onViewAttachedToWindow(View view) { observe(view); onPreDraw(); }
        @Override public void onViewDetachedFromWindow(View view) { stopObserving(); }
    }

    private static final class State {
        Drawable background;
        WeakReference<SurfaceDrawable> material = new WeakReference<>(null);
        final Map<String, Object[]> nativeRequests = new LinkedHashMap<>();
        ControlCenterConfig config;
        boolean track;
        SurfaceDrawable drawable() {
            SurfaceDrawable drawable = material.get();
            if (drawable == null) {
                drawable = new SurfaceDrawable();
                material = new WeakReference<>(drawable);
            }
            return drawable;
        }
        State(View view) {
            background = view.getBackground();
            for (String name : new String[] {"MiViewBlurMode", "MiBackgroundBlurMode",
                    "MiBackgroundBlurRadius", "MiBackgroundBlurScaleRatio"}) {
                Object value = invoke(view, "get" + name);
                if (value != null) nativeRequests.put("set" + name, new Object[] {value});
            }
        }
    }

    private record TextState(ColorStateList colors, float size) { }
    private static final class WindowState {
        final int radius, blurFlag;
        Integer backgroundRadius;
        int amount = 100;
        WindowState(int radius, int blurFlag) { this.radius = radius; this.blurFlag = blurFlag; }
    }

    private static final class BackdropState {
        Integer radius, mode;
        int amount = 100;
        BackdropState(View view) {
            Object radius = invoke(view, "getMiBackgroundBlurRadius");
            Object mode = invoke(view, "getMiBackgroundBlurMode");
            this.radius = radius instanceof Integer value ? value : null;
            this.mode = mode instanceof Integer value ? value : null;
        }
    }

    static final class SurfaceDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF bounds = new RectF();
        private final Rect originalBounds = new Rect();
        private Drawable original;
        private ControlCenterConfig config;
        private WeakReference<View> owner = new WeakReference<>(null);
        private float density;
        private int strength, alpha = 255;
        private ColorFilter colorFilter;

        void configure(Drawable original, ControlCenterConfig config, int strength, View view) {
            this.original = original;
            this.config = config;
            this.strength = strength;
            this.owner = new WeakReference<>(view);
            this.density = view.getResources().getDisplayMetrics().density;
            invalidateSelf();
        }

        boolean nativeContent() { return config.surface == ControlCenterSurface.DEFAULT && original != null; }

        @Override public void draw(Canvas canvas) {
            bounds.set(getBounds());
            if (bounds.isEmpty()) return;
            View view = owner.get();
            ControlCenterSurface surface = view == null ? config.surface : config.surface.resolve(view.getContext());
            float radius = Math.min(config.cornerRadius * density, Math.min(bounds.width(), bounds.height()) / 2f);
            float opacity = alpha / 255f * strength / 100f;
            path.reset();
            path.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            if (nativeContent()) {
                originalBounds.set(original.getBounds());
                int previousAlpha = original.getAlpha();
                original.setBounds(getBounds());
                original.setAlpha(Math.round(previousAlpha * opacity));
                original.draw(canvas);
                original.setAlpha(previousAlpha);
                original.setBounds(originalBounds);
            } else {
                int color = surface == ControlCenterSurface.DEFAULT ? 0x663c4149 : surface.color;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ControlCenterMaterials.color(color, Math.round(opacity * 100)));
                paint.setColorFilter(colorFilter);
                canvas.drawRoundRect(bounds, radius, radius, paint);
            }
            canvas.restoreToCount(save);
            if (config.borderEnabled) {
                float stroke = config.borderWidthDp * density;
                bounds.inset(stroke / 2f, stroke / 2f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(ControlCenterMaterials.color(surface.neutralForeground() == 0xff202124
                        ? 0x66000000 : 0x80ffffff, Math.round(alpha / 255f * 100)));
                canvas.drawRoundRect(bounds, Math.max(0, radius - stroke / 2f),
                        Math.max(0, radius - stroke / 2f), paint);
            }
            if (surface == ControlCenterSurface.SOFT_GLASS) {
                bounds.set(getBounds());
                bounds.inset(density / 2f, density / 2f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(density);
                paint.setColor(ControlCenterMaterials.color(0x70ffffff, Math.round(opacity * 100)));
                canvas.drawRoundRect(bounds, radius, radius, paint);
            }
        }

        @Override public void getOutline(Outline outline) {
            outline.setRoundRect(getBounds(), config.cornerRadius * density);
            outline.setAlpha(0f);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter filter) { colorFilter = filter; invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
