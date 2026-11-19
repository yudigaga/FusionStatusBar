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
        apply(view, config, enabled, track, false);
    }

    static void applyNotification(View view, ControlCenterConfig config, boolean enabled) {
        apply(view, config, enabled, false, true);
    }

    private static void apply(View view, ControlCenterConfig config, boolean enabled,
            boolean track, boolean notification) {
        if (view == null) return;
        if (!enabled) { restore(view); return; }
        State state = SURFACES.get(view);
        if (state == null) {
            state = new State(view, notification);
            SURFACES.put(view, state);
        } else {
            state.notification = notification;
            if (!ownsBackground(view.getBackground(), state.material.get())) captureHostBackground(view, state);
        }
        long revision = ++state.revision;
        state.config = config;
        state.track = track;
        state.notification = notification;
        if (notification && !ownsBackground(view.getBackground(), state.material.get())
                && HostDrawableGraph.inspect(view.getBackground()) == HostDrawableGraph.Result.UNKNOWN) {
            // Decline ownership without erasing an external host container we cannot enumerate.
            restore(view);
            return;
        }
        Drawable original = originalBackground(view, state);
        if (notification && original == null && (!state.hostBackgroundKnown || notificationFallback(state))) {
            // Unknown custom drawing needs the host's onDraw, not an invented gray fill.
            restore(view);
            return;
        }
        SurfaceDrawable drawable = state.drawable();
        drawable.configure(original, config, strength(state), view, notificationFallback(state),
                state.nativeBackgroundSource, notification);
        if (!ownsBackground(view.getBackground(), drawable)) {
            int left = view.getPaddingLeft(), top = view.getPaddingTop();
            int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
            view.setBackground(drawable);
            if (SURFACES.get(view) != state || state.revision != revision) return;
            view.setPadding(left, top, right, bottom);
        }
        // This background is also wrapped by NotificationSurfaceHooks. Keep its
        // reflection inside the drawable so actual-height clipping and fade apply.
        ControlCenterSoftGlass.apply(view, false, 0);
        if (config.surface == ControlCenterSurface.HIGHLIGHT_GLASS) watchTheme(view);
        else releaseTheme(view);
        if (SURFACES.get(view) != state || state.revision != revision) return;
        updateNative(view, state);
        view.invalidate();
    }

    static void applyTrack(View view, ControlCenterConfig config, boolean enabled) {
        apply(view, config, enabled, true);
    }

    static boolean isApplied(View view) { return SURFACES.containsKey(view); }

    static boolean drawsNotificationBackground(View view) {
        State state = SURFACES.get(view);
        return state != null && (!notificationFallback(state) || originalBackground(view, state) != null);
    }

    static boolean usesNativeBackground(Drawable drawable) {
        return drawable instanceof SurfaceDrawable surface && surface.nativeContent();
    }

    private static boolean ownsBackground(Drawable background, Drawable material) {
        // Theme callbacks can arrive outside applyRow's explicit unwrap/re-wrap.
        // Never adopt our own bounds wrapper as the OEM original: it leads back
        // to this material and would recurse when DEFAULT draws native content.
        return material != null && (background == material
                || background instanceof NotificationSurfaceHooks.BoundedBackground bounded
                        && bounded.content == material);
    }

    private static void captureHostBackground(View view, State state) {
        Drawable background = view.getBackground();
        if (!usableHostBackground(background, state.notification)) return;
        state.background = background;
        state.hostBackgroundKnown = true;
        state.nativeBackgroundSource = background == null || background == nativeBackground(view);
    }

    private static boolean usableHostBackground(Drawable drawable, boolean notification) {
        HostDrawableGraph.Result result = HostDrawableGraph.inspect(drawable);
        return result == HostDrawableGraph.Result.SAFE
                || !notification && result == HostDrawableGraph.Result.UNKNOWN;
    }

    static void restore(View view) {
        if (view == null) return;
        State state = SURFACES.remove(view);
        if (state == null) return;
        Drawable external = view.getBackground();
        boolean preserveExternal = state.notification && !ownsBackground(external, state.material.get())
                && HostDrawableGraph.inspect(external) == HostDrawableGraph.Result.UNKNOWN;
        if (!ownsBackground(view.getBackground(), state.material.get())) {
            captureHostBackground(view, state);
        }
        ControlCenterSoftGlass.apply(view, false, 0);
        releaseTheme(view);
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        Drawable original = originalBackground(view, state);
        view.setBackground(preserveExternal ? external : state.background == null ? null : original);
        if (SURFACES.containsKey(view)) return;
        view.setPadding(left, top, right, bottom);
        guarded(() -> {
            for (Map.Entry<String, Object[]> entry : state.nativeRequests.entrySet()) {
                if (SURFACES.containsKey(view)) break;
                invoke(view, entry.getKey(), entry.getValue());
            }
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
            if (state != null && state.active) {
                state.active = false;
                if (state.observer != null) state.observer.close();
                try { state.write(window, false); }
                finally {
                    if (WINDOWS.get(window) == state) WINDOWS.remove(window);
                }
            }
            return;
        }
        if (state == null || !state.active) {
            state = new WindowState(window, state);
            WINDOWS.put(window, state);
        }
        state.amount = config.backgroundBlur;
        state.surface = config.surface;
        if (state.observer == null && !state.observing) state.observe(window.getDecorView());
        state.write(window, true);
    }

    static int windowRadius(Window window, int requested) {
        WindowState state = WINDOWS.get(window);
        if (state == null || !state.active || APPLYING.get()) return requested;
        state.backgroundRadius = requested;
        return state.scaled(requested);
    }

    /** Capture host requests before transforming a copy; internal writes must never become raw. */
    static WindowManager.LayoutParams windowAttributes(Window window, WindowManager.LayoutParams requested) {
        WindowState state = WINDOWS.get(window);
        if (state == null || !state.active || requested == null || APPLYING.get()) return requested;
        int requestedRadius = requested.getBlurBehindRadius();
        int requestedFlag = requested.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        // Hosts also round-trip current attributes when changing unrelated fields.
        if (requestedRadius != state.lastAppliedRadius) state.radius = requestedRadius;
        if (requestedFlag != state.lastAppliedFlag) state.blurFlag = requestedFlag;
        return state.attributes(requested, true);
    }

    static void windowFlags(Window window, int flags, int mask) {
        WindowState state = WINDOWS.get(window);
        if (state == null || !state.active || APPLYING.get()
                || (mask & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0) return;
        state.blurFlag = flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        state.lastAppliedFlag = state.blurFlag;
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
        if (operation.equals("setMiBackgroundBlurRadius") || operation.equals("setMiBackgroundBlurMode")
                || operation.equals("setMiViewBlurMode")) state.oemRequestObserved = true;
        state.nativeRequests.put(operation, copy(arguments));
        return transform(state, operation, arguments);
    }

    private static Object[] transform(State state, String operation, Object[] arguments) {
        if (notificationFallback(state)) return arguments;
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
            for (Map.Entry<String, Object[]> entry : state.nativeRequests.entrySet()) {
                if (SURFACES.get(view) != state) break;
                invoke(view, entry.getKey(), transform(state, entry.getKey(), entry.getValue()));
            }
        });
    }

    private static int strength(State state) {
        return state.track ? state.config.tileBlur : state.config.cardBlur;
    }

    private static boolean notificationFallback(State state) {
        // OEM setters/getters do not establish compositor availability. Public window blur
        // capability cannot establish it either. Keep the native baseline while unknown.
        // Automatic notification glass keeps the host's readable body even at configured zero.
        return state.notification && state.config.surface == ControlCenterSurface.HIGHLIGHT_GLASS;
    }

    static String notificationBlurStatus(View view) {
        State state = SURFACES.get(view);
        return "oem_private_hooks_" + (state != null && state.oemRequestObserved ? "observed" : "unknown")
                + "_request_" + (state != null && state.oemRequestObserved ? "observed" : "unknown")
                + "_availability_unknown_native_fallback";
    }

    private static Drawable originalBackground(View view, State state) {
        if (!usableHostBackground(state.background, state.notification)) {
            state.background = null;
            state.hostBackgroundKnown = false;
            state.nativeBackgroundSource = true;
        }
        Drawable nativeDrawable = nativeBackground(view);
        if (state.notification && state.nativeBackgroundSource)
            return HostDrawableGraph.isSafe(nativeDrawable) ? nativeDrawable : state.background;
        if (state.background != null) return state.background;
        return usableHostBackground(nativeDrawable, state.notification) ? nativeDrawable : null;
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
            if (state != null) apply(view, state.config, true, state.track, state.notification);
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
        long revision;
        boolean track, notification, oemRequestObserved, hostBackgroundKnown;
        boolean nativeBackgroundSource = true;
        SurfaceDrawable drawable() {
            SurfaceDrawable drawable = material.get();
            if (drawable == null) {
                drawable = new SurfaceDrawable();
                material = new WeakReference<>(drawable);
            }
            return drawable;
        }
        State(View view, boolean notification) {
            this.notification = notification;
            captureHostBackground(view, this);
            for (String name : new String[] {"MiViewBlurMode", "MiBackgroundBlurMode",
                    "MiBackgroundBlurRadius", "MiBackgroundBlurScaleRatio"}) {
                Object value = invoke(view, "get" + name);
                if (value != null) nativeRequests.put("set" + name, new Object[] {value});
            }
        }
    }

    private record TextState(ColorStateList colors, float size) { }
    private static final class WindowState {
        final WeakReference<Window> window;
        int radius, blurFlag, lastAppliedRadius, lastAppliedFlag;
        Integer backgroundRadius;
        int amount = 100;
        boolean active = true, observing;
        ControlCenterSurface surface;
        PublicWindowBlurObserver observer;
        PublicWindowBlurObserver.Availability availability = PublicWindowBlurObserver.Availability.UNKNOWN;

        WindowState(Window window, WindowState predecessor) {
            this.window = new WeakReference<>(window);
            WindowManager.LayoutParams attributes = window.getAttributes();
            radius = attributes.getBlurBehindRadius();
            blurFlag = attributes.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            lastAppliedRadius = radius;
            lastAppliedFlag = blurFlag;
            // A reentrant reopen replaces restoration before its background setter can run.
            if (predecessor != null && !predecessor.active)
                backgroundRadius = predecessor.backgroundRadius;
        }

        void observe(View decor) {
            // The initial callback can synchronously dispatch host Window.Callback methods.
            observing = true;
            try {
                observer = new PublicWindowBlurObserver(decor, value -> {
                    Window owner = window.get();
                    if (!active || owner == null || WINDOWS.get(owner) != this) return;
                    availability = value;
                    write(owner, true);
                });
                if (!active) observer.close();
            } finally {
                observing = false;
            }
        }

        boolean suspended() {
            return surface == ControlCenterSurface.HIGHLIGHT_GLASS && amount > 0
                    && availability != PublicWindowBlurObserver.Availability.TRUE;
        }

        int scaled(int requested) {
            return suspended() ? requested : Math.round(requested * amount / 100f);
        }

        WindowManager.LayoutParams attributes(WindowManager.LayoutParams current, boolean enabled) {
            WindowManager.LayoutParams result = new WindowManager.LayoutParams();
            result.copyFrom(current);
            int applied = enabled ? scaled(radius) : radius;
            result.setBlurBehindRadius(applied);
            int flag = enabled && !suspended() && applied == 0 ? 0 : blurFlag;
            result.flags = (result.flags & ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND) | flag;
            lastAppliedRadius = applied;
            lastAppliedFlag = flag;
            return result;
        }

        void write(Window owner, boolean enabled) {
            if (WINDOWS.get(owner) != this || enabled && !active) return;
            guarded(() -> {
                owner.setAttributes(attributes(owner.getAttributes(), enabled));
                if (WINDOWS.get(owner) != this || enabled && !active) return;
                if (backgroundRadius != null)
                    owner.setBackgroundBlurRadius(enabled ? scaled(backgroundRadius) : backgroundRadius);
            });
        }
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
        private boolean notificationFallback, followNativeBackground, notification;
        private ColorFilter colorFilter;
        private ControlCenterSoftGlass.GlassDrawable reflection;

        void configure(Drawable original, ControlCenterConfig config, int strength, View view,
                boolean notificationFallback, boolean nativeBackgroundSource, boolean notification) {
            this.original = original;
            this.config = config;
            this.strength = strength;
            this.notificationFallback = notificationFallback;
            this.followNativeBackground = notificationFallback && nativeBackgroundSource;
            this.notification = notification;
            this.owner = new WeakReference<>(view);
            this.density = view.getResources().getDisplayMetrics().density;
            if (config.surface == ControlCenterSurface.HIGHLIGHT_GLASS && !notificationFallback) {
                if (reflection == null) reflection = new ControlCenterSoftGlass.GlassDrawable(view);
                reflection.configureBackground(view, strength, config.surface);
            } else reflection = null;
            invalidateSelf();
        }

        boolean nativeContent() {
            return (notificationFallback || config.surface == ControlCenterSurface.DEFAULT) && original != null;
        }

        @Override public void draw(Canvas canvas) {
            bounds.set(getBounds());
            if (bounds.isEmpty()) return;
            View view = owner.get();
            if (view != null) density = view.getResources().getDisplayMetrics().density;
            if (followNativeBackground && view != null) {
                Drawable latest = nativeBackground(view);
                if (latest == null) return;
                if (HostDrawableGraph.isSafe(latest)) original = latest;
            }
            if (!usableHostBackground(original, notification)) {
                // A previously accepted host container may have been mutated in place.
                State state = view == null ? null : SURFACES.get(view);
                if (state != null && state.material.get() == this) restore(view);
                return;
            }
            ControlCenterSurface requested = notificationFallback ? ControlCenterSurface.DEFAULT : config.surface;
            ControlCenterSurface surface = view == null ? requested : requested.resolve(view.getContext());
            float radius = Math.min(config.cornerRadius * density, Math.min(bounds.width(), bounds.height()) / 2f);
            float opacity = alpha / 255f * (notificationFallback ? 1f : strength / 100f);
            boolean drawNative = nativeContent();
            float borderOpacity = alpha / 255f;
            // Native content already carries its host fade; only our extra border needs it again.
            if (notificationFallback && drawNative) borderOpacity *= original.getAlpha() / 255f;
            path.reset();
            path.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            if (drawNative) {
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
                        ? 0x66000000 : 0x80ffffff, Math.round(borderOpacity * 100)));
                canvas.drawRoundRect(bounds, Math.max(0, radius - stroke / 2f),
                        Math.max(0, radius - stroke / 2f), paint);
            }
            if (reflection != null) {
                reflection.setAlpha(alpha);
                reflection.setColorFilter(colorFilter);
                reflection.drawRoundRect(canvas, getBounds(), radius);
            } else if (surface == ControlCenterSurface.SOFT_GLASS) {
                bounds.set(getBounds());
                bounds.inset(density / 2f, density / 2f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(density);
                paint.setColor(ControlCenterMaterials.color(0x70ffffff, Math.round(opacity * 100)));
                canvas.drawRoundRect(bounds, radius, radius, paint);
            }
        }

        @Override public void getOutline(Outline outline) {
            View view = owner.get();
            if (view != null) density = view.getResources().getDisplayMetrics().density;
            outline.setRoundRect(getBounds(), config.cornerRadius * density);
            outline.setAlpha(0f);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter filter) { colorFilter = filter; invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
