package com.xtjm.fusionstatusbar;

import android.animation.Animator;
import android.content.Context;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

import io.github.libxposed.api.XposedInterface;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class LauncherBackReadyFeedbackGuardTest {
    private Arrow arrow;
    private Stub stub;
    private ReadyListener listener;
    private Registry registry;

    @Before public void setUp() {
        arrow = new Arrow(context());
        stub = new Stub(context(), arrow);
        listener = new ReadyListener(arrow, true);
        registry = new Registry();
        assertEquals(1, install(Arrow.class, ReadyListener.class, registry));
    }

    @Test @Config(sdk = 33) public void nativePullFeedbackBeforeReadyProceedsOnce() throws Throwable {
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        assertEquals(1, registry.proceeds);
        assertEquals(1, arrow.readyPulses);
        assertEquals(0, arrow.setterCalls);
    }

    @Test public void readySuppressesSynchronousSuccessAfterAnimationResetsItsFlag()
            throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(stub);
        arrow.restartAnimation();

        // A zero-duration animator can finish inside startArrowAnimating/onDraw itself.
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        assertEquals(1, registry.proceeds);
        assertEquals(0, arrow.readyPulses);
        assertEquals(1, arrow.setterCalls);
        assertTrue(arrow.feedbackDone);
    }

    @Test public void repeatedAnimationRestartsCannotAddThirdPulse() throws Throwable {
        registry.call(listener, () -> listener.onAnimationSuccess(null));
        LauncherBackReadyFeedbackGuard.markReady(stub);
        for (int i = 0; i < 3; i++) {
            arrow.restartAnimation();
            registry.call(listener, () -> listener.onAnimationSuccess(null));
        }

        assertEquals(4, registry.proceeds);
        assertEquals(1, arrow.readyPulses);
        assertEquals(3, arrow.setterCalls);
    }

    @Test public void markerIsLocalToItsArrow() throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(stub);
        Arrow other = new Arrow(context());
        ReadyListener otherListener = new ReadyListener(other, true);

        registry.call(otherListener, () -> otherListener.onAnimationSuccess(null));
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        assertEquals(1, other.readyPulses);
        assertEquals(0, other.setterCalls);
        assertEquals(0, arrow.readyPulses);
    }

    @Test public void lateCompletionStaysSuppressedUntilNextDownReset() throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(stub);
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        // No terminal clear: an old success callback can arrive after native cleanup.
        arrow.restartAnimation();
        registry.call(listener, () -> listener.onAnimationSuccess(null));
        assertEquals(0, arrow.readyPulses);

        // The next physical DOWN explicitly starts a fresh feedback lifecycle.
        LauncherBackReadyFeedbackGuard.reset(stub);
        arrow.restartAnimation();
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        assertEquals(3, registry.proceeds);
        assertEquals(1, arrow.readyPulses);
        assertEquals(2, arrow.setterCalls);
    }

    @Test public void inheritedPrivateStubFieldCanBeMarkedAndReset() throws Throwable {
        Stub inherited = new DerivedStub(context(), arrow);
        LauncherBackReadyFeedbackGuard.markReady(inherited);
        registry.call(listener, () -> listener.onAnimationSuccess(null));
        assertEquals(0, arrow.readyPulses);

        LauncherBackReadyFeedbackGuard.reset(inherited);
        arrow.restartAnimation();
        registry.call(listener, () -> listener.onAnimationSuccess(null));
        assertEquals(1, arrow.readyPulses);
    }

    @Test public void missingNullOrStaticStubArrowDoesNotSuppressNativeFeedback()
            throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(null);
        LauncherBackReadyFeedbackGuard.reset(null);
        LauncherBackReadyFeedbackGuard.markReady(new View(context()));
        LauncherBackReadyFeedbackGuard.markReady(new Stub(context(), null));
        StaticArrowStub.mGestureBackArrowView = arrow;
        try {
            LauncherBackReadyFeedbackGuard.markReady(new StaticArrowStub(context()));
            registry.call(listener, () -> listener.onAnimationSuccess(null));
            assertEquals(1, arrow.readyPulses);
        } finally {
            StaticArrowStub.mGestureBackArrowView = null;
        }
    }

    @Test public void setterFailureAfterSideEffectIsNotRetried() throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(stub);
        arrow.throwAfterSet = true;
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        assertEquals(1, arrow.setterCalls);
        assertEquals(1, registry.proceeds);
        assertEquals(0, arrow.readyPulses);
    }

    @Test public void setterFailureBeforeSideEffectStillProceedsOnlyOnce() throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(stub);
        arrow.throwBeforeSet = true;
        registry.call(listener, () -> listener.onAnimationSuccess(null));

        assertEquals(1, arrow.setterCalls);
        assertEquals(1, registry.proceeds);
        assertEquals(1, arrow.readyPulses);
    }

    @Test public void originalFailureIsPropagatedWithoutRetry() throws Throwable {
        LauncherBackReadyFeedbackGuard.markReady(stub);
        IllegalStateException failure = new IllegalStateException("fixture original failed");
        try {
            registry.call(listener, () -> { throw failure; });
            fail("native failure must propagate to the protective hook boundary");
        } catch (IllegalStateException error) {
            assertSame(failure, error);
        }
        assertEquals(1, registry.proceeds);
        assertEquals(1, arrow.setterCalls);
    }

    @Test public void wrongCallbackInstanceFailsOpenWithoutRetry() throws Throwable {
        registry.call(new Object(), () -> { });
        assertEquals(1, registry.proceeds);
    }

    @Test public void missingOrIncompatibleListenerContractsDoNotInstall() {
        assertUnavailable(Arrow.class, null);
        assertUnavailable(Arrow.class, WrongReturnListener.class);
        assertUnavailable(Arrow.class, StaticListener.class);
        assertUnavailable(Arrow.class, MissingOwnerListener.class);
        assertUnavailable(Arrow.class, StaticOwnerListener.class);
        assertUnavailable(Arrow.class, AmbiguousOwnerListener.class);
        assertUnavailable(WrongSetterArrow.class, WrongSetterListener.class);
        assertUnavailable(View.class, MissingOwnerListener.class);
    }

    @Test public void declinedRegistrationDoesNotReportInstalled() {
        assertEquals(0, LauncherBackReadyFeedbackGuard.install(getClass().getClassLoader(),
                (name, loader) -> name.equals(LauncherBackAppearance.ARROW)
                        ? Arrow.class : ReadyListener.class,
                (method, hook) -> false));
    }

    private void assertUnavailable(Class<?> owner, Class<?> callback) {
        Registry unavailable = new Registry();
        assertEquals(0, install(owner, callback, unavailable));
        assertNull(unavailable.hook);
    }

    private int install(Class<?> owner, Class<?> callback, Registry target) {
        return LauncherBackReadyFeedbackGuard.install(getClass().getClassLoader(),
                (name, loader) -> {
                    if (name.equals(LauncherBackAppearance.ARROW)) return owner;
                    if (name.equals(LauncherBackAppearance.ARROW + "$1")) return callback;
                    throw new ClassNotFoundException(name);
                }, target);
    }

    private static Context context() { return RuntimeEnvironment.getApplication(); }

    public static final class Arrow extends View {
        boolean feedbackDone;
        int readyPulses;
        int setterCalls;
        boolean throwBeforeSet;
        boolean throwAfterSet;

        Arrow(Context context) { super(context); }

        public void setArrowFeedBackDone(boolean done) {
            setterCalls++;
            if (throwBeforeSet) throw new IllegalStateException("fixture setter failed");
            feedbackDone = done;
            if (throwAfterSet) throw new IllegalStateException("fixture setter already applied");
        }

        void restartAnimation() { feedbackDone = false; }
    }

    public static class Stub extends View {
        private final Arrow mGestureBackArrowView;
        Stub(Context context, Arrow owner) {
            super(context);
            mGestureBackArrowView = owner;
        }
    }

    public static final class DerivedStub extends Stub {
        DerivedStub(Context context, Arrow owner) { super(context, owner); }
    }

    public static final class StaticArrowStub extends View {
        private static Arrow mGestureBackArrowView;
        StaticArrowStub(Context context) { super(context); }
    }

    public static final class ReadyListener {
        private final Arrow enclosingArrow;
        private final boolean show;

        ReadyListener(Arrow owner, boolean show) {
            enclosingArrow = owner;
            this.show = show;
        }

        public void onAnimationSuccess(Animator ignored) {
            if (enclosingArrow.feedbackDone || !show) return;
            enclosingArrow.readyPulses++;
            enclosingArrow.feedbackDone = true;
        }
    }

    public static final class WrongReturnListener {
        private Arrow owner;
        public boolean onAnimationSuccess(Animator ignored) { return false; }
    }

    public static final class StaticListener {
        private Arrow owner;
        public static void onAnimationSuccess(Animator ignored) { }
    }

    public static final class MissingOwnerListener {
        public void onAnimationSuccess(Animator ignored) { }
    }

    public static final class StaticOwnerListener {
        private static Arrow owner;
        public void onAnimationSuccess(Animator ignored) { }
    }

    public static final class AmbiguousOwnerListener {
        private Arrow first;
        private Arrow second;
        public void onAnimationSuccess(Animator ignored) { }
    }

    public static final class WrongSetterArrow extends View {
        WrongSetterArrow(Context context) { super(context); }
        public boolean setArrowFeedBackDone(boolean ignored) { return false; }
    }

    public static final class WrongSetterListener {
        private WrongSetterArrow owner;
        public void onAnimationSuccess(Animator ignored) { }
    }

    private interface Original { void run() throws Throwable; }

    private static final class Registry implements LauncherBackReadyFeedbackGuard.Registrar {
        XposedInterface.Hooker hook;
        int proceeds;

        @Override public boolean install(Method method, XposedInterface.Hooker installed) {
            assertEquals("onAnimationSuccess", method.getName());
            hook = installed;
            return true;
        }

        Object call(Object callback, Original original) throws Throwable {
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getThisObject" -> callback;
                        case "getArgs" -> Collections.singletonList(null);
                        case "proceed" -> {
                            proceeds++;
                            original.run();
                            yield null;
                        }
                        default -> null;
                    });
            return hook.intercept(chain);
        }
    }
}
