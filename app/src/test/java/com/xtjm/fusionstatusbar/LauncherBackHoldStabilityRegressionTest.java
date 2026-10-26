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
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
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

/** Symptom traces: a completed pause should survive normal finger-lift drift. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherBackHoldStabilityRegressionTest {
    private final Registry registry = new Registry();
    private final Processor processor = new Processor();
    private final Callback callback = new Callback();
    private final List<Boolean> nativeFinishes = new ArrayList<>();
    private final List<String> completionOrder = new ArrayList<>();
    private Stub stub;
    private int executed;
    private int slop;
    private float stationaryTolerance;
    private long downTime;

    @Before public void setUp() {
        stub = new Stub(RuntimeEnvironment.getApplication(), completionOrder);
        Processor.threshold = 180f;
        slop = ViewConfiguration.get(stub.getContext()).getScaledTouchSlop();
        stationaryTolerance = Math.max(slop, 16f * stub.getResources().getDisplayMetrics().density);
        DesktopConfig config = DesktopConfig.defaults().withGesture(
                DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_SCREENSHOT);
        assertEquals(4, LauncherBackHoldGesture.install(getClass().getClassLoader(),
                (name, loader) -> switch (name) {
                    case LauncherBackHoldGesture.PROCESSOR -> Processor.class;
                    case LauncherBackHoldGesture.STUB -> Stub.class;
                    case LauncherBackHoldGesture.CALLBACK -> Callback.class;
                    default -> null;
                }, registry, () -> config, ignored -> { }, (context, snapshot) -> () -> {
                    executed++;
                    completionOrder.add("action");
                }));
    }

    @Test public void sparseStationaryEventsTriggerTheSameCompletedPause() throws Throwable {
        start();
        stop(600, 220, 100, true);
        assertEquals(1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void readinessUsesTheInstalledHostDistanceInsteadOfAFixedPixelThreshold() throws Throwable {
        Processor.threshold = 140f;
        event(MotionEvent.ACTION_DOWN, 0, 0, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 50, 150, 100,
                () -> registry.call(callback, "onSwipeStart", new Object[] {100f}, args -> null));
        idle(500);
        stop(600, 150, 100, true);
        assertEquals(1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void readyFeedbackPrecedesFingerLiftAndNativeCleanupThenTheAction() throws Throwable {
        start();
        idle(500);
        assertEquals(List.of("ready-cue"), completionOrder);
        assertEquals(0, executed);
        stop(600, 220, 100, true);
        assertEquals(List.of("ready-cue", "native-cancel", "action"), completionOrder);
    }

    @Test public void subSlopSensorJitterDoesNotChangeTheResult() throws Throwable {
        start();
        float noise = slop * .3f;
        for (long time = 100; time <= 550; time += 50) {
            float sign = time % 100 == 0 ? 1 : -1;
            event(MotionEvent.ACTION_MOVE, time, 220 + noise * sign, 100 - noise * sign,
                    () -> { });
        }
        stop(600, 220, 100, true);
        assertEquals(1, executed);
    }

    @Test public void completedPauseSurvivesTheFingerLiftCoordinateShift() throws Throwable {
        start();
        // The stationary MOVE proves that the 500ms pause completed before lifting.
        event(MotionEvent.ACTION_MOVE, 550, 220, 100, () -> { });
        // Contact centroid shifts on lift; still far inside the host's committed region.
        stop(600, 220 + slop + 1, 100, true);
        assertEquals("A completed pause must not be lost only because UP shifts the contact centroid",
                1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void completedPauseSurvivesSmallMovementBeforeTheLift() throws Throwable {
        start();
        event(MotionEvent.ACTION_MOVE, 550, 220, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 580, 220 + slop + 1, 100, () -> { });
        stop(600, 220 + slop + 1, 100, true);
        assertEquals("After a confirmed pause, a small inward adjustment must not discard it",
                1, executed);
    }

    @Test public void pauseAlarmConfirmsReadinessWithoutStationaryMoveEvents() throws Throwable {
        start();
        idle(500);
        stop(600, 220 + slop + 1, 100, true);
        assertEquals("Silence while the finger is still must not require an extra MOVE", 1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void capturedWaitReleasedBeforeReadyCancelsBackAndTheAlarm() throws Throwable {
        start();
        idle(499);
        stop(549, 220 + slop + 1, 100, true);
        idle(1000);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
        assertFalse(completionOrder.contains("ready-cue"));
    }

    @Test public void deliberateMovementRestartsTheAlarmInsteadOfUsingTheOldDeadline() throws Throwable {
        start();
        idle(300);
        event(MotionEvent.ACTION_MOVE, 350, 220 + stationaryTolerance + 2, 100, () -> { });
        idle(250);
        stop(600, 220 + stationaryTolerance + 2, 100, true);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);

        start();
        idle(300);
        event(MotionEvent.ACTION_MOVE, 350, 220 + stationaryTolerance + 2, 100, () -> { });
        idle(500);
        stop(900, 220 + stationaryTolerance + 2, 100, true);
        assertEquals(1, executed);
        assertEquals(List.of(false, false), nativeFinishes);
    }

    @Test public void cancelledGestureAlarmCannotArmTheNextGesture() throws Throwable {
        start();
        idle(300);
        event(MotionEvent.ACTION_CANCEL, 350, 220, 100, () -> nativeStop(false));
        start();
        idle(250); // Past the old gesture's deadline, before this gesture's deadline.
        stop(300, 220 + slop + 1, 100, true);
        idle(1000);
        assertEquals(0, executed);
        assertEquals(List.of(false, false), nativeFinishes);
    }

    @Test public void withdrawingAfterReadinessRequiresANewPauseWhenReturningInside() throws Throwable {
        start();
        event(MotionEvent.ACTION_MOVE, 550, 220, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 580, 100, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 600, 220, 100, () -> { });
        stop(800, 220, 100, true);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void delayedInputMovementBeforeTheAlarmRunsDoesNotInventAPause() throws Throwable {
        start();
        // Advance uptime without delivering queued alarms: this input reaches the hook first.
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));
        event(MotionEvent.ACTION_MOVE, 650, 220 + stationaryTolerance * 3, 100, () -> { });
        stop(700, 220 + stationaryTolerance * 3, 100, true);
        assertEquals(0, executed);
        assertEquals(List.of(true), nativeFinishes);
        assertFalse(completionOrder.contains("ready-cue"));
    }

    @Test public void emittedReadyCueIsNotRetroactivelyRevokedByDelayedOldMovement() throws Throwable {
        long base = startAlignedWithUptime();
        idle(500); // The readiness alarm runs at base+550 before the delayed input batch.
        idle(10);
        MotionEvent batch = MotionEvent.obtain(base, base + 200, MotionEvent.ACTION_MOVE,
                220 + stationaryTolerance * 2, 100, 0);
        batch.addBatch(base + 400, 220 + stationaryTolerance * 4, 100, 1, 1, 0);
        batch.addBatch(base + 560, 220 + stationaryTolerance * 4, 100, 1, 1, 0);
        try { dispatch(batch, () -> { }); }
        finally { batch.recycle(); }
        idle(20);
        eventAtUptime(base, base + 580, MotionEvent.ACTION_UP,
                220 + stationaryTolerance * 4, 100, () -> nativeStop(true));
        assertEquals("An already emitted cue must not be revoked using late historical samples",
                1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void delayedHistoryMovingAfterTheReadyDeadlineKeepsTheLatchedPause() throws Throwable {
        long base = startAlignedWithUptime();
        idle(500);
        idle(100);
        MotionEvent batch = MotionEvent.obtain(base, base + 570, MotionEvent.ACTION_MOVE,
                220 + stationaryTolerance * 2, 100, 0);
        batch.addBatch(base + 600, 220 + stationaryTolerance * 4, 100, 1, 1, 0);
        batch.addBatch(base + 650, 220 + stationaryTolerance * 4, 100, 1, 1, 0);
        try { dispatch(batch, () -> { }); }
        finally { batch.recycle(); }
        idle(20);
        eventAtUptime(base, base + 670, MotionEvent.ACTION_UP,
                220 + stationaryTolerance * 4, 100, () -> nativeStop(true));
        assertEquals(1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void delayedOldHistoryCannotHideARealCurrentWithdrawalAfterTheCue() throws Throwable {
        long base = startAlignedWithUptime();
        idle(500);
        assertEquals(List.of("ready-cue"), completionOrder);
        idle(10);
        MotionEvent batch = MotionEvent.obtain(base, base + 200, MotionEvent.ACTION_MOVE,
                220 + stationaryTolerance * 2, 100, 0);
        batch.addBatch(base + 400, 220, 100, 1, 1, 0);
        batch.addBatch(base + 560, 80, 100, 1, 1, 0);
        try { dispatch(batch, () -> { }); }
        finally { batch.recycle(); }
        idle(20);
        eventAtUptime(base, base + 580, MotionEvent.ACTION_UP, 80, 100, () -> nativeStop(false));
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
        assertEquals(List.of("ready-cue", "native-cancel"), completionOrder);
    }

    @Test public void earlyFingerLiftDeliveredAfterTheAlarmCancelsWithoutExecutingOrGoingBack()
            throws Throwable {
        long base = startAlignedWithUptime();
        idle(500);
        eventAtUptime(base, base + 549, MotionEvent.ACTION_UP,
                220, 100, () -> nativeStop(true));
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void inputEarlierThanTheActuallyDeliveredCueCannotUseTheNominalReadyDeadline()
            throws Throwable {
        long base = startAlignedWithUptime();
        // A blocked owner Looper delivers the nominal base+550ms cue only at base+750ms.
        ShadowSystemClock.advanceBy(Duration.ofMillis(700));
        idle(0);
        assertEquals(List.of("ready-cue"), completionOrder);
        assertEquals(0, executed);
        // The finger lifted after the nominal deadline, but before the user felt readiness.
        eventAtUptime(base, base + 650, MotionEvent.ACTION_UP, 220, 100, () -> nativeStop(true));
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void coalescedStationaryHistoryMatchesIndividualSamples() throws Throwable {
        start();
        ShadowSystemClock.advanceBy(Duration.ofMillis(500));
        MotionEvent batched = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_MOVE,
                220, 100, 0);
        batched.addBatch(downTime + 300, 220, 100, 1, 1, 0);
        batched.addBatch(downTime + 550, 220, 100, 1, 1, 0);
        try {
            dispatch(batched, () -> { });
        } finally {
            batched.recycle();
        }
        idle(0); // Deliver the queued READY cue before release.
        stop(600, 220, 100, true);
        assertEquals(1, executed);
    }

    @Test public void continuousDeliberateMovementDoesNotBecomeAHold() throws Throwable {
        start();
        for (long time = 100; time <= 650; time += 50) {
            event(MotionEvent.ACTION_MOVE, time, 220 + (time / 50f) * (stationaryTolerance + 2), 100,
                    () -> { });
        }
        stop(700, 220 + 14 * (stationaryTolerance + 2), 100, true);
        assertEquals(0, executed);
        assertEquals(List.of(true), nativeFinishes);
    }

    @Test public void cancellationAfterAPauseKeepsTheNativeCancellation() throws Throwable {
        start();
        event(MotionEvent.ACTION_MOVE, 550, 220, 100, () -> { });
        event(MotionEvent.ACTION_CANCEL, 600, 220, 100, () -> nativeStop(false));
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void retractionAfterAPauseDoesNotFireAWithdrawnGesture() throws Throwable {
        start();
        event(MotionEvent.ACTION_MOVE, 550, 220, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 580, 100, 100, () -> { });
        stop(600, 100, 100, false);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    private void start() throws Throwable {
        event(MotionEvent.ACTION_DOWN, 0, 0, 100, () -> { });
        event(MotionEvent.ACTION_MOVE, 50, 220, 100,
                () -> registry.call(callback, "onSwipeStart", new Object[] {100f}, args -> null));
    }

    private void stop(long time, float x, float y, boolean finish) throws Throwable {
        event(MotionEvent.ACTION_UP, time, x, y, () -> nativeStop(finish));
    }

    private void nativeStop(boolean finish) throws Throwable {
        registry.call(callback, "onSwipeStop", new Object[] {finish, 220f, true}, args -> {
            nativeFinishes.add((Boolean) args[0]);
            completionOrder.add(Boolean.TRUE.equals(args[0]) ? "native-back" : "native-cancel");
            return null;
        });
    }

    private long startAlignedWithUptime() throws Throwable {
        long base = SystemClock.uptimeMillis();
        downTime = base;
        eventAtUptime(base, base, MotionEvent.ACTION_DOWN, 0, 100, () -> { });
        idle(50);
        eventAtUptime(base, base + 50, MotionEvent.ACTION_MOVE, 220, 100,
                () -> registry.call(callback, "onSwipeStart", new Object[] {100f}, args -> null));
        return base;
    }

    private void eventAtUptime(long downTime, long time, int action, float x, float y,
            NativeCall original) throws Throwable {
        // Explicit delivery tests may intentionally carry an older input timestamp.
        MotionEvent event = MotionEvent.obtain(downTime, time, action, x, y, 0);
        try { dispatch(event, original); }
        finally { event.recycle(); }
    }

    private void event(int action, long time, float x, float y, NativeCall original) throws Throwable {
        // Fixed fake eventTime=1000 previously ran in a different clock domain from Handler.
        // Ordinary events now advance the paused Looper and input uptime together.
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
        long eventTime = downTime + time;
        long untilEvent = eventTime - SystemClock.uptimeMillis();
        if (untilEvent > 0) idle(untilEvent);
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try { dispatch(event, original); }
        finally { event.recycle(); }
    }

    private void dispatch(MotionEvent event, NativeCall original) throws Throwable {
        registry.call(processor, "onPointerEvent", new Object[] {event, stub}, args -> {
            original.run();
            return null;
        });
    }

    private static void idle(long milliseconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS);
    }

    interface NativeCall { void run() throws Throwable; }
    interface Original { Object run(Object[] args) throws Throwable; }

    public static final class Stub extends View {
        private final List<String> completionOrder;
        public Stub(Context context, List<String> completionOrder) {
            super(context);
            this.completionOrder = completionOrder;
        }
        private void shockByBackGesture() { completionOrder.add("ready-cue"); }
    }
    public static final class Processor {
        static float threshold;
        public static boolean isShowBackArrow(float distance) { return distance > threshold; }
        public void onPointerEvent(MotionEvent event, Stub view) { }
    }
    public static final class Callback {
        public void onSwipeStart(float y) { }
        public void onSwipeStop(boolean finish, float distance, boolean feedback) { }
    }

    private static final class Registry implements LauncherBackHoldGesture.Registrar {
        private final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
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
                        case "proceed" -> original.run(values == null || values.length == 0
                                ? args : (Object[]) values[0]);
                        default -> null;
                    });
            return hooks.get(method).intercept(chain);
        }
    }
}
