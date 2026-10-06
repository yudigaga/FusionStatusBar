package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import io.github.libxposed.api.XposedInterface;

/** Styles native notification backgrounds without replacing notification content or actions. */
final class NotificationSurfaceHooks {
    static final String ROW_CLASS =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    static final String BACKGROUND_CLASS =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    static final String INJECTOR_CLASS =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
    private static final Map<View, WeakReference<View>> ROWS = new WeakHashMap<>();
    private static final Map<View, WeakReference<BackgroundState>> BACKGROUNDS = new WeakHashMap<>();
    private static Supplier<FusionConfig> current = FusionConfig::defaults;
    private static Consumer<Context> start = ignored -> { };
    private static volatile Reporter reporter = (settings, result) -> { };
    private static volatile boolean hooksAvailable;

    interface Reporter { void report(ControlCenterConfig settings, RuntimeApplicationPolicy.Result result); }

    interface Registrar {
        boolean install(String id, Method method, XposedInterface.Hooker hooker);
    }

    private NotificationSurfaceHooks() { }

    static void setReporter(Reporter value) { reporter = value == null ? (s, r) -> { } : value; }

    static int install(ClassLoader loader, Registrar registrar, Supplier<FusionConfig> config,
            Consumer<Context> ensureStarted) {
        if (loader == null || registrar == null || config == null || ensureStarted == null) return 0;
        current = config;
        start = ensureStarted;
        int installed = 0;
        Class<?> row = resolve(loader, ROW_CLASS);
        for (String method : new String[] {"onFinishInflate", "onAttachedToWindow", "onLayout",
                "setHeadsUp", "setHeadsUpAnimatingAway", "setPinned", "setPinnedStatus", "updateBackgroundColors",
                "updateBackgroundTint", "updateBackgroundForGroupState", "setActualHeight"}) {
            installed += hookDeclared(row, method, registrar, chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof View view) safelyApply(view);
                return result;
            });
        }
        Class<?> injector = resolve(loader, INJECTOR_CLASS);
        for (String method : new String[] {"updateBackground$1", "updateBlurBg", "initBackground"}) {
            installed += hookDeclared(injector, method, registrar, chain -> {
                Object result = chain.proceed();
                Object view = field(chain.getThisObject(), "view");
                if (view instanceof View target) safelyApply(target);
                return result;
            });
        }
        Class<?> background = resolve(loader, BACKGROUND_CLASS);
        installed += hookDeclared(background, "onDraw", registrar, chain -> {
            Object target = chain.getThisObject();
            if (target instanceof View view) {
                BackgroundState state = backgroundState(view);
                if (state != null && state.active && view.getBackground() == state.bounded) return null;
            }
            return chain.proceed();
        });
        for (String method : new String[] {"setCustomBackground", "setTint", "setRadius",
                "setActualHeight", "setActualWidth", "setExpandAnimationRunning",
                "setExpandAnimationSize", "setClipTopAmount", "setClipBottomAmount"}) {
            installed += hookDeclared(background, method, registrar, chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof View view) {
                    View owner = ownerOf(view);
                    if (owner != null) safelyApply(owner);
                }
                return result;
            });
        }
        hooksAvailable = installed > 0;
        return installed;
    }

    static void refresh() {
        for (View row : new ArrayList<>(ROWS.keySet())) if (row != null) safelyApply(row);
        reportApplication();
    }

    static void reportApplication() {
        FusionConfig value;
        try { value = current.get(); } catch (RuntimeException error) { return; }
        boolean enabled = value.controlCenter.systemSurfaces.notifications
                || value.controlCenter.systemSurfaces.headsUp;
        boolean mounted = !ROWS.isEmpty();
        reporter.report(value.controlCenter, RuntimeApplicationPolicy.systemSurface(
                FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE, enabled, hooksAvailable,
                mounted && enabled, mounted));
        reporter.report(value.controlCenter, RuntimeApplicationPolicy.systemSurface(
                FusionActivationStatus.FEATURE_HEADS_UP, value.controlCenter.systemSurfaces.headsUp,
                hooksAvailable, mounted && value.controlCenter.systemSurfaces.headsUp, mounted));
    }

    static void applyRow(View row, FusionConfig config) {
        if (row == null || config == null) return;
        View background = backgroundOf(row);
        if (background == null) return;
        ROWS.put(row, new WeakReference<>(background));
        boolean headsUp = isHeadsUp(row);
        boolean enabled = headsUp ? config.controlCenter.systemSurfaces.headsUp
                : config.controlCenter.systemSurfaces.notifications;
        SystemSurfaceStyle.applyContent(row, config.controlCenter, enabled);
        BackgroundState state = backgroundState(background);
        if (!enabled && state == null) return;
        if (state == null) {
            state = new BackgroundState(row);
            BACKGROUNDS.put(background, new WeakReference<>(state));
        }
        state.owner = new WeakReference<>(row);
        if (background.getBackground() == state.bounded) background.setBackground(state.styled);
        SystemSurfaceStyle.apply(background, config.controlCenter, enabled);
        state.active = enabled;
        if (enabled) {
            state.styled = background.getBackground();
            if (state.styled != null) {
                if (state.bounded == null) state.bounded = new BoundedBackground(background);
                state.bounded.state = state;
                state.bounded.content = state.styled;
                background.setBackground(state.bounded);
            } else {
                state.active = false;
            }
        } else {
            BACKGROUNDS.remove(background);
        }
        background.invalidate();
        reporter.report(config.controlCenter, RuntimeApplicationPolicy.systemSurface(
                FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE, enabled, hooksAvailable,
                enabled, true));
    }

    static boolean isHeadsUp(View row) {
        for (String name : new String[] {"isHeadsUpState", "isHeadsUp", "isPinned"}) {
            try {
                Method method = RuntimeReflection.exact(row.getClass(), name, new Class<?>[0], true);
                if (method != null && Boolean.TRUE.equals(method.invoke(row))) return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return truth(field(row, "mIsHeadsUp")) || truth(field(row, "mHeadsUp"))
                || truth(field(row, "mHeadsupDisappearRunning"))
                || truth(field(row, "mHeadsUpAnimatingAway"));
    }

    static View backgroundOf(View row) {
        Object background = field(row, "mBackgroundNormal");
        if (background instanceof View view) return view;
        if (!(row instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().equals(BACKGROUND_CLASS)
                    || child.getClass().getSimpleName().equals("NotificationBackgroundView")) return child;
        }
        return null;
    }

    private static void safelyApply(View row) {
        try {
            start.accept(row.getContext());
            applyRow(row, current.get());
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "notification surface update failed", error);
        }
    }

    private static View ownerOf(View background) {
        BackgroundState state = backgroundState(background);
        if (state != null && state.owner.get() != null) return state.owner.get();
        View ancestor = background.getParent() instanceof View view ? view : null;
        for (int depth = 0; ancestor != null && depth < 8; depth++) {
            if (backgroundOf(ancestor) == background) return ancestor;
            ancestor = ancestor.getParent() instanceof View view ? view : null;
        }
        return null;
    }

    private static int hookDeclared(Class<?> type, String name, Registrar registrar,
            XposedInterface.Hooker hooker) {
        if (type == null) return 0;
        int count = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            method.setAccessible(true);
            if (registrar.install("SystemSurface:" + type.getName() + "#" + name
                    + "/" + method.getParameterCount(), method, hooker)) count++;
        }
        return count;
    }

    private static Class<?> resolve(ClassLoader loader, String name) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException | LinkageError ignored) { return null; }
    }

    private static Object field(Object owner, String name) {
        if (owner == null) return null;
        try {
            Field value = RuntimeReflection.field(owner.getClass(), name);
            return value == null ? null : value.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static boolean truth(Object value) { return Boolean.TRUE.equals(value); }
    private static BackgroundState backgroundState(View view) {
        WeakReference<BackgroundState> reference = BACKGROUNDS.get(view);
        return reference == null ? null : reference.get();
    }
    private static int number(Object owner, String name, int fallback) {
        Object value = field(owner, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static final class BackgroundState {
        WeakReference<View> owner;
        Drawable styled;
        BoundedBackground bounded;
        boolean active;
        BackgroundState(View row) { owner = new WeakReference<>(row); }
    }

    /** NotificationBackgroundView is measured taller than the visible collapsed notification. */
    static final class BoundedBackground extends Drawable {
        private final WeakReference<View> owner;
        private final Rect savedBounds = new Rect();
        Drawable content;
        private BackgroundState state;
        private int alpha = 255;
        BoundedBackground(View background) { owner = new WeakReference<>(background); }

        @Override public void draw(Canvas canvas) {
            View background = owner.get();
            if (background == null || content == null) return;
            boolean expanding = truth(field(background, "mExpandAnimationRunning"));
            int height = number(background, expanding ? "mExpandAnimationHeight" : "mActualHeight", -1);
            if (height < 0) height = background.getHeight();
            int width = number(background, expanding ? "mExpandAnimationWidth" : "mActualWidth", -1);
            if (width < 0) width = background.getWidth();
            int clipTop = Math.max(0, number(background, "mClipTopAmount", 0));
            int clipBottom = Math.max(0, number(background, "mClipBottomAmount", 0));
            if (!expanding && clipTop + clipBottom >= height) return;
            int left = expanding ? (background.getWidth() - width) / 2
                    : background.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? background.getWidth() - width : 0;
            int actualBottom = height;
            if (!expanding && truth(field(background, "mBottomIsRounded"))
                    && truth(field(background, "mBottomAmountClips"))) actualBottom -= clipBottom;
            Drawable nativeBackground = field(background, "mBackground") instanceof Drawable value ? value : null;
            int nativeAlpha = nativeBackground == null ? number(background, "mDrawableAlpha", 255)
                    : nativeBackground.getAlpha();
            int savedAlpha = content.getAlpha();
            savedBounds.set(content.getBounds());
            int save = canvas.save();
            if (!expanding) canvas.clipRect(0, clipTop, background.getWidth(), height - clipBottom);
            try {
                content.setBounds(left, 0, left + width, Math.max(0, actualBottom));
                float nativeFade = SystemSurfaceStyle.usesNativeBackground(content) ? 1f : nativeAlpha / 255f;
                content.setAlpha(Math.round(savedAlpha * nativeFade * (alpha / 255f)));
                content.draw(canvas);
            } finally {
                content.setAlpha(savedAlpha);
                content.setBounds(savedBounds);
                canvas.restoreToCount(save);
            }
        }

        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter filter) {
            if (content != null) content.setColorFilter(filter);
        }
        @Override public void getOutline(Outline outline) {
            View background = owner.get();
            if (background == null || content == null) return;
            int height = number(background, "mActualHeight", -1);
            if (height < 0) height = background.getHeight();
            savedBounds.set(content.getBounds());
            content.setBounds(0, 0, background.getWidth(), height);
            content.getOutline(outline);
            content.setBounds(savedBounds);
            outline.setAlpha(0f);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
