package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.service.quicksettings.TileService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Discover available TileServices; identifiers preserve component case at the platform boundary. */
final class ControlCenterAppTiles {
    static final class Entry {
        final String key, nativeSpec, label, application;
        final Bitmap icon;
        Entry(String nativeSpec, String label, String application, Bitmap icon) {
            this.nativeSpec = nativeSpec; this.key = nativeSpec.toLowerCase(Locale.ROOT);
            this.label = label; this.application = application; this.icon = icon;
        }
    }
    private ControlCenterAppTiles() { }

    static List<Entry> query(Context context, boolean icons) {
        PackageManager manager = context.getPackageManager();
        Intent intent = new Intent(TileService.ACTION_QS_TILE);
        List<ResolveInfo> services;
        try {
            java.lang.reflect.Method queryAsUser = PackageManager.class.getMethod(
                    "queryIntentServicesAsUser", Intent.class, int.class, UserHandle.class);
            @SuppressWarnings("unchecked")
            List<ResolveInfo> queried = (List<ResolveInfo>) queryAsUser.invoke(manager, intent,
                    PackageManager.GET_META_DATA, Process.myUserHandle());
            services = queried;
        } catch (RuntimeException error) {
            services = manager.queryIntentServices(intent, PackageManager.GET_META_DATA);
        } catch (ReflectiveOperationException error) {
            services = manager.queryIntentServices(intent, PackageManager.GET_META_DATA);
        }
        LinkedHashMap<String, Entry> result = new LinkedHashMap<>();
        for (ResolveInfo resolved : services) {
            ServiceInfo service = resolved.serviceInfo;
            if (service == null || service.applicationInfo == null
                    || !"android.permission.BIND_QUICK_SETTINGS_TILE".equals(service.permission)
                    || service.packageName == null || service.name == null) continue;
            ComponentName component = new ComponentName(service.packageName, service.name.startsWith(".")
                    ? service.packageName + service.name : service.name);
            if (!enabled(manager, component, service)) continue;
            String nativeSpec = "custom(" + component.flattenToShortString() + ")";
            if (nativeSpec.length() > 128) continue;
            String key = nativeSpec.toLowerCase(Locale.ROOT);
            if (result.containsKey(key)) continue;
            String label = service.name, application = service.packageName;
            Bitmap icon = null;
            try {
                CharSequence title = service.loadLabel(manager), app = service.applicationInfo.loadLabel(manager);
                if (title != null && title.length() > 0) label = title.toString();
                if (app != null && app.length() > 0) application = app.toString();
                if (icons) {
                    Drawable drawable = loadIcon(manager, service);
                    if (drawable != null) {
                        icon = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                        int width = Math.max(1, drawable.getIntrinsicWidth()), height = Math.max(1, drawable.getIntrinsicHeight());
                        float scale = 96f / Math.max(width, height);
                        int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
                        drawable.setBounds((96 - w) / 2, (96 - h) / 2, (96 + w) / 2, (96 + h) / 2);
                        drawable.draw(new Canvas(icon));
                    }
                }
            } catch (RuntimeException ignored) {
                if (icon != null) { icon.recycle(); icon = null; }
            }
            result.put(key, new Entry(nativeSpec, label, application, icon));
        }
        ArrayList<Entry> entries = new ArrayList<>(result.values());
        entries.sort(java.util.Comparator.comparing((Entry e) -> e.application).thenComparing(e -> e.label).thenComparing(e -> e.key));
        return entries;
    }

    static Drawable loadIcon(PackageManager manager, ServiceInfo service) {
        // OEM launcher theming can replace service.loadIcon() with an empty
        // backing plate. Read the TileService's declared artwork first.
        try {
            android.content.res.Resources resources = manager.getResourcesForApplication(service.applicationInfo);
            for (int id : new int[] {service.icon, service.applicationInfo.icon}) {
                if (id == 0) continue;
                try {
                    Drawable drawable = resources.getDrawable(id, null);
                    if (drawable != null) return drawable;
                } catch (RuntimeException ignored) {
                    // A missing service resource may still have a usable application icon.
                }
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Keep the platform loader as the last available fallback.
        }
        return service.loadIcon(manager);
    }

    private static boolean enabled(PackageManager pm, ComponentName component, ServiceInfo service) {
        try {
            int app = pm.getApplicationEnabledSetting(component.getPackageName());
            int state = pm.getComponentEnabledSetting(component);
            return (app == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || app == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && service.applicationInfo.enabled)
                    && (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && service.enabled);
        } catch (IllegalArgumentException ignored) { return service.enabled && service.applicationInfo.enabled; }
    }

    static Entry find(List<Entry> entries, String spec) {
        if (spec == null) return null;
        for (Entry entry : entries) if (entry.key.equalsIgnoreCase(spec)) return entry;
        // Captured native specs may use a fully qualified class rather than flattenToShortString.
        if (!spec.startsWith("custom(") || !spec.endsWith(")")) return null;
        ComponentName target = ComponentName.unflattenFromString(spec.substring(7, spec.length() - 1));
        if (target == null) return null;
        for (Entry entry : entries) {
            ComponentName candidate = ComponentName.unflattenFromString(entry.nativeSpec.substring(7, entry.nativeSpec.length() - 1));
            if (candidate != null && candidate.flattenToString().equalsIgnoreCase(target.flattenToString())) return entry;
        }
        return null;
    }
}
