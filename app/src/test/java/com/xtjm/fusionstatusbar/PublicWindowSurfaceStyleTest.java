package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPhoneWindow;
import org.robolectric.shadows.ShadowWindowManagerImpl;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {PublicWindowSurfaceStyleTest.BlurWindowManager.class,
        PublicWindowSurfaceStyleTest.BlurPhoneWindow.class})
public class PublicWindowSurfaceStyleTest {
    @Before public void resetBackend() {
        BlurWindowManager.enabled = false;
        BlurWindowManager.failRead = false;
        BlurWindowManager.failRemove = false;
        BlurWindowManager.callbacks.clear();
        BlurWindowManager.removed.clear();
    }

    @Test public void autoHighlightSuspendsAndResumesFromRawWithoutCompounding() {
        Window window = window();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        assertBehind(window, 160, true);
        assertEquals(160, SystemSurfaceStyle.windowRadius(window, 160));
        Consumer<Boolean> callback = BlurWindowManager.callbacks.get(0);
        callback.accept(true);
        assertBehind(window, 40, true);
        assertBackground(window, 40);
        assertEquals(40, SystemSurfaceStyle.windowRadius(window, 160));
        callback.accept(false);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
        callback.accept(true);
        assertBehind(window, 40, true);
        assertBackground(window, 40);
        SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        assertBehind(window, 80, true);
        assertBackground(window, 80);
        assertEquals(1, BlurWindowManager.callbacks.size());
        SystemSurfaceStyle.applyWindow(window, automatic(50), false);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
    }

    @Test public void latestHostRequestWhileSuspendedIsRestoredOnDisableAndCallerParamsStayRaw() {
        Window window = window();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        BlurWindowManager.callbacks.get(0).accept(true);
        assertBehind(window, 40, true);
        BlurWindowManager.callbacks.get(0).accept(false);
        WindowManager.LayoutParams raw = raw(window, 200, false);
        WindowManager.LayoutParams transformed = SystemSurfaceStyle.windowAttributes(window, raw);
        assertNotSame(raw, transformed);
        window.setAttributes(transformed);
        assertEquals(200, raw.getBlurBehindRadius());
        assertEquals(0, raw.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        assertBehind(window, 200, false);
        assertEquals(200, SystemSurfaceStyle.windowRadius(window, 200));
        BlurWindowManager.callbacks.get(0).accept(true);
        assertBehind(window, 50, false);
        assertBackground(window, 50);
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 200, false);
        assertBackground(window, 200);
        assertEquals(200, SystemSurfaceStyle.windowRadius(window, 200));
    }

    @Test public void activeHostAttributesAreScaledOnceAndLatestFlagIsPreserved() {
        BlurWindowManager.enabled = true;
        Window window = window();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        WindowManager.LayoutParams raw = raw(window, 200, false);
        raw.dimAmount = .37f;
        raw.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        WindowManager.LayoutParams transformed = SystemSurfaceStyle.windowAttributes(window, raw);
        window.setAttributes(transformed);
        assertEquals(200, raw.getBlurBehindRadius());
        assertEquals(50, transformed.getBlurBehindRadius());
        assertEquals(.37f, transformed.dimAmount, 0f);
        assertEquals(raw.flags, transformed.flags);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        assertBehind(window, 50, false);
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 200, false);
    }

    @Test public void closeAndReenableIgnoreQueuedOldCallbackEvenWhenRemovalFails() {
        Window window = window();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        Consumer<Boolean> old = BlurWindowManager.callbacks.get(0);
        old.accept(true);
        BlurWindowManager.failRemove = true;
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        Consumer<Boolean> current = BlurWindowManager.callbacks.get(1);
        current.accept(true);
        assertBehind(window, 80, true);
        old.accept(false);
        assertBehind(window, 80, true);
        SystemSurfaceStyle.applyWindow(window, automatic(50), false);
    }

    @Test public void unavailableInitialReadKeepsRawAndTwoWindowsHaveIndependentState() {
        BlurWindowManager.failRead = true;
        Window first = window();
        hostBehind(first, 160, true);
        SystemSurfaceStyle.applyWindow(first, automatic(25), true);
        assertBehind(first, 160, true);
        BlurWindowManager.failRead = false;
        Window second = window();
        hostBehind(second, 240, true);
        SystemSurfaceStyle.applyWindow(second, automatic(50), true);
        BlurWindowManager.callbacks.get(0).accept(true);
        assertBehind(first, 160, true);
        assertBehind(second, 120, true);
        SystemSurfaceStyle.applyWindow(first, automatic(25), false);
        assertBehind(second, 120, true);
        SystemSurfaceStyle.applyWindow(second, automatic(50), false);
    }

    @Test public void multipleRootsShareWindowRegistrationAndPublicChangesDoNotReplayOemBlur() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        Window window = activity.getWindow();
        NativeView first = new NativeView(activity), second = new NativeView(activity);
        SystemSurfaceStyle.applyBackdrop(first, window, automatic(25), true);
        SystemSurfaceStyle.applyBackdrop(second, window, automatic(25), true);
        assertEquals(1, BlurWindowManager.callbacks.size());
        assertEquals(30, first.radius);
        assertEquals(30, second.radius);
        int writes = first.writes + second.writes;
        BlurWindowManager.callbacks.get(0).accept(true);
        BlurWindowManager.callbacks.get(0).accept(false);
        assertEquals(writes, first.writes + second.writes);
        assertEquals(30, first.radius);
        SystemSurfaceStyle.applyBackdrop(first, window, automatic(25), false);
        SystemSurfaceStyle.applyBackdrop(second, window, automatic(25), false);
        assertEquals(120, first.radius);
        assertEquals(120, second.radius);
    }

    @Test public void zeroClearAndManualSelectionsRetainTheirExistingWindowScaling() {
        ControlCenterConfig[] configs = { automatic(0), automatic(25).withSurface(ControlCenterSurface.CLEAR),
                automatic(25).withSurface(ControlCenterSurface.SOFT_GLASS),
                automatic(25).withSurface(ControlCenterSurface.DEFAULT) };
        int[] expected = {0, 40, 40, 40};
        for (int index = 0; index < configs.length; index++) {
            Window window = window();
            hostBehind(window, 160, true);
            SystemSurfaceStyle.applyWindow(window, configs[index], true);
            assertBehind(window, expected[index], expected[index] > 0);
            assertEquals(expected[index], SystemSurfaceStyle.windowRadius(window, 160));
            SystemSurfaceStyle.applyWindow(window, configs[index], false);
            assertBehind(window, 160, true);
        }
    }

    @Test public void internalWritesBypassBothPublicHooksAndCannotReplaceRawRequests() {
        HookObservingActivity activity = Robolectric.buildActivity(HookObservingActivity.class)
                .setup().visible().get();
        Window window = activity.getWindow();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        SystemSurfaceStyle.windowRadius(window, 160);
        activity.checkInternalHooks = true;
        BlurWindowManager.callbacks.get(0).accept(true);
        assertBehind(window, 40, true);
        SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        assertBehind(window, 80, true);
        SystemSurfaceStyle.applyWindow(window, automatic(50), false);
        assertBehind(window, 160, true);
        assertEquals(3, activity.checkedWrites);
    }

    @Test public void reentrantWindowCallbacksDuringInitialObservationDoNotDuplicateRegistration() {
        HookObservingActivity activity = Robolectric.buildActivity(HookObservingActivity.class)
                .setup().visible().get();
        activity.reenter = true;
        SystemSurfaceStyle.applyWindow(activity.getWindow(), automatic(25), true);
        assertEquals(1, BlurWindowManager.callbacks.size());
        SystemSurfaceStyle.applyWindow(activity.getWindow(), automatic(25), false);
    }

    @Test public void disablingDuringInitialObservationReleasesTheJustCreatedObserver() {
        HookObservingActivity activity = Robolectric.buildActivity(HookObservingActivity.class)
                .setup().visible().get();
        hostBehind(activity.getWindow(), 160, true);
        activity.disableOnWrite = true;
        SystemSurfaceStyle.applyWindow(activity.getWindow(), automatic(25), true);
        assertBehind(activity.getWindow(), 160, true);
        assertEquals(1, BlurWindowManager.callbacks.size());
        assertEquals(1, BlurWindowManager.removed.size());
        BlurWindowManager.callbacks.get(0).accept(true);
        assertBehind(activity.getWindow(), 160, true);
    }

    @Test public void delayedHostWindowCallbackFailureReturnsToRawAndCanRecover() {
        HookObservingActivity activity = Robolectric.buildActivity(HookObservingActivity.class)
                .setup().visible().get();
        Window window = activity.getWindow();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        window.setBackgroundBlurRadius(160);
        activity.onNextAttributes = () -> { throw new IllegalStateException("host callback"); };
        Consumer<Boolean> callback = BlurWindowManager.callbacks.get(0);
        callback.accept(true);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
        callback.accept(true);
        assertBehind(window, 40, true);
        assertBackground(window, 40);
        assertEquals(1, BlurWindowManager.callbacks.size());
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
    }

    @Test public void closeDuringAttributesWritePreventsTheOldBackgroundContinuation() {
        HookObservingActivity activity = reentrantActivity();
        Window window = activity.getWindow();
        activity.onNextAttributes = () -> {
            SystemSurfaceStyle.applyWindow(window, automatic(25), false);
            window.setBackgroundBlurRadius(777);
        };
        SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        assertBehind(window, 160, true);
        assertBackground(window, 777);
    }

    @Test public void closeAndReopenDuringAttributesWriteKeepTheNewWindowsBackgroundRequest() {
        HookObservingActivity activity = reentrantActivity();
        Window window = activity.getWindow();
        activity.onNextAttributes = () -> {
            SystemSurfaceStyle.applyWindow(window, automatic(25), false);
            SystemSurfaceStyle.applyWindow(window, automatic(75), true);
            window.setBackgroundBlurRadius(777);
        };
        SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        assertBehind(window, 120, true);
        assertBackground(window, 777);
        window.setBackgroundBlurRadius(777);
        assertBackground(window, 583);
        SystemSurfaceStyle.applyWindow(window, automatic(75), false);
        assertBehind(window, 160, true);
        assertBackground(window, 777);
    }

    @Test public void reopenDuringRestoreCannotBeOverwrittenByTheOldRestoration() {
        HookObservingActivity activity = reentrantActivity();
        Window window = activity.getWindow();
        activity.onNextAttributes = () -> {
            SystemSurfaceStyle.applyWindow(window, automatic(50), true);
            window.setBackgroundBlurRadius(777);
        };
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 80, true);
        assertBackground(window, 777);
        window.setBackgroundBlurRadius(777);
        assertBackground(window, 389);
        SystemSurfaceStyle.applyWindow(window, automatic(50), false);
        assertBehind(window, 160, true);
        assertBackground(window, 777);
    }

    @Test public void reopeningDuringRestoreTransfersCapturedBackgroundRawWithoutANewRequest() {
        HookObservingActivity activity = reentrantActivity();
        Window window = activity.getWindow();
        activity.onNextAttributes = () -> SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 80, true);
        assertBackground(window, 80);
        Consumer<Boolean> current = BlurWindowManager.callbacks.get(1);
        current.accept(false);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
        current.accept(true);
        assertBackground(window, 80);
        SystemSurfaceStyle.applyWindow(window, automatic(50), false);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
    }

    @Test public void unknownReopeningDuringRestoreUsesTheTransferredCapturedBackgroundRaw() {
        HookObservingActivity activity = reentrantActivity();
        Window window = activity.getWindow();
        activity.onNextAttributes = () -> {
            BlurWindowManager.failRead = true;
            SystemSurfaceStyle.applyWindow(window, automatic(50), true);
        };
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
        SystemSurfaceStyle.applyWindow(window, automatic(50), false);
        assertBackground(window, 160);
    }

    @Test public void closingInsideDelayedAvailabilityCallbackRestoresRawWithoutANewRequest() {
        HookObservingActivity activity = Robolectric.buildActivity(HookObservingActivity.class)
                .setup().visible().get();
        Window window = activity.getWindow();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        window.setBackgroundBlurRadius(160);
        activity.onNextAttributes = () -> SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        Consumer<Boolean> old = BlurWindowManager.callbacks.get(0);
        old.accept(true);
        assertBehind(window, 160, true);
        assertBackground(window, 160);
        old.accept(false);
        assertBackground(window, 160);
    }

    @Test public void editingTheCurrentAttributesWithoutChangingBlurDoesNotCompound() {
        assertNonBlurAttributesRoundTrip(false);
    }

    @Test public void editingACopyOfCurrentAttributesWithoutChangingBlurDoesNotCompound() {
        assertNonBlurAttributesRoundTrip(true);
    }

    @Test public void clearFlagsPreservesTheLatestRawBlurFlagAcrossAvailabilityAndDisable() {
        BlurWindowManager.enabled = true;
        Window window = window();
        hostBehind(window, 160, true);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        SystemSurfaceStyle.windowFlags(window, 0, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        assertBehind(window, 40, false);
        Consumer<Boolean> callback = BlurWindowManager.callbacks.get(0);
        callback.accept(false);
        assertBehind(window, 160, false);
        callback.accept(true);
        assertBehind(window, 40, false);
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 160, false);
        assertTrue((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
    }

    @Test public void addFlagsPreservesTheLatestRawBlurFlagAndIgnoresOtherMasks() {
        BlurWindowManager.enabled = true;
        Window window = window();
        hostBehind(window, 160, false);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        SystemSurfaceStyle.windowFlags(window, WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        SystemSurfaceStyle.windowFlags(window, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Consumer<Boolean> callback = BlurWindowManager.callbacks.get(0);
        callback.accept(false);
        assertBehind(window, 160, true);
        callback.accept(true);
        assertBehind(window, 40, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 160, true);
        assertTrue((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
    }

    @Test public void publicCrossWindowBlurDoesNotChangeTheControlCenterCanvasMaterial() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        Window window = activity.getWindow();
        ControlCenterConfig config = automatic(25).withEnabled(true).withBlur(25, 30, 30);
        QSCardItemView card = new QSCardItemView(activity);
        GradientDrawable nativeBackground = new GradientDrawable();
        nativeBackground.setColor(0xff353840);
        nativeBackground.setAlpha(143);
        card.setBackground(nativeBackground);
        TextView label = new TextView(activity);
        ColorStateList nativeColors = new ColorStateList(new int[][] {{android.R.attr.state_pressed}, {}},
                new int[] {Color.BLUE, 0xcc000000});
        label.setTextColor(nativeColors);
        card.addView(label);
        hostBehind(window, 160, true);
        int body = ControlCenterSurface.HIGHLIGHT_GLASS.resolve(card.getContext()).color;
        ColorStateList canvasColors = nativeColors.withLStar(100f);
        int[] pressed = {android.R.attr.state_pressed};
        try {
            ControlCenterMaterials.registerRoot(card, config);
            SystemSurfaceStyle.applyWindow(window, config, true);
            Consumer<Boolean> callback = BlurWindowManager.callbacks.get(0);
            for (boolean publicEnabled : new boolean[] {false, true, false}) {
                callback.accept(publicEnabled);
                ControlCenterMaterials.refresh(config);
                assertBehind(window, publicEnabled ? 40 : 160, true);
                assertEquals(body, nativeBackground.getColor().getDefaultColor());
                assertEquals(43, nativeBackground.getAlpha());
                assertEquals(canvasColors.getDefaultColor(), label.getTextColors().getDefaultColor());
                assertEquals(canvasColors.getColorForState(pressed, 0), label.getTextColors().getColorForState(pressed, 0));
            }
        } finally {
            SystemSurfaceStyle.applyWindow(window, config, false);
            ControlCenterMaterials.refresh(config.withEnabled(false));
            ControlCenterMaterials.clearForTest();
        }
        assertEquals(0xff353840, nativeBackground.getColor().getDefaultColor());
        assertEquals(143, nativeBackground.getAlpha());
        assertSame(nativeColors, label.getTextColors());
    }

    private static HookObservingActivity reentrantActivity() {
        BlurWindowManager.enabled = true;
        HookObservingActivity activity = Robolectric.buildActivity(HookObservingActivity.class)
                .setup().visible().get();
        hostBehind(activity.getWindow(), 160, true);
        SystemSurfaceStyle.applyWindow(activity.getWindow(), automatic(25), true);
        activity.getWindow().setBackgroundBlurRadius(160);
        assertBehind(activity.getWindow(), 40, true);
        assertBackground(activity.getWindow(), 40);
        return activity;
    }

    private static void assertNonBlurAttributesRoundTrip(boolean copy) {
        BlurWindowManager.enabled = true;
        Window window = window();
        hostBehind(window, 160, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), true);
        WindowManager.LayoutParams requested = window.getAttributes();
        if (copy) {
            WindowManager.LayoutParams separate = new WindowManager.LayoutParams();
            separate.copyFrom(requested);
            requested = separate;
        }
        requested.dimAmount = .61f;
        requested.gravity = Gravity.TOP;
        window.setAttributes(SystemSurfaceStyle.windowAttributes(window, requested));
        assertBehind(window, 40, true);
        assertEquals(.61f, window.getAttributes().dimAmount, 0f);
        assertEquals(Gravity.TOP, window.getAttributes().gravity);
        Consumer<Boolean> callback = BlurWindowManager.callbacks.get(0);
        callback.accept(false);
        assertBehind(window, 160, true);
        callback.accept(true);
        assertBehind(window, 40, true);
        SystemSurfaceStyle.applyWindow(window, automatic(25), false);
        assertBehind(window, 160, true);
    }

    private static ControlCenterConfig automatic(int amount) {
        return ControlCenterConfig.defaults().withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
                .withBlur(amount, 100, 100);
    }
    private static Window window() {
        return Robolectric.buildActivity(Activity.class).setup().visible().get().getWindow();
    }
    private static WindowManager.LayoutParams raw(Window window, int radius, boolean blur) {
        WindowManager.LayoutParams result = new WindowManager.LayoutParams();
        result.copyFrom(window.getAttributes());
        result.setBlurBehindRadius(radius);
        if (blur) result.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        else result.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        return result;
    }
    private static void hostBehind(Window window, int radius, boolean blur) {
        window.setAttributes(raw(window, radius, blur));
    }
    private static void assertBehind(Window window, int radius, boolean blur) {
        assertEquals(radius, window.getAttributes().getBlurBehindRadius());
        assertEquals(blur ? WindowManager.LayoutParams.FLAG_BLUR_BEHIND : 0,
                window.getAttributes().flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    }

    private static void assertBackground(Window window, int radius) {
        assertEquals(radius, ((BlurPhoneWindow) Shadow.extract(window)).backgroundRequest);
    }

    @Implements(className = "com.android.internal.policy.PhoneWindow", isInAndroidSdk = false)
    public static class BlurPhoneWindow extends ShadowPhoneWindow {
        @RealObject private Window window;
        int backgroundRequest;
        @Implementation protected void setBackgroundBlurRadius(int radius) {
            // Exercise the hook boundary while observing requests independently of GPU support.
            backgroundRequest = SystemSurfaceStyle.windowRadius(window, radius);
        }
    }

    public static class HookObservingActivity extends Activity {
        boolean checkInternalHooks, reenter, disableOnWrite;
        Runnable onNextAttributes;
        int checkedWrites;
        @Override public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
            super.onWindowAttributesChanged(params);
            if (checkInternalHooks) {
                assertSame(params, SystemSurfaceStyle.windowAttributes(getWindow(), params));
                assertEquals(160, SystemSurfaceStyle.windowRadius(getWindow(), 160));
                SystemSurfaceStyle.windowFlags(getWindow(), 0, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                checkedWrites++;
            }
            if (reenter) {
                reenter = false;
                SystemSurfaceStyle.applyWindow(getWindow(), automatic(25), true);
            }
            if (disableOnWrite) {
                disableOnWrite = false;
                SystemSurfaceStyle.applyWindow(getWindow(), automatic(25), false);
            }
            Runnable action = onNextAttributes;
            onNextAttributes = null;
            if (action != null) action.run();
        }
    }

    @Implements(className = "android.view.WindowManagerImpl", isInAndroidSdk = false)
    public static class BlurWindowManager extends ShadowWindowManagerImpl {
        static boolean enabled, failRead, failRemove;
        static final List<Consumer<Boolean>> callbacks = new ArrayList<>(), removed = new ArrayList<>();
        @Implementation protected boolean isCrossWindowBlurEnabled() {
            if (failRead) throw new IllegalStateException("read");
            return enabled;
        }
        @Implementation protected void addCrossWindowBlurEnabledListener(Executor executor, Consumer<Boolean> callback) {
            callbacks.add(callback);
        }
        // DecorView's own listener belongs to Android; only the module's explicit-executor API is driven here.
        @Implementation protected void addCrossWindowBlurEnabledListener(Consumer<Boolean> callback) { }
        @Implementation protected void removeCrossWindowBlurEnabledListener(Consumer<Boolean> callback) {
            removed.add(callback);
            if (failRemove) throw new IllegalStateException("remove");
        }
    }

    public static final class NativeView extends View {
        int radius = 120, writes;
        NativeView(Context context) { super(context); }
        public int getMiBackgroundBlurRadius() { return radius; }
        public int getMiBackgroundBlurMode() { return 1; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; writes++; }
        public void setMiBackgroundBlurMode(int value) { }
    }

    public static final class QSCardItemView extends FrameLayout {
        QSCardItemView(Context context) { super(context); }
    }
}
