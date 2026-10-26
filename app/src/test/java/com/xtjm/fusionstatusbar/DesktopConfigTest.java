package com.xtjm.fusionstatusbar;

import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DesktopConfigTest {
    private android.content.Context context;

    @Before public void resetStore() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("desktop_settings_v1", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void serializesHiddenPackagesAndGestureMappings() {
        DesktopConfig expected = DesktopConfig.defaults()
                .withHideIcons(true)
                .withHiddenPackages(new TreeSet<>(Arrays.asList("com.example.alpha", "com.example.beta")))
                .withGesture(DesktopConfig.GESTURE_SINGLE_DOWN,
                        DesktopConfig.ACTION_NOTIFICATIONS)
                .withGesture(DesktopConfig.GESTURE_DOUBLE_UP, DesktopConfig.ACTION_LAUNCH_APP)
                .withGestureTarget(DesktopConfig.GESTURE_DOUBLE_UP,
                        "com.example.beta|com.example.beta.Main", "Beta");

        DesktopConfig actual = DesktopConfig.fromBundle(expected.toBundle());

        assertEquals(expected, actual);
        assertTrue(actual.hidesPackage("com.example.alpha"));
        assertFalse(actual.hidesPackage("com.example.missing"));
        assertEquals(DesktopConfig.ACTION_LAUNCH_APP,
                actual.gestureAction(DesktopConfig.GESTURE_DOUBLE_UP));
        assertEquals("com.example.beta",
                actual.gesturePackage(DesktopConfig.GESTURE_DOUBLE_UP));
    }

    @Test public void malformedValuesFallBackWithoutAcceptingInvalidPackages() {
        Bundle values = new Bundle();
        values.putBoolean("hide_icons", true);
        values.putStringArrayList("hidden_packages",
                new java.util.ArrayList<>(Arrays.asList("com.example.good", "bad/package", "com..bad")));
        values.putInt("gesture_0_action", 99);
        values.putInt("gesture_1_action", DesktopConfig.ACTION_LAUNCH_APP);
        values.putString("gesture_1_target", "bad/package|bad.Activity");

        DesktopConfig actual = DesktopConfig.fromBundle(values);

        assertEquals(Set.of("com.example.good"), actual.hiddenPackages());
        assertEquals(DesktopConfig.ACTION_NATIVE,
                actual.gestureAction(DesktopConfig.GESTURE_SINGLE_DOWN));
        assertEquals(DesktopConfig.ACTION_NATIVE,
                actual.gestureAction(DesktopConfig.GESTURE_DOUBLE_DOWN));
        assertFalse(DesktopConfig.isValidPackageName("com..example"));
        assertTrue(DesktopConfig.isValidPackageName("com.example.app_1"));
    }

    @Test public void storeRoundTripsImmutableSnapshot() {
        DesktopConfig saved = DesktopConfig.defaults()
                .withHideIcons(true)
                .withHiddenPackages(Set.of("com.example.app"))
                .withGesture(DesktopConfig.GESTURE_SINGLE_UP,
                        DesktopConfig.ACTION_NONE);

        assertTrue(DesktopConfigStore.write(context, saved));
        assertEquals(saved, DesktopConfigStore.read(context));
    }

    @Test public void backHoldRoundTripsTargetAndKeepsExistingGestureAssignments() {
        DesktopConfig expected = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_SINGLE_DOWN, DesktopConfig.ACTION_NOTIFICATIONS)
                .withGesture(DesktopConfig.GESTURE_EXPAND, DesktopConfig.ACTION_RECENTS)
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_APP)
                .withGestureTarget(DesktopConfig.GESTURE_BACK_HOLD,
                        "com.example.app|com.example.app.Main", "Example");

        DesktopConfig fromBundle = DesktopConfig.fromBundle(expected.toBundle());
        assertEquals(expected, fromBundle);
        assertEquals(DesktopConfig.ACTION_LAUNCH_APP,
                fromBundle.gestureAction(DesktopConfig.GESTURE_BACK_HOLD));
        assertEquals("Example", fromBundle.gestureTargetLabel(DesktopConfig.GESTURE_BACK_HOLD));
        assertTrue(fromBundle.isGestureComplete(DesktopConfig.GESTURE_BACK_HOLD));
        assertTrue(DesktopConfigStore.write(context, expected));
        assertEquals(expected, DesktopConfigStore.read(context));
    }

    @Test public void preBackHoldSnapshotsDefaultNewGestureToNative() {
        Bundle oldSnapshot = new Bundle();
        oldSnapshot.putInt("desktop_schema_version", 2);
        oldSnapshot.putInt("gesture_0_action", DesktopConfig.ACTION_NOTIFICATIONS);
        oldSnapshot.putInt("gesture_9_action", DesktopConfig.ACTION_SCREENSHOT);

        DesktopConfig upgraded = DesktopConfig.fromBundle(oldSnapshot);
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS, upgraded.gestureAction(0));
        assertEquals(DesktopConfig.ACTION_SCREENSHOT, upgraded.gestureAction(9));
        assertEquals(DesktopConfig.ACTION_NATIVE,
                upgraded.gestureAction(DesktopConfig.GESTURE_BACK_HOLD));
        assertEquals(DesktopConfig.ACTION_NATIVE,
                DesktopConfig.defaults().gestureAction(DesktopConfig.GESTURE_BACK_HOLD));

        context.getSharedPreferences("desktop_settings_v1", android.content.Context.MODE_PRIVATE)
                .edit().putInt("desktop_schema_version", 2)
                .putInt("gesture_0_action", DesktopConfig.ACTION_NOTIFICATIONS)
                .putInt("gesture_9_action", DesktopConfig.ACTION_SCREENSHOT).commit();
        assertEquals(upgraded, DesktopConfigStore.read(context));
    }

    @Test public void backHoldToggleCanBeSavedAndResetToNative() {
        DesktopConfig selected = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_TOGGLE)
                .withGestureToggle(DesktopConfig.GESTURE_BACK_HOLD, 12);
        assertTrue(DesktopConfigStore.write(context, selected));
        assertEquals(12, DesktopConfigStore.read(context)
                .gestureToggle(DesktopConfig.GESTURE_BACK_HOLD));
        DesktopConfig reset = selected.withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                DesktopConfig.ACTION_NATIVE);
        assertTrue(DesktopConfigStore.write(context, reset));
        assertEquals(DesktopConfig.defaults(), DesktopConfigStore.read(context));
    }

    @Test public void oldSnapshotsAndStoreUseNativeDistanceAndFiveHundredMillisecondDelay() {
        DesktopConfig defaults = DesktopConfig.defaults();
        assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM, defaults.backHoldDistanceDp);
        assertEquals(DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS, defaults.backHoldDelayMs);
        Bundle old = new Bundle();
        old.putInt("desktop_schema_version", 2);
        old.putInt("gesture_10_action", DesktopConfig.ACTION_SCREENSHOT);
        DesktopConfig upgraded = DesktopConfig.fromBundle(old);
        assertEquals(0, upgraded.backHoldDistanceDp);
        assertEquals(500, upgraded.backHoldDelayMs);
        assertEquals(DesktopConfig.ACTION_SCREENSHOT,
                upgraded.gestureAction(DesktopConfig.GESTURE_BACK_HOLD));
        context.getSharedPreferences("desktop_settings_v1", android.content.Context.MODE_PRIVATE)
                .edit().putInt("desktop_schema_version", 2)
                .putInt("gesture_10_action", DesktopConfig.ACTION_SCREENSHOT).commit();
        assertEquals(upgraded, DesktopConfigStore.read(context));
    }

    @Test public void distanceAcceptsNativeOrClampsToTheSupportedDpRange() {
        DesktopConfig defaults = DesktopConfig.defaults();
        assertEquals(0, defaults.withBackHoldThresholds(-1, 500).backHoldDistanceDp);
        assertEquals(0, defaults.withBackHoldThresholds(Integer.MIN_VALUE, 500).backHoldDistanceDp);
        assertEquals(0, defaults.withBackHoldThresholds(0, 500).backHoldDistanceDp);
        assertEquals(16, defaults.withBackHoldThresholds(1, 500).backHoldDistanceDp);
        assertEquals(16, defaults.withBackHoldThresholds(16, 500).backHoldDistanceDp);
        assertEquals(72, defaults.withBackHoldThresholds(72, 500).backHoldDistanceDp);
        assertEquals(240, defaults.withBackHoldThresholds(240, 500).backHoldDistanceDp);
        assertEquals(240, defaults.withBackHoldThresholds(Integer.MAX_VALUE, 500).backHoldDistanceDp);
    }

    @Test public void delayClampsToTheSupportedMillisecondRangeWithoutRounding() {
        DesktopConfig defaults = DesktopConfig.defaults();
        assertEquals(200, defaults.withBackHoldThresholds(0, Integer.MIN_VALUE).backHoldDelayMs);
        assertEquals(200, defaults.withBackHoldThresholds(0, 199).backHoldDelayMs);
        assertEquals(200, defaults.withBackHoldThresholds(0, 200).backHoldDelayMs);
        assertEquals(735, defaults.withBackHoldThresholds(0, 735).backHoldDelayMs);
        assertEquals(3000, defaults.withBackHoldThresholds(0, 3000).backHoldDelayMs);
        assertEquals(3000, defaults.withBackHoldThresholds(0, Integer.MAX_VALUE).backHoldDelayMs);
    }

    @Test public void customThresholdsRoundTripInBundleStoreAndRuntimeSnapshot() {
        DesktopConfig selected = DesktopConfig.defaults()
                .withBackGesturePreset(DesktopConfig.BACK_STYLE_RAINBOW)
                .withGesture(DesktopConfig.GESTURE_SINGLE_UP, DesktopConfig.ACTION_NOTIFICATIONS)
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_SCREENSHOT)
                .withBackHoldThresholds(84, 735);
        Bundle encoded = selected.toBundle();
        assertEquals(84, encoded.getInt("back_hold_distance_dp"));
        assertEquals(735, encoded.getInt("back_hold_delay_ms"));
        assertEquals(selected, DesktopConfig.fromBundle(encoded));
        assertTrue(DesktopConfigStore.write(context, selected));
        assertEquals(selected, DesktopConfigStore.read(context));
        assertEquals(selected, DesktopConfig.readForRuntime(context));
        DesktopConfig restored = DesktopConfigStore.read(context);
        assertEquals(DesktopConfig.BACK_STYLE_RAINBOW, restored.backGestureStyle);
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS,
                restored.gestureAction(DesktopConfig.GESTURE_SINGLE_UP));
        assertEquals(DesktopConfig.ACTION_SCREENSHOT,
                restored.gestureAction(DesktopConfig.GESTURE_BACK_HOLD));
        assertTrue(DesktopConfigStore.write(context, selected.withBackHoldThresholds(0, 500)));
        assertEquals(0, DesktopConfigStore.read(context).backHoldDistanceDp);
        assertEquals(500, DesktopConfigStore.read(context).backHoldDelayMs);
    }

    @Test public void otherEditsPreserveBothCustomThresholds() {
        DesktopConfig original = DesktopConfig.defaults().withBackHoldThresholds(64, 850);
        for (DesktopConfig edited : new DesktopConfig[] {
                original.withHideIcons(true),
                original.withHiddenPackages(Set.of("com.example.hidden")),
                original.withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_APP),
                original.withGestureTarget(DesktopConfig.GESTURE_BACK_HOLD,
                        "com.example.app|com.example.app.Main", "Example"),
                original.withGestureToggle(DesktopConfig.GESTURE_SINGLE_UP, 12),
                original.withBackGestureStyle(DesktopConfig.BACK_STYLE_CAPSULE),
                original.withBackGestureColors(0x804488CC, 0x60332211),
                original.withBackGesturePreset(DesktopConfig.BACK_STYLE_RAINBOW),
                original.withBackGesturePreset(DesktopConfig.BACK_STYLE_NATIVE),
                original.withGesture(-1, DesktopConfig.ACTION_SCREENSHOT),
                original.withGestureTarget(-1, null, null),
                original.withGestureToggle(-1, 1)
        }) {
            assertEquals(64, edited.backHoldDistanceDp);
            assertEquals(850, edited.backHoldDelayMs);
            assertEquals(edited, DesktopConfig.fromBundle(edited.toBundle()));
        }
        assertEquals(64, original.backHoldDistanceDp);
        assertEquals(850, original.backHoldDelayMs);
        assertEquals(DesktopConfig.BACK_STYLE_NATIVE, original.backGestureStyle);
    }

    @Test public void thresholdValuesParticipateInSnapshotEqualityAndHashing() {
        DesktopConfig selected = DesktopConfig.defaults().withBackHoldThresholds(64, 850);
        DesktopConfig same = DesktopConfig.defaults().withBackHoldThresholds(64, 850);
        assertEquals(selected, same);
        assertEquals(selected.hashCode(), same.hashCode());
        assertFalse(selected.equals(selected.withBackHoldThresholds(65, 850)));
        assertFalse(selected.equals(selected.withBackHoldThresholds(64, 851)));
        assertEquals(DesktopConfig.defaults(), selected.withBackHoldThresholds(0, 500));
    }

    @Test public void externalThresholdValuesAreNormalizedBeforePersistence() {
        Bundle values = new Bundle();
        values.putInt("back_hold_distance_dp", 999);
        values.putInt("back_hold_delay_ms", -1);
        DesktopConfig normalized = DesktopConfig.fromBundle(values);
        assertEquals(240, normalized.backHoldDistanceDp);
        assertEquals(200, normalized.backHoldDelayMs);
        assertTrue(DesktopConfigStore.write(context, normalized));
        assertEquals(normalized, DesktopConfigStore.read(context));
    }

    @Test public void backAppearanceKeepsArgbColorsAcrossOtherConfigChanges() {
        DesktopConfig expected = DesktopConfig.defaults()
                .withBackGestureStyle(DesktopConfig.BACK_STYLE_RIPPLE)
                .withBackGestureColors(0x804488CC, 0x60332211)
                .withHideIcons(true)
                .withHiddenPackages(Set.of("com.example.hidden"))
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_APP)
                .withGestureTarget(DesktopConfig.GESTURE_BACK_HOLD,
                        "com.example.app|com.example.app.Main", "Example")
                .withGestureToggle(DesktopConfig.GESTURE_SINGLE_DOWN, 12);

        DesktopConfig decoded = DesktopConfig.fromBundle(expected.toBundle());
        assertEquals(expected, decoded);
        assertEquals(expected.hashCode(), decoded.hashCode());
        assertEquals(DesktopConfig.BACK_STYLE_RIPPLE, decoded.backGestureStyle);
        assertEquals(0x804488CC, decoded.backGestureBackgroundColor);
        assertEquals(0x60332211, decoded.backGestureArrowColor);
        assertTrue(DesktopConfigStore.write(context, expected));
        assertEquals(expected, DesktopConfigStore.read(context));
    }

    @Test public void oldAppearanceSnapshotsAndUnknownStylesKeepSystemDefault() {
        DesktopConfig old = DesktopConfig.fromBundle(new Bundle());
        assertEquals(DesktopConfig.BACK_STYLE_NATIVE, old.backGestureStyle);
        assertEquals(0xCC202124, old.backGestureBackgroundColor);
        assertEquals(0xFFFFFFFF, old.backGestureArrowColor);
        Bundle unknown = new Bundle();
        unknown.putInt("back_gesture_style", 99);
        assertEquals(DesktopConfig.BACK_STYLE_NATIVE,
                DesktopConfig.fromBundle(unknown).backGestureStyle);
        assertEquals(DesktopConfig.BACK_STYLE_NATIVE,
                old.withBackGestureStyle(-1).backGestureStyle);
    }

    @Test public void rainbowPresetRoundTripsWithCustomColorsAndBackHoldTarget() {
        DesktopConfig selected = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_APP)
                .withGestureTarget(DesktopConfig.GESTURE_BACK_HOLD,
                        "com.example.app|com.example.app.Main", "Example")
                .withBackGesturePreset(DesktopConfig.BACK_STYLE_RAINBOW);
        assertEquals(5, DesktopConfig.BACK_STYLE_RAINBOW);
        assertEquals(6, DesktopConfig.BACK_STYLE_COUNT);
        assertEquals(0xD94F6BEA, selected.backGestureBackgroundColor);
        assertEquals(0xFFFFFFFF, selected.backGestureArrowColor);
        assertEquals(selected, DesktopConfig.fromBundle(selected.toBundle()));
        assertTrue(DesktopConfigStore.write(context, selected));
        assertEquals(selected, DesktopConfigStore.read(context));

        DesktopConfig customized = selected.withBackGestureColors(0x804477CC, 0xCC332211)
                .withHideIcons(true);
        DesktopConfig restored = DesktopConfig.fromBundle(customized.toBundle());
        assertEquals(customized, restored);
        assertEquals(DesktopConfig.BACK_STYLE_RAINBOW, restored.backGestureStyle);
        assertEquals(0x804477CC, restored.backGestureBackgroundColor);
        assertEquals(0xCC332211, restored.backGestureArrowColor);
        assertEquals(DesktopConfig.ACTION_LAUNCH_APP,
                restored.gestureAction(DesktopConfig.GESTURE_BACK_HOLD));
        assertEquals("com.example.app|com.example.app.Main",
                restored.gestureTarget(DesktopConfig.GESTURE_BACK_HOLD));
        assertTrue(DesktopConfigStore.write(context, restored));
        assertEquals(restored, DesktopConfigStore.read(context));
        assertEquals(DesktopConfig.defaults()
                        .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_LAUNCH_APP)
                        .withGestureTarget(DesktopConfig.GESTURE_BACK_HOLD,
                                "com.example.app|com.example.app.Main", "Example")
                        .withHideIcons(true),
                restored.withBackGesturePreset(DesktopConfig.BACK_STYLE_NATIVE));
    }

    @Test public void nativePresetResetsAppearanceAndKeepsBackHoldAction() {
        DesktopConfig selected = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_SCREENSHOT)
                .withBackGesturePreset(DesktopConfig.BACK_STYLE_NEON)
                .withBackGestureColors(0x004488CC, 0x60332211);
        assertEquals(0x004488CC, selected.backGestureBackgroundColor);
        assertFalse(selected.equals(selected.withBackGestureColors(0xFF4488CC, 0x60332211)));
        DesktopConfig reset = selected.withBackGesturePreset(DesktopConfig.BACK_STYLE_NATIVE);
        assertEquals(DesktopConfig.defaults().withGesture(DesktopConfig.GESTURE_BACK_HOLD,
                DesktopConfig.ACTION_SCREENSHOT), reset);
        assertTrue(DesktopConfigStore.write(context, reset));
        assertEquals(reset, DesktopConfigStore.read(context));
    }

    @Test public void launcherCanReadOnlyTheDesktopEndpoint() {
        String ownPackage = BuildConfig.APPLICATION_ID;
        FusionConfigProvider.enforceDesktopReadCaller(ownPackage, ownPackage);
        FusionConfigProvider.enforceDesktopReadCaller("com.miui.home", ownPackage);
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceDesktopReadCaller("com.android.systemui", ownPackage));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceDesktopReadCaller("malicious.app", ownPackage));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceCaller("com.miui.home", ownPackage, true));
    }

    @Test public void mapsOnlySupportedVerticalSwipes() {
        assertEquals(DesktopConfig.GESTURE_SINGLE_DOWN,
                DesktopConfig.gestureForVerticalDirection(11, 1));
        assertEquals(DesktopConfig.GESTURE_DOUBLE_DOWN,
                DesktopConfig.gestureForVerticalDirection(11, 2));
        assertEquals(DesktopConfig.GESTURE_SINGLE_UP,
                DesktopConfig.gestureForVerticalDirection(10, 1));
        assertEquals(DesktopConfig.GESTURE_DOUBLE_UP,
                DesktopConfig.gestureForVerticalDirection(10, 2));
        assertEquals(-1, DesktopConfig.gestureForVerticalDirection(11, 3));
        assertEquals(-1, DesktopConfig.gestureForVerticalDirection(9, 1));
        assertEquals(DesktopConfig.GESTURE_PINCH, DesktopConfig.gestureForPinch(0.6f));
        assertEquals(DesktopConfig.GESTURE_EXPAND, DesktopConfig.gestureForPinch(-0.6f));
        assertEquals(-1, DesktopConfig.gestureForPinch(0.01f));
    }

    @Test public void launcherActionsUseStableReferenceIdsAndRequireTargets() {
        assertTrue(DesktopConfig.isSupportedAction(DesktopConfig.ACTION_NONE));
        assertTrue(DesktopConfig.isSupportedAction(DesktopConfig.ACTION_SIDEBAR));
        assertTrue(DesktopConfig.isSupportedAction(DesktopConfig.ACTION_LAUNCH_ACTIVITY));
        assertFalse(DesktopConfig.isSupportedAction(11));
        DesktopConfig activity = DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_DOUBLE_TAP, DesktopConfig.ACTION_LAUNCH_ACTIVITY)
                .withGestureTarget(DesktopConfig.GESTURE_DOUBLE_TAP, "com.example.app|",
                        "Example");
        assertFalse(activity.isGestureComplete(DesktopConfig.GESTURE_DOUBLE_TAP));
        activity = activity.withGestureTarget(DesktopConfig.GESTURE_DOUBLE_TAP,
                "com.example.app|com.example.app.Detail", "Example · Detail");
        assertTrue(activity.isGestureComplete(DesktopConfig.GESTURE_DOUBLE_TAP));
    }

    @Test public void actionCatalogMatchesLauncherReferenceIdsAndOrder() {
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 7, 12, 26, 8, 9, 20, 10, 13, 22, 23},
                context.getResources().getIntArray(R.array.desktop_gesture_action_values));
        assertArrayEquals(new String[] {
                "无动作", "展开通知中心", "展开控制中心", "锁定设备", "熄屏", "截图", "打开最近",
                "打开电源菜单", "展开侧边栏", "启动应用", "启动快捷方式", "启动活动", "切换功能",
                "清理内存", "单手模式", "清理通知"
        }, context.getResources().getStringArray(R.array.desktop_gesture_actions));
        assertEquals(12, context.getResources().getIntArray(R.array.desktop_toggle_values).length);
    }

    @Test public void desktopActionCommandsAreValidatedBeforeBroadcast() {
        FusionConfigProvider.enforceLauncherCaller("com.miui.home");
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceLauncherCaller("malicious.app"));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceLauncherCaller(BuildConfig.APPLICATION_ID));
        assertTrue(DesktopActionQueue.isValidCommand(DesktopConfig.ACTION_NOTIFICATIONS, null, 1));
        assertTrue(DesktopActionQueue.isValidCommand(DesktopConfig.ACTION_TOGGLE, null,
                DesktopConfig.TOGGLE_COUNT));
        assertFalse(DesktopActionQueue.isValidCommand(DesktopConfig.ACTION_TOGGLE, null,
                DesktopConfig.TOGGLE_COUNT + 1));
    }

    @Test public void upgradePreservesOldNotificationAndApplicationAssignments() {
        context.getSharedPreferences("desktop_settings_v1", android.content.Context.MODE_PRIVATE)
                .edit().putInt("gesture_0_action", 1).putInt("gesture_1_action", 2)
                .putInt("gesture_2_action", 4).putString("gesture_2_package", "com.example.app")
                .putBoolean("hide_icons", true).putStringSet("hidden_packages", Set.of("com.example.app"))
                .commit();
        DesktopConfig upgraded = DesktopConfigStore.read(context);
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS, upgraded.gestureAction(0));
        assertEquals(DesktopConfig.ACTION_QUICK_SETTINGS, upgraded.gestureAction(1));
        assertEquals(DesktopConfig.ACTION_LAUNCH_APP, upgraded.gestureAction(2));
        assertEquals("com.example.app", upgraded.gestureTarget(2));
        assertTrue(upgraded.hidesPackage("com.example.app"));
        assertTrue(DesktopConfigStore.write(context, upgraded));
        assertEquals(upgraded, DesktopConfigStore.read(context));
    }

}

