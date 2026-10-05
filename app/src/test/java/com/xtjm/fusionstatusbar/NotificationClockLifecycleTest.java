package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 35}, manifest = Config.NONE)
public class NotificationClockLifecycleTest {
    private ActivityController<Activity> controller;
    private Header header;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        header = new Header(controller.get());
        controller.get().setContentView(header);
    }

    @After public void cleanup() {
        NotificationCenterClockController.onHeader(header, FusionConfig.defaults());
        controller.pause().stop().destroy();
    }

    @Test public void repeatedCustomizationRestoresNativeParentsAndOrder() {
        var settings = FusionConfig.defaults().withNotificationClock(
                NotificationClockConfig.defaults().withEnabled(true).withPattern("HH:mm")
                        .withTimeCentered(true).withDateCentered(false));
        Object timeParams = header.mBigTime.getLayoutParams();
        Object dateParams = header.mDateView.getLayoutParams();
        for (int i = 0; i < 5; i++) {
            NotificationCenterClockController.onHeader(header, settings);
            assertNotSame(header.row, header.mBigTime.getParent());
            NotificationCenterClockController.onHeader(header, settings);
            assertEquals(1, header.row.getChildCount());
            NotificationCenterClockController.onHeader(header, FusionConfig.defaults());
            assertSame(header.row, header.mBigTime.getParent());
            assertSame(header.row, header.mDateView.getParent());
            assertSame(header.mBigTime, header.row.getChildAt(0));
            assertSame(header.mDateView, header.row.getChildAt(1));
            assertSame(timeParams, header.mBigTime.getLayoutParams());
            assertSame(dateParams, header.mDateView.getLayoutParams());
            assertEquals(2, header.row.getChildCount());
        }
    }

    @Test public void detachIsReflectedInRuntimeAvailability() {
        NotificationCenterClockController.onHeader(header, FusionConfig.defaults());
        assertTrue(NotificationCenterClockController.hasAttachedViews());
        controller.get().setContentView(new LinearLayout(controller.get()));
        assertFalse(NotificationCenterClockController.hasAttachedViews());
    }

    @Test public void landscapeKeepsCustomClockButUsesNativeSingleLineBounds() {
        var settings = FusionConfig.defaults().withNotificationClock(
                NotificationClockConfig.defaults().withEnabled(true)
                        .withPattern("HH:mm\nMM/dd").withSize(120).withTimeCentered(true));
        float nativeLandSize = header.mLandClock.getTextSize();

        Configuration landscape = new Configuration(controller.get().getResources().getConfiguration());
        landscape.orientation = Configuration.ORIENTATION_LANDSCAPE;
        controller.configurationChange(landscape);
        header.hostResources.getConfiguration().orientation = Configuration.ORIENTATION_LANDSCAPE;
        header.getResources().updateConfiguration(landscape, header.getResources().getDisplayMetrics());
        header.mBigTime.getResources().updateConfiguration(landscape,
                header.mBigTime.getResources().getDisplayMetrics());
        header.mLandClock.getResources().updateConfiguration(landscape,
                header.mLandClock.getResources().getDisplayMetrics());
        NotificationCenterClockController.onConfigChanged(settings);
        NotificationCenterClockController.onHeader(header, settings);

        String rendered = header.mLandClock.getText().toString();
        assertTrue("landscape retains configured time", rendered.contains(":"));
        assertFalse("horizontal OEM clock remains one line", rendered.contains("\n"));
        assertTrue("horizontal OEM clock keeps native compact size actual="
                        + header.mLandClock.getTextSize() + " native=" + nativeLandSize,
                header.mLandClock.getTextSize() <= nativeLandSize + 0.5f);
        assertTrue("horizontal OEM clock remains single line max=" + header.mLandClock.getMaxLines()
                        + " single=" + header.mLandClock.isSingleLine(),
                header.mLandClock.isSingleLine());
        assertSame("landscape does not reparent portrait time", header.row,
                header.mBigTime.getParent());
        assertSame("landscape does not reparent portrait date", header.row,
                header.mDateView.getParent());

        Configuration portrait = new Configuration(controller.get().getResources().getConfiguration());
        portrait.orientation = Configuration.ORIENTATION_PORTRAIT;
        controller.configurationChange(portrait);
        header.hostResources.getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        header.getResources().updateConfiguration(portrait, header.getResources().getDisplayMetrics());
        header.mBigTime.getResources().updateConfiguration(portrait,
                header.mBigTime.getResources().getDisplayMetrics());
        header.mLandClock.getResources().updateConfiguration(portrait,
                header.mLandClock.getResources().getDisplayMetrics());
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                header.getResources().getConfiguration().orientation);
        NotificationCenterClockController.onConfigChanged(settings);
        NotificationCenterClockController.onHeader(header, settings);
        try {
            var orientation = NotificationCenterClockController.class
                    .getDeclaredField("landscapeHeader");
            orientation.setAccessible(true);
            assertFalse("controller orientation", orientation.getBoolean(null));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        assertTrue("portrait restores the configured multiline layout actual="
                        + header.mBigTime.getText(),
                header.mBigTime.getText().toString().contains("\n"));
        assertNotSame(header.row, header.mBigTime.getParent());
    }

    public static final class Header extends LinearLayout {
        private android.content.res.Resources hostResources;
        public final TextView mBigTime;
        public final TextView mDateView;
        public final TextView mLandClock;
        final LinearLayout row;
        Header(Context context) {
            super(context);
            android.content.res.Resources resources = super.getResources();
            hostResources = new android.content.res.Resources(resources.getAssets(),
                    resources.getDisplayMetrics(), resources.getConfiguration()) {
                @Override public int getIdentifier(String name, String type, String pkg) {
                    if ("notification_header_clock_container".equals(name)) return 0x12345;
                    if ("horizontal_time".equals(name)) return 0x12346;
                    return super.getIdentifier(name, type, pkg);
                }
            };
            row = new LinearLayout(context);
            row.setId(0x12345);
            mBigTime = new TextView(context); mBigTime.setText("09:30");
            mDateView = new TextView(context); mDateView.setText("Thu, October 1");
            mLandClock = new TextView(context); mLandClock.setId(0x12346);
            mLandClock.setText("16:00 10月3日 周六");
            mLandClock.setSingleLine(true);
            row.addView(mBigTime, new LinearLayout.LayoutParams(-2, -2));
            row.addView(mDateView, new LinearLayout.LayoutParams(-2, -2));
            addView(row, new LinearLayout.LayoutParams(-1, -2));
            addView(mLandClock, new LinearLayout.LayoutParams(-2, -2));
        }
        @Override public android.content.res.Resources getResources() {
            return hostResources == null ? super.getResources() : hostResources;
        }
    }
}
