package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.app.BroadcastOptions;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class DesktopActionBridgeTest {
    @Before public void clearBridgeToken() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("desktop_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.createDeviceProtectedStorageContext().getSharedPreferences("desktop_settings_v1",
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void gestureDispatchTargetsSystemUiWithValidatedCommand() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        DesktopActionBridgeToken.cacheHostToken(context, "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY");
        Intent intent = DesktopActionQueue.dispatchIntent(context,
                DesktopConfig.ACTION_NOTIFICATIONS, null, 1);

        assertNotNull(intent);
        assertEquals(DesktopActionQueue.ACTION_DISPATCH, intent.getAction());
        assertEquals("com.android.systemui", intent.getPackage());
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS,
                intent.getIntExtra(DesktopActionQueue.KEY_ACTION, -1));
        assertTrue(intent.hasExtra(DesktopActionQueue.KEY_SIGNATURE));
    }

    @Test public void invalidAndIncompleteCommandsCannotEnterSystemUi() {
        assertNull(DesktopActionQueue.dispatchIntent(null, DesktopConfig.ACTION_NATIVE, null, 1));
        assertNull(DesktopActionQueue.dispatchIntent(null, DesktopConfig.ACTION_TOGGLE, null,
                DesktopConfig.TOGGLE_COUNT + 1));
        assertNull(DesktopActionQueue.dispatchIntent(null, DesktopConfig.ACTION_LAUNCH_APP, null, 1));
    }

    @Test public void noHostTokenMeansNoProviderOrBroadcastFallback() {
        Intent intent = DesktopActionQueue.dispatchIntent(RuntimeEnvironment.getApplication(),
                DesktopConfig.ACTION_NOTIFICATIONS, null, 1);
        assertNull(intent);
    }

    @Test public void api34DispatchSharesTheLauncherIdentityWithSystemUi() {
        Context app = RuntimeEnvironment.getApplication();
        DesktopActionBridgeToken.cacheHostToken(app,
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY");
        Bundle[] options = {null};
        Context launcher = new ContextWrapper(app) {
            @Override public void sendBroadcast(Intent intent, String permission, Bundle bundle) {
                options[0] = bundle;
            }
        };

        assertTrue(DesktopActionQueue.enqueue(launcher, DesktopConfig.ACTION_SCREENSHOT, null, 1));
        assertNotNull(options[0]);
        assertTrue(BroadcastOptions.fromBundle(options[0]).isShareIdentityEnabled());
    }

    @Test public void onlyTheLauncherUidIsTrustedForTheExportedReceiver() {
        assertTrue(DesktopSystemActionReceiver.isTrustedLauncherUid(10234,
                new String[] {"com.miui.home"}));
        assertFalse(DesktopSystemActionReceiver.isTrustedLauncherUid(10234,
                new String[] {"com.example.other"}));
        assertFalse(DesktopSystemActionReceiver.isTrustedLauncherUid(-1,
                new String[] {"com.miui.home"}));
        assertFalse(DesktopSystemActionReceiver.isTrustedLauncherUid(10234, null));
    }

    @Test public void receiverRejectsBroadcastsWithoutTheSystemUiTarget() {
        Context context = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(DesktopActionQueue.ACTION_DISPATCH)
                .putExtra(DesktopActionQueue.KEY_ACTION, DesktopConfig.ACTION_NOTIFICATIONS);

        assertFalse(DesktopSystemActionReceiver.receiveLauncherAction(context, intent, 10234));
    }

    @Test @Config(sdk = 33)
    public void authenticatedActionRunsWithoutAProviderQueueOnApi33() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        DesktopActionBridgeToken.cacheHostToken(context,
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY");
        Intent intent = DesktopActionQueue.dispatchIntent(context,
                DesktopConfig.ACTION_SCREENSHOT, null, 1);

        assertTrue(DesktopSystemActionReceiver.receiveLauncherAction(context, intent, -1));
        assertFalse("A signed command may only be used once",
                DesktopSystemActionReceiver.receiveLauncherAction(context, intent, -1));
        Intent sent = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getBroadcastIntents().get(0);
        assertEquals("SYSTEM_ACTION_TAKE_SCREENSHOT", sent.getAction());
        assertEquals("com.android.systemui", sent.getPackage());
    }

    @Test public void api34ReceiverAlsoRequiresTheLauncherPackageUid() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        DesktopActionBridgeToken.cacheHostToken(context,
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY");
        Intent intent = DesktopActionQueue.dispatchIntent(context,
                DesktopConfig.ACTION_SCREENSHOT, null, 1);

        assertFalse(DesktopSystemActionReceiver.receiveLauncherAction(context, intent, 10234));
    }
}
