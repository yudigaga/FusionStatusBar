package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Shared only with injected hosts; signs launcher-to-SystemUI action broadcasts. */
final class DesktopActionBridgeToken {
    static final String KEY_TOKEN = "desktop_action_bridge_token";
    private static final String PREFS = "desktop_settings_v1";
    private static final long MAX_AGE_MS = 5_000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Long> USED_NONCES = new ConcurrentHashMap<>();

    private DesktopActionBridgeToken() { }

    static void appendModuleToken(Context context, Bundle values) {
        if (context != null && values != null) values.putString(KEY_TOKEN, moduleToken(context));
    }

    static boolean cacheHostToken(Context context, Bundle values) {
        return values != null && cacheHostToken(context, values.getString(KEY_TOKEN));
    }

    static boolean cacheHostToken(Context context, String token) {
        byte[] secret = decode(token);
        if (context == null || secret == null || secret.length != 32) return false;
        return preferences(context).edit()
                .putString(KEY_TOKEN, token).commit();
    }

    static String cachedToken(Context context) {
        if (context == null) return null;
        String token = preferences(context).getString(KEY_TOKEN, null);
        byte[] secret = decode(token);
        return secret != null && secret.length == 32 ? token : null;
    }

    static String sign(Context context, int action, String target, int toggle,
            long timestamp, String nonce) {
        String token = cachedToken(context);
        if (token == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(decode(token), "HmacSHA256"));
            return Base64.encodeToString(mac.doFinal(payload(action, target, toggle,
                    timestamp, nonce)), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (Exception error) {
            Log.w("FusionStatusBar", "desktop action signing failed: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean verify(Context context, int action, String target, int toggle,
            long timestamp, String nonce, String signature) {
        long now = SystemClock.elapsedRealtime();
        if (timestamp <= 0L || timestamp > now || now - timestamp > MAX_AGE_MS
                || nonce == null || nonce.length() < 16 || nonce.length() > 64
                || signature == null) return false;
        String expected = sign(context, action, target, toggle, timestamp, nonce);
        byte[] expectedBytes = decode(expected);
        byte[] actualBytes = decode(signature);
        if (expectedBytes == null || actualBytes == null
                || !MessageDigest.isEqual(expectedBytes, actualBytes)) return false;
        USED_NONCES.entrySet().removeIf(entry -> now - entry.getValue() > MAX_AGE_MS);
        return USED_NONCES.size() < 512 && USED_NONCES.putIfAbsent(nonce, now) == null;
    }

    private static synchronized String moduleToken(Context context) {
        SharedPreferences preferences = preferences(context);
        String existing = preferences.getString(KEY_TOKEN, null);
        byte[] decoded = decode(existing);
        if (decoded != null && decoded.length == 32) return existing;
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String created = Base64.encodeToString(secret,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return preferences.edit().putString(KEY_TOKEN, created).commit() ? created : null;
    }

    private static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static byte[] payload(int action, String target, int toggle, long timestamp,
            String nonce) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(action);
            output.writeBoolean(target != null);
            if (target != null) output.writeUTF(target);
            output.writeInt(toggle);
            output.writeLong(timestamp);
            output.writeUTF(nonce);
        }
        return bytes.toByteArray();
    }

    private static byte[] decode(String value) {
        if (value == null || value.length() > 128) return null;
        try { return Base64.decode(value, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
