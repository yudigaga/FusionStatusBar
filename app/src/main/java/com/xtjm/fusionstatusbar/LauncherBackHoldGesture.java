package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.Objects;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Adds a stationary pause to an accepted native edge-back gesture. */
final class LauncherBackHoldGesture {
    static final String PROCESSOR = "com.miui.home.recents.GesturesBackTouchProcessor";
    static final String STUB = "com.miui.home.recents.GestureStubView";
    static final String CALLBACK = STUB + "$3";
    static final long HOLD_MS = 500L;
    static final long CAPTURE_MS = 100L;
    private static final String TAG = "FusionStatusBar";

    interface ActionDispatcher {
        /** Prepares without dispatching. Called for availability checks and again on release. */
        Runnable prepare(Context context, DesktopConfig snapshot);
    }

    interface ClassResolver { Class<?> resolve(String name, ClassLoader loader) throws Exception; }
    interface Registrar { boolean install(Method method, XposedInterface.Hooker hook); }

    private final Supplier<DesktopConfig> settings;
    private final Consumer<Context> attachRuntime;
    private final ActionDispatcher dispatcher;
    private final LauncherBackDistanceGate distanceGate;
    private final Map<View, State> states = new WeakHashMap<>();
    private final ThreadLocal<Frame> current = new ThreadLocal<>();

    private LauncherBackHoldGesture(Supplier<DesktopConfig> settings,
            Consumer<Context> attachRuntime, ActionDispatcher dispatcher,
            LauncherBackDistanceGate distanceGate) {
        this.settings = settings;
        this.attachRuntime = attachRuntime;
        this.dispatcher = dispatcher;
        this.distanceGate = distanceGate;
    }

    static int install(XposedModule module, ClassLoader loader,
            Supplier<DesktopConfig> settings, Consumer<Context> attachRuntime,
            ActionDispatcher dispatcher) {
        if (module == null || loader == null) return 0;
        int installed = install(loader, (name, source) -> Class.forName(name, false, source),
                (method, hook) -> {
                    try {
                        try { module.deoptimize(method); }
                        catch (Throwable ignored) { }
                        module.hook(method)
                                .setId("fusion-statusbar:launcher-back-hold:" + method.toGenericString())
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(hook);
                        return true;
                    } catch (Throwable error) {
                        Log.w(TAG, "back hold hook unavailable " + method.getName(), error);
                        return false;
                    }
                }, settings, attachRuntime, dispatcher);
        if (installed == 4) {
            try {
                Class<?> arrow = Class.forName(LauncherBackAppearance.ARROW, false, loader);
                Method progress = arrow.getDeclaredMethod("onSwipeProgress", float.class);
                if (progress.getReturnType() == void.class) module.deoptimize(progress);
            } catch (Throwable error) {
                Log.w(TAG, "launcher back arrow progress deoptimization unavailable", error);
            }
        }
        return installed;
    }

    static int install(ClassLoader loader, ClassResolver resolver, Registrar registrar,
            Supplier<DesktopConfig> settings, Consumer<Context> attachRuntime,
            ActionDispatcher dispatcher) {
        if (loader == null || resolver == null || registrar == null || settings == null
                || attachRuntime == null || dispatcher == null) return 0;
        try {
            Class<?> processor = resolver.resolve(PROCESSOR, loader);
            Class<?> stub = resolver.resolve(STUB, loader);
            Class<?> callback = resolver.resolve(CALLBACK, loader);
            if (processor == null || stub == null || callback == null
                    || !View.class.isAssignableFrom(stub)) return 0;
            Method pointer = processor.getDeclaredMethod("onPointerEvent", MotionEvent.class, stub);
            Method start = callback.getDeclaredMethod("onSwipeStart", float.class);
            Method stop = callback.getDeclaredMethod("onSwipeStop", boolean.class, float.class,
                    boolean.class);
            if (pointer.getReturnType() != void.class || start.getReturnType() != void.class
                    || stop.getReturnType() != void.class) return 0;
            pointer.setAccessible(true);
            start.setAccessible(true);
            stop.setAccessible(true);
            LauncherBackHoldGesture gesture = new LauncherBackHoldGesture(settings, attachRuntime,
                    dispatcher, LauncherBackDistanceGate.resolve(processor));
            int installed = 0;
            if (registrar.install(pointer, gesture::pointerEvent)) installed++;
            if (registrar.install(start, gesture::swipeStart)) installed++;
            if (registrar.install(stop, gesture::swipeStop)) installed++;
            Method predicate = gesture.distanceGate.nativePredicate();
            if (predicate != null && registrar.install(predicate, gesture::nativeDistance)) installed++;
            Log.i(TAG, "launcher back hold hooks installed=" + installed
                    + (predicate == null ? "/3" : "/4"));
            return installed;
        } catch (Exception | LinkageError error) {
            Log.w(TAG, "launcher native back hold capability unavailable", error);
            return 0;
        }
    }

    private Object pointerEvent(XposedInterface.Chain chain) throws Throwable {
        List<?> args = chain.getArgs();
        if (args.size() < 2 || !(args.get(0) instanceof MotionEvent event)
                || !(args.get(1) instanceof View stub)) return chain.proceed();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            LauncherBackReadyFeedbackGuard.reset(stub);
        }
        State state;
        synchronized (states) {
            state = states.computeIfAbsent(stub, ignored -> new State(stub, distanceGate,
                    this::available));
        }
        if (!state.attached) {
            try {
                attachRuntime.accept(stub.getContext());
                state.attached = true;
            } catch (RuntimeException error) {
                state.reset();
                Log.w(TAG, "launcher back hold runtime unavailable", error);
                return chain.proceed();
            }
        }
        state.before(event);
        Frame previous = current.get();
        Frame frame = new Frame(state, stub, stub.getContext(), event);
        current.set(frame);
        boolean completed = false;
        try {
            Object result = chain.proceed();
            completed = true;
            return result;
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
            if (!completed || event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) state.reset();
        }
    }

    private Object swipeStart(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Frame frame = current.get();
        if (frame != null) frame.state.accept(frame.event, configuredSnapshot());
        return result;
    }

    private Object nativeDistance(XposedInterface.Chain chain) throws Throwable {
        Frame frame = current.get();
        if (frame == null || frame.event.getActionMasked() != MotionEvent.ACTION_MOVE
                || !frame.state.accepted || !frame.state.customEnabled
                || frame.state.snapshot == null || frame.state.snapshot.backHoldDistanceDp == 0
                || chain.getArgs().size() != 1
                || !(chain.getArgs().get(0) instanceof Float distance)) return chain.proceed();
        // Align the native arrow/first feedback with this gesture's configured entry distance.
        // UP keeps the host's ordinary Back rule; our custom release consumes its own READY.
        return frame.state.insideDistance(distance);
    }

    private DesktopConfig configuredSnapshot() {
        try {
            DesktopConfig snapshot = settings.get();
            return snapshot != null && snapshot.gestureAction(DesktopConfig.GESTURE_BACK_HOLD)
                    > DesktopConfig.ACTION_NONE
                    && snapshot.isGestureComplete(DesktopConfig.GESTURE_BACK_HOLD) ? snapshot : null;
        } catch (RuntimeException error) {
            Log.w(TAG, "launcher back hold settings unavailable", error);
            return null;
        }
    }

    private boolean available(View stub, DesktopConfig snapshot) {
        return prepare(stub, snapshot) != null;
    }

    private Runnable prepare(View stub, DesktopConfig snapshot) {
        DesktopConfig latest = configuredSnapshot();
        int gesture = DesktopConfig.GESTURE_BACK_HOLD;
        if (stub == null || snapshot == null || latest == null
                || snapshot.gestureAction(gesture) != latest.gestureAction(gesture)
                || snapshot.gestureToggle(gesture) != latest.gestureToggle(gesture)
                || snapshot.backHoldDistanceDp != latest.backHoldDistanceDp
                || snapshot.backHoldDelayMs != latest.backHoldDelayMs
                || !Objects.equals(snapshot.gestureTarget(gesture), latest.gestureTarget(gesture))) {
            return null;
        }
        try { return dispatcher.prepare(stub.getContext(), snapshot); }
        catch (RuntimeException error) {
            Log.w(TAG, "launcher back hold action preparation failed", error);
            return null;
        }
    }

    private Object swipeStop(XposedInterface.Chain chain) throws Throwable {
        Frame frame = current.get();
        List<?> args = chain.getArgs();
        if (frame == null || args.size() != 3) return chain.proceed();
        State state = frame.state;
        if (state.nativeCancelled) return chain.proceed(cancelledArgs(args));
        if (state.finished) return chain.proceed();
        state.finished = true;
        DesktopConfig snapshot = state.snapshot;
        if (snapshot == null || !state.customEnabled) return chain.proceed();
        int actionId = snapshot.gestureAction(DesktopConfig.GESTURE_BACK_HOLD);
        boolean held = frame.event.getActionMasked() == MotionEvent.ACTION_UP
                && state.held(frame.event.getEventTime());
        if (!held && !state.holdCaptured && !state.cancelledHold) {
            logCompletion(state, frame.event, actionId, "native_tracking");
            return chain.proceed();
        }
        // The cue and release consume one latch. Native finish can be false solely because
        // lift jitter crossed its entry threshold; an actual CANCEL still cannot execute.
        Runnable action = held ? prepare(frame.stub, snapshot) : null;
        state.nativeCancelled = true;
        // Home owns its arrow and predictive-back lifecycle, including cancellation.
        // Its touch callbacks may run off-main, so prepare must not schedule the action.
        Object result = chain.proceed(cancelledArgs(args));
        if (action == null) {
            logCompletion(state, frame.event, actionId, held ? "hold_action_unavailable" : "hold_cancelled");
            return result;
        }
        try { action.run(); }
        catch (RuntimeException error) {
            Log.w(TAG, "launcher back hold prepared action failed", error);
            logCompletion(state, frame.event, actionId, "custom_request_failed");
            return result;
        }
        logCompletion(state, frame.event, actionId, "custom_requested");
        return result;
    }

    private static void logCompletion(State state, MotionEvent event, int action, String outcome) {
        Log.i(TAG, "launcher back hold result=" + outcome + " action=" + action
                + " durationMs=" + Math.max(0L, event.getEventTime() - state.downTime)
                + " pauseMs=" + Math.max(0L, event.getEventTime() - state.stationarySince)
                + " distanceDp=" + (state.snapshot == null ? 0 : state.snapshot.backHoldDistanceDp)
                + " delayMs=" + (state.snapshot == null ? HOLD_MS : state.snapshot.backHoldDelayMs)
                + " captured=" + state.holdCaptured
                + " ready=" + state.holdReady + " valid=" + (!state.invalid && state.eligible));
    }

    private static Object[] cancelledArgs(List<?> args) {
        return new Object[] {false, args.get(1), args.get(2)};
    }

    private static Handler gestureHandler(View stub) {
        // Run timers on the pointer callback's Looper, so release and readiness are serialized.
        Looper owner = Looper.myLooper();
        Handler attached = stub.getHandler();
        if (owner == null && attached != null) owner = attached.getLooper();
        if (owner == null) owner = Looper.getMainLooper();
        // A ViewRoot Handler can retain its root/View; keep only its Looper in a plain Handler.
        return owner == null ? null : new Handler(owner);
    }

    private record Frame(State state, View stub, Context context, MotionEvent event) { }

    private interface Availability { boolean check(View stub, DesktopConfig snapshot); }

    private static final class State {
        private final Handler handler;
        private final WeakReference<View> stub;
        private final LauncherBackDistanceGate distanceGate;
        private final Availability availability;
        private float jitter;
        private float density;
        private float pendingRetraction;
        private float readyRetraction;
        private Runnable readyAlarm;
        private long alarmGeneration;
        DesktopConfig snapshot;
        boolean customEnabled;
        boolean holdCaptured;
        boolean cancelledHold;
        boolean attached;
        boolean active;
        boolean accepted;
        boolean invalid;
        boolean eligible;
        boolean holdReady;
        boolean finished;
        boolean nativeCancelled;
        long downTime;
        long lastTime;
        long stationarySince;
        long readyAt;
        long cueIssuedAt;
        float downX;
        float direction;
        float anchorX;
        float anchorY;

        State(View view, LauncherBackDistanceGate distanceGate, Availability availability) {
            this.handler = gestureHandler(view);
            this.stub = new WeakReference<>(view);
            this.distanceGate = distanceGate;
            this.availability = availability;
            updateDensity(view);
        }

        private void updateDensity(View view) {
            float displayDensity = view.getResources().getDisplayMetrics().density;
            density = Float.isFinite(displayDensity) && displayDensity > 0 ? displayDensity : 1f;
            jitter = Math.max(ViewConfiguration.get(view.getContext()).getScaledTouchSlop(), 16f * density);
            pendingRetraction = 12f * density;
            readyRetraction = 24f * density;
        }

        void before(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                reset();
                active = true;
                downTime = event.getDownTime();
                lastTime = event.getEventTime();
                downX = event.getRawX();
                invalid = event.getPointerCount() != 1 || !finite(event.getRawX(), event.getRawY());
                return;
            }
            if (!active) return;
            if (event.getDownTime() != downTime || event.getEventTime() < lastTime
                    || event.getPointerCount() != 1 || action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                invalid = true;
                withdraw();
                return;
            }
            if (customEnabled && accepted && !invalid && (action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP)) {
                float offsetX = event.getRawX() - event.getX();
                float offsetY = event.getRawY() - event.getY();
                // A cue already delivered to the user is a promise. Delayed history cannot
                // retroactively undo it; current coordinates can still explicitly withdraw.
                if (!holdReady) {
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        sample(event.getHistoricalX(i) + offsetX, event.getHistoricalY(i) + offsetY,
                                event.getHistoricalEventTime(i), false, false);
                    }
                }
                sample(event.getRawX(), event.getRawY(), event.getEventTime(),
                        action == MotionEvent.ACTION_MOVE, action == MotionEvent.ACTION_UP);
            }
            lastTime = event.getEventTime();
        }

        void accept(MotionEvent event, DesktopConfig config) {
            if (!active || invalid || event.getActionMasked() != MotionEvent.ACTION_MOVE
                    || event.getDownTime() != downTime || accepted) return;
            accepted = true;
            snapshot = config;
            customEnabled = config != null;
            View view = stub.get();
            if (view != null) updateDensity(view);
            direction = Math.signum(event.getRawX() - downX);
            anchorX = event.getRawX();
            anchorY = event.getRawY();
            stationarySince = event.getEventTime();
            eligible = direction != 0 && distanceReady((anchorX - downX) * direction);
            if (customEnabled && eligible) scheduleReadyAlarm();
        }

        void sample(float x, float y, long time, boolean mayAnnounce, boolean releasing) {
            if (!finite(x, y) || time < lastTime) {
                invalid = true;
                withdraw();
                return;
            }
            lastTime = time;
            float inward = (x - downX) * direction;
            boolean inside = direction != 0 && insideDistance(inward);
            float dx = x - anchorX;
            float dy = y - anchorY;
            boolean moved = dx * dx + dy * dy > jitter * jitter;
            if (!inside) {
                withdraw();
                stationarySince = time;
                anchorX = x;
                anchorY = y;
            } else if (!holdReady) {
                if (!eligible || (moved && !(releasing && holdCaptured))) {
                    holdCaptured = false;
                    stationarySince = time;
                    anchorX = x;
                    anchorY = y;
                    eligible = true;
                    scheduleReadyAlarm();
                } else {
                    if (!holdCaptured && time - stationarySince >= CAPTURE_MS) capture();
                    // UP never creates the readiness pulse. Only a live MOVE or timer can.
                    if (mayAnnounce && time - stationarySince >= holdDelayMs()) enterReady();
                }
            }
            eligible = inside;
        }

        boolean held(long eventTime) {
            return active && accepted && !invalid && eligible && holdReady
                    && eventTime >= readyAt && eventTime >= cueIssuedAt;
        }

        private long holdDelayMs() {
            return snapshot == null ? HOLD_MS : snapshot.backHoldDelayMs;
        }

        private boolean distanceReady(float inward) {
            if (!Float.isFinite(inward) || inward < 0) return false;
            return snapshot != null && snapshot.backHoldDistanceDp > 0
                    ? inward >= snapshot.backHoldDistanceDp * density : distanceGate.isReady(inward);
        }

        private boolean insideDistance(float inward) {
            if (!Float.isFinite(inward) || inward < 0) return false;
            float margin = eligible ? (holdReady ? readyRetraction : pendingRetraction) : 0f;
            if (snapshot != null && snapshot.backHoldDistanceDp > 0) {
                // Even the smallest entry distance must allow a clear withdrawal to cancel.
                margin = Math.min(margin, snapshot.backHoldDistanceDp * density * .5f);
            }
            return distanceReady(inward + margin);
        }

        private void withdraw() {
            cancelledHold |= holdCaptured || holdReady;
            eligible = false;
            holdReady = false;
            holdCaptured = false;
            readyAt = 0;
            cueIssuedAt = 0;
            cancelReadyAlarm();
        }

        void reset() {
            cancelReadyAlarm();
            active = false;
            accepted = false;
            invalid = false;
            eligible = false;
            holdReady = false;
            holdCaptured = false;
            cancelledHold = false;
            customEnabled = false;
            snapshot = null;
            readyAt = 0;
            cueIssuedAt = 0;
            finished = false;
            nativeCancelled = false;
        }

        private void scheduleReadyAlarm() {
            cancelReadyAlarm();
            if (handler == null || !customEnabled || !active || !accepted || invalid
                    || !eligible || holdReady) return;
            long candidate = alarmGeneration;
            long deadline = stationarySince + (holdCaptured ? holdDelayMs() : CAPTURE_MS);
            // MotionEvent and Handler share uptime: delivery latency must not extend the dwell.
            // The alarm retains only a weak View reference on its owning gesture Looper.
            readyAlarm = () -> {
                if (candidate != alarmGeneration) return;
                readyAlarm = null;
                if (active && accepted && !invalid && eligible && !finished) {
                    if (!holdCaptured) {
                        capture();
                    } else enterReady();
                }
            };
            if (!handler.postAtTime(readyAlarm, deadline)) readyAlarm = null;
        }

        private void capture() {
            if (!customEnabled || !active || holdCaptured || invalid || !eligible || finished) return;
            View view = stub.get();
            long candidate = alarmGeneration;
            boolean available = view != null && availability.check(view, snapshot);
            if (candidate != alarmGeneration || !active || invalid || !eligible || finished) return;
            if (!available) {
                customEnabled = false;
                cancelReadyAlarm();
                return;
            }
            holdCaptured = true;
            scheduleReadyAlarm();
        }

        private void enterReady() {
            if (!customEnabled || !active || holdReady || invalid || !eligible || finished) return;
            if (!holdCaptured) capture();
            if (!holdCaptured) return;
            View view = stub.get();
            long candidate = alarmGeneration;
            boolean available = view != null && availability.check(view, snapshot);
            if (candidate != alarmGeneration || !active || invalid || !eligible || finished) return;
            if (!available) {
                cancelledHold = true;
                cancelReadyAlarm();
                return;
            }
            holdReady = true;
            readyAt = stationarySince + holdDelayMs();
            cueIssuedAt = SystemClock.uptimeMillis();
            cancelReadyAlarm();
            LauncherBackReadyFeedbackGuard.markReady(view);
            LauncherBackHaptics.trigger(view);
        }

        private void cancelReadyAlarm() {
            alarmGeneration++;
            if (readyAlarm != null) {
                if (handler != null) handler.removeCallbacks(readyAlarm);
                readyAlarm = null;
            }
        }

        private static boolean finite(float x, float y) {
            return Float.isFinite(x) && Float.isFinite(y);
        }
    }
}
