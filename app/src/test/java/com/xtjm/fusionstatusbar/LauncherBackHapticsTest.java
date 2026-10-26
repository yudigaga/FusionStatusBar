package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class LauncherBackHapticsTest {
    @Test @Config(sdk = 33) public void nativeFeedbackEmitsOnceWithoutFrameworkFallback() {
        NativeStub stub = new NativeStub(context());
        LauncherBackHaptics.trigger(stub);
        assertEquals(1, stub.nativePulses);
        assertEquals(0, stub.frameworkPulses);
    }

    @Test public void inheritedPrivateNativeFeedbackIsFoundOnTheHostBase() {
        DerivedStub stub = new DerivedStub(context());
        LauncherBackHaptics.trigger(stub);
        LauncherBackHaptics.trigger(stub);
        assertEquals(2, stub.nativePulses);
        assertEquals(0, stub.frameworkPulses);
    }

    @Test public void missingNativeContractUsesConfirmWithoutIgnoringSettings() {
        FrameworkStub stub = new FrameworkStub(context());
        LauncherBackHaptics.trigger(stub);
        assertEquals(1, stub.frameworkPulses);
        assertEquals(HapticFeedbackConstants.CONFIRM, stub.feedback);
        assertEquals(0, stub.flags);
    }

    @Test public void nativeFeedbackThatAlreadyStartedThenThrowsIsNotRetried() {
        ThrowingNativeStub stub = new ThrowingNativeStub(context());
        LauncherBackHaptics.trigger(stub);
        assertEquals(1, stub.nativePulses);
        assertEquals(0, stub.frameworkPulses);
    }

    @Test public void wrongReturnAndStaticNativeContractsUseFrameworkFeedback() {
        WrongReturnStub wrongReturn = new WrongReturnStub(context());
        StaticNativeStub staticNative = new StaticNativeStub(context());
        LauncherBackHaptics.trigger(wrongReturn);
        LauncherBackHaptics.trigger(staticNative);
        assertEquals(1, wrongReturn.frameworkPulses);
        assertEquals(1, staticNative.frameworkPulses);
        assertEquals(0, wrongReturn.nativePulses);
    }

    @Test public void disabledOrFailingFrameworkFeedbackDoesNotRetryOrBreakTheAction() {
        FrameworkStub disabled = new FrameworkStub(context());
        disabled.enabled = false;
        LauncherBackHaptics.trigger(disabled);
        assertEquals(1, disabled.frameworkPulses);
        FrameworkStub failed = new FrameworkStub(context());
        failed.fail = true;
        LauncherBackHaptics.trigger(failed);
        assertEquals(1, failed.frameworkPulses);
    }

    @Test public void nullStubDoesNotPreventTheFollowingAction() {
        LauncherBackHaptics.trigger(null);
    }

    private Context context() { return RuntimeEnvironment.getApplication(); }

    public static class FrameworkStub extends View {
        int frameworkPulses;
        int feedback;
        int flags = -1;
        boolean enabled = true;
        boolean fail;

        FrameworkStub(Context context) { super(context); }

        @Override public boolean performHapticFeedback(int feedbackConstant) {
            return performHapticFeedback(feedbackConstant, 0);
        }

        @Override public boolean performHapticFeedback(int feedbackConstant, int feedbackFlags) {
            frameworkPulses++;
            feedback = feedbackConstant;
            flags = feedbackFlags;
            if (fail) throw new IllegalStateException("fixture feedback unavailable");
            return enabled;
        }
    }

    public static class NativeStub extends FrameworkStub {
        int nativePulses;
        NativeStub(Context context) { super(context); }
        private void shockByBackGesture() { nativePulses++; }
    }

    public static final class DerivedStub extends NativeStub {
        DerivedStub(Context context) { super(context); }
    }

    public static final class ThrowingNativeStub extends FrameworkStub {
        int nativePulses;
        ThrowingNativeStub(Context context) { super(context); }
        private void shockByBackGesture() {
            nativePulses++;
            throw new IllegalStateException("fixture already emitted feedback");
        }
    }

    public static final class WrongReturnStub extends FrameworkStub {
        int nativePulses;
        WrongReturnStub(Context context) { super(context); }
        private boolean shockByBackGesture() { nativePulses++; return true; }
    }

    public static final class StaticNativeStub extends FrameworkStub {
        StaticNativeStub(Context context) { super(context); }
        private static void shockByBackGesture() { throw new AssertionError("must not be called"); }
    }
}
