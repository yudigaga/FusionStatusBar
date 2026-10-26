package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Replaces only native back-arrow pixels while retaining its animation and feedback callbacks. */
final class LauncherBackAppearance {
    static final String ARROW = "com.miui.home.recents.GestureBackArrowView";
    private static final String TAG = "FusionStatusBar";
    private static final Map<Class<?>, Optional<HostFields>> FIELDS = new ConcurrentHashMap<>();

    interface ClassResolver { Class<?> resolve(String name, ClassLoader loader) throws Exception; }
    interface Registrar { boolean install(Method method, XposedInterface.Hooker hook); }

    private final Supplier<DesktopConfig> settings;
    private final Consumer<Context> attachRuntime;
    private final Map<View, DrawState> views = new WeakHashMap<>();

    private LauncherBackAppearance(Supplier<DesktopConfig> settings, Consumer<Context> attachRuntime) {
        this.settings = settings;
        this.attachRuntime = attachRuntime;
    }

    static int install(XposedModule module, ClassLoader loader, Supplier<DesktopConfig> settings,
            Consumer<Context> attachRuntime) {
        if (module == null || loader == null) return 0;
        return install(loader, (name, source) -> Class.forName(name, false, source), (method, hook) -> {
            try {
                try { module.deoptimize(method); }
                catch (Throwable ignored) { }
                module.hook(method).setId("fusion-statusbar:launcher-back-appearance")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hook);
                return true;
            } catch (Throwable error) {
                Log.w(TAG, "launcher back appearance hook unavailable", error);
                return false;
            }
        }, settings, attachRuntime);
    }

    static int install(ClassLoader loader, ClassResolver resolver, Registrar registrar,
            Supplier<DesktopConfig> settings, Consumer<Context> attachRuntime) {
        if (loader == null || resolver == null || registrar == null || settings == null
                || attachRuntime == null) return 0;
        try {
            Class<?> arrow = resolver.resolve(ARROW, loader);
            if (arrow == null || !View.class.isAssignableFrom(arrow)) return 0;
            Method draw = arrow.getDeclaredMethod("onDraw", Canvas.class);
            if (draw.getReturnType() != void.class) return 0;
            draw.setAccessible(true);
            LauncherBackAppearance appearance = new LauncherBackAppearance(settings, attachRuntime);
            return registrar.install(draw, appearance::draw) ? 1 : 0;
        } catch (Exception | LinkageError error) {
            Log.w(TAG, "launcher back appearance capability unavailable", error);
            return 0;
        }
    }

    private Object draw(XposedInterface.Chain chain) throws Throwable {
        if (!(chain.getThisObject() instanceof View view) || chain.getArgs().isEmpty()
                || !(chain.getArgs().get(0) instanceof Canvas canvas)) return chain.proceed();
        DrawState state;
        synchronized (views) { state = views.computeIfAbsent(view, ignored -> new DrawState()); }
        DesktopConfig snapshot;
        try {
            if (!state.attached) {
                attachRuntime.accept(view.getContext());
                state.attached = true;
            }
            snapshot = settings.get();
        } catch (RuntimeException error) {
            if (!state.failureReported) {
                state.failureReported = true;
                Log.w(TAG, "launcher back appearance settings unavailable", error);
            }
            return chain.proceed();
        }
        if (snapshot == null || snapshot.backGestureStyle == DesktopConfig.BACK_STYLE_NATIVE
                || state.customUnavailable) {
            return chain.proceed();
        }
        Optional<HostFields> contract = FIELDS.computeIfAbsent(view.getClass(), HostFields::resolve);
        if (contract.isEmpty()) return chain.proceed();
        HostFields fields = contract.get();
        Geometry before = fields.read(view);
        if (before == null) return chain.proceed();
        Object result;
        int save = canvas.save();
        try {
            canvas.clipRect(0, 0, 0, 0);
            result = chain.proceed();
        } finally {
            canvas.restoreToCount(save);
        }
        Geometry geometry = fields.read(view);
        if (geometry == null) geometry = before;
        try {
            state.renderer.drawThemed(canvas, snapshot.backGestureStyle, snapshot.backGestureBackgroundColor,
                    snapshot.backGestureArrowColor, geometry.right, geometry.edgeX, geometry.centerY,
                    geometry.depth, geometry.height, geometry.scale, geometry.arrowAlpha,
                    view.getResources().getDisplayMetrics().density,
                    BackGesturePalette.isNight(view.getResources()));
        } catch (RuntimeException | LinkageError error) {
            // Native onDraw already completed; replaying it could restart haptics or animators.
            state.customUnavailable = true;
            if (!state.failureReported) {
                state.failureReported = true;
                Log.w(TAG, "launcher custom back appearance draw failed", error);
            }
        }
        return result;
    }

    private static final class DrawState {
        final BackGestureRenderer renderer = new BackGestureRenderer();
        boolean attached;
        boolean customUnavailable;
        boolean failureReported;
    }

    private record Geometry(boolean right, float edgeX, float centerY, float depth, float height,
            float scale, float arrowAlpha) { }

    private static final class HostFields {
        final Field position;
        final Field width;
        final Field scale;
        final Field startX;
        final Field startY;
        final Field height;
        final Field icon;
        final Field trigger;
        final Field arrowPaint;

        HostFields(Class<?> type) throws ReflectiveOperationException {
            position = field(type, "mPosition", int.class);
            width = field(type, "mBackgroundWidth", int.class);
            scale = field(type, "mBackgroundScale", float.class);
            startX = field(type, "mStartX", float.class);
            startY = field(type, "mStartY", float.class);
            height = field(type, "mExpectBackHeight", float.class);
            icon = field(type, "mIconNeedDraw", boolean.class);
            trigger = field(type, "mTriggerType", null);
            arrowPaint = field(type, "mArrowPaint", Paint.class);
            if (!trigger.getType().isEnum()) throw new NoSuchFieldException("trigger_type_not_enum");
        }

        static Optional<HostFields> resolve(Class<?> type) {
            try { return Optional.of(new HostFields(type)); }
            catch (ReflectiveOperationException | RuntimeException error) {
                Log.w(TAG, "launcher back appearance geometry contract unavailable", error);
                return Optional.empty();
            }
        }

        Geometry read(View view) {
            try {
                int side = position.getInt(view);
                int nativeWidth = width.getInt(view);
                float nativeScale = scale.getFloat(view);
                float x = startX.getFloat(view);
                float y = startY.getFloat(view);
                float nativeHeight = height.getFloat(view);
                Object nativeTrigger = trigger.get(view);
                Object nativePaint = arrowPaint.get(view);
                if ((side != 0 && side != 1) || view.getWidth() <= 0 || view.getHeight() <= 0
                        || nativeWidth <= 0 || nativeWidth > view.getWidth() * 2f
                        || !Float.isFinite(nativeScale) || nativeScale < 0f || nativeScale > 1.5f
                        || !Float.isFinite(x) || x < 0f || x > view.getWidth()
                        || !Float.isFinite(y) || !Float.isFinite(nativeHeight) || nativeHeight <= 0f
                        || nativeHeight > view.getHeight() * 4f
                        || !(nativeTrigger instanceof Enum<?> type)
                        || !(nativePaint instanceof Paint paint)) return null;
                String triggerName = type.name();
                if (!"TRIGGER_TYPE_NONE".equals(triggerName)
                        && !"TRIGGER_TYPE_BACK".equals(triggerName)) return null;
                float alpha = icon.getBoolean(view) && nativeScale > .1f
                        && "TRIGGER_TYPE_BACK".equals(triggerName) ? paint.getAlpha() / 255f : 0f;
                return new Geometry(side == 1, side == 1 ? view.getWidth() - x : x, y,
                        nativeWidth * nativeScale, nativeHeight, nativeScale, alpha);
            } catch (ReflectiveOperationException | RuntimeException error) {
                return null;
            }
        }

        private static Field field(Class<?> type, String name, Class<?> expected)
                throws ReflectiveOperationException {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    if (expected != null && !expected.isAssignableFrom(field.getType())) {
                        throw new NoSuchFieldException(name + "_type_mismatch");
                    }
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException error) {
                    if (current.getSuperclass() == null) throw error;
                }
            }
            throw new NoSuchFieldException(name);
        }
    }
}
