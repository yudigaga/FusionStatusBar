package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RobolectricTestRunner;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusClockControllerTest {
    @Test public void customSizeFollowsNativeUpdatesAndDisableRestoresLatestNativeSize() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView clock = new TextView(activity);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f);
        FusionConfig nativeConfig = FusionConfig.defaults();
        StatusClockController.onNativeUpdate(clock, nativeConfig);
        assertEquals(24f, clock.getTextSize(), 0.01f);

        FusionConfig custom = nativeConfig.withStatusClockSizeSp(20);
        StatusClockController.onConfigChanged(custom);
        float customPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20,
                activity.getResources().getDisplayMetrics());
        assertEquals(customPx, clock.getTextSize(), 0.01f);

        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f);
        StatusClockController.onNativeUpdate(clock, custom);
        assertEquals(customPx, clock.getTextSize(), 0.01f);

        StatusClockController.onConfigChanged(nativeConfig);
        assertEquals(30f, clock.getTextSize(), 0.01f);
        activity.finish();
    }

    @Test public void aHiddenAppClockCannotTakeTheTicksAwayFromTheVisibleHomeClock() {
        try (StatusBars bars = new StatusBars()) {
            FusionConfig settings = bars.nativeConfig.withCustomClock(true).withClockPattern("HH:mm:ss");
            StatusClockController.onNativeUpdate(bars.home, settings);
            StatusClockController.onNativeUpdate(bars.app, settings);
            String homeTime = bars.home.getText().toString();
            String hiddenAppTime = bars.app.getText().toString();

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));

            assertNotEquals("The visible desktop clock keeps ticking after the hidden app callback",
                    homeTime, bars.home.getText().toString());
            assertEquals("The hidden status bar has no periodic rendering",
                    hiddenAppTime, bars.app.getText().toString());
            bars.showApp(true);
            assertEquals("Showing the app bar immediately catches it up with the desktop clock",
                    bars.home.getText().toString(), bars.app.getText().toString());
        }
    }

    @Test public void visibleMinuteClockAdvancesWithoutAnOemCallback() {
        try (StatusBars bars = new StatusBars()) {
            FusionConfig settings = bars.nativeConfig.withCustomClock(true).withClockPattern("HH:mm");
            StatusClockController.onNativeUpdate(bars.home, settings);
            String firstMinute = bars.home.getText().toString();

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));

            assertNotEquals("A visible custom clock needs the next minute even without a native callback",
                    firstMinute, bars.home.getText().toString());
        }
    }

    @Test public void disablingTheCustomClockRestoresBothViewsOwnNativeTextAndSize() {
        try (StatusBars bars = new StatusBars()) {
            FusionConfig custom = bars.nativeConfig.withCustomClock(true).withStatusClockSizeSp(22);
            StatusClockController.onNativeUpdate(bars.home, custom);
            StatusClockController.onNativeUpdate(bars.app, custom);
            bars.app.setText("app rebound");
            bars.app.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32f);

            StatusClockController.onConfigChanged(bars.nativeConfig);

            assertEquals("home native", bars.home.getText().toString());
            assertEquals("app rebound", bars.app.getText().toString());
            assertEquals(24f, bars.home.getTextSize(), .01f);
            assertEquals(32f, bars.app.getTextSize(), .01f);
        }
    }

    private static final class StatusBars implements AutoCloseable {
        final ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible();
        final FusionConfig nativeConfig = FusionConfig.defaults().withCustomClock(false);
        final TextView home = clock("home native", 24f);
        final TextView app = clock("app native", 30f);
        final FrameLayout root = new FrameLayout(activity.get());

        StatusBars() {
            StatusClockController.setWallTimeForTest(() -> 1_700_000_000_000L + SystemClock.uptimeMillis());
            shadowOf(activity.get().getSystemService(PowerManager.class)).setIsInteractive(true);
            root.addView(home);
            root.addView(app);
            activity.get().setContentView(root);
            activity.visible();
            showApp(false);
            assertTrue(home.isAttachedToWindow() && home.isShown());
            assertTrue(app.isAttachedToWindow() && !app.isShown());
        }

        private TextView clock(String nativeText, float size) {
            TextView clock = new TextView(activity.get()) {
                @Override public int getWindowVisibility() { return View.VISIBLE; }
            };
            clock.setText(nativeText);
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            return clock;
        }

        void showApp(boolean show) {
            home.setVisibility(show ? View.GONE : View.VISIBLE);
            app.setVisibility(show ? View.VISIBLE : View.GONE);
            root.getViewTreeObserver().dispatchOnGlobalLayout();
        }

        @Override public void close() {
            StatusClockController.onConfigChanged(nativeConfig);
            StatusClockController.setWallTimeForTest(null);
            activity.pause().stop().destroy();
            shadowOf(Looper.getMainLooper()).idle();
        }
    }
}
