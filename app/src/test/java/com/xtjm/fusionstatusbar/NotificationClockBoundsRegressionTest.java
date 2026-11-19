package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** A flexible synthetic shade; its dimensions are not a claim about the OEM shared header. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationClockBoundsRegressionTest {
    private ViewGroup header;

    @After public void restoreNativeClock() {
        if (header != null) {
            NotificationCenterClockController.onHeader(header, FusionConfig.defaults());
        }
    }

    @Test public void largeTimeAndSmallMergedDateRemainInsideMeasuredTextBounds() {
        Fixture fixture = layout(360, 120, "HH:mm\nyyyy/MM/dd E");
        TextView time = fixture.header.mBigTime;
        assertEquals(120f * time.getResources().getDisplayMetrics().scaledDensity,
                time.getTextSize(), 0.5f);
        Spanned text = (Spanned) time.getText();
        int dateStart = text.toString().indexOf('\n') + 1;
        AbsoluteSizeSpan[] sizes = text.getSpans(dateStart, text.length(), AbsoluteSizeSpan.class);
        assertTrue("Merged date retains an independent smaller font", sizes.length > 0);
        assertEquals(Math.round(16f * time.getResources().getDisplayMetrics().scaledDensity),
                sizes[0].getSize());
        assertAllTextVisible(time, 2);
        assertTextBeforeNotifications(fixture);
    }

    @Test public void narrowWidthKeepsEveryConfiguredDateLineVisible() {
        Fixture fixture = layout(180, 64, "HH:mm\nyyyy/MM/dd E\n\u519c\u5386\u65e5\u671f");
        assertAllTextVisible(fixture.header.mBigTime, 3);
        assertTextBeforeNotifications(fixture);
    }

    @Test public void mergedClockLeavesVisibleSettingsShortcutUncovered() {
        Fixture fixture = layout(240, 120, "HH:mm:ss\nyyyy/MM/dd E");
        assertEquals(View.VISIBLE, fixture.header.mShortCut.getVisibility());
        RectF shortcut = boundsInRoot(fixture.header.mShortCut);
        TextView time = fixture.header.mBigTime;
        Layout text = time.getLayout();
        assertNotNull("Clock must be measured before checking the shortcut", text);
        for (int line = 0; line < text.getLineCount(); line++) {
            assertFalse("Clock line " + line + " overlaps the settings touch area",
                    RectF.intersects(lineBounds(time, line), shortcut));
        }
    }

    @Test public void legacyVerticalHeaderFitsLargeTextAcrossDensityAndFontScale() {
        int[] densities = {160, 320, 320};
        float[] fontScales = {1f, 1f, 1.3f};
        int[] widths = {360, 240, 320};
        for (int index = 0; index < densities.length; index++) {
            Context context = configuredContext(densities[index], fontScales[index]);
            NativeFixture fixture = nativeLayout(context, widths[index], clockSettings(120));
            assertSame("Reference XML keeps time in its first horizontal row",
                    fixture.header.clockContainer, fixture.header.mBigTime.getParent());
            assertSame("Reference XML keeps the native date in another parent",
                    fixture.header.dateContainer, fixture.header.mDateView.getParent());
            assertAllTextVisible(fixture.header.mBigTime, 3);
            assertShortcutClear(fixture.header.mBigTime, fixture.header.mShortCut);
            assertTextInsideNativeHeader(fixture);
            assertEquals("The separate control-center header retains its native height",
                    dp(context, 125), fixture.controlCenterHeader.getLayoutParams().height);
            assertTrue("The list floor covers the complete merged text",
                    minimumPadding(fixture.stack, 0f)
                            >= lineBounds(fixture.header.mBigTime,
                            fixture.header.mBigTime.getLayout().getLineCount() - 1).bottom - 1f);
            NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        }
    }

    @Test public void expandedRebindingAndDisableRestoreTheFixedNativeRows() {
        Context context = configuredContext(320, 1.3f);
        FusionConfig settings = clockSettings(120).withNotificationClock(
                clockSettings(120).notificationClock.withOffsetY(16));
        NativeFixture fixture = nativeLayout(context, 320, settings);
        for (float progress : new float[] {0.25f, 0.5f, 1f}) {
            NotificationCenterClockController.onExpansionChanged(progress);
            NotificationCenterClockController.onHeader(fixture.header, settings);
            measureNative(fixture);
        }
        assertAllTextVisible(fixture.header.mBigTime, 3);
        assertShortcutClear(fixture.header.mBigTime, fixture.header.mShortCut);
        assertTextInsideNativeHeader(fixture);
        NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        measureNative(fixture);
        assertNativeRowsRestored(fixture);
        assertEquals("Disabling removes the automatic list floor", 17f,
                minimumPadding(fixture.stack, 17f), 0.01f);
    }

    @Test public void firstEnabledMeasureFitsBothNativeRowsWithoutASecondTraversal() {
        Context context = configuredContext(320, 1.3f);
        NativeFixture fixture = new NativeFixture(context, 320);
        header = fixture.header;
        NotificationCenterClockController.onHeader(fixture.header, clockSettings(120));
        NotificationCenterClockController.onExpansionChanged(1f);
        measureNative(fixture);
        assertAllTextVisible(fixture.header.mBigTime, 3);
        assertShortcutClear(fixture.header.mBigTime, fixture.header.mShortCut);
        assertTextInsideNativeHeader(fixture);
        assertTrue("The native settings row is measured on the first traversal",
                ((View) fixture.header.mShortCut.getParent()).getHeight()
                        >= fixture.header.mShortCut.getHeight());
        assertEquals(dp(context, 125), fixture.controlCenterHeader.getLayoutParams().height);
    }

    @Test public void nativeFixedHeightAndMinimumHeightRemainFloorsForSmallText() {
        int[] nativeMinimums = {80, 180};
        int[] expectedHeights = {125, 180};
        for (int index = 0; index < nativeMinimums.length; index++) {
            Context context = configuredContext(160, 1f);
            NativeFixture fixture = new NativeFixture(context, 360);
            header = fixture.header;
            fixture.header.setMinimumHeight(dp(context, nativeMinimums[index]));
            NotificationCenterClockController.onHeader(fixture.header, clockSettings(16));
            measureNative(fixture);
            assertEquals("Small text retains both native lower bounds",
                    dp(context, expectedHeights[index]), fixture.header.getHeight());
            NotificationCenterClockController.onHeader(fixture.header, clockSettings(16));
            measureNative(fixture);
            NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
            measureNative(fixture);
            assertEquals("Disabling restores the native fixed height", dp(context, 125),
                    fixture.header.getLayoutParams().height);
            assertEquals("Disabling restores the independently captured minimum",
                    dp(context, nativeMinimums[index]), fixture.header.getMinimumHeight());
        }
    }

    @Test public void rebindingRetainsUpdatedNativeHeightAndMinimumHeight() {
        Context context = configuredContext(160, 1f);
        NativeFixture fixture = new NativeFixture(context, 360);
        header = fixture.header;
        fixture.header.setMinimumHeight(dp(context, 80));
        FusionConfig settings = clockSettings(16);
        NotificationCenterClockController.onHeader(fixture.header, settings);
        measureNative(fixture);
        ViewGroup.LayoutParams params = fixture.header.getLayoutParams();
        params.height = dp(context, 180);
        fixture.header.setLayoutParams(params);
        fixture.header.setMinimumHeight(dp(context, 220));
        NotificationCenterClockController.onHeader(fixture.header, settings);
        measureNative(fixture);
        assertEquals("Updated native bounds constrain the enabled header", dp(context, 220),
                fixture.header.getHeight());
        NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        measureNative(fixture);
        assertEquals(dp(context, 180), fixture.header.getLayoutParams().height);
        assertEquals(dp(context, 220), fixture.header.getMinimumHeight());
    }

    @Test public void disablingLeavesLaterNativeHeightAndMinimumHeightUntouched() {
        Context context = configuredContext(160, 1f);
        NativeFixture fixture = new NativeFixture(context, 360);
        header = fixture.header;
        fixture.header.setMinimumHeight(dp(context, 80));
        NotificationCenterClockController.onHeader(fixture.header, clockSettings(16));
        measureNative(fixture);
        ViewGroup.LayoutParams params = fixture.header.getLayoutParams();
        params.height = dp(context, 180);
        fixture.header.setLayoutParams(params);
        fixture.header.setMinimumHeight(dp(context, 200));
        NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        measureNative(fixture);
        assertEquals(dp(context, 180), fixture.header.getLayoutParams().height);
        assertEquals(dp(context, 200), fixture.header.getMinimumHeight());
    }

    @Test public void updatedNativeHeightDoesNotCaptureTheModulesMinimumAsNative() {
        Context context = configuredContext(160, 1f);
        NativeFixture fixture = new NativeFixture(context, 360);
        header = fixture.header;
        fixture.header.setMinimumHeight(dp(context, 80));
        FusionConfig settings = clockSettings(16);
        NotificationCenterClockController.onHeader(fixture.header, settings);
        measureNative(fixture);
        ViewGroup.LayoutParams params = fixture.header.getLayoutParams();
        params.height = dp(context, 180);
        fixture.header.setLayoutParams(params);
        NotificationCenterClockController.onHeader(fixture.header, settings);
        measureNative(fixture);
        assertEquals(dp(context, 180), fixture.header.getHeight());
        NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        assertEquals(dp(context, 180), fixture.header.getLayoutParams().height);
        assertEquals("The 125dp module floor was never a native minimum", dp(context, 80),
                fixture.header.getMinimumHeight());
    }

    @Test public void landscapeRestoresThePortraitHeaderMinimumHeight() {
        Context context = configuredContext(160, 1f);
        NativeFixture fixture = new NativeFixture(context, 360);
        header = fixture.header;
        fixture.header.setMinimumHeight(dp(context, 80));
        NotificationCenterClockController.onHeader(fixture.header, clockSettings(16));
        measureNative(fixture);
        try {
            context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_LANDSCAPE;
            fixture.header.getResources().getConfiguration().orientation = Configuration.ORIENTATION_LANDSCAPE;
            NotificationCenterClockController.onHeader(fixture.header, clockSettings(16));
            measureNative(fixture);
            assertEquals(dp(context, 125), fixture.header.getLayoutParams().height);
            assertEquals(dp(context, 80), fixture.header.getMinimumHeight());
        } finally {
            context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
            fixture.header.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        }
    }

    @Test public void replacingAHeaderRestoresItsNativeMinimumHeight() {
        Context context = configuredContext(160, 1f);
        NativeFixture previous = new NativeFixture(context, 360);
        header = previous.header;
        previous.header.setMinimumHeight(dp(context, 80));
        NotificationCenterClockController.onHeader(previous.header, clockSettings(16));
        measureNative(previous);
        nativeLayout(context, 320, clockSettings(16));
        assertEquals(dp(context, 125), previous.header.getLayoutParams().height);
        assertEquals(dp(context, 80), previous.header.getMinimumHeight());
    }

    @Test public void replacingAHeaderRestoresTheOldRowsBeforeItsListenerFires() {
        Context context = configuredContext(160, 1f);
        NativeFixture previous = nativeLayout(context, 360, clockSettings(120));
        NativeFixture current = nativeLayout(context, 320, clockSettings(100));
        assertNativeRowsRestored(previous);
        int currentHeight = current.header.getLayoutParams().height;
        int currentTimeHeight = current.header.mBigTime.getLayoutParams().height;
        String currentText = current.header.mBigTime.getText().toString();
        previous.header.layout(0, 0, previous.header.getWidth() + 1, previous.header.getHeight());
        assertEquals("Old layout listener cannot change the new header height", currentHeight,
                current.header.getLayoutParams().height);
        assertEquals("Old layout listener cannot change the new time bounds", currentTimeHeight,
                current.header.mBigTime.getLayoutParams().height);
        assertEquals(currentText, current.header.mBigTime.getText().toString());
        assertSame(current.header.clockContainer, current.header.mBigTime.getParent());
    }

    @Test public void listFloorMapsTheMeasuredTextIntoTheTransformedStack() {
        NativeFixture fixture = nativeLayout(configuredContext(160, 1f), 360, clockSettings(120));
        TextView clock = fixture.header.mBigTime;
        clock.setPivotY(0f);
        clock.setScaleY(2f);
        clock.setTranslationY(7f);
        fixture.header.clockContainer.setTranslationY(11f);
        fixture.header.setTranslationY(13f);
        fixture.stack.setPivotY(0f);
        fixture.stack.setScaleY(0.5f);
        fixture.stack.setTranslationY(17f);
        float localBottom = clock.getTotalPaddingTop() - clock.getScrollY()
                + clock.getLayout().getLineBottom(clock.getLayout().getLineCount() - 1);
        // Hand-derived for the literal transforms above, independent of the production matrix helper.
        float rootBottom = localBottom * 2f + 7f + clock.getTop() + 11f
                + fixture.header.clockContainer.getTop() + 13f + fixture.header.getTop();
        float expected = (rootBottom - fixture.stack.getTop() - 17f) / 0.5f;
        assertEquals(expected,
                minimumPadding(fixture.stack, 0f), 1f);
        assertEquals("A larger native padding remains authoritative", expected + 100f,
                minimumPadding(fixture.stack, expected + 100f), 0.01f);
        FrameLayout otherRoot = new FrameLayout(fixture.header.getContext());
        assertEquals("A different root cannot inherit this header's floor", 29f,
                minimumPadding(otherRoot, 29f), 0.01f);
    }

    @Test public void aHiddenHeaderDoesNotReserveSpaceForItsVisibleChildClock() {
        NativeFixture fixture = nativeLayout(configuredContext(160, 1f), 360, clockSettings(120));
        fixture.header.setVisibility(View.GONE);
        assertEquals(23f,
                minimumPadding(fixture.stack, 23f), 0.01f);
    }

    @Test public void disabledHeaderPassesRespectLaterNativeExpandedGeometry() {
        Context context = configuredContext(160, 1f);
        NativeFixture fixture = new NativeFixture(context, 360);
        header = fixture.header;
        NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        measureNative(fixture);
        // The reference OEM expansion listener itself switches the time height to WRAP_CONTENT.
        fixture.header.mBigTime.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        fixture.header.mBigTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        fixture.header.mBigTime.requestLayout();
        for (int pass = 0; pass < 3; pass++) {
            NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
            measureNative(fixture);
        }
        assertEquals("A disabled module cannot repeatedly restore its initial 64dp snapshot",
                ViewGroup.LayoutParams.WRAP_CONTENT, fixture.header.mBigTime.getLayoutParams().height);
        assertEquals("Later native text size remains owned by the host",
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 52,
                        context.getResources().getDisplayMetrics()),
                fixture.header.mBigTime.getTextSize(), 0.5f);
    }

    @Test public void disablingAfterHostGeometryChangesRestoresTheLatestNativeBounds() {
        assertLatestNativeBoundsRestored(false);
    }

    @Test public void rebindingAfterHostGeometryChangesRestoresTheLatestNativeBounds() {
        assertLatestNativeBoundsRestored(true);
    }

    private void assertLatestNativeBoundsRestored(boolean rebind) {
        Context context = configuredContext(160, 1f);
        FusionConfig settings = clockSettings(120);
        NativeFixture fixture = nativeLayout(context, 360, settings);
        int nativeHeaderHeight = dp(context, 180);
        int nativeTimeHeight = dp(context, 76);
        ViewGroup.LayoutParams headerParams = fixture.header.getLayoutParams();
        headerParams.height = nativeHeaderHeight;
        fixture.header.setLayoutParams(headerParams);
        ViewGroup.LayoutParams timeParams = fixture.header.mBigTime.getLayoutParams();
        timeParams.height = nativeTimeHeight;
        fixture.header.mBigTime.setLayoutParams(timeParams);
        // A retained header can receive fresh native dimensions and padding on configuration change.
        fixture.header.clockContainer.setPaddingRelative(3, 4, 5, 8);
        if (rebind) {
            for (int pass = 0; pass < 3; pass++) {
                NotificationCenterClockController.onHeader(fixture.header, settings);
                measureNative(fixture);
            }
            assertAllTextVisible(fixture.header.mBigTime, 3);
            assertTextInsideNativeHeader(fixture);
        }
        NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
        measureNative(fixture);
        assertEquals("Disabling retains the host's replacement header height", nativeHeaderHeight,
                fixture.header.getLayoutParams().height);
        assertEquals("Disabling retains the host's replacement time height", nativeTimeHeight,
                fixture.header.mBigTime.getLayoutParams().height);
        assertEquals(3, fixture.header.clockContainer.getPaddingStart());
        assertEquals(4, fixture.header.clockContainer.getPaddingTop());
        assertEquals(5, fixture.header.clockContainer.getPaddingEnd());
        assertEquals(8, fixture.header.clockContainer.getPaddingBottom());
        assertEquals(View.VISIBLE, fixture.header.mDateView.getVisibility());
        assertTrue(fixture.header.mBigTime.isSingleLine());
    }

    @Test public void listPaddingRoundsTheFractionalFloorUpBeforeTheHostTruncatesIt()
            throws ReflectiveOperationException {
        NativeFixture fixture = nativeLayout(configuredContext(160, 1f), 360, clockSettings(120));
        fixture.header.mBigTime.setTranslationY(0.75f);
        Field configField = SystemUiHooks.class.getDeclaredField("currentConfig");
        Field nativePaddingField = SystemUiHooks.class.getDeclaredField("nativeNotificationTopPadding");
        configField.setAccessible(true);
        nativePaddingField.setAccessible(true);
        Object previousConfig = configField.get(null);
        float previousNativePadding = nativePaddingField.getFloat(null);
        Method paddingMethod;
        try {
            paddingMethod = SystemUiHooks.class.getDeclaredMethod("notificationListPadding", View.class);
        } catch (NoSuchMethodException baselineWithoutFloor) {
            fail("The baseline has no notification text space reservation");
            return;
        }
        paddingMethod.setAccessible(true);
        try {
            configField.set(null, clockSettings(120));
            nativePaddingField.setFloat(null, 0f);
            float floor = minimumPadding(fixture.stack, 0f);
            assertTrue("This regression requires a fractional text boundary", floor != Math.floor(floor));
            float desired = ((Number) paddingMethod.invoke(null, fixture.stack)).floatValue();
            assertEquals("The OEM casts its argument to int, so round the floor upward first",
                    (float) Math.ceil(floor), desired, 0.01f);
            assertEquals("The desired value matches what the host stores", desired, (int) desired, 0f);
            assertTrue("The stored integer still covers the complete text", (int) desired >= floor);
        } finally {
            configField.set(null, previousConfig);
            nativePaddingField.setFloat(null, previousNativePadding);
        }
    }

    @Test public void firstMeasureContainsOddAndEvenNegativeMarginsAndFractionalTranslation() {
        int[] margins = {-1, -5, -6, 0};
        for (int index = 0; index < margins.length; index++) {
            for (int direction : new int[] {View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
                float translation = (index + direction) % 2 == 0 ? 0.25f : 0.75f;
                int topMargin = margins[index] == -6 ? 3 : 0;
                NativeFixture fixture = firstNativeMeasure(topMargin, margins[index], Gravity.CENTER_VERTICAL,
                        -1, false, translation, direction);
                assertNativeClockClear(fixture);
                assertEquals(topMargin,
                        ((LinearLayout.LayoutParams) fixture.header.mBigTime.getLayoutParams()).topMargin);
                assertEquals("The configured native margin remains intact", margins[index],
                        ((LinearLayout.LayoutParams) fixture.header.mBigTime.getLayoutParams()).bottomMargin);
                assertEquals(direction, fixture.header.clockContainer.getLayoutDirection());
                assertEquals(direction, fixture.header.mBigTime.getLayoutDirection());
                NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
            }
        }
    }

    @Test public void firstMeasureUsesTheEffectiveVerticalGravityWithAndWithoutBaselines() {
        int[][] gravities = {
                {Gravity.TOP, -1, 0}, {Gravity.CENTER_VERTICAL, -1, 1}, {Gravity.BOTTOM, -1, 2},
                {Gravity.TOP, Gravity.CENTER_VERTICAL, 2}, {Gravity.CENTER_VERTICAL, Gravity.BOTTOM, 1},
                {Gravity.BOTTOM, Gravity.TOP, 0}
        };
        for (int index = 0; index < gravities.length; index++) {
            for (boolean baselineAligned : new boolean[] {false, true}) {
                NativeFixture fixture = firstNativeMeasure(gravities[index][2], -5,
                        gravities[index][0], gravities[index][1],
                        baselineAligned, baselineAligned ? 0.75f : 0.25f,
                        index % 2 == 0 ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
                assertEquals(baselineAligned, fixture.header.clockContainer.isBaselineAligned());
                assertTrue("The Android TextView contributes a real baseline",
                        fixture.header.mBigTime.getBaseline() >= 0);
                assertEquals(gravities[index][1],
                        ((LinearLayout.LayoutParams) fixture.header.mBigTime.getLayoutParams()).gravity);
                assertNativeClockClear(fixture);
                NotificationCenterClockController.onHeader(fixture.header, FusionConfig.defaults());
            }
        }
    }

    @Test public void repeatedHeaderAndExpansionCallbacksDoNotAccumulateClockSpace() {
        NativeFixture fixture = firstNativeMeasure(0, -5, Gravity.CENTER_VERTICAL, -1,
                false, 0.75f, View.LAYOUT_DIRECTION_RTL);
        int bottomPadding = fixture.header.clockContainer.getPaddingBottom();
        int rowHeight = fixture.header.clockContainer.getHeight();
        int headerHeight = fixture.header.getHeight();
        int timeTop = fixture.header.mBigTime.getTop();
        for (float progress : new float[] {0.25f, 0.5f, 1f, 1f}) {
            NotificationCenterClockController.onExpansionChanged(progress);
            NotificationCenterClockController.onHeader(fixture.header, clockSettings(120));
            measureNative(fixture);
            assertEquals("Rebinding does not add the reservation again", bottomPadding,
                    fixture.header.clockContainer.getPaddingBottom());
            assertEquals(rowHeight, fixture.header.clockContainer.getHeight());
            assertEquals(headerHeight, fixture.header.getHeight());
            assertEquals(timeTop, fixture.header.mBigTime.getTop());
            assertEquals(0.75f, fixture.header.mBigTime.getTranslationY(), 0f);
            assertEquals(-5,
                    ((LinearLayout.LayoutParams) fixture.header.mBigTime.getLayoutParams()).bottomMargin);
            assertNativeClockClear(fixture);
        }
    }

    @Test public void disablingRestoresHostPaddingReplacedDuringFractionalClockReservation() {
        NativeFixture fixture = firstNativeMeasure(0, -5, Gravity.CENTER_VERTICAL, -1,
                false, 0.25f, View.LAYOUT_DIRECTION_LTR);
        fixture.header.clockContainer.setPaddingRelative(7, 9, 11, 13);
        NotificationCenterClockController.onHeader(fixture.header, clockSettings(120));
        measureNative(fixture);
        assertNativeClockClear(fixture);
        int appliedBottom = fixture.header.clockContainer.getPaddingBottom();
        assertTrue("The replacement host padding remains beneath the clock reservation", appliedBottom > 13);
        for (boolean enabled : new boolean[] {false, true, false}) {
            NotificationCenterClockController.onHeader(fixture.header,
                    enabled ? clockSettings(120) : FusionConfig.defaults());
            measureNative(fixture);
            assertEquals(7, fixture.header.clockContainer.getPaddingStart());
            assertEquals(9, fixture.header.clockContainer.getPaddingTop());
            assertEquals(11, fixture.header.clockContainer.getPaddingEnd());
            assertEquals(enabled ? appliedBottom : 13, fixture.header.clockContainer.getPaddingBottom());
            assertEquals(-5,
                    ((LinearLayout.LayoutParams) fixture.header.mBigTime.getLayoutParams()).bottomMargin);
            if (enabled) {
                assertNativeClockClear(fixture);
            } else {
                assertEquals(dp(fixture.header.getContext(), 125), fixture.header.getLayoutParams().height);
                assertEquals(dp(fixture.header.getContext(), 64), fixture.header.mBigTime.getLayoutParams().height);
                assertEquals(View.VISIBLE, fixture.header.mDateView.getVisibility());
                assertTrue(fixture.header.mBigTime.isSingleLine());
            }
        }
    }

    private NativeFixture firstNativeMeasure(int topMargin, int bottomMargin, int rowGravity, int childGravity,
            boolean baselineAligned, float translationY, int direction) {
        NativeFixture fixture = new NativeFixture(configuredContext(160, 1f), 360);
        header = fixture.header;
        fixture.root.setLayoutDirection(direction);
        fixture.header.clockContainer.setGravity(rowGravity);
        fixture.header.clockContainer.setBaselineAligned(baselineAligned);
        TextView clock = fixture.header.mBigTime;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) clock.getLayoutParams();
        params.topMargin = topMargin;
        params.bottomMargin = bottomMargin;
        params.gravity = childGravity;
        clock.setLayoutParams(params);
        clock.setTranslationY(translationY);
        NotificationCenterClockController.onHeader(fixture.header, clockSettings(120));
        NotificationCenterClockController.onExpansionChanged(1f);
        measureNative(fixture);
        return fixture;
    }

    private static void assertNativeClockClear(NativeFixture fixture) {
        TextView clock = fixture.header.mBigTime;
        assertAllTextVisible(clock, 3);
        assertShortcutClear(clock, fixture.header.mShortCut);
        RectF row = boundsInRoot(fixture.header.clockContainer);
        for (int line = 0; line < clock.getLayout().getLineCount(); line++) {
            RectF text = lineBounds(clock, line);
            assertTrue("Clock text stays within its actual first-row frame"
                            + collisionDiagnostics(clock, fixture.header.mShortCut, line),
                    text.top >= row.top && text.bottom <= row.bottom);
        }
        assertTextInsideNativeHeader(fixture);
    }

    private static float minimumPadding(View stack, float nativePadding) {
        try {
            Method method = NotificationCenterClockController.class.getDeclaredMethod(
                    "minimumNotificationTopPadding", View.class, float.class);
            method.setAccessible(true);
            return ((Number) method.invoke(null, stack, nativePadding)).floatValue();
        } catch (NoSuchMethodException baselineWithoutFloor) {
            return nativePadding;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Context configuredContext(int densityDpi, float fontScale) {
        Context application = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(application.getResources().getConfiguration());
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        configuration.densityDpi = densityDpi;
        configuration.fontScale = fontScale;
        return application.createConfigurationContext(configuration);
    }

    private static FusionConfig clockSettings(int sizeSp) {
        return FusionConfig.defaults().withNotificationClock(NotificationClockConfig.defaults()
                .withEnabled(true).withPattern("HH:mm\nyyyy/MM/dd E\n\u519c\u5386\u65e5\u671f")
                .withSize(sizeSp).withDateSize(16));
    }

    private NativeFixture nativeLayout(Context context, int widthDp, FusionConfig settings) {
        NativeFixture fixture = new NativeFixture(context, widthDp);
        header = fixture.header;
        for (int pass = 0; pass < 6; pass++) {
            NotificationCenterClockController.onHeader(fixture.header, settings);
            NotificationCenterClockController.onExpansionChanged(1f);
            measureNative(fixture);
        }
        return fixture;
    }

    private static void measureNative(NativeFixture fixture) {
        fixture.root.measure(View.MeasureSpec.makeMeasureSpec(fixture.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(fixture.root.getContext(), 2000), View.MeasureSpec.EXACTLY));
        fixture.root.layout(0, 0, fixture.root.getMeasuredWidth(), fixture.root.getMeasuredHeight());
    }

    private static void assertNativeRowsRestored(NativeFixture fixture) {
        Context context = fixture.header.getContext();
        assertEquals("Native fixed header height is restored", dp(context, 125),
                fixture.header.getLayoutParams().height);
        assertEquals("Native fixed time height is restored", dp(context, 64),
                fixture.header.mBigTime.getLayoutParams().height);
        assertEquals("Native negative bottom margin is restored", -dp(context, 6),
                ((ViewGroup.MarginLayoutParams) fixture.header.mBigTime.getLayoutParams()).bottomMargin);
        assertEquals(View.VISIBLE, fixture.header.mDateView.getVisibility());
        assertTrue(fixture.header.mBigTime.isSingleLine());
        assertTrue("Date ancestor clipping is restored", fixture.header.dateContainer.getClipChildren());
    }

    private static void assertShortcutClear(TextView clock, View shortcut) {
        RectF target = boundsInRoot(shortcut);
        for (int line = 0; line < clock.getLayout().getLineCount(); line++) {
            assertFalse("Clock line " + line + " covers the native second-row settings button"
                            + collisionDiagnostics(clock, shortcut, line),
                    RectF.intersects(lineBounds(clock, line), target));
        }
    }

    private static String collisionDiagnostics(TextView clock, View shortcut, int line) {
        Layout layout = clock.getLayout();
        StringBuilder result = new StringBuilder("; textRoot=").append(lineBounds(clock, line))
                .append(" buttonRoot=").append(boundsInRoot(shortcut))
                .append(" layout=").append(layout.getWidth()).append('x').append(layout.getHeight())
                .append(" lines=").append(layout.getLineCount())
                .append(" localLine=[").append(layout.getLineLeft(line)).append(',')
                .append(layout.getLineTop(line)).append(',').append(layout.getLineRight(line))
                .append(',').append(layout.getLineBottom(line)).append(']')
                .append(" totalPaddingTop=").append(clock.getTotalPaddingTop())
                .append(" compoundPadding=[").append(clock.getCompoundPaddingLeft()).append(',')
                .append(clock.getCompoundPaddingTop()).append(',').append(clock.getCompoundPaddingRight())
                .append(',').append(clock.getCompoundPaddingBottom()).append(']')
                .append(" density/fontScale=").append(clock.getResources().getDisplayMetrics().density)
                .append('/').append(clock.getResources().getConfiguration().fontScale);
        appendGeometry(result, " time", clock);
        View current = clock;
        while (current.getParent() instanceof View parent) {
            appendGeometry(result, " <- ", parent);
            if (parent instanceof NativeHeader nativeHeader
                    && nativeHeader.getParent() instanceof ViewGroup root && root.getChildCount() > 0) {
                result.append(" headerFloor=").append(nativeHeader.getMinimumHeight())
                        .append(" listFloor=").append(minimumPadding(root.getChildAt(0), 0f));
            }
            current = parent;
        }
        appendGeometry(result, " button", shortcut);
        if (shortcut.getParent() instanceof View row) appendGeometry(result, " buttonRow", row);
        return result.toString();
    }

    private static void appendGeometry(StringBuilder result, String label, View view) {
        result.append(label).append(view.getClass().getSimpleName())
                .append(" frame=[").append(view.getLeft()).append(',').append(view.getTop()).append(',')
                .append(view.getRight()).append(',').append(view.getBottom()).append(']')
                .append(" actual/measured=").append(view.getWidth()).append('x').append(view.getHeight())
                .append('/').append(view.getMeasuredWidth()).append('x').append(view.getMeasuredHeight())
                .append(" padding=[").append(view.getPaddingLeft()).append(',').append(view.getPaddingTop())
                .append(',').append(view.getPaddingRight()).append(',').append(view.getPaddingBottom()).append(']')
                .append(" scroll=").append(view.getScrollX()).append(',').append(view.getScrollY())
                .append(" translation=").append(view.getTranslationX()).append(',').append(view.getTranslationY())
                .append(" scale=").append(view.getScaleX()).append(',').append(view.getScaleY())
                .append(" pivot=").append(view.getPivotX()).append(',').append(view.getPivotY())
                .append(" direction=").append(view.getLayoutDirection())
                .append(" baseline=").append(view.getBaseline())
                .append(" minimumHeight=").append(view.getMinimumHeight());
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) result.append(" lp=").append(params.width).append('x').append(params.height);
        if (params instanceof ViewGroup.MarginLayoutParams margins) {
            result.append(" margins=[").append(margins.leftMargin).append(',').append(margins.topMargin)
                    .append(',').append(margins.rightMargin).append(',').append(margins.bottomMargin)
                    .append("] start/end=").append(margins.getMarginStart()).append('/').append(margins.getMarginEnd());
        }
        if (params instanceof LinearLayout.LayoutParams linear) result.append(" childGravity=").append(linear.gravity);
        if (view instanceof LinearLayout row) {
            result.append(" orientation/gravity/baselineAligned=").append(row.getOrientation())
                    .append('/').append(row.getGravity()).append('/').append(row.isBaselineAligned());
            for (int index = 0; index < row.getChildCount(); index++) {
                View child = row.getChildAt(index);
                result.append(" child[").append(index).append("]=").append(child.getVisibility())
                        .append('/').append(child.getMeasuredHeight()).append('/').append(child.getBaseline());
                if (child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins)
                    result.append(" tm/bm=").append(margins.topMargin).append('/').append(margins.bottomMargin);
            }
        }
    }

    private static void assertTextInsideNativeHeader(NativeFixture fixture) {
        RectF headerBounds = boundsInRoot(fixture.header);
        TextView clock = fixture.header.mBigTime;
        for (int line = 0; line < clock.getLayout().getLineCount(); line++) {
            RectF text = lineBounds(clock, line);
            assertTrue("Bottom gravity must not move clock line " + line + " above the header",
                    text.top >= headerBounds.top + fixture.header.getPaddingTop() - 1f);
            assertTrue("Clock line " + line + " remains inside the header's measured bottom",
                    text.bottom <= headerBounds.bottom - fixture.header.getPaddingBottom() + 1f);
        }
        View secondRow = (View) fixture.header.mShortCut.getParent();
        int contentHeight = fixture.header.clockContainer.getHeight() + secondRow.getHeight()
                + fixture.header.getPaddingTop() + fixture.header.getPaddingBottom();
        assertTrue("The fixed header grows enough to measure both native rows",
                fixture.header.getHeight() >= contentHeight);
    }

    private Fixture layout(int widthDp, int sizeSp, String pattern) {
        Context context = RuntimeEnvironment.getApplication();
        Header flexible = new Header(context);
        header = flexible;
        Fixture fixture = new Fixture(context, flexible);
        FusionConfig config = FusionConfig.defaults().withNotificationClock(
                NotificationClockConfig.defaults().withEnabled(true).withPattern(pattern)
                        .withSize(sizeSp).withDateSize(16));
        // Repeat the public header callback and a real measure/layout pass, as resource
        // changes can reapply the controller after its initial reparenting.
        for (int pass = 0; pass < 3; pass++) {
            NotificationCenterClockController.onHeader(flexible, config);
            NotificationCenterClockController.onExpansionChanged(1f);
            fixture.root.measure(
                    View.MeasureSpec.makeMeasureSpec(dp(context, widthDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(context, 2000), View.MeasureSpec.AT_MOST));
            fixture.root.layout(0, 0, fixture.root.getMeasuredWidth(), fixture.root.getMeasuredHeight());
        }
        return fixture;
    }

    private static void assertAllTextVisible(TextView view, int minimumLines) {
        Layout layout = view.getLayout();
        assertNotNull("Clock has an Android text layout", layout);
        assertTrue("All configured paragraphs reach the text layout", layout.getLineCount() >= minimumLines);
        assertEquals("Text layout reaches the end of the merged date", view.length(),
                layout.getLineEnd(layout.getLineCount() - 1));
        int bottom = view.getHeight() - view.getCompoundPaddingBottom();
        int contentWidth = view.getWidth() - view.getCompoundPaddingLeft()
                - view.getCompoundPaddingRight();
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals("Clock line " + line + " must not be ellipsized", 0,
                    layout.getEllipsisCount(line));
            assertTrue("Clock line " + line + " exceeds its measured content width",
                    layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= contentWidth + 1f);
            assertTrue("Clock line " + line + " is scrolled above the measured text area",
                    view.getTotalPaddingTop() - view.getScrollY() + layout.getLineTop(line)
                            >= view.getCompoundPaddingTop());
            assertTrue("Clock line " + line + " exceeds its measured content height"
                            + heightDiagnostics(view, layout, line),
                    view.getTotalPaddingTop() - view.getScrollY() + layout.getLineBottom(line) <= bottom);
        }
    }

    private static String heightDiagnostics(TextView view, Layout layout, int line) {
        StringBuilder result = new StringBuilder("; view actual/measured=")
                .append(view.getHeight()).append('/').append(view.getMeasuredHeight())
                .append(" layoutHeight=").append(layout.getHeight())
                .append(" lineBottom=").append(layout.getLineBottom(line))
                .append(" totalPaddingTop=").append(view.getTotalPaddingTop())
                .append(" compoundPadding=").append(view.getCompoundPaddingTop()).append('/')
                .append(view.getCompoundPaddingBottom())
                .append(" scrollY=").append(view.getScrollY())
                .append(" maxLines/Height=").append(view.getMaxLines()).append('/')
                .append(view.getMaxHeight());
        View current = view;
        while (current.getParent() instanceof View parent) {
            result.append(" <- ").append(parent.getClass().getSimpleName())
                    .append(" actual/measured=").append(parent.getHeight()).append('/')
                    .append(parent.getMeasuredHeight()).append(" lpHeight=")
                    .append(parent.getLayoutParams() == null ? "none"
                            : Integer.toString(parent.getLayoutParams().height))
                    .append(" minimumHeight=").append(parent.getMinimumHeight());
            current = parent;
        }
        return result.toString();
    }

    private static void assertTextBeforeNotifications(Fixture fixture) {
        TextView time = fixture.header.mBigTime;
        RectF notifications = boundsInRoot(fixture.notifications);
        Layout layout = time.getLayout();
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertTrue("Clock line " + line + " reaches the notification list",
                    lineBounds(time, line).bottom <= notifications.top + 1f);
        }
    }

    private static RectF lineBounds(TextView view, int line) {
        Layout layout = view.getLayout();
        float left = view.getCompoundPaddingLeft() - view.getScrollX();
        float top = view.getTotalPaddingTop() - view.getScrollY();
        return mapToRoot(view, new RectF(left + layout.getLineLeft(line), top + layout.getLineTop(line),
                left + layout.getLineRight(line), top + layout.getLineBottom(line)));
    }

    private static RectF boundsInRoot(View view) {
        return mapToRoot(view, new RectF(0f, 0f, view.getWidth(), view.getHeight()));
    }

    private static RectF mapToRoot(View view, RectF bounds) {
        View current = view;
        while (current.getParent() instanceof View parent) {
            current.getMatrix().mapRect(bounds);
            bounds.offset(current.getLeft() - parent.getScrollX(), current.getTop() - parent.getScrollY());
            current = parent;
        }
        return bounds;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Fixture {
        final LinearLayout root;
        final Header header;
        final View notifications;

        Fixture(Context context, Header header) {
            this.header = header;
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.addView(header, new LinearLayout.LayoutParams(-1, -2));
            notifications = new View(context);
            root.addView(notifications, new LinearLayout.LayoutParams(-1, dp(context, 400)));
        }
    }

    private static final class NativeFixture {
        final FrameLayout root;
        final NativeHeader header;
        final FrameLayout stack;
        final View controlCenterHeader;
        final int width;

        NativeFixture(Context context, int widthDp) {
            width = dp(context, widthDp);
            root = new FrameLayout(context);
            stack = new FrameLayout(context);
            root.addView(stack, new FrameLayout.LayoutParams(-1, -1));
            header = new NativeHeader(context);
            root.addView(header, new FrameLayout.LayoutParams(-1, dp(context, 125)));
            controlCenterHeader = new View(context);
            controlCenterHeader.setVisibility(View.GONE);
            root.addView(controlCenterHeader, new FrameLayout.LayoutParams(-1, dp(context, 125)));
        }
    }

    /** Mirrors the reference XML's parent structure, not OEM custom widget implementations. */
    public static final class NativeHeader extends LinearLayout {
        private Resources hostResources;
        public final TextView mBigTime;
        public final TextView mDateView;
        public final TextView mLandClock;
        public final View mShortCut;
        final LinearLayout clockContainer;
        final LinearLayout dateContainer;

        NativeHeader(Context context) {
            super(context);
            Resources resources = super.getResources();
            hostResources = new Resources(resources.getAssets(), resources.getDisplayMetrics(),
                    resources.getConfiguration()) {
                @Override public int getIdentifier(String name, String type, String pkg) {
                    if ("notification_header_clock_container".equals(name)) return 0x12411;
                    return super.getIdentifier(name, type, pkg);
                }
            };
            // Reference dimens.xml: header 125dp, bottom 10dp, horizontal padding 26dp;
            // time 64dp with -6dp bottom margin, shortcut 24.7273dp plus 6dp start margin.
            setOrientation(VERTICAL);
            setGravity(Gravity.BOTTOM);
            setPaddingRelative(dp(context, 26), 0, dp(context, 26), dp(context, 10));
            setClipChildren(false);
            setClipToPadding(false);
            clockContainer = new LinearLayout(context);
            clockContainer.setId(0x12411);
            clockContainer.setGravity(Gravity.CENTER_VERTICAL);
            clockContainer.setBaselineAligned(false);
            clockContainer.setClipChildren(false);
            clockContainer.setClipToPadding(false);
            mLandClock = new TextView(context);
            mLandClock.setVisibility(View.GONE);
            clockContainer.addView(mLandClock, new LinearLayout.LayoutParams(-2, -1));
            mBigTime = new TextView(context);
            mBigTime.setText("09:30");
            mBigTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
            mBigTime.setGravity(Gravity.CENTER_VERTICAL);
            mBigTime.setSingleLine(true);
            mBigTime.setPivotX(0f);
            mBigTime.setPivotY(0f);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, dp(context, 64));
            timeParams.bottomMargin = -dp(context, 6);
            clockContainer.addView(mBigTime, timeParams);
            LinearLayout carrier = new LinearLayout(context);
            clockContainer.addView(carrier, new LinearLayout.LayoutParams(0, -2, 1f));
            clockContainer.addView(new LinearLayout(context), new LinearLayout.LayoutParams(-2, -2));
            addView(clockContainer, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout secondRow = new LinearLayout(context);
            secondRow.setGravity(Gravity.BOTTOM);
            secondRow.setClipChildren(false);
            secondRow.setClipToPadding(false);
            mShortCut = new View(context);
            int shortcutSize = Math.round(24.7273f * context.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams shortcut = new LinearLayout.LayoutParams(shortcutSize, shortcutSize);
            shortcut.setMarginStart(dp(context, 6));
            secondRow.addView(mShortCut, shortcut);
            dateContainer = new LinearLayout(context);
            mDateView = new TextView(context);
            mDateView.setText("Native date");
            mDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            mDateView.setLetterSpacing(0.05f);
            dateContainer.addView(mDateView, new LinearLayout.LayoutParams(-2, -2));
            secondRow.addView(dateContainer, new LinearLayout.LayoutParams(-1, -2));
            addView(secondRow, new LinearLayout.LayoutParams(-1, -2));
        }

        @Override public Resources getResources() {
            return hostResources == null ? super.getResources() : hostResources;
        }
    }

    public static final class Header extends FrameLayout {
        private Resources hostResources;
        public final TextView mBigTime;
        public final TextView mDateView;
        public final View mShortCut;

        Header(Context context) {
            super(context);
            Resources resources = super.getResources();
            hostResources = new Resources(resources.getAssets(), resources.getDisplayMetrics(),
                    resources.getConfiguration()) {
                @Override public int getIdentifier(String name, String type, String pkg) {
                    if ("notification_header_clock_container".equals(name)) return 0x12401;
                    return super.getIdentifier(name, type, pkg);
                }
            };
            LinearLayout clockContainer = new LinearLayout(context);
            clockContainer.setId(0x12401);
            clockContainer.setBaselineAligned(false);
            mBigTime = new TextView(context);
            mBigTime.setText("09:30");
            mBigTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
            mBigTime.setSingleLine(true);
            mDateView = new TextView(context);
            mDateView.setText("Native date");
            clockContainer.addView(mBigTime, new LinearLayout.LayoutParams(-2, -2));
            clockContainer.addView(mDateView, new LinearLayout.LayoutParams(-2, -2));
            // This is one explicit constrained-parent fixture, not an OEM dimension.
            addView(clockContainer, new FrameLayout.LayoutParams(-1, dp(context, 96)));
            mShortCut = new View(context);
            FrameLayout.LayoutParams shortcut = new FrameLayout.LayoutParams(
                    dp(context, 48), dp(context, 48), Gravity.TOP | Gravity.END);
            addView(mShortCut, shortcut);
        }

        @Override public Resources getResources() {
            return hostResources == null ? super.getResources() : hostResources;
        }
    }
}
