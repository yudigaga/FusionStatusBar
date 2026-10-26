package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedInterface;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherBackHoldGestureTest {
    private final Registry registry = new Registry();
    private final List<String> order = new ArrayList<>();
    private final List<Boolean> nativeFinishes = new ArrayList<>();
    private DesktopConfig config;
    private Stub stub;
    private final Processor processor = new Processor();
    private final Callback callback = new Callback();
    private int attached;
    private int prepared;
    private int executed;
    private boolean prepareAccepted = true;
    private RuntimeException prepareFailure;
    private RuntimeException nativeFailure;
    private long downTime;

    @Before public void setUp() {
        config = DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                DesktopConfig.ACTION_SCREENSHOT);
        stub = new Stub(RuntimeEnvironment.getApplication(), order);
        assertEquals(4, LauncherBackHoldGesture.install(getClass().getClassLoader(),
                (name, loader) -> switch (name) {
                    case LauncherBackHoldGesture.PROCESSOR -> Processor.class;
                    case LauncherBackHoldGesture.STUB -> Stub.class;
                    case LauncherBackHoldGesture.CALLBACK -> Callback.class;
                    default -> null;
                }, registry, () -> config, context -> attached++, (context, snapshot) -> {
                    assertSame(stub.getContext(), context);
                    assertSame(config, snapshot);
                    prepared++;
                    order.add("prepare");
                    if (prepareFailure != null) throw prepareFailure;
                    return !prepareAccepted ? null : () -> {
                        executed++;
                        order.add("action");
                    };
                }));
    }

    @Test public void committedPauseCancelsNativeBackBeforeRunningPreparedAction() throws Throwable {
        startAt(220, 100);
        stop(550, 220, 100, true);

        assertEquals(List.of(false), nativeFinishes);
        assertEquals(List.of("start", "ready-cue", "cancel", "action"),
                order.stream().filter(step -> !step.equals("prepare")).toList());
        assertEquals(1, executed);
    }

    @Test public void quickBackAndLongContinuousDragRemainNative() throws Throwable {
        startAt(220, 100);
        stop(149, 220, 100, true); // 99ms of pause has not captured hold intent.
        startAt(220, 100);
        float step = stationaryTolerance() + 2;
        for (long time = 100; time <= 900; time += 50) {
            event(MotionEvent.ACTION_MOVE, time, 220 + (time / 50f) * step, 100, () -> { });
        }
        stop(950, 220 + 19 * step, 100, true);

        assertEquals(List.of(true, true), nativeFinishes);
        assertEquals(0, prepared);
    }

    @Test public void preReadyMovementResumesNativeButLiftCentroidDriftKeepsCapturedWaiting()
            throws Throwable {
        startAt(220, 100);
        event(MotionEvent.ACTION_MOVE, 200, 220, 100 + stationaryTolerance() + 2, () -> { });
        stop(250, 220, 100 + stationaryTolerance() + 2, true);
        startAt(220, 100);
        // Only a continuing MOVE proves resumed swiping; inward lift drift must not revive Back.
        stop(350, 220 + stationaryTolerance() + 2, 100, true);

        assertEquals(List.of(true, false), nativeFinishes);
        assertEquals(0, executed);
        assertFalse(order.contains("ready-cue"));
    }

    @Test public void touchSlopJitterStillCountsAsStationary() throws Throwable {
        startAt(220, 100);
        event(MotionEvent.ACTION_MOVE, 300, 221, 101, () -> { });
        stop(550, 220, 100, true);

        assertEquals(List.of(false), nativeFinishes);
        assertEquals(1, executed);
    }

    @Test public void rightEdgeUsesItsInwardDirectionForTheSameHold() throws Throwable {
        event(MotionEvent.ACTION_DOWN, 0, 1000, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 50, 780, 100, () -> registry.call(callback,
                "onSwipeStart", new Object[] {100f}, args -> null));
        stop(550, 780, 100, true);

        assertEquals(List.of(false), nativeFinishes);
        assertEquals(1, executed);
    }

    @Test public void onlyPausingAfterTheNativeEntryDistanceCapturesHoldIntent() throws Throwable {
        startAt(100, 100);
        stop(700, 100, 100, true);
        startAt(100, 100);
        event(MotionEvent.ACTION_MOVE, 700, 220, 100, () -> { });
        stop(900, 220, 100, true);

        assertEquals(List.of(true, false), nativeFinishes);
        assertEquals(0, executed);
        assertFalse(order.contains("ready-cue"));
    }

    @Test public void unacceptedGestureAndNativeRetractionNeverBecomeCustomActions() throws Throwable {
        event(MotionEvent.ACTION_DOWN, 0, 0, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 50, 220, 100, () -> { });
        stop(900, 220, 100, true);
        startAt(220, 100);
        event(MotionEvent.ACTION_MOVE, 600, 100, 100, () -> { });
        stop(1200, 100, 100, false);

        assertEquals(List.of(true, false), nativeFinishes);
        assertEquals(0, executed);
    }

    @Test public void cancelAndMultitouchCannotTriggerAndNextGestureIsIndependent() throws Throwable {
        startAt(220, 100);
        event(MotionEvent.ACTION_CANCEL, 900, 220, 100, () -> nativeStop(false));
        startAt(220, 100);
        event(MotionEvent.ACTION_POINTER_DOWN, 500, 220, 100, () -> { });
        stop(1200, 220, 100, true);
        startAt(220, 100);
        stop(550, 220, 100, true);

        // Multi-touch after the waiting phase cancels that attempt instead of going back.
        assertEquals(List.of(false, false, false), nativeFinishes);
        assertEquals(1, executed);
    }

    @Test public void defaultNoneAndIncompleteTargetsKeepOriginalCompletion() throws Throwable {
        for (DesktopConfig mapping : List.of(DesktopConfig.defaults(),
                DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                        DesktopConfig.ACTION_NONE),
                DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                        DesktopConfig.ACTION_LAUNCH_APP))) {
            config = mapping;
            startAt(220, 100);
            stop(550, 220, 100, true);
        }

        assertEquals(List.of(true, true, true), nativeFinishes);
        assertEquals(0, prepared);
    }

    @Test public void failedPreparationPreservesNativeAndDoesNotRunTheAction() throws Throwable {
        prepareAccepted = false;
        startAt(220, 100);
        stop(550, 220, 100, true);
        prepareFailure = new IllegalStateException("unavailable_token");
        startAt(220, 100);
        stop(550, 220, 100, true);

        assertEquals(List.of(true, true), nativeFinishes);
        assertEquals(2, prepared);
        assertEquals(0, executed);
    }

    @Test public void repeatedCompletionInOneFrameExecutesOnlyOnce() throws Throwable {
        startAt(220, 100);
        event(MotionEvent.ACTION_UP, 550, 220, 100, () -> {
            nativeStop(true);
            nativeStop(true);
        });

        assertEquals(List.of(false, false), nativeFinishes);
        assertEquals(1, executed);
    }

    @Test public void nativeCleanupFailureDoesNotRetryCompletionOrExecuteAction() throws Throwable {
        startAt(220, 100);
        nativeFailure = new IllegalStateException("native_cleanup_failed");
        try {
            stop(550, 220, 100, true);
            fail("native failure should propagate to libxposed protective handling");
        } catch (IllegalStateException expected) {
            assertSame(nativeFailure, expected);
        }

        assertEquals(List.of(false), nativeFinishes);
        assertEquals(0, executed);
    }

    @Test public void nativeFallbackFailureIsNotRetriedByPreparationCatch() throws Throwable {
        config = DesktopConfig.defaults();
        startAt(220, 100);
        nativeFailure = new IllegalStateException("native_default_failed");
        try {
            stop(550, 220, 100, true);
            fail("native failure should propagate");
        } catch (IllegalStateException expected) {
            assertSame(nativeFailure, expected);
        }

        assertEquals(List.of(true), nativeFinishes);
        assertEquals(0, prepared);
    }

    @Test public void runtimeIsAttachedOnceAndCallbacksOutsidePointerDispatchStayNative() throws Throwable {
        startAt(220, 100);
        stop(550, 220, 100, true);
        startAt(220, 100);
        stop(550, 220, 100, true);
        nativeStop(true);

        assertEquals(1, attached);
        assertEquals(List.of(false, false, true), nativeFinishes);
        assertEquals(2, executed);
    }

    @Test public void missingNativeContractDoesNotInstallPartialHooks() {
        Registry missing = new Registry();
        int count = LauncherBackHoldGesture.install(getClass().getClassLoader(),
                (name, loader) -> name.equals(LauncherBackHoldGesture.CALLBACK) ? null
                        : name.equals(LauncherBackHoldGesture.STUB) ? Stub.class : Processor.class,
                missing, () -> config, context -> { }, (context, snapshot) -> () -> { });

        assertEquals(0, count);
        assertTrue(missing.hooks.isEmpty());
    }

    private void startAt(float x, float y) throws Throwable {
        event(MotionEvent.ACTION_DOWN, 0, 0, y, () -> { });
        event(MotionEvent.ACTION_MOVE, 50, x, y, () -> registry.call(callback, "onSwipeStart",
                new Object[] {y}, args -> { order.add("start"); return null; }));
    }

    private void stop(long time, float x, float y, boolean finish) throws Throwable {
        event(MotionEvent.ACTION_UP, time, x, y, () -> nativeStop(finish));
    }

    private void nativeStop(boolean finish) throws Throwable {
        registry.call(callback, "onSwipeStop", new Object[] {finish, 220f, true}, args -> {
            boolean actual = (Boolean) args[0];
            assertEquals(220f, (Float) args[1], 0f);
            assertEquals(Boolean.TRUE, args[2]);
            nativeFinishes.add(actual);
            order.add(actual ? "back" : "cancel");
            if (nativeFailure != null) throw nativeFailure;
            return null;
        });
    }

    private void event(int action, long time, float x, float y, NativeCall nativeCall)
            throws Throwable {
        // Handler deadlines and MotionEvent times must share the monotonic uptime clock.
        // Advancing both also delivers the READY cue before an ordinary later UP.
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
        long eventTime = downTime + time;
        long untilEvent = eventTime - SystemClock.uptimeMillis();
        if (untilEvent > 0) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(untilEvent, TimeUnit.MILLISECONDS);
        }
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            registry.call(processor, "onPointerEvent", new Object[] {event, stub}, args -> {
                nativeCall.run();
                return null;
            });
        } finally { event.recycle(); }
    }

    private interface NativeCall { void run() throws Throwable; }
    private interface Original { Object call(Object[] args) throws Throwable; }

    private float stationaryTolerance() {
        return Math.max(ViewConfiguration.get(stub.getContext()).getScaledTouchSlop(),
                16f * stub.getResources().getDisplayMetrics().density);
    }

    public static final class Stub extends View {
        private final List<String> order;
        public Stub(Context context, List<String> order) { super(context); this.order = order; }
        private void shockByBackGesture() { order.add("ready-cue"); }
    }
    public static final class Processor {
        public static boolean isShowBackArrow(float distance) { return distance > 180f; }
        public void onPointerEvent(MotionEvent event, Stub view) { }
    }
    public static final class Callback {
        public void onSwipeStart(float y) { }
        public void onSwipeStop(boolean finish, float distance, boolean feedback) { }
    }

    private static final class Registry implements LauncherBackHoldGesture.Registrar {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();

        @Override public boolean install(Method method, XposedInterface.Hooker hook) {
            hooks.put(method, hook);
            return true;
        }

        Object call(Object receiver, String name, Object[] args, Original original) throws Throwable {
            Method method = hooks.keySet().stream().filter(candidate -> candidate.getName().equals(name)
                    && candidate.getDeclaringClass().isInstance(receiver)).findFirst().orElseThrow();
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, called, values) -> switch (called.getName()) {
                        case "getThisObject" -> receiver;
                        case "getArgs" -> Arrays.asList(args);
                        case "proceed" -> original.call(values == null || values.length == 0
                                ? args : (Object[]) values[0]);
                        default -> null;
                    });
            return hooks.get(method).intercept(chain);
        }
    }
}
