package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.Intent;
import android.app.BroadcastOptions;
import android.os.Build;
import android.util.Log;

/** Authenticated one-shot bridge from Launcher gestures to privileged SystemUI actions. */
final class DesktopActionQueue {
    static final String ACTION_DISPATCH = BuildConfig.APPLICATION_ID + ".action.DESKTOP_GESTURE";
    static final String KEY_ACTION = "desktop_action";
    static final String KEY_TARGET = "desktop_target";
    static final String KEY_TOGGLE = "desktop_toggle";
    static final String KEY_TIMESTAMP = "desktop_action_timestamp";
    static final String KEY_NONCE = "desktop_action_nonce";
    static final String KEY_SIGNATURE = "desktop_action_signature";

    private DesktopActionQueue() { }

    static boolean enqueue(Context context, int action, String target, int toggle) {
        if (context == null) return false;
        Intent intent = dispatchIntent(context, action, target, toggle);
        if (intent == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                context.sendBroadcast(intent, null, BroadcastOptions.makeBasic()
                        .setShareIdentityEnabled(true).toBundle());
            } else {
                context.sendBroadcast(intent);
            }
            return true;
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop action broadcast failed", error);
            return false;
        }
    }

    static Intent dispatchIntent(Context context, int action, String target, int toggle) {
        if (!isValidCommand(action, target, toggle)) return null;
        long timestamp = android.os.SystemClock.elapsedRealtime();
        String nonce = java.util.UUID.randomUUID().toString();
        String signature = DesktopActionBridgeToken.sign(context, action, target, toggle,
                timestamp, nonce);
        if (signature == null) return null;
        return new Intent(ACTION_DISPATCH).setPackage("com.android.systemui")
                .putExtra(KEY_ACTION, action).putExtra(KEY_TARGET, target)
                .putExtra(KEY_TOGGLE, toggle).putExtra(KEY_TIMESTAMP, timestamp)
                .putExtra(KEY_NONCE, nonce).putExtra(KEY_SIGNATURE, signature);
    }

    static boolean isValidCommand(int action, String target, int toggle) {
        if (!DesktopConfig.isSupportedAction(action) || action == DesktopConfig.ACTION_NATIVE
                || action == DesktopConfig.ACTION_NONE) return false;
        if ((action == DesktopConfig.ACTION_LAUNCH_APP
                || action == DesktopConfig.ACTION_LAUNCH_SHORTCUT
                || action == DesktopConfig.ACTION_LAUNCH_ACTIVITY)
                && (target == null || target.isEmpty() || target.length() > 8192)) return false;
        if (action == DesktopConfig.ACTION_TOGGLE
                && (toggle < 1 || toggle > DesktopConfig.TOGGLE_COUNT)) return false;
        DesktopConfig validated = DesktopConfig.defaults().withGesture(0, action)
                .withGestureTarget(0, target, null).withGestureToggle(0, toggle);
        return validated.gestureAction(0) == action && validated.isGestureComplete(0);
    }
}
