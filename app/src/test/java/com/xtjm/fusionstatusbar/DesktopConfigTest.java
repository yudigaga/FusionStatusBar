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

    @Test public void actionQueueAllowsOnlyLauncherRequestsAndPreservesOrder() {
        FusionConfigProvider.enforceLauncherCaller("com.miui.home");
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceLauncherCaller("malicious.app"));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceLauncherCaller(BuildConfig.APPLICATION_ID));
        Bundle first = new Bundle();
        first.putInt(DesktopActionQueue.KEY_ACTION, DesktopConfig.ACTION_NOTIFICATIONS);
        assertTrue(DesktopActionQueue.enqueueFromProvider(context, first)
                .getBoolean(DesktopActionQueue.KEY_ACCEPTED));
        Bundle second = new Bundle();
        second.putInt(DesktopActionQueue.KEY_ACTION, DesktopConfig.ACTION_TOGGLE);
        second.putInt(DesktopActionQueue.KEY_TOGGLE, 12);
        assertTrue(DesktopActionQueue.enqueueFromProvider(context, second)
                .getBoolean(DesktopActionQueue.KEY_ACCEPTED));
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS,
                DesktopActionQueue.takeFromProvider(context).getInt(DesktopActionQueue.KEY_ACTION));
        Bundle takenToggle = DesktopActionQueue.takeFromProvider(context);
        assertEquals(DesktopConfig.ACTION_TOGGLE, takenToggle.getInt(DesktopActionQueue.KEY_ACTION));
        assertEquals(12, takenToggle.getInt(DesktopActionQueue.KEY_TOGGLE));
        assertEquals(null, DesktopActionQueue.takeFromProvider(context));
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

    @Test public void queuedCommandsExpireInsteadOfExecutingAfterSystemUiRestarts() {
        long expiredAt = System.currentTimeMillis() - DesktopActionQueue.MAX_COMMAND_AGE_MS - 1L;
        String expired = "[{\"desktop_action\":4,\"created_at\":" + expiredAt + "}]";
        context.getSharedPreferences("desktop_settings_v1", android.content.Context.MODE_PRIVATE)
                .edit().putString("pending_desktop_actions", expired).commit();
        assertEquals(null, DesktopActionQueue.takeFromProvider(context));
        assertEquals("[]", context.getSharedPreferences("desktop_settings_v1", android.content.Context.MODE_PRIVATE)
                .getString("pending_desktop_actions", "missing"));
    }
}

