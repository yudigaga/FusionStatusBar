package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Shortcut keys are queried and launched by the host launcher, which holds shortcut access. */
final class DesktopShortcutCatalog {
    static final String METHOD_GET = "desktop_shortcuts_get";
    static final String METHOD_REQUEST = "desktop_shortcuts_request";
    static final String METHOD_PUBLISH = "desktop_shortcuts_publish";
    static final String KEY_CATALOG = "desktop_shortcuts";
    private static final int MAX_SHORTCUTS = 512;

    private DesktopShortcutCatalog() { }

    static Uri contentUri() {
        return Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".config/desktop_shortcuts");
    }

    static void publishFromLauncher(Context context) {
        try {
            LauncherApps launcher = context.getSystemService(LauncherApps.class);
            if (launcher == null || !launcher.hasShortcutHostPermission()) return;
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> shortcuts = launcher.getShortcuts(query, Process.myUserHandle());
            JSONArray catalog = new JSONArray();
            if (shortcuts != null) {
                for (ShortcutInfo shortcut : shortcuts) {
                    if (!shortcut.isEnabled() || catalog.length() >= MAX_SHORTCUTS) continue;
                    JSONObject entry = new JSONObject();
                    entry.put("package", shortcut.getPackage());
                    entry.put("id", shortcut.getId());
                    CharSequence label = shortcut.getShortLabel();
                    entry.put("label", label == null ? shortcut.getId() : label.toString());
                    catalog.put(entry);
                }
            }
            Bundle data = new Bundle();
            data.putString(KEY_CATALOG, catalog.toString());
            context.getContentResolver().call(contentUri(), METHOD_PUBLISH, null, data);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop shortcut catalog unavailable", error);
        }
    }

    static List<Choice> read(Context context) {
        ArrayList<Choice> result = new ArrayList<>();
        try {
            Bundle data = context.getContentResolver().call(contentUri(), METHOD_GET, null, null);
            JSONArray catalog = new JSONArray(data == null ? "[]" : data.getString(KEY_CATALOG, "[]"));
            for (int i = 0; i < catalog.length() && i < MAX_SHORTCUTS; i++) {
                JSONObject item = catalog.getJSONObject(i);
                JSONObject target = new JSONObject();
                target.put("package", item.getString("package"));
                target.put("id", item.getString("id"));
                result.add(new Choice(item.getString("package"), target.toString(),
                        item.getString("label")));
            }
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop shortcut catalog read failed", error);
        }
        return result;
    }

    static boolean launchFromLauncher(Context context, String target) {
        try {
            JSONObject item = new JSONObject(target);
            LauncherApps launcher = context.getSystemService(LauncherApps.class);
            if (launcher == null || !launcher.hasShortcutHostPermission()) return false;
            launcher.startShortcut(item.getString("package"), item.getString("id"),
                    null, null, Process.myUserHandle());
            return true;
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop shortcut launch failed", error);
            return false;
        }
    }

    static final class Choice {
        final String packageName;
        final String target;
        final String label;

        Choice(String packageName, String target, String label) {
            this.packageName = packageName;
            this.target = target;
            this.label = label;
        }
    }
}
