package com.xtjm.fusionstatusbar;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Native system surfaces controlled by the shared appearance settings. */
final class SystemSurfaceConfig {
    static final String KEY_POWER_MENU = "system_surface_power_menu";
    static final String KEY_VOLUME_DIALOG = "system_surface_volume_dialog";
    static final String KEY_NOTIFICATIONS = "system_surface_notifications";
    static final String KEY_HEADS_UP = "system_surface_heads_up";
    static final String KEY_EXTENDED_POWER_MENU = "system_surface_extended_power_menu";
    static final String KEY_COMPACT_VOLUME = "system_surface_compact_volume";

    final boolean powerMenu;
    final boolean volumeDialog;
    final boolean notifications;
    final boolean headsUp;
    final boolean extendedPowerMenu;
    final boolean compactVolume;

    private SystemSurfaceConfig(boolean powerMenu, boolean volumeDialog, boolean notifications,
            boolean headsUp, boolean extendedPowerMenu, boolean compactVolume) {
        this.powerMenu = powerMenu;
        this.volumeDialog = volumeDialog;
        this.notifications = notifications;
        this.headsUp = headsUp;
        this.extendedPowerMenu = extendedPowerMenu;
        this.compactVolume = compactVolume;
    }

    static SystemSurfaceConfig defaults() {
        return new SystemSurfaceConfig(true, true, true, true, false, false);
    }

    static SystemSurfaceConfig fromBundle(Bundle values) {
        if (values == null) return defaults();
        return new SystemSurfaceConfig(values.getBoolean(KEY_POWER_MENU, true),
                values.getBoolean(KEY_VOLUME_DIALOG, true),
                values.getBoolean(KEY_NOTIFICATIONS, true),
                values.getBoolean(KEY_HEADS_UP, true),
                values.getBoolean(KEY_EXTENDED_POWER_MENU, false),
                values.getBoolean(KEY_COMPACT_VOLUME, false));
    }

    static SystemSurfaceConfig read(SharedPreferences values) {
        if (values == null) return defaults();
        return new SystemSurfaceConfig(values.getBoolean(KEY_POWER_MENU, true),
                values.getBoolean(KEY_VOLUME_DIALOG, true),
                values.getBoolean(KEY_NOTIFICATIONS, true),
                values.getBoolean(KEY_HEADS_UP, true),
                values.getBoolean(KEY_EXTENDED_POWER_MENU, false),
                values.getBoolean(KEY_COMPACT_VOLUME, false));
    }

    void writeTo(Bundle values) {
        values.putBoolean(KEY_POWER_MENU, powerMenu);
        values.putBoolean(KEY_VOLUME_DIALOG, volumeDialog);
        values.putBoolean(KEY_NOTIFICATIONS, notifications);
        values.putBoolean(KEY_HEADS_UP, headsUp);
        values.putBoolean(KEY_EXTENDED_POWER_MENU, extendedPowerMenu);
        values.putBoolean(KEY_COMPACT_VOLUME, compactVolume);
    }

    void writeTo(SharedPreferences.Editor editor) {
        editor.putBoolean(KEY_POWER_MENU, powerMenu);
        editor.putBoolean(KEY_VOLUME_DIALOG, volumeDialog);
        editor.putBoolean(KEY_NOTIFICATIONS, notifications);
        editor.putBoolean(KEY_HEADS_UP, headsUp);
        editor.putBoolean(KEY_EXTENDED_POWER_MENU, extendedPowerMenu);
        editor.putBoolean(KEY_COMPACT_VOLUME, compactVolume);
    }

    SystemSurfaceConfig withPowerMenu(boolean value) {
        return new SystemSurfaceConfig(value, volumeDialog, notifications, headsUp,
                extendedPowerMenu, compactVolume);
    }

    SystemSurfaceConfig withVolumeDialog(boolean value) {
        return new SystemSurfaceConfig(powerMenu, value, notifications, headsUp,
                extendedPowerMenu, compactVolume);
    }

    SystemSurfaceConfig withNotifications(boolean value) {
        return new SystemSurfaceConfig(powerMenu, volumeDialog, value, headsUp,
                extendedPowerMenu, compactVolume);
    }

    SystemSurfaceConfig withHeadsUp(boolean value) {
        return new SystemSurfaceConfig(powerMenu, volumeDialog, notifications, value,
                extendedPowerMenu, compactVolume);
    }

    SystemSurfaceConfig withExtendedPowerMenu(boolean value) {
        return new SystemSurfaceConfig(powerMenu, volumeDialog, notifications, headsUp,
                value, compactVolume);
    }

    SystemSurfaceConfig withCompactVolume(boolean value) {
        return new SystemSurfaceConfig(powerMenu, volumeDialog, notifications, headsUp,
                extendedPowerMenu, value);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SystemSurfaceConfig value)) return false;
        return powerMenu == value.powerMenu && volumeDialog == value.volumeDialog
                && notifications == value.notifications && headsUp == value.headsUp
                && extendedPowerMenu == value.extendedPowerMenu
                && compactVolume == value.compactVolume;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(powerMenu, volumeDialog, notifications, headsUp,
                extendedPowerMenu, compactVolume);
    }
}
