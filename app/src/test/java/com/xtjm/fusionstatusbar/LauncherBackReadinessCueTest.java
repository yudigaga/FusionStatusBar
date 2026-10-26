package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
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

/** The custom second pulse announces readiness while the finger is still held. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherBackReadinessCueTest {
    private final Registry registry = new Registry();
    private final Processor processor = new Processor();
    private final Callback callback = new Callback();
    private final List<String> order = new ArrayList<>();
    private final List<Boolean> nativeFinishes = new ArrayList<>();
    private DesktopConfig config;
    private Stub stub;
    private boolean actionAvailable;
    private int executed;
    private int preparations;
    private NativeCall duringReadyPreparation;
    private long downTime;
    private float currentX;

    @Before public void setUp() {
        config = DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                DesktopConfig.ACTION_SCREENSHOT);
        actionAvailable = true;
        stub = new Stub(RuntimeEnvironment.getApplication(), order);
        assertEquals(4, LauncherBackHoldGesture.install(getClass().getClassLoader(),
                (name, loader) -> switch (name) {
                    case LauncherBackHoldGesture.PROCESSOR -> Processor.class;
                    case LauncherBackHoldGesture.STUB -> Stub.class;
                    case LauncherBackHoldGesture.CALLBACK -> Callback.class;
                    default -> null;
                }, registry, () -> config, ignored -> { }, (context, snapshot) -> {
                    preparations++;
                    if (preparations == 2 && duringReadyPreparation != null) {
                        NativeCall reentry = duringReadyPreparation;
                        duringReadyPreparation = null;
                        try { reentry.run(); }
                        catch (Throwable error) { throw new AssertionError("Preparation reentry failed", error); }
                    }
                    return !actionAvailable ? null : () -> {
                            executed++;
                            order.add("action");
                        };
                }));
    }

    @Test public void secondPulseAnnouncesReadyBeforeAnyFingerLift() throws Throwable {
        start(220);
        idle(499);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals("The second pulse must arrive when the hold becomes ready, before UP",
                1, stub.readyPulses);
        assertEquals(0, executed);
        assertTrue(nativeFinishes.isEmpty());
    }

    @Test public void releaseExecutesAfterTheReadyCueWithoutAThirdPulse() throws Throwable {
        start(220);
        idle(500);
        stop(true);
        assertEquals(1, executed);
        assertEquals(1, stub.readyPulses);
        assertEquals(List.of("ready-cue", "native-cancel", "action"), order);
    }

    @Test public void continuingToHoldDoesNotRepeatTheReadyCueOrExecuteEarly() throws Throwable {
        start(220);
        idle(2500);
        assertEquals(1, stub.readyPulses);
        assertEquals(0, executed);
        move(224);
        idle(500);
        assertEquals(1, stub.readyPulses);
        assertEquals(0, executed);
        stop(true);
        assertEquals(1, executed);
        assertEquals(1, stub.readyPulses);
    }

    @Test public void unconfiguredNoneAndIncompleteActionsNeverAnnounceCustomReadiness() throws Throwable {
        for (DesktopConfig snapshot : List.of(DesktopConfig.defaults(),
                DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                        DesktopConfig.ACTION_NONE),
                DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                        DesktopConfig.ACTION_LAUNCH_APP))) {
            config = snapshot;
            start(220);
            idle(600);
            assertEquals(0, stub.readyPulses);
            stop(true);
        }
        assertEquals(0, executed);
        assertEquals(List.of(true, true, true), nativeFinishes);
    }

    @Test public void cancellationAndMultiTouchBeforeTheDeadlineRemoveTheCueAndAction()
            throws Throwable {
        for (int interrupted : new int[] {MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN}) {
            start(220);
            idle(499);
            event(interrupted, currentX, () -> nativeStop(false));
            idle(1000);
            assertEquals(0, stub.readyPulses);
            assertEquals(0, executed);
        }
        assertEquals(List.of(false, false), nativeFinishes);
    }

    @Test public void exactlyOneHundredMillisecondsCapturesTheWaitWithoutExecutingEarly()
            throws Throwable {
        start(220);
        idle(99);
        stop(true);
        start(220);
        idle(100);
        stop(true);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals("A captured hold attempt must not fall through to Back before readiness",
                List.of(true, false), nativeFinishes);
    }

    @Test public void quickContinuousSwipeRetainsTheNativeBackGesture() throws Throwable {
        start(220);
        idle(80);
        move(220 + stationaryTolerance() + 2);
        idle(80);
        stop(true);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals(List.of(true), nativeFinishes);
    }

    @Test public void tinyJitterAroundTheEntryBoundaryDoesNotRestartTheWholeHold() throws Throwable {
        start(182);
        idle(200);
        move(179); // Three raw pixels of noise around the native >180px boundary.
        idle(200);
        move(183);
        idle(100);
        assertEquals("Tiny distance-boundary noise must not endlessly restart readiness",
                1, stub.readyPulses);
        assertEquals(0, executed);
    }

    @Test public void afterReadySmallBoundaryDriftKeepsTheCuePromiseEvenIfNativeFinishIsFalse()
            throws Throwable {
        start(220);
        idle(500);
        move(176);
        stop(false);
        assertEquals("A ready cue must use the same latched criterion as the release",
                1, executed);
        assertEquals(1, stub.readyPulses);
        assertEquals(List.of("ready-cue", "native-cancel", "action"), order);
    }

    @Test public void genuineWithdrawalAfterReadyCancelsWithoutAnotherCueOrAction() throws Throwable {
        start(220);
        idle(500);
        int announced = stub.readyPulses;
        move(80);
        idle(1000);
        stop(false);
        assertEquals(announced, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void deliberateMotionBeforeReadyRestartsTheCueDeadline() throws Throwable {
        start(220);
        idle(300);
        move(220 + stationaryTolerance() + 2);
        idle(300);
        assertEquals(0, stub.readyPulses);
        idle(200);
        assertEquals(1, stub.readyPulses);
        assertEquals(0, executed);
    }

    @Test public void resumingDeliberateMovementBeforeReadyReleasesCapturedWaiting()
            throws Throwable {
        start(220);
        idle(200); // A wait attempt has already captured the original Back gesture.
        move(220 + stationaryTolerance() + 2);
        idle(99);
        stop(true);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals("Continuing the swipe must release capture until a new pause reaches 100ms",
                List.of(true), nativeFinishes);
    }

    @Test @Config(qualifiers = "xhdpi")
    public void twelveDpSensorDriftStillCountsAsStationaryOnDenseScreens() throws Throwable {
        assertEquals(2f, density(), .01f);
        start(220);
        idle(250);
        // 24 physical pixels exceed the stock 8dp touch slop, but remain below 16dp.
        move(220 + 12 * density());
        idle(250);
        assertEquals(1, stub.readyPulses);
        assertEquals(0, executed);
        stop(true);
        assertEquals(1, executed);
        assertEquals(1, stub.readyPulses);
    }

    @Test @Config(qualifiers = "xhdpi")
    public void waitingAndReadyWithdrawalTolerancesScaleWithDensity() throws Throwable {
        assertEquals(2f, density(), .01f);
        start(182);
        idle(250);
        move(162); // 18px below native entry remains inside the 12dp waiting margin.
        idle(250);
        assertEquals(1, stub.readyPulses);
        move(140); // 40px below native entry remains inside the larger 24dp READY margin.
        stop(false);
        assertEquals(1, executed);
        assertEquals(1, stub.readyPulses);
        assertEquals(List.of("ready-cue", "native-cancel", "action"), order);
    }

    @Test @Config(qualifiers = "mdpi")
    public void theSamePhysicalWithdrawalExitsReadyOnALowerDensityScreen() throws Throwable {
        assertEquals(1f, density(), .01f);
        start(182);
        idle(500);
        assertEquals(1, stub.readyPulses);
        move(140); // The same 40px withdrawal now exceeds the 24dp READY margin.
        stop(false);
        assertEquals(0, executed);
        assertEquals(1, stub.readyPulses);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void unavailableActionDoesNotGiveAMisleadingReadyCue() throws Throwable {
        actionAvailable = false;
        start(220);
        idle(700);
        assertEquals(0, stub.readyPulses);
        stop(true);
        assertEquals(0, executed);
        assertEquals(List.of(true), nativeFinishes);
    }

    @Test public void alarmUsesThePointerOwnerInsteadOfAnAttachedViewOnAnotherLooper()
            throws Throwable {
        HandlerThread otherOwner = new HandlerThread("back-readiness-other-view-owner");
        otherOwner.start();
        Looper otherLooper = otherOwner.getLooper();
        Shadows.shadowOf(otherLooper).pause();
        stub.attachedHandler = new Handler(otherLooper);
        try {
            assertNotSame(Looper.getMainLooper(), otherLooper);
            start(220); // Pointer events belong to Main; the attached View Handler does not.
            idle(500);
            assertEquals("The readiness alarm must share the pointer dispatch Looper", 1, stub.readyPulses);
            assertEquals(0, executed);
            stop(true);
            assertEquals(1, executed);
        } finally {
            otherOwner.quit();
            otherOwner.join(1000);
            assertFalse("The isolated test Looper must terminate", otherOwner.isAlive());
        }
    }

    @Test public void releaseReenteredDuringAvailabilityCannotEmitAnOldTimerCue() throws Throwable {
        duringReadyPreparation = () -> stop(true);
        start(220);
        idle(500);
        assertEquals("A preparation callback that already released the finger must invalidate its alarm",
                0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
        idle(1000);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
    }

    private void start(float x) throws Throwable {
        downTime = SystemClock.uptimeMillis();
        event(MotionEvent.ACTION_DOWN, 0, () -> { });
        idle(50);
        event(MotionEvent.ACTION_MOVE, x,
                () -> registry.call(callback, "onSwipeStart", new Object[] {100f}, args -> null));
    }

    private void move(float x) throws Throwable {
        event(MotionEvent.ACTION_MOVE, x, () -> { });
    }

    private void stop(boolean finish) throws Throwable {
        event(MotionEvent.ACTION_UP, currentX, () -> nativeStop(finish));
    }

    private void event(int action, float x, NativeCall original) throws Throwable {
        currentX = x;
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x, 100, 0);
        try {
            registry.call(processor, "onPointerEvent", new Object[] {event, stub}, args -> {
                original.run();
                return null;
            });
        } finally {
            event.recycle();
        }
    }

    private void nativeStop(boolean finish) throws Throwable {
        registry.call(callback, "onSwipeStop", new Object[] {finish, currentX, true}, args -> {
            nativeFinishes.add((Boolean) args[0]);
            order.add(Boolean.TRUE.equals(args[0]) ? "native-back" : "native-cancel");
            return null;
        });
    }

    private static void idle(long milliseconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS);
    }

    private float density() {
        return stub.getResources().getDisplayMetrics().density;
    }

    private float stationaryTolerance() {
        return Math.max(ViewConfiguration.get(stub.getContext()).getScaledTouchSlop(),
                16f * density());
    }

    interface NativeCall { void run() throws Throwable; }
    interface Original { Object run(Object[] args) throws Throwable; }

    public static final class Stub extends View {
        final List<String> order;
        int readyPulses;
        Handler attachedHandler;
        Stub(Context context, List<String> order) { super(context); this.order = order; }
        @Override public Handler getHandler() {
            return attachedHandler == null ? super.getHandler() : attachedHandler;
        }
        private void shockByBackGesture() { pulse(); }
        @Override public boolean performHapticFeedback(int feedback) { pulse(); return true; }
        @Override public boolean performHapticFeedback(int feedback, int flags) { pulse(); return true; }
        private void pulse() { readyPulses++; order.add("ready-cue"); }
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
                        case "proceed" -> original.run(values == null || values.length == 0
                                ? args : (Object[]) values[0]);
                        default -> null;
                    });
            return hooks.get(method).intercept(chain);
        }
    }
}
