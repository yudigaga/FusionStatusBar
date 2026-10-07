package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small authenticated queue that transfers launcher gestures to privileged SystemUI. */
final class DesktopActionQueue {
    static final String METHOD_ENQUEUE = "desktop_action_enqueue";
    static final String METHOD_TAKE = "desktop_action_take";
    static final String KEY_ACTION = "desktop_action";
    static final String KEY_TARGET = "desktop_target";
    static final String KEY_TOGGLE = "desktop_toggle";
    static final String KEY_ACCEPTED = "desktop_action_accepted";
    private static final String URI_STRING = "content://" + BuildConfig.APPLICATION_ID
            + ".config/desktop_action";
    private static final String PREFS = "desktop_settings_v1";
    private static final String KEY_QUEUE = "pending_desktop_actions";
    private static final int MAX_QUEUED_ACTIONS = 16;
    static final long MAX_COMMAND_AGE_MS = 5000L;
    private static final String KEY_CREATED = "created_at";
    private static final Object LOCK = new Object();

    private DesktopActionQueue() { }

    static Uri contentUri() { return Uri.parse(URI_STRING); }

    static boolean enqueue(Context context, int action, String target, int toggle) {
        if (context == null || !DesktopConfig.isSupportedAction(action)
                || action == DesktopConfig.ACTION_NATIVE || action == DesktopConfig.ACTION_NONE) {
            return false;
        }
        Bundle values = new Bundle();
        values.putInt(KEY_ACTION, action);
        values.putString(KEY_TARGET, target);
        values.putInt(KEY_TOGGLE, toggle);
        try {
            Bundle result = context.getContentResolver().call(contentUri(), METHOD_ENQUEUE,
                    null, values);
            return result != null && result.getBoolean(KEY_ACCEPTED, false);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop action enqueue failed", error);
            return false;
        }
    }

    static Bundle enqueueFromProvider(Context context, Bundle request) {
        if (context == null || request == null) return accepted(false);
        int action = request.getInt(KEY_ACTION, DesktopConfig.ACTION_NATIVE);
        if (!DesktopConfig.isSupportedAction(action) || action == DesktopConfig.ACTION_NATIVE
                || action == DesktopConfig.ACTION_NONE) return accepted(false);
        String target = request.getString(KEY_TARGET);
        int toggle = request.getInt(KEY_TOGGLE, 1);
        if ((action == DesktopConfig.ACTION_LAUNCH_APP
                || action == DesktopConfig.ACTION_LAUNCH_SHORTCUT
                || action == DesktopConfig.ACTION_LAUNCH_ACTIVITY)
                && (target == null || target.isEmpty() || target.length() > 8192)) {
            return accepted(false);
        }
        DesktopConfig validated = DesktopConfig.defaults().withGesture(0, action)
                .withGestureTarget(0, target, null).withGestureToggle(0, toggle);
        if (validated.gestureAction(0) != action || !validated.isGestureComplete(0)) {
            return accepted(false);
        }
        if (action == DesktopConfig.ACTION_TOGGLE
                && (toggle < 1 || toggle > DesktopConfig.TOGGLE_COUNT)) return accepted(false);
        JSONObject command = new JSONObject();
        try {
            command.put(KEY_ACTION, action);
            command.put(KEY_TARGET, target == null ? JSONObject.NULL : target);
            command.put(KEY_TOGGLE, toggle);
            command.put(KEY_CREATED, System.currentTimeMillis());
        } catch (JSONException error) {
            return accepted(false);
        }
        synchronized (LOCK) {
            JSONArray queue = readQueue(context);
            if (queue.length() >= MAX_QUEUED_ACTIONS) queue.remove(0);
            queue.put(command);
            if (!writeQueue(context, queue)) return accepted(false);
        }
        context.getContentResolver().notifyChange(contentUri(), null);
        return accepted(true);
    }

    static Bundle takeFromProvider(Context context) {
        if (context == null) return null;
        synchronized (LOCK) {
            JSONArray queue = readQueue(context);
            JSONObject command = null;
            long now = System.currentTimeMillis();
            while (queue.length() > 0) {
                JSONObject candidate = queue.optJSONObject(0);
                queue.remove(0);
                long created = candidate == null ? 0L : candidate.optLong(KEY_CREATED, 0L);
                if (created > 0L && created <= now && now - created <= MAX_COMMAND_AGE_MS) {
                    command = candidate;
                    break;
                }
            }
            if (!writeQueue(context, queue) || command == null) return null;
            Bundle result = new Bundle();
            result.putInt(KEY_ACTION, command.optInt(KEY_ACTION, DesktopConfig.ACTION_NATIVE));
            Object target = command.opt(KEY_TARGET);
            if (target instanceof String value) result.putString(KEY_TARGET, value);
            result.putInt(KEY_TOGGLE, command.optInt(KEY_TOGGLE, 1));
            return result;
        }
    }

    static Command take(Context systemUiContext) {
        if (systemUiContext == null) return null;
        try {
            Bundle result = systemUiContext.getContentResolver().call(contentUri(), METHOD_TAKE,
                    null, null);
            if (result == null) return null;
            return new Command(result.getInt(KEY_ACTION, DesktopConfig.ACTION_NATIVE),
                    result.getString(KEY_TARGET), result.getInt(KEY_TOGGLE, 1));
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop action dequeue failed", error);
            return null;
        }
    }

    private static JSONArray readQueue(Context context) {
        String encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_QUEUE, "[]");
        try { return new JSONArray(encoded); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    private static boolean writeQueue(Context context, JSONArray queue) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_QUEUE, queue.toString()).commit();
    }

    private static Bundle accepted(boolean value) {
        Bundle result = new Bundle();
        result.putBoolean(KEY_ACCEPTED, value);
        return result;
    }

    static final class Command {
        final int action;
        final String target;
        final int toggle;

        Command(int action, String target, int toggle) {
            this.action = action;
            this.target = target;
            this.toggle = toggle;
        }
    }
}
