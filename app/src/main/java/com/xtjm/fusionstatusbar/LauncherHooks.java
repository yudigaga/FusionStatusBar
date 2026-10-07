package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Hooks the MIUI Home launcher without coupling launcher classes into SystemUI hooks. */
final class LauncherHooks {
    private static final String TAG = "FusionStatusBar";
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String WORKSPACE = "com.miui.home.launcher.Workspace";
    private static final String HOTSEATS = "com.miui.home.launcher.hotseats.HotSeats";
    private static final String ITEM_INFO = "com.miui.home.launcher.ItemInfo";
    private static final String HOTSEAT_CONTENT =
            "com.miui.home.launcher.hotseats.HotSeatsScreenViewContent";
    private static final AtomicBoolean INSTALL_ATTEMPTED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Integer> ORIGINAL_VISIBILITY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, GestureDetector> DOCK_GESTURES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, TapState> DOUBLE_TAP_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile DesktopConfig config = DesktopConfig.defaults();
    private static volatile WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static volatile ContentObserver configObserver;
    private static final java.util.concurrent.ExecutorService SHORTCUT_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static volatile WeakReference<SensorManager> shakeSensorManager = new WeakReference<>(null);
    private static volatile ShakeListener shakeListener;
    private static volatile boolean launcherResumed;

    private LauncherHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        if (module == null || loader == null || !INSTALL_ATTEMPTED.compareAndSet(false, true)) {
            return 0;
        }
        int count = 0;
        count += hook(module, loader, LAUNCHER, "onCreate", new Class<?>[] {Bundle.class}, chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof Activity activity) attach(activity);
            return result;
        });
        count += hook(module, loader, LAUNCHER, "onResume", new Class<?>[0], chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof Activity activity) {
                attach(activity);
                launcherResumed = true;
                updateShakeRegistration();
            }
            return result;
        });
        count += hook(module, loader, LAUNCHER, "onPause", new Class<?>[0], chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof Activity activity && activity == currentActivity.get()) {
                launcherResumed = false;
                updateShakeRegistration();
            }
            return result;
        });
        count += hook(module, loader, WORKSPACE, "onVerticalGesture",
                new Class<?>[] {int.class, MotionEvent.class}, LauncherHooks::verticalGesture);
        count += installVerticalOverrides(module, loader);
        count += hook(module, loader, LAUNCHER, "onDestroy", new Class<?>[0], chain -> {
            if (chain.getThisObject() == currentActivity.get()) {
                launcherResumed = false;
                updateShakeRegistration();
                currentActivity = new WeakReference<>(null);
                DOCK_GESTURES.clear();
                DOUBLE_TAP_STATES.clear();
            }
            return chain.proceed();
        });
        count += hook(module, loader, WORKSPACE, "dispatchTouchEvent",
                new Class<?>[] {MotionEvent.class}, LauncherHooks::doubleTapGesture);
        count += hook(module, loader, WORKSPACE, "onPinching",
                new Class<?>[] {float.class}, LauncherHooks::pinching);
        count += hook(module, loader, WORKSPACE, "onPinchingEnd",
                new Class<?>[] {float.class}, LauncherHooks::pinchingEnd);
        count += hook(module, loader, WORKSPACE, "addInScreen",
                new Class<?>[] {View.class, long.class, int.class, int.class, int.class,
                        int.class, boolean.class}, chain -> {
                    Object result = chain.proceed();
                    if (chain.getArgs().get(0) instanceof View icon) applyDesktopIcon(icon);
                    return result;
                });
        count += hook(module, loader, HOTSEATS, "dispatchTouchEvent",
                new Class<?>[] {MotionEvent.class}, LauncherHooks::dockGesture);
        count += hook(module, loader, HOTSEAT_CONTENT, "addDesktopIcon",
                new Class<?>[] {resolve(loader, ITEM_INFO), int.class, boolean.class}, chain -> {
                    Object result = chain.proceed();
                    if (result instanceof View icon) applyDesktopIcon(icon);
                    return result;
                });
        Log.i(TAG, "launcher hook capability installed=" + count
                + " launcherPackage=com.miui.home");
        return count;
    }

    private static int installVerticalOverrides(XposedModule module, ClassLoader loader) {
        int count = 0;
        String[] controllers = {
                "com.miui.home.launcher.uioverrides.GlobalSearchSwipeController",
                "com.miui.home.launcher.uioverrides.StatusBarSwipeController",
                "com.miui.home.launcher.uioverrides.AllAppsSwipeController",
                "com.miui.home.launcher.overlay.search.SearchOverlaySwipeController",
                "com.miui.home.launcher.overlay.internationalsearch.InternationalSearchOverlaySwipeController"
        };
        for (String controller : controllers) {
            int direction = controller.endsWith("AllAppsSwipeController") ? 10 : 11;
            for (String method : new String[] {"canInterceptTouch", "onControllerInterceptTouchEvent"}) {
                count += hook(module, loader, controller, method,
                        new Class<?>[] {MotionEvent.class}, chain -> {
                            if (!chain.getArgs().isEmpty()
                                    && chain.getArgs().get(0) instanceof MotionEvent event
                                    && hasCustomVerticalAction(config, direction, event.getPointerCount())
                                    && isNormalDesktop(chain.getThisObject())) {
                                return false;
                            }
                            return chain.proceed();
                        });
            }
        }
        String mode = "com.miui.home.launcher.allapps.LauncherMode";
        count += hook(module, loader, mode, "getPullDownGesture", new Class<?>[] {Context.class},
                chain -> normalVerticalOverride(11) ? "no_action" : chain.proceed());
        count += hook(module, loader, mode, "getSlideUpGesture", new Class<?>[] {Context.class},
                chain -> normalVerticalOverride(10) ? "no_action" : chain.proceed());
        String edge = "com.miui.home.launcher.search.SearchEdgeLayout";
        count += hook(module, loader, edge, "isTopSearchEnable", new Class<?>[0],
                chain -> normalVerticalOverride(11) ? false : chain.proceed());
        count += hook(module, loader, edge, "isBottomGlobalSearchEnable", new Class<?>[0],
                chain -> normalVerticalOverride(10) ? false : chain.proceed());
        count += hook(module, loader, edge, "onInterceptTouchEvent",
                new Class<?>[] {MotionEvent.class}, LauncherHooks::searchEdgeIntercept);
        String device = "com.miui.home.launcher.DeviceConfig";
        for (String method : new String[] {"isGlobalSearchEnable", "isGlobalSearchBottomEffectEnable"}) {
            count += hook(module, loader, device, method, new Class<?>[] {Context.class},
                    chain -> normalVerticalOverride(10) ? false : chain.proceed());
        }
        return count;
    }

    private static Object searchEdgeIntercept(XposedInterface.Chain chain) throws Throwable {
        if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof MotionEvent event
                && event.getActionMasked() == MotionEvent.ACTION_MOVE
                && (hasCustomVerticalAction(config, 10, event.getPointerCount())
                        || hasCustomVerticalAction(config, 11, event.getPointerCount()))
                && isNormalDesktop(chain.getThisObject())) {
            Object lastY = readField(chain.getThisObject(), "mLastMotionY");
            if (lastY instanceof Number y) {
                float dy = event.getY() - y.floatValue();
                int direction = dy > 0 ? 11 : dy < 0 ? 10 : -1;
                if (hasCustomVerticalAction(config, direction, event.getPointerCount())) return false;
            }
        }
        return chain.proceed();
    }

    static boolean hasCustomVerticalAction(DesktopConfig source, int direction, int pointerCount) {
        int gesture = DesktopConfig.gestureForVerticalDirection(direction, pointerCount);
        return source != null && gesture >= 0
                && source.gestureAction(gesture) > DesktopConfig.ACTION_NONE;
    }

    private static boolean normalVerticalOverride(int direction) {
        return hasCustomVerticalAction(config, direction, 1) && isNormalDesktop(null);
    }

    private static boolean isNormalDesktop(Object owner) {
        Activity activity = currentActivity.get();
        if (!launcherResumed || activity == null) return false;
        if (owner != null) {
            try {
                Object launcher = readField(owner, "mLauncher");
                if (launcher != null && launcher != activity) return false;
            } catch (NoSuchFieldException ignored) {
                // Some launcher entry points expose their Activity only through the lifecycle hooks.
            } catch (ReflectiveOperationException error) {
                return false;
            }
        }
        return isNormalDesktopLauncher(activity);
    }

    static boolean isNormalDesktopLauncher(Object launcher) {
        if (launcher == null) return false;
        try {
            if (Boolean.TRUE.equals(invoke(launcher, "isInEditing", new Class<?>[0]))
                    || Boolean.TRUE.equals(invoke(launcher, "isFolderOpened", new Class<?>[0]))) {
                return false;
            }
            Object manager = invoke(launcher, "getStateManager", new Class<?>[0]);
            Object state = invoke(manager, "getState", new Class<?>[0]);
            if (state == null || state != readStaticField(state.getClass(), "NORMAL")) return false;
            for (String flag : new String[] {"isFolderShow", "isFolderSheetShow", "isFolderAnimating",
                    "isWidgetThumbnailViewShowing", "isUninstallDialogShowing", "isPreviewShowing",
                    "isInLayoutPreview", "isSearchEdgeShowing", "isSearchBarMenuShowing",
                    "isActivityLocked", "isWorkspaceLoading", "isInMultiWindowMode"}) {
                if (optionalBoolean(launcher, flag)) return false;
            }
            Object drag = optionalInvoke(launcher, "getDragController");
            if (drag != null && optionalBoolean(drag, "isDragging")) return false;
            Object layer = optionalInvoke(launcher, "getDragLayer");
            Class<?> floating = resolve(launcher.getClass().getClassLoader(),
                    "com.miui.home.customview.AbstractFloatingView");
            Method top = floating == null ? null : declaredMethod(floating, "getTopOpenView",
                    new Class<?>[] {ViewGroup.class});
            return layer == null || top == null || top.invoke(null, layer) == null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    private static Object optionalInvoke(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = declaredMethod(target.getClass(), name, new Class<?>[0]);
        return method == null ? null : method.invoke(target);
    }

    private static boolean optionalBoolean(Object target, String name) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(optionalInvoke(target, name));
    }

    private static int hook(XposedModule module, ClassLoader loader, String className,
            String methodName, Class<?>[] parameters, XposedInterface.Hooker hooker) {
        Class<?> type = resolve(loader, className);
        if (type == null) {
            Log.w(TAG, "launcher hook class missing " + className);
            return 0;
        }
        Method method = declaredMethod(type, methodName, parameters);
        if (method == null) {
            Log.w(TAG, "launcher hook method missing " + className + "#" + methodName);
            return 0;
        }
        try {
            try {
                module.deoptimize(method);
            } catch (Throwable error) {
                Log.w(TAG, "launcher hook deoptimize failed " + methodName, error);
            }
            module.hook(method)
                    .setId("fusion-statusbar:launcher-" + className + "#" + methodName)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
            return 1;
        } catch (Throwable error) {
            Log.w(TAG, "launcher hook installation failed " + className + "#" + methodName,
                    error);
            return 0;
        }
    }

    private static Object verticalGesture(XposedInterface.Chain chain) throws Throwable {
        if (chain.getArgs().size() < 2 || !(chain.getArgs().get(0) instanceof Integer direction)
                || !(chain.getArgs().get(1) instanceof MotionEvent event)
                || !isNormalDesktop(chain.getThisObject())) {
            return chain.proceed();
        }
        int gestureId = DesktopConfig.gestureForVerticalDirection(direction, event.getPointerCount());
        Context context = chain.getThisObject() instanceof View view ? view.getContext() : null;
        if (!performGestureAction(context, gestureId)) {
            return chain.proceed();
        }
        return true;
    }

    private static Object dockGesture(XposedInterface.Chain chain) throws Throwable {
        if (chain.getArgs().isEmpty() || !(chain.getArgs().get(0) instanceof MotionEvent event)
                || !(chain.getThisObject() instanceof View dock)) return chain.proceed();
        if (config.gestureAction(DesktopConfig.GESTURE_DOCK_RIGHT) > DesktopConfig.ACTION_NONE
                || config.gestureAction(DesktopConfig.GESTURE_DOCK_LEFT) > DesktopConfig.ACTION_NONE) {
            if (!isNormalDesktop(dock)) return chain.proceed();
            boolean handled = gestureDetector(DOCK_GESTURES, dock, true).onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && handled) return true;
        }
        return chain.proceed();
    }

    private static GestureDetector gestureDetector(Map<View, GestureDetector> detectors,
            View owner, boolean dock) {
        synchronized (detectors) {
            GestureDetector existing = detectors.get(owner);
            if (existing != null) return existing;
            GestureDetector created = new GestureDetector(owner.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDown(MotionEvent event) { return false; }

                        @Override public boolean onFling(MotionEvent start, MotionEvent end,
                                float velocityX, float velocityY) {
                            if (!dock) return false;
                            if (start == null || end == null) return false;
                            float dx = end.getX() - start.getX();
                            float dy = end.getY() - start.getY();
                            float density = owner.getResources().getDisplayMetrics().density;
                            float distanceThreshold = 75f * density;
                            float velocityThreshold = 33f * density;
                            if (Math.abs(dx) < distanceThreshold
                                    || Math.abs(dx) < Math.abs(dy) * 1.35f
                                    || Math.abs(velocityX) < velocityThreshold) return false;
                            int gesture = dx > 0 ? DesktopConfig.GESTURE_DOCK_RIGHT
                                    : DesktopConfig.GESTURE_DOCK_LEFT;
                            return performGestureAction(owner.getContext(), gesture);
                        }
                    });
            created.setIsLongpressEnabled(false);
            detectors.put(owner, created);
            return created;
        }
    }

    private static Object doubleTapGesture(XposedInterface.Chain chain) throws Throwable {
        if (chain.getArgs().isEmpty() || !(chain.getArgs().get(0) instanceof MotionEvent event)
                || !(chain.getThisObject() instanceof View workspace)
                || config.gestureAction(DesktopConfig.GESTURE_DOUBLE_TAP)
                        <= DesktopConfig.ACTION_NONE) return chain.proceed();
        if (!isNormalDesktop(workspace)) {
            DOUBLE_TAP_STATES.remove(workspace);
            return chain.proceed();
        }
        detectDoubleTap(workspace, event);
        return chain.proceed();
    }

    private static void detectDoubleTap(View workspace, MotionEvent event) {
        TapState state;
        synchronized (DOUBLE_TAP_STATES) {
            state = DOUBLE_TAP_STATES.computeIfAbsent(workspace, ignored -> new TapState());
        }
        int action = event.getActionMasked();
        long eventTime = event.getEventTime();
        if (action == MotionEvent.ACTION_DOWN) {
            int slop = android.view.ViewConfiguration.get(workspace.getContext())
                    .getScaledDoubleTapSlop();
            boolean doubleTap = state.completed && eventTime - state.firstUpAt
                    <= android.view.ViewConfiguration.getDoubleTapTimeout()
                    && distanceSquared(event.getX(), event.getY(), state.firstX, state.firstY)
                            <= (long) slop * slop;
            if (doubleTap && isBlankWorkspaceAt(workspace, state.firstX, state.firstY)
                    && isBlankWorkspaceAt(workspace, event.getX(), event.getY())) {
                int gesture = DesktopConfig.GESTURE_DOUBLE_TAP;
                performGestureAction(workspace.getContext(), gesture);
            }
            state.start(event.getX(), event.getY(), eventTime);
            return;
        }
        if (action == MotionEvent.ACTION_MOVE && state.active) {
            int slop = android.view.ViewConfiguration.get(workspace.getContext())
                    .getScaledTouchSlop();
            if (distanceSquared(event.getX(), event.getY(), state.downX, state.downY)
                    > (long) slop * slop) state.clear();
            return;
        }
        if (action == MotionEvent.ACTION_UP && state.active) {
            state.finish(eventTime, eventTime - state.downAt
                    <= android.view.ViewConfiguration.getTapTimeout());
            return;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            state.clear();
        }
    }

    private static long distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (long) (dx * dx + dy * dy);
    }

    static boolean isBlankWorkspaceAt(View workspace, float x, float y) {
        try {
            if (Boolean.TRUE.equals(invoke(workspace, "isInNormalEditingMode", new Class<?>[0]))) {
                return false;
            }
            int[] origin = new int[2];
            workspace.getLocationOnScreen(origin);
            float screenX = x + origin[0];
            float screenY = y + origin[1];
            Object inArea = invoke(workspace, "hitViewArea",
                    new Class<?>[] {float.class, float.class}, screenX, screenY);
            Object onChild = invoke(workspace, "pointOnChildViewRect",
                    new Class<?>[] {float.class, float.class}, screenX, screenY);
            return Boolean.TRUE.equals(inArea) && !Boolean.TRUE.equals(onChild);
        } catch (Throwable error) {
            Log.w(TAG, "launcher double tap target unavailable", error);
            return false;
        }
    }

    private static Object pinching(XposedInterface.Chain chain) throws Throwable {
        if (!(chain.getThisObject() instanceof View workspace) || chain.getArgs().isEmpty()
                || !(chain.getArgs().get(0) instanceof Float progress)) return chain.proceed();
        int gesture = DesktopConfig.gestureForPinch(progress);
        if (gesture < 0 || config.gestureAction(gesture) <= DesktopConfig.ACTION_NONE
                || !isNormalDesktop(workspace)) {
            return chain.proceed();
        }
        return false;
    }

    private static Object pinchingEnd(XposedInterface.Chain chain) throws Throwable {
        if (!(chain.getThisObject() instanceof View workspace) || chain.getArgs().isEmpty()
                || !(chain.getArgs().get(0) instanceof Float progress)) return chain.proceed();
        int gesture = DesktopConfig.gestureForPinch(progress);
        if (gesture < 0) return chain.proceed();
        if (config.gestureAction(gesture) <= DesktopConfig.ACTION_NONE
                || !isNormalDesktop(workspace)
                || !performGestureAction(workspace.getContext(), gesture)) {
            return chain.proceed();
        }
        float scale = progress;
        try {
            Object damping = invoke(workspace, "getDampingScale",
                    new Class<?>[] {float.class}, progress);
            if (damping instanceof Number value) scale = value.floatValue();
        } catch (Throwable ignored) { }
        resetPinch(workspace, scale);
        return null;
    }

    private static void resetPinch(View workspace, float progress) {
        try {
            invoke(workspace, "finishCurrentGesture", new Class<?>[0]);
            Object oldState = readField(workspace, "mState");
            Class<?> stateType = resolve(workspace.getClass().getClassLoader(),
                    WORKSPACE + "$PinchingState");
            Object follow = stateType == null ? null : readStaticField(stateType, "FOLLOW");
            Object ready = stateType == null ? null : readStaticField(stateType, "READY_TO_EDIT");
            if (follow != null) writeField(workspace, "mState", follow);
            if (oldState != null && oldState.equals(ready)) {
                Object launcher = readField(workspace, "mLauncher");
                if (launcher != null) invoke(launcher, "changeEditingEntryViewToHotseats",
                        new Class<?>[0]);
            }
            invoke(workspace, "resetCellScreenScale", new Class<?>[] {float.class}, progress);
        } catch (Throwable error) {
            Log.w(TAG, "launcher pinch reset failed", error);
        }
    }

    private static void updateShakeRegistration() {
        Activity activity = currentActivity.get();
        if (activity == null) return;
        SensorManager manager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) return;
        if (!launcherResumed || config.gestureAction(DesktopConfig.GESTURE_SHAKE)
                <= DesktopConfig.ACTION_NONE) {
            SensorManager previous = shakeSensorManager.get();
            if (shakeListener != null && previous != null) {
                previous.unregisterListener(shakeListener);
            }
            shakeListener = null;
            shakeSensorManager = new WeakReference<>(null);
            return;
        }
        SensorManager previous = shakeSensorManager.get();
        if (shakeListener != null && previous == manager) return;
        if (shakeListener != null && previous != null) {
            previous.unregisterListener(shakeListener);
        }
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            Log.w(TAG, "launcher shake gesture has no accelerometer");
            return;
        }
        ShakeListener listener = new ShakeListener(activity.getApplicationContext());
        if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            shakeSensorManager = new WeakReference<>(manager);
            shakeListener = listener;
        }
    }

    private static final class ShakeListener implements SensorEventListener {
        private final WeakReference<Context> context;
        private long lastTriggerAt;
        private float lastX;
        private float lastY;
        private float lastZ;
        private boolean hasPrevious;

        ShakeListener(Context context) { this.context = new WeakReference<>(context); }

        @Override public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
            float delta = hasPrevious ? (float) Math.sqrt(
                    (x - lastX) * (x - lastX) + (y - lastY) * (y - lastY)
                            + (z - lastZ) * (z - lastZ)) : 0f;
            lastX = x;
            lastY = y;
            lastZ = z;
            hasPrevious = true;
            long now = SystemClock.elapsedRealtime();
            if ((magnitude < 2.7f * SensorManager.GRAVITY_EARTH && delta < 13.5f)
                    || now - lastTriggerAt < 1200L) return;
            lastTriggerAt = now;
            performGestureAction(context.get(), DesktopConfig.GESTURE_SHAKE);
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }

    private static final class TapState {
        float downX;
        float downY;
        float firstX;
        float firstY;
        long downAt;
        long firstUpAt;
        boolean active;
        boolean completed;

        void start(float x, float y, long eventTime) {
            downX = x;
            downY = y;
            downAt = eventTime;
            active = true;
            completed = false;
        }

        void finish(long eventTime, boolean isTap) {
            firstX = downX;
            firstY = downY;
            firstUpAt = eventTime;
            active = false;
            completed = isTap;
        }

        void clear() {
            active = false;
            completed = false;
        }
    }

    private static boolean performGestureAction(Context context, int gestureId) {
        if (context == null || gestureId < 0 || gestureId >= DesktopConfig.GESTURE_COUNT
                || !isNormalDesktop(null)) return false;
        int action = config.gestureAction(gestureId);
        if (action <= DesktopConfig.ACTION_NONE) return false;
        if (action == DesktopConfig.ACTION_LAUNCH_SHORTCUT) {
            return DesktopShortcutCatalog.launchFromLauncher(context, config.gestureTarget(gestureId));
        }
        try {
            return DesktopActionQueue.enqueue(context, action, config.gestureTarget(gestureId),
                    config.gestureToggle(gestureId));
        } catch (Throwable error) {
            Log.w(TAG, "launcher gesture dispatch failed action=" + action, error);
            return false;
        }
    }

    private static void attach(Activity activity) {
        currentActivity = new WeakReference<>(activity);
        Context app = activity.getApplicationContext();
        synchronized (LauncherHooks.class) {
            if (configObserver == null) {
                ContentObserver observer = new ContentObserver(MAIN) {
                    @Override public void onChange(boolean selfChange, Uri uri) {
                        refreshConfiguration();
                    }
                };
                try {
                    app.getContentResolver().registerContentObserver(
                            DesktopConfig.contentUri(), false, observer);
                    app.getContentResolver().registerContentObserver(
                            DesktopShortcutCatalog.contentUri(), false, new ContentObserver(MAIN) {
                                @Override public void onChange(boolean selfChange, Uri uri) {
                                    SHORTCUT_WORKER.execute(() -> DesktopShortcutCatalog.publishFromLauncher(app));
                                }
                            });
                    configObserver = observer;
                } catch (Throwable error) {
                    Log.w(TAG, "launcher desktop config observer unavailable", error);
                }
            }
        }
        config = DesktopConfig.read(activity);
        SHORTCUT_WORKER.execute(() -> DesktopShortcutCatalog.publishFromLauncher(app));
        updateShakeRegistration();
        applyDesktopIcons(activity.getWindow() == null
                ? null : activity.getWindow().getDecorView());
    }

    private static void refreshConfiguration() {
        if (currentActivity.get() == null) return;
        Runnable refresh = () -> {
            Activity activity = currentActivity.get();
            if (activity == null) return;
            Context context = activity.getApplicationContext();
            config = DesktopConfig.read(context);
            updateShakeRegistration();
            applyDesktopIcons(activity.getWindow() == null
                    ? null : activity.getWindow().getDecorView());
        };
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run();
        else MAIN.post(refresh);
    }

    private static void applyDesktopIcons(View root) {
        if (root == null) return;
        applyDesktopIcon(root);
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                applyDesktopIcons(group.getChildAt(i));
            }
        }
    }

    private static void applyDesktopIcon(View view) {
        if (view == null || !"com.miui.home.launcher.ShortcutIcon".equals(
                view.getClass().getName()) && !isShortcutIcon(view.getClass())) return;
        Object tag = view.getTag();
        String packageName = packageName(tag);
        if (packageName == null || !isAttachedToDesktop(view)) return;
        boolean hide = config.hidesPackage(packageName);
        synchronized (ORIGINAL_VISIBILITY) {
            if (hide) {
                if (!ORIGINAL_VISIBILITY.containsKey(view)) {
                    ORIGINAL_VISIBILITY.put(view, view.getVisibility());
                }
                if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
            } else if (ORIGINAL_VISIBILITY.containsKey(view)) {
                Integer original = ORIGINAL_VISIBILITY.remove(view);
                if (original != null && view.getVisibility() != original) {
                    view.setVisibility(original);
                }
            }
        }
    }

    private static boolean isShortcutIcon(Class<?> type) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            if ("com.miui.home.launcher.ShortcutIcon".equals(cursor.getName())) return true;
        }
        return false;
    }

    private static boolean isAttachedToDesktop(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View parentView) {
            String className = parentView.getClass().getName();
            if (WORKSPACE.equals(className) || HOTSEAT_CONTENT.equals(className)) return true;
            parent = parentView.getParent();
        }
        return false;
    }

    private static String packageName(Object itemInfo) {
        if (itemInfo == null) return null;
        try {
            Method method = declaredMethod(itemInfo.getClass(), "getPackageName", new Class<?>[0]);
            Object result = method == null ? null : method.invoke(itemInfo);
            return result instanceof String value ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> resolve(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable error) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] parameters,
            Object... arguments) throws ReflectiveOperationException {
        if (target == null) throw new NullPointerException("missing_target_" + name);
        Method method = declaredMethod(target.getClass(), name, parameters);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        return method.invoke(target, arguments);
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        for (Class<?> cursor = target.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                java.lang.reflect.Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue through the launcher hierarchy.
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "#" + name);
    }

    private static Object readStaticField(Class<?> type, String name)
            throws ReflectiveOperationException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                java.lang.reflect.Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private static void writeField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        if (target == null) throw new NullPointerException("missing_target_" + name);
        for (Class<?> cursor = target.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                java.lang.reflect.Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Continue through the launcher hierarchy.
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "#" + name);
    }

    private static Method declaredMethod(Class<?> type, String name, Class<?>[] parameters) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Method method = cursor.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through the launcher hierarchy for inherited Activity callbacks.
            } catch (Throwable error) {
                return null;
            }
        }
        return null;
    }
}

