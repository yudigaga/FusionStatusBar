package com.xtjm.fusionstatusbar;

import android.content.ContentResolver;
import android.provider.Settings;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSettings;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = ScreenshotClipboardApplyTest.SecureWriteBoundary.class)
public class ScreenshotClipboardApplyTest {
    private static final String KEY = "mi_screen_shots_write_clipboard_enable";

    @Before public void resetBoundary() {
        SecureWriteBoundary.values.clear();
        SecureWriteBoundary.persistWrites = true;
        SecureWriteBoundary.writes = 0;
    }

    @Test public void acceptedRpcWithoutPersistenceIsNotReportedAsApplied() throws Exception {
        SecureWriteBoundary.values.put(10, "0");
        SecureWriteBoundary.persistWrites = false;

        assertFalse("a successful RPC does not prove the setting changed",
                ScreenshotClipboardHooks.applyForUser(resolver(), 10, true));
        assertEquals("0", SecureWriteBoundary.values.get(10));
        assertEquals(1, SecureWriteBoundary.writes);
    }

    @Test public void enablingAndDisablingUseTheRequestedUserAndReadTheStoredValue() throws Exception {
        SecureWriteBoundary.values.put(0, "0");
        SecureWriteBoundary.values.put(10, "0");

        assertTrue(ScreenshotClipboardHooks.applyForUser(resolver(), 10, true));
        assertEquals("1", SecureWriteBoundary.values.get(10));
        assertEquals("0", SecureWriteBoundary.values.get(0));

        assertTrue(ScreenshotClipboardHooks.applyForUser(resolver(), 10, false));
        assertEquals("0", SecureWriteBoundary.values.get(10));
        assertEquals("0", SecureWriteBoundary.values.get(0));
        assertEquals(2, SecureWriteBoundary.writes);
    }

    @Test public void matchingValuesDoNotCreateAnotherWrite() throws Exception {
        SecureWriteBoundary.values.put(10, "1");
        SecureWriteBoundary.values.put(0, "0");

        assertTrue(ScreenshotClipboardHooks.applyForUser(resolver(), 10, true));
        assertTrue(ScreenshotClipboardHooks.applyForUser(resolver(), 0, false));
        assertEquals(0, SecureWriteBoundary.writes);
    }

    private static ContentResolver resolver() {
        return RuntimeEnvironment.getApplication().getContentResolver();
    }

    /** Models Settings' successful Binder RPC independently of provider persistence. */
    @Implements(Settings.Secure.class)
    public static class SecureWriteBoundary extends ShadowSettings.ShadowSecure {
        static final Map<Integer, String> values = new HashMap<>();
        static boolean persistWrites = true;
        static int writes;

        @Implementation
        protected static String getStringForUser(ContentResolver resolver, String name, int user) {
            if (!KEY.equals(name)) return ShadowSettings.ShadowSecure.getStringForUser(resolver, name, user);
            return values.get(user);
        }

        @Implementation
        protected static boolean putStringForUser(ContentResolver resolver, String name, String value, int user) {
            if (!KEY.equals(name)) return ShadowSettings.ShadowSecure.putString(resolver, name, value);
            writes++;
            if (persistWrites) values.put(user, value);
            return true;
        }
    }
}
