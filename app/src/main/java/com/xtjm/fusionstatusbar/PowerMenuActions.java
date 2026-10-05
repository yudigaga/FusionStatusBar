package com.xtjm.fusionstatusbar;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.PowerManager;
import android.provider.Settings;
import java.lang.reflect.Method;

/** Side-track actions have explicit meanings, independent of OEM action-number assignments. */
final class PowerMenuActions {
    enum Action { BOOTLOADER, RECOVERY, AIRPLANE, SILENT }

    interface Operations {
        void reboot(String reason) throws Exception;
        boolean airplane();
        void airplane(boolean value) throws Exception;
        boolean silent();
        void silent(boolean value) throws Exception;
    }

    private PowerMenuActions() { }

    static void execute(Action action, Operations operations) throws Exception {
        switch (action) {
            case BOOTLOADER -> operations.reboot("bootloader");
            case RECOVERY -> operations.reboot("recovery");
            case AIRPLANE -> operations.airplane(!operations.airplane());
            case SILENT -> operations.silent(!operations.silent());
        }
    }

    static boolean restricted(Context context) {
        if (context == null) return true;
        if (Settings.Global.getInt(context.getContentResolver(), "com.xiaomi.system.devicelock.locked", 0) != 0)
            return true;
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return keyguard != null && keyguard.isDeviceLocked()
                && Settings.Secure.getInt(context.getContentResolver(), "miui_shut_down_password_enabled", 0) == 1;
    }

    static Operations platform(Context context) {
        return new Operations() {
            // This runs in SystemUI, whose signature permission is checked before the call.
            @android.annotation.SuppressLint("MissingPermission")
            @Override public void reboot(String reason) {
                if (context.checkSelfPermission(android.Manifest.permission.REBOOT)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("systemui_reboot_permission_missing");
                PowerManager power = context.getSystemService(PowerManager.class);
                if (power == null) throw new IllegalStateException("power_service_unavailable");
                power.reboot(reason);
            }

            @Override public boolean airplane() {
                return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            }

            @Override public void airplane(boolean value) throws Exception {
                Object connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Method setter = connectivity == null ? null : RuntimeReflection.exact(
                        connectivity.getClass(), "setAirplaneMode", new Class<?>[] {boolean.class}, false);
                if (setter != null) { setter.invoke(connectivity, value); return; }
                boolean previous = airplane();
                if (!Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, value ? 1 : 0))
                    throw new IllegalStateException("airplane_setting_rejected");
                try {
                    context.sendBroadcast(new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", value));
                } catch (RuntimeException error) {
                    Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, previous ? 1 : 0);
                    throw error;
                }
            }

            @Override public boolean silent() {
                AudioManager audio = context.getSystemService(AudioManager.class);
                return audio != null && audio.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
            }

            @Override public void silent(boolean value) {
                AudioManager audio = context.getSystemService(AudioManager.class);
                if (audio == null) throw new IllegalStateException("audio_service_unavailable");
                audio.setRingerMode(value ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL);
            }
        };
    }
}
