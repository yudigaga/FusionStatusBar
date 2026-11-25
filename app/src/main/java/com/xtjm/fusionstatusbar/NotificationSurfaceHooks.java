package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
    private static final String CONTENT_CLASS =
            "com.android.systemui.statusbar.notification.row.NotificationContentView";
    private static final String OEM_BLUR_CARRIER =
            "com.android.systemui:drawable/notification_heads_up_transparent_bg";
    private static final Map<View, WeakReference<View>> ROWS = new WeakHashMap<>();
    private static final Map<View, WeakReference<BackgroundState>> BACKGROUNDS = new WeakHashMap<>();
    private static final Map<View, WeakReference<Drawable>> BLUR_CARRIERS = new WeakHashMap<>();
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
                "onNotificationUpdated", "onUiModeChanged", "setHideSensitive", "setChildrenExpanded",
                "setHeadsUp", "setHeadsUpAnimatingAway", "setPinned", "setPinnedStatus", "updateBackgroundColors",
                "updateBackgroundTint", "updateBackgroundForGroupState"}) {
            installed += hookDeclared(row, method, registrar, chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof View view) safelyApply(view);
                return result;
            });
        }
        installed += hookDeclared(row, "setActualHeight", registrar, chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof View view) safelyUpdateBounds(view);
            return result;
        });
        installed += hookDeclared(resolve(loader, CONTENT_CLASS), "selectLayout", registrar, chain -> {
            Object content = chain.getThisObject();
            int previousType = number(content, "mVisibleType", -1);
            Object result = chain.proceed();
            if (number(content, "mVisibleType", -1) != previousType
                    && field(content, "mContainingNotification") instanceof View owner)
                refreshContrast(backgroundOf(owner));
            return result;
        });
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
                if (state != null && state.active && view.getBackground() == state.bounded
                        && SystemSurfaceStyle.drawsNotificationBackground(view)) return null;
            }
            return chain.proceed();
        });
        for (String method : new String[] {"setCustomBackground", "setTint", "setRadius"}) {
            installed += hookDeclared(background, method, registrar, chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof View view) {
                    if (method.equals("setCustomBackground") && chain.getArgs().size() == 1)
                        rememberBlurCarrier(view, chain.getArgs().get(0));
                    View owner = ownerOf(view);
                    if (owner != null) safelyApply(owner);
                }
                return result;
            });
        }
        for (String method : new String[] {"setActualHeight", "setActualWidth", "setExpandAnimationRunning",
                "setExpandAnimationSize", "setClipTopAmount", "setClipBottomAmount"}) {
            installed += hookDeclared(background, method, registrar, chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof View view) {
                    View owner = ownerOf(view);
                    if (owner != null) safelyUpdateBounds(owner);
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
        if (value == null) return;
        View notification = null, headsUp = null;
        for (View row : new ArrayList<>(ROWS.keySet())) {
            if (row == null || backgroundOf(row) == null) continue;
            if (isHeadsUp(row)) headsUp = row;
            else notification = row;
        }
        reportMountedOrWaiting(value, notification, value.controlCenter.systemSurfaces.notifications,
                FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE);
        reportMountedOrWaiting(value, headsUp, value.controlCenter.systemSurfaces.headsUp,
                FusionActivationStatus.FEATURE_HEADS_UP);
    }

    private static void reportMountedOrWaiting(FusionConfig config, View row, boolean enabled, String feature) {
        if (row != null) reportRow(row, config, enabled, backgroundOf(row));
        else reporter.report(config.controlCenter, RuntimeApplicationPolicy.systemSurface(
                feature, enabled, hooksAvailable, false, false));
    }

    static void applyRow(View row, FusionConfig config) {
        if (row == null || config == null) return;
        View background = backgroundOf(row);
        WeakReference<View> previous = background == null ? ROWS.remove(row)
                : ROWS.put(row, new WeakReference<>(background));
        View oldBackground = previous == null ? null : previous.get();
        BackgroundState oldState = oldBackground == null || oldBackground == background
                ? null : backgroundState(oldBackground);
        if (oldState != null && oldState.owner.get() == row)
            SystemSurfaceStyle.setNotificationContrast(oldBackground, null, null);
        if (background == null) {
            NotificationGlassContrast.restore(row);
            return;
        }
        boolean headsUp = isHeadsUp(row);
        boolean enabled = headsUp ? config.controlCenter.systemSurfaces.headsUp
                : config.controlCenter.systemSurfaces.notifications;
        BackgroundState state = backgroundState(background);
        if (!enabled && state == null) {
            NotificationGlassContrast.restore(row);
            return;
        }
        if (state == null) {
            state = new BackgroundState(row);
            BACKGROUNDS.put(background, new WeakReference<>(state));
        }
        long revision = ++state.revision;
        state.owner = new WeakReference<>(row);
        state.config = config.controlCenter;
        state.headsUp = headsUp;
        if (background.getBackground() == state.bounded) {
            background.setBackground(state.styled);
            if (!isCurrentBackground(background, state, revision)) return;
        }
        SystemSurfaceStyle.applyNotification(background, config.controlCenter, enabled);
        if (!isCurrentBackground(background, state, revision)) return;
        state.active = enabled && SystemSurfaceStyle.isApplied(background);
        if (state.active) {
            state.styled = background.getBackground();
            if (state.styled != null) {
                if (state.bounded == null) state.bounded = new BoundedBackground(background);
                state.bounded.state = state;
                state.bounded.content = state.styled;
                background.setBackground(state.bounded);
                if (!isCurrentBackground(background, state, revision)) return;
            } else {
                state.active = false;
            }
        }
        if (!state.active) BACKGROUNDS.remove(background);
        refreshContrast(row, background, state.active);
        background.invalidate();
        reportRow(row, config, enabled, background);
    }

    private static void rememberBlurCarrier(View background, Object requested) {
        BLUR_CARRIERS.remove(background);
        try {
            if (!(requested instanceof Integer id)
                    || !(field(background, "mBackground") instanceof GradientDrawable drawable)) return;
            ColorStateList fill = drawable.getColor();
            if (fill == null || fill.isStateful() || Color.alpha(fill.getDefaultColor()) != 0) return;
            if (OEM_BLUR_CARRIER.equals(background.getResources().getResourceName(id)))
                BLUR_CARRIERS.put(background, new WeakReference<>(drawable));
        } catch (RuntimeException ignored) { }
    }

    static void refreshContrast(View background) {
        BackgroundState state = background == null ? null : backgroundState(background);
        View row = state == null ? null : state.owner.get();
        try {
            if (row != null && backgroundOf(row) == background) refreshContrast(row, background, state.active);
        } catch (RuntimeException error) {
            ModuleLog.w("FusionStatusBar", "notification contrast refresh failed", error);
        }
    }

    private static void refreshContrast(View row, View background, boolean active) {
        WeakReference<Drawable> reference = active ? BLUR_CARRIERS.get(background) : null;
        Drawable carrier = reference == null ? null : reference.get();
        NotificationGlassContrast.Tone tone = null;
        if (carrier != null && field(background, "mBackground") == carrier
                && SystemSurfaceStyle.usesNotificationCarrier(background, carrier)) {
            tone = NotificationGlassContrast.apply(row);
        } else {
            NotificationGlassContrast.restore(row);
        }
        SystemSurfaceStyle.setNotificationContrast(background, carrier, tone);
    }

    private static void reportRow(View row, FusionConfig config, boolean enabled, View background) {
        String feature = isHeadsUp(row) ? FusionActivationStatus.FEATURE_HEADS_UP
                : FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE;
        RuntimeApplicationPolicy.Result result = RuntimeApplicationPolicy.systemSurface(
                feature, enabled, hooksAvailable, SystemSurfaceStyle.isApplied(background), true);
        if (enabled && hooksAvailable && config.controlCenter.surface == ControlCenterSurface.HIGHLIGHT_GLASS)
            result = new RuntimeApplicationPolicy.Result(feature, FusionActivationStatus.DEGRADED,
                    SystemSurfaceStyle.notificationBlurStatus(background));
        reporter.report(config.controlCenter, result);
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
            ModuleLog.w("FusionStatusBar", "notification surface update failed", error);
        }
    }

    private static void safelyUpdateBounds(View row) {
        try {
            start.accept(row.getContext());
            FusionConfig config = current.get();
            View background = backgroundOf(row);
            BackgroundState state = background == null ? null : backgroundState(background);
            WeakReference<View> recorded = ROWS.get(row);
            boolean headsUp = isHeadsUp(row);
            boolean enabled = config != null && (headsUp ? config.controlCenter.systemSurfaces.headsUp
                    : config.controlCenter.systemSurfaces.notifications);
            if (state != null && state.active && enabled && state.config == config.controlCenter
                    && state.headsUp == headsUp && state.owner.get() == row
                    && recorded != null && recorded.get() == background
                    && state.bounded != null && state.styled != null && state.bounded.content == state.styled
                    && background.getBackground() == state.bounded && SystemSurfaceStyle.isApplied(background)) {
                // The drawable reads live geometry and native alpha on this next draw.
                background.invalidate();
                return;
            }
            applyRow(row, config);
        } catch (Throwable error) {
            ModuleLog.w("FusionStatusBar", "notification surface bounds update failed", error);
            safelyApply(row);
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

    static Object field(Object owner, String name) {
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
    private static boolean isCurrentBackground(View view, BackgroundState state, long revision) {
        return backgroundState(view) == state && state.revision == revision;
    }
    private static int number(Object owner, String name, int fallback) {
        Object value = field(owner, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static final class BackgroundState {
        WeakReference<View> owner;
        Drawable styled;
        BoundedBackground bounded;
        ControlCenterConfig config;
        long revision;
        boolean headsUp;
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
