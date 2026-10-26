package com.xtjm.fusionstatusbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class LauncherBackDistanceGateTest {
    @Test public void lowerNativeThresholdUsesItsExactStrictBoundary() {
        LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(LowerThreshold.class);
        assertFalse(gate.isReady(140f));
        assertTrue(gate.isReady(140.01f));
        assertTrue(gate.isReady(160f));
    }

    @Test public void higherNativeThresholdDoesNotArmAtTheOldConstant() {
        LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(HigherThreshold.class);
        assertFalse(gate.isReady(220f));
        assertFalse(gate.isReady(250f));
        assertTrue(gate.isReady(250.01f));
    }

    @Test public void nativeBooleanResultIsUsedRatherThanApproximatingADistance() {
        LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(ExactPredicate.class);
        assertTrue(gate.isReady(42f));
        assertFalse(gate.isReady(220f));
    }

    @Test public void nonfiniteAndNegativeDistancesNeverReachTheNativeRule() {
        CountingPredicate.calls = 0;
        LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(CountingPredicate.class);
        for (float distance : new float[] {Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, -1f}) {
            assertFalse(gate.isReady(distance));
        }
        assertEquals(0, CountingPredicate.calls);
        assertTrue(gate.isReady(1f));
        assertEquals(1, CountingPredicate.calls);
    }

    @Test public void missingAndUnsupportedNativeContractsKeepTheProvidedHostFallback() {
        for (Class<?> type : new Class<?>[] {MissingPredicate.class, WrongReturn.class,
                InstancePredicate.class, WrongParameter.class, null}) {
            LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(type);
            assertFalse(gate.isReady(180f));
            assertTrue(gate.isReady(180.01f));
            assertFalse(gate.isReady(-1f));
            assertFalse(gate.isReady(Float.NaN));
        }
    }

    @Test public void aThrowingRuleFallsBackAndIsDisabledAfterOneAttempt() {
        ThrowingPredicate.calls = 0;
        LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(ThrowingPredicate.class);
        assertTrue(gate.isReady(220f));
        assertFalse(gate.isReady(180f));
        assertFalse(gate.isReady(140f));
        assertTrue(gate.isReady(500f));
        assertEquals(1, ThrowingPredicate.calls);
    }

    @Test public void anInheritedPrivateStaticHostRuleIsSupported() {
        LauncherBackDistanceGate gate = LauncherBackDistanceGate.resolve(DerivedProcessor.class);
        assertFalse(gate.isReady(140f));
        assertTrue(gate.isReady(140.01f));
    }

    public static class LowerThreshold {
        private static boolean isShowBackArrow(float distance) { return distance > 140f; }
    }
    public static final class DerivedProcessor extends LowerThreshold { }
    public static final class HigherThreshold {
        private static boolean isShowBackArrow(float distance) { return distance > 250f; }
    }
    public static final class ExactPredicate {
        private static boolean isShowBackArrow(float distance) { return distance == 42f; }
    }
    public static final class CountingPredicate {
        static int calls;
        private static boolean isShowBackArrow(float distance) { calls++; return true; }
    }
    public static final class MissingPredicate { }
    public static final class WrongReturn {
        private static int isShowBackArrow(float distance) { throw new AssertionError(); }
    }
    public static final class InstancePredicate {
        private boolean isShowBackArrow(float distance) { throw new AssertionError(); }
    }
    public static final class WrongParameter {
        private static boolean isShowBackArrow(double distance) { throw new AssertionError(); }
    }
    public static final class ThrowingPredicate {
        static int calls;
        private static boolean isShowBackArrow(float distance) {
            calls++;
            throw new IllegalStateException("fixture native rule unavailable");
        }
    }
}
