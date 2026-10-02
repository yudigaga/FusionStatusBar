package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
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

    public static final class Header extends LinearLayout {
        private android.content.res.Resources hostResources;
        public final TextView mBigTime;
        public final TextView mDateView;
        final LinearLayout row;
        Header(Context context) {
            super(context);
            android.content.res.Resources resources = super.getResources();
            hostResources = new android.content.res.Resources(resources.getAssets(),
                    resources.getDisplayMetrics(), resources.getConfiguration()) {
                @Override public int getIdentifier(String name, String type, String pkg) {
                    if ("notification_header_clock_container".equals(name)) return 0x12345;
                    return super.getIdentifier(name, type, pkg);
                }
            };
            row = new LinearLayout(context);
            row.setId(0x12345);
            mBigTime = new TextView(context); mBigTime.setText("09:30");
            mDateView = new TextView(context); mDateView.setText("Thu, October 1");
            row.addView(mBigTime, new LinearLayout.LayoutParams(-2, -2));
            row.addView(mDateView, new LinearLayout.LayoutParams(-2, -2));
            addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        @Override public android.content.res.Resources getResources() {
            return hostResources == null ? super.getResources() : hostResources;
        }
    }
}
