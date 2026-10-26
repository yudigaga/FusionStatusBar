package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedInterface;

import static org.junit.Assert.*;

/** Configured thresholds drive the actual pointer/start/stop interception chain. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherBackHoldThresholdsTest {
    private final Registry registry = new Registry();
    private final Processor processor = new Processor();
    private final Callback callback = new Callback();
    private final List<Boolean> nativeFinishes = new ArrayList<>();
    private final List<Boolean> arrowDecisions = new ArrayList<>();
    private final List<Boolean> nativeUpDecisions = new ArrayList<>();
    private DesktopConfig config;
    private Stub stub;
    private int executed;
    private int nativeReadyTransitions;
    private boolean arrowVisible;
    private long downTime;
    private float downX;
    private float currentX;
    private float direction;

    @Before public void setUp() {
        Processor.threshold = 180f;
        config = DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                DesktopConfig.ACTION_SCREENSHOT);
        stub = new Stub(RuntimeEnvironment.getApplication());
        int installed = LauncherBackHoldGesture.install(getClass().getClassLoader(),
                (name, loader) -> switch (name) {
                    case LauncherBackHoldGesture.PROCESSOR -> Processor.class;
                    case LauncherBackHoldGesture.STUB -> Stub.class;
                    case LauncherBackHoldGesture.CALLBACK -> Callback.class;
                    default -> null;
                }, registry, () -> config, ignored -> { },
                (context, snapshot) -> () -> executed++);
        assertTrue("The three lifecycle hooks must be available", installed >= 3);
    }

    @Test public void eachConfiguredWaitingTimeControlsTheExactReadyDeadline() throws Throwable {
        for (int delay : new int[] {200, 500, 1000, 3000}) {
            config = config.withBackHoldThresholds(60, delay);
            int beforeCue = stub.readyPulses;
            int beforeAction = executed;
            start(60);
            idle(delay - 1L);
            assertEquals("Must not use an earlier deadline for " + delay + "ms",
                    beforeCue, stub.readyPulses);
            assertEquals(beforeAction, executed);
            idle(1);
            assertEquals("The configured deadline must announce readiness before UP: " + delay,
                    beforeCue + 1, stub.readyPulses);
            assertEquals(beforeAction, executed);
            stopFromHost();
            assertEquals(beforeAction + 1, executed);
            assertEquals("Release must not add a completion pulse", beforeCue + 1, stub.readyPulses);
        }
        assertEquals(List.of(false, false, false, false), nativeFinishes);
    }

    @Test public void longerWaitDoesNotAccidentallyUseTheFormerFiveHundredMilliseconds()
            throws Throwable {
        config = config.withBackHoldThresholds(60, 1200);
        start(60);
        idle(500);
        assertEquals(0, stub.readyPulses);
        move(60); // The MOVE sample path must also use 1200ms, not the old fixed 500ms.
        assertEquals(0, stub.readyPulses);
        idle(699);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
    }

    @Test public void earlyReleaseStillCancelsAfterTheHundredMillisecondWaitCapture()
            throws Throwable {
        config = config.withBackHoldThresholds(60, 200);
        start(60);
        idle(199);
        stopFromHost();
        idle(1000);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void cancelledShortDelayCannotArmTheNextLongDelayGesture() throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60);
        idle(200);
        stopFromHost();
        config = config.withBackHoldThresholds(60, 1000);
        start(60);
        idle(500); // The previous gesture's deadline is now in the past.
        assertEquals(0, stub.readyPulses);
        idle(499);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
        assertEquals(List.of(false, false), nativeFinishes);
    }

    @Test public void configuredDistanceMustBeReachedBeforeAnyWaitStarts() throws Throwable {
        config = config.withBackHoldThresholds(120, 200);
        start(100);
        idle(1500);
        assertEquals(0, stub.readyPulses);
        assertFalse(arrowDecisions.get(arrowDecisions.size() - 1));
        move(120);
        idle(199);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
    }

    @Test public void customDistanceIncludesItsExactEntryBoundary() throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(59);
        idle(700);
        assertEquals(0, stub.readyPulses);
        assertEquals(List.of(false), arrowDecisions);
        move(60);
        assertEquals(List.of(false, true), arrowDecisions);
        idle(499);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
    }

    @Test public void aLowerCustomDistanceAlsoEnablesTheNativeFirstStageArrowFeedback()
            throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60); // Native threshold is still 180px; only MOVE's arrow predicate is overridden.
        assertEquals("The first feedback must not remain tied to 180px", 1, nativeReadyTransitions);
        assertEquals(List.of(true), arrowDecisions);
        idle(500);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals("UP still evaluates the original native distance", List.of(false), nativeUpDecisions);
        assertEquals("READY must execute even when the native UP predicate rejects the distance",
                1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void aHigherCustomDistanceDoesNotAnnounceOrShowReadyAtTheOldDistance()
            throws Throwable {
        config = config.withBackHoldThresholds(240, 500);
        start(200);
        assertEquals(List.of(false), arrowDecisions);
        idle(700);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, nativeReadyTransitions);
        move(240);
        assertEquals(List.of(false, true), arrowDecisions);
        idle(500);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(List.of(true), nativeUpDecisions);
        assertEquals(List.of(false), nativeFinishes);
        assertEquals(1, executed);
    }

    @Test @Config(qualifiers = "xhdpi")
    public void customDistanceUsesDpRatherThanTheSameRawPixelCountOnDenseScreens()
            throws Throwable {
        assertEquals(2f, density(), .01f);
        config = config.withBackHoldThresholds(60, 500);
        start(119);
        idle(700);
        assertEquals(0, stub.readyPulses);
        assertEquals(List.of(false), arrowDecisions);
        move(120);
        assertEquals(List.of(false, true), arrowDecisions);
        idle(499);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
    }

    @Test public void theSameConfiguredDpDistanceAppliesToTheRightEdge() throws Throwable {
        config = config.withBackHoldThresholds(60, 200);
        start(60, true);
        assertEquals(List.of(true), arrowDecisions);
        idle(200);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void pendingDistanceJitterDoesNotRestartAConfiguredWait() throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(61);
        idle(250);
        move(55); // Within the 12dp pending band and stationary jitter radius.
        idle(250);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
    }

    @Test public void aSmallRetractionAfterReadyKeepsTheConfiguredCuePromise() throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60);
        idle(500);
        move(45); // Inside the ready band; native UP still rejects 45px.
        stopFromHost();
        assertEquals(1, stub.readyPulses);
        assertEquals(1, executed);
        assertEquals(List.of(false), nativeUpDecisions);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void theMinimumDistanceStillCancelsOnACompleteWithdrawal() throws Throwable {
        config = config.withBackHoldThresholds(16, 500);
        start(24); // Above Home's independent 20px native acceptance requirement.
        idle(500);
        assertEquals(1, stub.readyPulses);
        move(0);
        stopFromHost();
        assertEquals("A 24dp exit band must not swallow the entire 16dp entry distance",
                0, executed);
        assertEquals(1, stub.readyPulses);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void withdrawingDuringTheMinimumDistanceWaitCancelsThePendingAlarm()
            throws Throwable {
        config = config.withBackHoldThresholds(16, 500);
        start(24);
        idle(200);
        move(0);
        idle(1000);
        stopFromHost();
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void changesDuringWaitingCancelTheOldGestureAndApplyToTheNextOne()
            throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60);
        idle(300); // Waiting is already captured under the old snapshot.
        config = config.withBackHoldThresholds(90, 1000);
        move(60);
        assertTrue("The ongoing native MOVE must keep its own original snapshot",
                arrowDecisions.get(arrowDecisions.size() - 1));
        idle(200);
        assertEquals("A settings change must not mix old distance with new timing", 0, stub.readyPulses);
        stopFromHost();
        assertEquals(0, executed);
        start(90);
        idle(999);
        assertEquals(0, stub.readyPulses);
        idle(1);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
        assertEquals(List.of(false, false), nativeFinishes);
    }

    @Test public void changingTheThresholdsAfterTheCueCancelsInsteadOfRunningAnotherMapping()
            throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60);
        idle(500);
        assertEquals(1, stub.readyPulses);
        config = config.withBackHoldThresholds(90, 1000);
        stopFromHost();
        assertEquals(0, executed);
        assertEquals(1, stub.readyPulses);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void changingOnlyAppearanceDoesNotInvalidateTheTwoThresholds() throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60);
        idle(500);
        config = config.withBackGesturePreset(DesktopConfig.BACK_STYLE_RAINBOW);
        stopFromHost();
        assertEquals(1, executed);
        assertEquals(1, stub.readyPulses);
    }

    @Test public void aQuickNormalReturnKeepsItsNativeUpThresholdWithAHigherCustomDistance()
            throws Throwable {
        config = config.withBackHoldThresholds(240, 1000);
        start(200);
        idle(50);
        stopFromHost();
        assertEquals(List.of(false), arrowDecisions);
        assertEquals(List.of(true), nativeUpDecisions);
        assertEquals(List.of(true), nativeFinishes);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
    }

    @Test public void aLowerCustomDistanceDoesNotMakeAQuickNativeReturnEasier()
            throws Throwable {
        config = config.withBackHoldThresholds(60, 1000);
        start(60);
        idle(50);
        stopFromHost();
        assertEquals(List.of(true), arrowDecisions);
        assertEquals(List.of(false), nativeUpDecisions);
        assertEquals(List.of(false), nativeFinishes);
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
    }

    @Test public void theSystemDistanceOptionStillUsesTheInstalledNativePredicate()
            throws Throwable {
        Processor.threshold = 140f;
        config = config.withBackHoldThresholds(0, 500);
        start(140);
        idle(700);
        assertEquals(0, stub.readyPulses);
        assertEquals(List.of(false), arrowDecisions);
        stopFromHost();
        start(141);
        idle(500);
        assertEquals(1, stub.readyPulses);
        stopFromHost();
        assertEquals(1, executed);
        assertEquals(List.of(false, false), nativeFinishes);
    }

    @Test public void anUnconfiguredHoldNeverOverridesTheNativeArrowThreshold() throws Throwable {
        config = DesktopConfig.defaults().withBackHoldThresholds(60, 200);
        start(60);
        assertEquals(List.of(false), arrowDecisions);
        idle(1000);
        stopFromHost();
        assertEquals(0, stub.readyPulses);
        assertEquals(0, executed);
        assertEquals(List.of(false), nativeFinishes);
    }

    @Test public void theNativePredicateIsNotChangedOutsideAnActivePointerFrame() throws Throwable {
        config = config.withBackHoldThresholds(60, 500);
        start(60);
        assertEquals(List.of(true), arrowDecisions);
        assertFalse("A native query from another call path must not inherit this gesture's override",
                registry.nativePredicate(60));
        assertTrue(registry.nativePredicate(181));
        idle(500);
        stopFromHost();
        assertFalse(registry.nativePredicate(60));
        assertEquals(1, executed);
    }

    private void start(float distance) throws Throwable { start(distance, false); }

    private void start(float distance, boolean right) throws Throwable {
        downTime = SystemClock.uptimeMillis();
        downX = right ? 1000 : 0;
        direction = right ? -1 : 1;
        arrowVisible = false;
        event(MotionEvent.ACTION_DOWN, 0, () -> { });
        idle(50);
        event(MotionEvent.ACTION_MOVE, distance, () -> {
            registry.call(callback, "onSwipeStart", new Object[] {100f}, args -> null);
            nativeArrow(distance);
        });
    }

    private void move(float distance) throws Throwable {
        event(MotionEvent.ACTION_MOVE, distance, () -> nativeArrow(distance));
    }

    private void nativeArrow(float distance) throws Throwable {
        boolean visible = registry.nativePredicate(distance);
        arrowDecisions.add(visible);
        if (visible && !arrowVisible) nativeReadyTransitions++;
        arrowVisible = visible;
    }

    private void stopFromHost() throws Throwable {
        float distance = (currentX - downX) * direction;
        event(MotionEvent.ACTION_UP, distance, () -> {
            // Simulate the host's isTriggerBack distance branch. No velocity shortcut is used.
            boolean originalFinish = registry.nativePredicate(distance);
            nativeUpDecisions.add(originalFinish);
            registry.call(callback, "onSwipeStop", new Object[] {originalFinish, distance, true}, args -> {
                nativeFinishes.add((Boolean) args[0]);
                return null;
            });
        });
    }

    private void event(int action, float distance, NativeCall original) throws Throwable {
        currentX = downX + direction * distance;
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                currentX, 100, 0);
        try {
            registry.call(processor, "onPointerEvent", new Object[] {event, stub}, args -> {
                original.run();
                return null;
            });
        } finally { event.recycle(); }
    }

    private static void idle(long milliseconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS);
    }

    private float density() { return stub.getResources().getDisplayMetrics().density; }

    private interface NativeCall { void run() throws Throwable; }
    private interface Original { Object run(Object[] args) throws Throwable; }

    public static final class Stub extends View {
        int readyPulses;
        Stub(Context context) { super(context); }
        private void shockByBackGesture() { readyPulses++; }
        @Override public boolean performHapticFeedback(int feedback) { readyPulses++; return true; }
        @Override public boolean performHapticFeedback(int feedback, int flags) { readyPulses++; return true; }
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
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        @Override public boolean install(Method method, XposedInterface.Hooker hook) {
            hooks.put(method, hook);
            return true;
        }

        boolean nativePredicate(float distance) throws Throwable {
            Method hook = hooks.keySet().stream()
                    .filter(candidate -> candidate.getName().equals("isShowBackArrow")
                            && Modifier.isStatic(candidate.getModifiers())).findFirst().orElse(null);
            if (hook == null) return Processor.isShowBackArrow(distance);
            return (Boolean) invoke(hook, null, new Object[] {distance},
                    args -> Processor.isShowBackArrow((Float) args[0]));
        }

        Object call(Object receiver, String name, Object[] args, Original original) throws Throwable {
            Method method = hooks.keySet().stream().filter(candidate -> candidate.getName().equals(name)
                    && candidate.getDeclaringClass().isInstance(receiver)).findFirst().orElseThrow();
            return invoke(method, receiver, args, original);
        }

        private Object invoke(Method method, Object receiver, Object[] args, Original original)
                throws Throwable {
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
