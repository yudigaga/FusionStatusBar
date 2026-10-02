package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 35}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class VisibleSecondTickerTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private TextView clock;
    private FrameLayout parent;
    private VisibleSecondTicker ticker;
    private int ticks;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(true);
        parent = new FrameLayout(activity);
        // A bare Robolectric Activity does not receive WindowManager visibility dispatch.
        clock = new TextView(activity) {
            @Override public int getWindowVisibility() { return View.VISIBLE; }
        };
        parent.addView(clock);
        activity.setContentView(parent);
        controller.visible();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Clock must be attached in this fixture", clock.isAttachedToWindow());
        assertTrue("Clock must be visible in this fixture", clock.isShown());
        assertEquals("Window must be visible in this fixture", View.VISIBLE, clock.getWindowVisibility());
        assertTrue("Screen must be interactive in this fixture", clock.getContext().getSystemService(PowerManager.class).isInteractive());
        ticker = new VisibleSecondTicker(() -> ticks++, android.os.SystemClock::uptimeMillis);
    }

    @After public void cleanup() {
        if (ticker != null) ticker.close();
        controller.pause().stop().destroy();
    }

    @Test public void hiddenClockStopsAndVisibleClockResumesWithoutPolling() {
        ticker.update(true, clock);
        assertTrue(ticker.isScheduled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(1, ticks);
        parent.setVisibility(View.GONE);
        parent.getViewTreeObserver().dispatchOnGlobalLayout();
        assertFalse(ticker.isScheduled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(1, ticks);
        parent.setVisibility(View.VISIBLE);
        parent.getViewTreeObserver().dispatchOnGlobalLayout();
        assertTrue(ticker.isScheduled());
    }

    @Test public void screenOffCancelsAndScreenOnResumesTicks() {
        ticker.update(true, clock);
        shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(false);
        activity.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(ticker.isScheduled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(0, ticks);
        shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(true);
        activity.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ticker.isScheduled());
    }

    @Test public void detachAndDisableReleaseTimers() {
        ticker.update(true, clock);
        parent.removeView(clock);
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(ticker.isScheduled());
        parent.addView(clock);
        assertTrue(ticker.isScheduled());
        ticker.close();
        parent.getViewTreeObserver().dispatchOnGlobalLayout();
        assertFalse(ticker.isScheduled());
    }

    @Test public void repeatedBindingDoesNotDuplicateTicks() {
        ticker.update(true, clock);
        ticker.update(true, clock);
        ticker.update(true, clock, clock);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(2, ticks);
    }
}
