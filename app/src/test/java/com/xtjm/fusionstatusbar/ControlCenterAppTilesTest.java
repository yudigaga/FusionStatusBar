package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.quicksettings.TileService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterAppTilesTest {
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void declaredServiceIconWinsOverThemedApplicationPlaceholder() {
        assertDirectIcon(false);
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void missingServiceResourceFallsBackToDeclaredApplicationIcon() {
        assertDirectIcon(true);
    }

    private void assertDirectIcon(boolean missingServiceResource) {
        var context = RuntimeEnvironment.getApplication();
        int[] themedLoads = {0};
        ResolveInfo resolved = new ResolveInfo();
        ServiceInfo service = new ServiceInfo() {
            @Override public android.graphics.drawable.Drawable loadIcon(PackageManager manager) {
                themedLoads[0]++;
                return new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE);
            }
        };
        service.packageName = context.getPackageName();
        service.name = service.packageName + ".IconFixtureTile";
        service.enabled = true; service.exported = true;
        service.permission = "android.permission.BIND_QUICK_SETTINGS_TILE";
        service.nonLocalizedLabel = "Icon fixture";
        service.applicationInfo = new ApplicationInfo(context.getApplicationInfo());
        service.applicationInfo.enabled = true;
        service.applicationInfo.icon = R.drawable.cc_icon_bluetooth;
        service.icon = missingServiceResource ? Integer.MAX_VALUE : R.drawable.cc_icon_videocam;
        resolved.serviceInfo = service;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(new Intent(TileService.ACTION_QS_TILE), resolved);
        var entries = ControlCenterAppTiles.query(context, true);
        var entry = ControlCenterAppTiles.find(entries, "custom(" + service.packageName + "/.IconFixtureTile)");
        assertNotNull(entry);
        assertNotNull(entry.icon);
        // PackageManager reparcels ServiceInfo during query; exercise the same loader
        // directly to reproduce the OEM loadIcon override without losing the fixture.
        var loaded = ControlCenterAppTiles.loadIcon(context.getPackageManager(), service);
        assertEquals("Declared resources must bypass the OEM launcher-icon placeholder", 0, themedLoads[0]);
        android.graphics.Bitmap expected = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
        var drawable = context.getDrawable(missingServiceResource ? R.drawable.cc_icon_bluetooth : R.drawable.cc_icon_videocam);
        drawable.setBounds(0, 0, 96, 96);
        drawable.draw(new android.graphics.Canvas(expected));
        assertTrue("Tile preview must contain its declared glyph", expected.sameAs(entry.icon));
        android.graphics.Bitmap actual = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
        loaded.setBounds(0, 0, 96, 96);
        loaded.draw(new android.graphics.Canvas(actual));
        assertTrue("OEM-themed launcher artwork must not replace the service glyph", expected.sameAs(actual));
    }

    private ResolveInfo service(String name, boolean enabled, boolean exported, String permission) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = "com.example.tiles";
        info.serviceInfo.name = name;
        info.serviceInfo.enabled = enabled; info.serviceInfo.exported = exported;
        info.serviceInfo.permission = permission; info.serviceInfo.nonLocalizedLabel = "应用开关 " + name;
        ApplicationInfo app = new ApplicationInfo();
        app.packageName = info.serviceInfo.packageName; app.enabled = true; app.nonLocalizedLabel = "测试应用";
        info.serviceInfo.applicationInfo = app;
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addResolveInfoForIntent(new Intent(TileService.ACTION_QS_TILE), info);
        return info;
    }

    @Test public void discoversAllPublicTileServicesRatherThanOnlyPreviouslyCapturedTiles() {
        service("com.example.tiles.FastTile", true, true, "android.permission.BIND_QUICK_SETTINGS_TILE");
        service("com.example.tiles.OtherTile", true, true, "android.permission.BIND_QUICK_SETTINGS_TILE");
        service("com.example.tiles.HiddenTile", true, false, "android.permission.BIND_QUICK_SETTINGS_TILE");
        service("com.example.tiles.DisabledTile", false, true, "android.permission.BIND_QUICK_SETTINGS_TILE");
        service("com.example.tiles.NotATile", true, true, null);
        var tiles = ControlCenterAppTiles.query(RuntimeEnvironment.getApplication(), false);
        assertEquals(3, tiles.size());
        var fast = ControlCenterAppTiles.find(tiles, "custom(com.example.tiles/.fasttile)");
        assertNotNull(fast);
        assertEquals("custom(com.example.tiles/.FastTile)", fast.nativeSpec);
        assertEquals("测试应用", fast.application);
        assertTrue(fast.label.contains("FastTile"));
        assertSame(fast, ControlCenterAppTiles.find(tiles, "custom(com.example.tiles/com.example.tiles.fasttile)"));
    }

    @Test public void duplicateResolverResultsProduceOneEntry() {
        service("com.example.tiles.FastTile", true, true, "android.permission.BIND_QUICK_SETTINGS_TILE");
        service("com.example.tiles.FastTile", true, true, "android.permission.BIND_QUICK_SETTINGS_TILE");
        assertEquals(1, ControlCenterAppTiles.query(RuntimeEnvironment.getApplication(), false).size());
    }

    @Test public void createsExactCaseSelectedTilesAndSharesOwnershipAcrossRebinds() {
        var entry = new ControlCenterAppTiles.Entry("custom(com.example.tiles/.FastTile)", "开关", "应用", null);
        var item = ControlCenterLayoutPlan.Item.tile(entry.key, 0, 0, 1, 1);
        LinkedHashMap<String, Object> available = new LinkedHashMap<>();
        LinkedHashMap<String, ControlCenterAppTileSource.Lease> previous = new LinkedHashMap<>(), pending = new LinkedHashMap<>();
        int[] count = {0, 0, 0};
        ControlCenterAppTileSource.acquire(List.of(item), available, previous, pending,
                () -> { count[0]++; return List.of(entry); }, spec -> {
                    assertEquals(entry.nativeSpec, spec); count[1]++;
                    return new ControlCenterAppTileSource.Lease(new Object(), () -> count[2]++);
                });
        assertNotNull(available.get(entry.key));
        previous.putAll(pending); pending.clear(); available.clear();
        ControlCenterAppTileSource.acquire(List.of(item), available, previous, pending,
                () -> { fail("Existing tile must not rescan packages on every rebind"); return List.of(); }, spec -> null);
        ControlCenterAppTileSource.release(previous);
        assertEquals(0, count[2]);
        ControlCenterAppTileSource.release(pending);
        assertArrayEquals(new int[] {1, 1, 1}, count);
    }

    @Test public void hostOwnedAndHiddenTilesAreNeverCreatedOrDestroyed() {
        String spec = "custom(com.example.tiles/.fasttile)";
        var item = ControlCenterLayoutPlan.Item.tile(spec, 0, 0, 1, 1);
        var pending = new LinkedHashMap<String, ControlCenterAppTileSource.Lease>();
        Object hostTile = new Object();
        LinkedHashMap<String, Object> available = new LinkedHashMap<>(); available.put(spec, hostTile);
        ControlCenterAppTileSource.acquire(List.of(item, item.withHidden(true)), available, java.util.Map.of(), pending,
                () -> { fail("Host-owned tile needs no app scan"); return List.of(); }, value -> null);
        assertTrue(pending.isEmpty());
        assertSame(hostTile, available.get(spec));
    }

    @Test public void missingNativeTileCanBeCreatedThroughHostFactory() {
        String spec = "wifi";
        var item = ControlCenterLayoutPlan.Item.tile(spec, 0, 0, 1, 1);
        LinkedHashMap<String, Object> available = new LinkedHashMap<>();
        LinkedHashMap<String, ControlCenterAppTileSource.Lease> pending = new LinkedHashMap<>();
        Object tile = new Object();
        int[] creates = {0};
        ControlCenterAppTileSource.acquire(List.of(item), available, java.util.Map.of(), pending,
                java.util.Collections::emptyList, value -> {
                    creates[0]++;
                    assertEquals(spec, value);
                    return new ControlCenterAppTileSource.Lease(tile, () -> { });
                });
        assertEquals(1, creates[0]);
        assertSame(tile, available.get(spec));
        ControlCenterAppTileSource.release(pending);
    }
}
