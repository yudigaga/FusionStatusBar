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

    @Test public void nativeTextRefreshDoesNotAccumulateOffsetAtUnchangedExpansion() {
        var settings = expandedClockSettings();
        NotificationCenterClockController.onHeader(header, settings);
        float density = header.mBigTime.getResources().getDisplayMetrics().density;
        // The OEM can update its native translation as the shade expands.
        header.mBigTime.setTranslationY(17f * density);
        NotificationCenterClockController.onExpansionChanged(0.5f);
        float expected = 29f * density;
        assertEquals(expected, header.mBigTime.getTranslationY(), 0.001f);

        for (int refresh = 0; refresh < 3; refresh++) {
            header.mBigTime.setText("09:3" + refresh);
            NotificationCenterClockController.onNativeUpdate(header.mBigTime, settings);
            // No OEM translation write occurred between these equal progress callbacks.
            NotificationCenterClockController.onExpansionChanged(0.5f);
            assertEquals("native text refresh must not move an unchanged shade, refresh="
                    + refresh, expected, header.mBigTime.getTranslationY(), 0.001f);
        }
    }

    @Test public void weatherRefreshDoesNotAccumulateOffsetAtUnchangedExpansion() {
        NotificationCenterClockController.onHeader(header, expandedClockSettings());
        float density = header.mBigTime.getResources().getDisplayMetrics().density;
        header.mBigTime.setTranslationY(17f * density);
        NotificationCenterClockController.onExpansionChanged(0.5f);
        float expected = 29f * density;
        assertEquals(expected, header.mBigTime.getTranslationY(), 0.001f);

        for (int refresh = 0; refresh < 3; refresh++) {
            NotificationCenterClockController.onWeatherChanged();
            NotificationCenterClockController.onExpansionChanged(0.5f);
            assertEquals("weather refresh must not move an unchanged shade, refresh="
                    + refresh, expected, header.mBigTime.getTranslationY(), 0.001f);
        }
    }

    @Test public void fullyExpandedTextRefreshPreservesLatestNativeTranslation() {
        var settings = expandedClockSettings();
        NotificationCenterClockController.onHeader(header, settings);
        float density = header.mBigTime.getResources().getDisplayMetrics().density;
        header.mBigTime.setTranslationY(17f * density);
        NotificationCenterClockController.onExpansionChanged(1f);
        float expected = 41f * density;
        assertEquals(expected, header.mBigTime.getTranslationY(), 0.001f);

        for (int refresh = 0; refresh < 3; refresh++) {
            header.mBigTime.setText("09:3" + refresh);
            NotificationCenterClockController.onNativeUpdate(header.mBigTime, settings);
            assertEquals("text refresh must preserve the expanded OEM translation, refresh="
                    + refresh, expected, header.mBigTime.getTranslationY(), 0.001f);
            NotificationCenterClockController.onExpansionChanged(1f);
            assertEquals(expected, header.mBigTime.getTranslationY(), 0.001f);
        }
    }

    private static FusionConfig expandedClockSettings() {
        return FusionConfig.defaults().withNotificationClock(
                NotificationClockConfig.defaults().withEnabled(true).withPattern("HH:mm")
                        .withOffsetY(24));
    }

    @Test public void expandedTextRefreshDoesNotRestoreTranslationCapturedDuringOpening() {
        var settings = FusionConfig.defaults().withNotificationClock(
                NotificationClockConfig.defaults().withEnabled(true).withPattern("HH:mm:ss"));
        float density = header.mBigTime.getResources().getDisplayMetrics().density;
        // Header binding can happen while the OEM is still moving the clock.
        header.mBigTime.setTranslationY(17f * density);
        NotificationCenterClockController.onHeader(header, settings);
        for (int cycle = 0; cycle < 3; cycle++) {
            for (float progress : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
                // Match the OEM callback: always write its animation Y before our hook.
                float nativeY = 17f * density * (1f - progress);
                header.mBigTime.setTranslationY(nativeY);
                NotificationCenterClockController.onExpansionChanged(progress);
                NotificationCenterClockController.onNativeUpdate(header.mBigTime, settings);
                NotificationCenterClockController.onWeatherChanged();
                assertEquals("text refresh must preserve OEM animation Y at progress="
                        + progress + " cycle=" + cycle,
                        nativeY, header.mBigTime.getTranslationY(), 0.001f);
            }
        }
    }

    @Test public void oemAnimationReplayKeepsConfiguredOffsetsStableAcrossOpenAndClose() {
        float density = header.mBigTime.getResources().getDisplayMetrics().density;
        for (int offset : new int[] {24, -24, 0}) {
            var settings = expandedClockSettings().withNotificationClock(
                    expandedClockSettings().notificationClock.withOffsetY(offset));
            NotificationCenterClockController.onHeader(header, settings);
            for (int cycle = 0; cycle < 3; cycle++) {
                for (float progress : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f,
                        1f, 0.75f, 0.5f, 0.25f, 0f}) {
                    float nativeY = 17f * density * (1f - progress);
                    header.mBigTime.setTranslationY(nativeY);
                    NotificationCenterClockController.onExpansionChanged(progress);
                    float expected = nativeY + offset * density * progress;
                    NotificationCenterClockController.onNativeUpdate(header.mBigTime, settings);
                    NotificationCenterClockController.onWeatherChanged();
                    assertEquals("OEM replay offset=" + offset + " progress=" + progress
                            + " cycle=" + cycle,
                            expected, header.mBigTime.getTranslationY(), 0.001f);
                }
            }
        }
    }

    @Test public void clearingExpandedOffsetRetainsCurrentNativeTranslation() {
        var settings = expandedClockSettings();
        NotificationCenterClockController.onHeader(header, settings);
        float density = header.mBigTime.getResources().getDisplayMetrics().density;
        header.mBigTime.setTranslationY(17f * density);
        NotificationCenterClockController.onExpansionChanged(1f);
        NotificationCenterClockController.onConfigChanged(settings.withNotificationClock(
                settings.notificationClock.withOffsetY(0)));
        assertEquals(17f * density, header.mBigTime.getTranslationY(), 0.001f);
        NotificationCenterClockController.onWeatherChanged();
        assertEquals(17f * density, header.mBigTime.getTranslationY(), 0.001f);
    }

    @Test public void nativeSmallClockPhaseIsHiddenWithoutChangingHostAlpha() {
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, expandedClockSettings());
        for (float progress : new float[] {0f, 0.1f, 0.25f, 0.5f}) {
            NotificationCenterClockController.onExpansionChanged(progress);
            assertEquals("small OEM clock must stay hidden at progress=" + progress,
                    0f, header.mBigTime.getTransitionAlpha(), 0.001f);
            assertEquals("module must preserve the host alpha at progress=" + progress,
                    0.63f, header.mBigTime.getAlpha(), 0.001f);
        }
    }

    @Test public void notificationClockFadesAfterSmallPhaseAndClosesSymmetrically() {
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, expandedClockSettings());
        float[] progressSteps = {0.5f, 0.625f, 0.75f, 0.875f, 1f,
                0.875f, 0.75f, 0.625f, 0.5f, 0f};
        // Native transition alpha (0.8) times smoothstep across the second half.
        float[] expectedAlpha = {0f, 0.125f, 0.4f, 0.675f, 0.8f,
                0.675f, 0.4f, 0.125f, 0f, 0f};
        for (int step = 0; step < progressSteps.length; step++) {
            NotificationCenterClockController.onExpansionChanged(progressSteps[step]);
            assertEquals("clock fade at step=" + step + " progress=" + progressSteps[step],
                    expectedAlpha[step], header.mBigTime.getTransitionAlpha(), 0.001f);
            assertEquals(0.63f, header.mBigTime.getAlpha(), 0.001f);
        }
    }

    @Test public void textAndWeatherRefreshDoNotRevealHiddenSmallClock() {
        var settings = expandedClockSettings();
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, settings);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals(0f, header.mBigTime.getTransitionAlpha(), 0.001f);

        for (int refresh = 0; refresh < 3; refresh++) {
            header.mBigTime.setText("09:3" + refresh);
            NotificationCenterClockController.onNativeUpdate(header.mBigTime, settings);
            assertEquals("native text refresh must not reveal the small clock",
                    0f, header.mBigTime.getTransitionAlpha(), 0.001f);
            NotificationCenterClockController.onWeatherChanged();
            assertEquals("weather refresh must not reveal the small clock",
                    0f, header.mBigTime.getTransitionAlpha(), 0.001f);
            NotificationCenterClockController.onHeader(header, settings);
            assertEquals("rebinding the same header must retain its last expansion visibility",
                    0f, header.mBigTime.getTransitionAlpha(), 0.001f);
            assertEquals(0.63f, header.mBigTime.getAlpha(), 0.001f);
        }
        NotificationCenterClockController.onExpansionChanged(1f);
        assertEquals("refreshes must not capture our hidden alpha as the native alpha",
                0.8f, header.mBigTime.getTransitionAlpha(), 0.001f);
    }

    @Test public void disablingCustomClockRestoresNativeTransitionAlpha() {
        var settings = expandedClockSettings();
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, settings);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals(0f, header.mBigTime.getTransitionAlpha(), 0.001f);

        NotificationCenterClockController.onConfigChanged(FusionConfig.defaults());
        assertEquals("disabling customization restores native transition alpha",
                0.8f, header.mBigTime.getTransitionAlpha(), 0.001f);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals("disabled clocks retain native visibility at small-clock progress",
                0.8f, header.mBigTime.getTransitionAlpha(), 0.001f);
        assertEquals(0.63f, header.mBigTime.getAlpha(), 0.001f);

        NotificationCenterClockController.onConfigChanged(settings);
        assertEquals(0f, header.mBigTime.getTransitionAlpha(), 0.001f);
        NotificationCenterClockController.onHeader(header, FusionConfig.defaults());
        assertEquals("binding disabled settings also restores native transition alpha",
                0.8f, header.mBigTime.getTransitionAlpha(), 0.001f);
    }

    @Test public void landscapeRestoresNativeTransitionAlphaAndPortraitResumesFade() {
        var settings = expandedClockSettings();
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, settings);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals(0f, header.mBigTime.getTransitionAlpha(), 0.001f);

        setOrientation(Configuration.ORIENTATION_LANDSCAPE);
        NotificationCenterClockController.onConfigChanged(settings);
        NotificationCenterClockController.onHeader(header, settings);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals("landscape restores native portrait-clock visibility",
                0.8f, header.mBigTime.getTransitionAlpha(), 0.001f);
        assertEquals(0.63f, header.mBigTime.getAlpha(), 0.001f);

        setOrientation(Configuration.ORIENTATION_PORTRAIT);
        NotificationCenterClockController.onConfigChanged(settings);
        NotificationCenterClockController.onHeader(header, settings);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals("portrait resumes its small-clock visibility gate",
                0f, header.mBigTime.getTransitionAlpha(), 0.001f);
        NotificationCenterClockController.onExpansionChanged(1f);
        assertEquals(0.8f, header.mBigTime.getTransitionAlpha(), 0.001f);
    }

    @Test public void replacingHeaderRestoresPreviousClockTransitionAlpha() {
        var settings = expandedClockSettings();
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, settings);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals(0f, header.mBigTime.getTransitionAlpha(), 0.001f);
        Header previousHeader = header;

        header = new Header(controller.get());
        header.mBigTime.setAlpha(0.45f);
        header.mBigTime.setTransitionAlpha(0.6f);
        controller.get().setContentView(header);
        NotificationCenterClockController.onHeader(header, settings);
        assertEquals("replaced clock must not remain hidden by the previous header gate",
                0.8f, previousHeader.mBigTime.getTransitionAlpha(), 0.001f);
        assertEquals(0.63f, previousHeader.mBigTime.getAlpha(), 0.001f);
        NotificationCenterClockController.onExpansionChanged(0.25f);
        assertEquals(0f, header.mBigTime.getTransitionAlpha(), 0.001f);
        NotificationCenterClockController.onExpansionChanged(1f);
        assertEquals("replacement has its own native transition alpha",
                0.6f, header.mBigTime.getTransitionAlpha(), 0.001f);
        assertEquals(0.45f, header.mBigTime.getAlpha(), 0.001f);
    }

    @Test public void invalidExpansionKeepsLastValidClockTransitionAlpha() {
        header.mBigTime.setAlpha(0.63f);
        header.mBigTime.setTransitionAlpha(0.8f);
        NotificationCenterClockController.onHeader(header, expandedClockSettings());
        float[] validProgress = {0.25f, 0.75f};
        float[] expectedAlpha = {0f, 0.4f};
        for (int step = 0; step < validProgress.length; step++) {
            NotificationCenterClockController.onExpansionChanged(validProgress[step]);
            assertEquals(expectedAlpha[step], header.mBigTime.getTransitionAlpha(), 0.001f);
            for (float invalid : new float[] {-0.1f, 1.1f, Float.NaN,
                    Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
                NotificationCenterClockController.onExpansionChanged(invalid);
                assertEquals("invalid progress must preserve last valid fade: " + invalid,
                        expectedAlpha[step], header.mBigTime.getTransitionAlpha(), 0.001f);
                NotificationCenterClockController.onWeatherChanged();
                assertEquals("refresh after invalid progress must preserve last valid fade",
                        expectedAlpha[step], header.mBigTime.getTransitionAlpha(), 0.001f);
                assertEquals(0.63f, header.mBigTime.getAlpha(), 0.001f);
            }
        }
    }

    private void setOrientation(int orientation) {
        Configuration next = new Configuration(controller.get().getResources().getConfiguration());
        next.orientation = orientation;
        controller.configurationChange(next);
        header.hostResources.getConfiguration().orientation = orientation;
        header.getResources().updateConfiguration(next, header.getResources().getDisplayMetrics());
        header.mBigTime.getResources().updateConfiguration(next,
                header.mBigTime.getResources().getDisplayMetrics());
        header.mLandClock.getResources().updateConfiguration(next,
                header.mLandClock.getResources().getDisplayMetrics());
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
