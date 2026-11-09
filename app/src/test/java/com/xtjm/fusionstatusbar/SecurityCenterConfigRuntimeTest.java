package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = AndroidAtomicFileShadow.class)
public class SecurityCenterConfigRuntimeTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void supportedHostLoadsItsDurableSnapshotBeforeApplicationContextExists() throws Exception {
        ApplicationInfo info = info(40001380L, "com.miui.securitycenter");
        File file = cache(info);
        Files.write(file.toPath(), "{\"misc_schema_version\":1,\"security_center_smart_privacy\":true}"
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(SecurityCenterConfigRuntime.prepare(info));
        assertTrue(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        assertFalse(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.CINEMA_MODE));
    }

    @Test public void unknownVersionsAndOtherPackagesNeverEnableObfuscatedMappings() throws Exception {
        assertFalse(SecurityCenterConfigRuntime.prepare(info(40001228L, "com.miui.securitycenter")));
        assertFalse(SecurityCenterConfigRuntime.prepare(info(40001380L, "other.application")));
        assertFalse(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
    }

    @Test public void validHostUpdatesPersistAndBadTypesCannotDisableItsCurrentOptions() throws Exception {
        ApplicationInfo info = info(40001380L, "com.miui.securitycenter");
        assertTrue(SecurityCenterConfigRuntime.prepare(info));
        Context host = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return "com.miui.securitycenter"; }
        };
        MiscConfig enabled = MiscConfig.defaults().withSecurityFeature(SecurityCenterFeature.SMART_PRIVACY, true);
        Intent update = new Intent(MiscSystemConfigExchange.ACTION_PUBLISH).setPackage("com.miui.securitycenter")
                .putExtras(enabled.toBundle());
        assertTrue(SecurityCenterConfigRuntime.receive(host, update));
        assertTrue(MiscConfigStore.read(new BoundedAtomicDocument(cache(info), 4096))
                .isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        update.putExtra("security_center_smart_privacy", "false");
        assertFalse(SecurityCenterConfigRuntime.receive(host, update));
        assertTrue(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        assertFalse(SecurityCenterConfigRuntime.receive(host, update.setPackage("android")));
    }

    @Test public void firstContextReadsFreshProviderBeforeHostInitializersUseTheCache() throws Exception {
        ApplicationInfo info = info(40001380L, "com.miui.securitycenter");
        assertTrue(MiscConfigStore.write(new BoundedAtomicDocument(cache(info), 4096), MiscConfig.defaults()));
        assertTrue(SecurityCenterConfigRuntime.prepare(info));
        assertFalse(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        InitialProvider provider = provider();
        provider.result = MiscConfig.defaults().withSecurityFeature(SecurityCenterFeature.SMART_PRIVACY, true).toBundle();

        SecurityCenterConfigRuntime.loadInitialConfig(host());

        assertEquals(1, provider.calls);
        assertTrue(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        assertTrue(MiscConfigStore.read(new BoundedAtomicDocument(cache(info), 4096))
                .isEnabled(SecurityCenterFeature.SMART_PRIVACY));
    }

    @Test public void unavailableInitialProviderLeavesTheLastCommittedSnapshotEnabled() throws Exception {
        ApplicationInfo info = info(40001380L, "com.miui.securitycenter");
        MiscConfig enabled = MiscConfig.defaults().withSecurityFeature(SecurityCenterFeature.SMART_PRIVACY, true);
        assertTrue(MiscConfigStore.write(new BoundedAtomicDocument(cache(info), 4096), enabled));
        assertTrue(SecurityCenterConfigRuntime.prepare(info));
        InitialProvider provider = provider();
        provider.unavailable = true;

        SecurityCenterConfigRuntime.loadInitialConfig(host());

        assertEquals(1, provider.calls);
        assertTrue(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        assertTrue(MiscConfigStore.read(new BoundedAtomicDocument(cache(info), 4096))
                .isEnabled(SecurityCenterFeature.SMART_PRIVACY));
    }

    @Test public void disabledSettingsPackageKeepsTheWarmSnapshotWithoutStartingItsProvider() throws Exception {
        ApplicationInfo info = info(40001380L, "com.miui.securitycenter");
        MiscConfig enabled = MiscConfig.defaults().withSecurityFeature(SecurityCenterFeature.SMART_PRIVACY, true);
        assertTrue(MiscConfigStore.write(new BoundedAtomicDocument(cache(info), 4096), enabled));
        assertTrue(SecurityCenterConfigRuntime.prepare(info));
        InitialProvider provider = provider();
        Context host = host();
        try {
            host.getPackageManager().setApplicationEnabledSetting(BuildConfig.APPLICATION_ID,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
            SecurityCenterConfigRuntime.loadInitialConfig(host);
            assertEquals(0, provider.calls);
            assertTrue(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        } finally {
            host.getPackageManager().setApplicationEnabledSetting(BuildConfig.APPLICATION_ID,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
        }
    }

    @Test public void malformedProviderSnapshotCannotReplaceTheWarmSnapshot() throws Exception {
        ApplicationInfo info = info(40001380L, "com.miui.securitycenter");
        MiscConfig enabled = MiscConfig.defaults().withSecurityFeature(SecurityCenterFeature.SMART_PRIVACY, true);
        assertTrue(MiscConfigStore.write(new BoundedAtomicDocument(cache(info), 4096), enabled));
        assertTrue(SecurityCenterConfigRuntime.prepare(info));
        InitialProvider provider = provider();
        provider.result = MiscConfig.defaults().toBundle();
        provider.result.putString(SecurityCenterFeature.SMART_PRIVACY.key(), "false");
        SecurityCenterConfigRuntime.loadInitialConfig(host());
        assertEquals(1, provider.calls);
        assertTrue(SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.SMART_PRIVACY));
        assertTrue(MiscConfigStore.read(new BoundedAtomicDocument(cache(info), 4096))
                .isEnabled(SecurityCenterFeature.SMART_PRIVACY));
    }

    private static Context host() {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return "com.miui.securitycenter"; }
        };
    }

    private static InitialProvider provider() {
        InitialProvider provider = Robolectric.buildContentProvider(InitialProvider.class).create().get();
        ShadowContentResolver.registerProviderInternal(MiscConfig.contentUri().getAuthority(), provider);
        return provider;
    }

    public static class InitialProvider extends ContentProvider {
        Bundle result;
        boolean unavailable;
        int calls;
        @Override public Bundle call(String method, String arg, Bundle extras) {
            calls++;
            if (unavailable) throw new IllegalStateException("provider_not_ready");
            return result;
        }
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    }

    private ApplicationInfo info(long version, String packageName) throws Exception {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.deviceProtectedDataDir = folder.newFolder().getAbsolutePath();
        info.dataDir = info.deviceProtectedDataDir;
        ReflectionHelpers.setField(info, "longVersionCode", version);
        return info;
    }

    private static File cache(ApplicationInfo info) throws Exception {
        File files = new File(info.deviceProtectedDataDir, "files");
        assertTrue(files.isDirectory() || files.mkdirs());
        return new File(files, "fusion_statusbar_security_config_v1.json");
    }
}
