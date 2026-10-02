package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterNativeEditBoundaryTest {
    private Field config;
    private Object previous;
    @Before public void setup() throws Exception {
        config = SystemUiHooks.class.getDeclaredField("currentConfig"); config.setAccessible(true);
        previous = config.get(null);
        config.set(null, FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults().withEnabled(true)
                .withTileShape("wifi", 2, 2).withColumns(5)));
    }
    @After public void cleanup() throws Exception { config.set(null, previous); }

    @Test public void bindingNativeEditorTilesMustNotQueueDelayedGeometryWork() throws Exception {
        Tile tile = new Tile();
        tile.mode = "EDIT";
        tile.setLayoutParams(new FrameLayout.LayoutParams(100, 100));
        invoke("applyPluginTileHeight", new Class<?>[] {Object.class, Object.class}, new Holder(tile), "wifi");
        assertEquals(0, tile.queued);
        assertEquals(100, tile.getLayoutParams().height);
    }

    @Test public void delayedNormalModeWorkMustNotResizeARecycledEditorTile() throws Exception {
        Tile tile = new Tile(); tile.mode = "EDIT";
        tile.setLayoutParams(new FrameLayout.LayoutParams(100, 100));
        invoke("applyPluginTileHeightNow", new Class<?>[] {View.class, String.class}, tile, "wifi");
        assertEquals(100, tile.getLayoutParams().height);
    }

    @Test public void nativeEditorSpanRefreshMustNotInvalidateAndRelayoutTheList() throws Exception {
        Adapter adapter = new Adapter();
        invoke("applyPluginAdapterSpan", new Class<?>[] {Object.class}, adapter);
        assertEquals(4, adapter.span);
        assertEquals(0, adapter.layoutManager.calls);
    }

    @Test public void repeatedNativeEditorBindsQueueNoWorkButNormalLayoutStillApplies() throws Exception {
        Tile tile = new Tile(); tile.mode = "EDIT";
        tile.setLayoutParams(new FrameLayout.LayoutParams(100, 100));
        for (int i = 0; i < 50; i++) invoke("applyPluginTileHeight", new Class<?>[] {Object.class, Object.class}, new Holder(tile), "wifi");
        assertEquals(0, tile.queued);
        tile.mode = "NORMAL";
        invoke("applyPluginTileHeight", new Class<?>[] {Object.class, Object.class}, new Holder(tile), "wifi");
        assertEquals(2, tile.queued);
        assertEquals(200, tile.getLayoutParams().height);
    }

    @Test public void modeReadsFollowProviderAndViewAncestryWithoutCachingEditState() {
        Tile tile = new Tile(); tile.mode = "EDIT";
        View icon = new View(tile.getContext()); tile.addView(icon);
        assertTrue(ControlCenterNativeMode.isEditing(icon));
        tile.mode = "NORMAL";
        assertFalse(ControlCenterNativeMode.isEditing(icon));
        Owner owner = new Owner();
        assertTrue(ControlCenterNativeMode.isEditing(owner));
        owner.mainPanelController.value.mode = "NORMAL";
        assertFalse(ControlCenterNativeMode.isEditing(owner));
    }

    @Test public void tileSpanHookLeavesTheNativeEditorSpanUnchanged() throws Exception {
        class Record { public String mode = "EDIT"; public String spec = "wifi"; }
        Object chain = java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {io.github.libxposed.api.XposedInterface.Chain.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getThisObject")) return new Record();
                    if (method.getName().equals("proceed")) return 1;
                    if (method.getName().equals("getArgs")) return java.util.List.of();
                    return null;
                });
        assertEquals(1, invoke("controlCenterPluginTileSpan", new Class<?>[] {io.github.libxposed.api.XposedInterface.Chain.class}, chain));
    }

    @Test @Config(sdk = 33)
    public void editingGuardAlsoRunsOnTheMinimumSupportedAndroidVersion() {
        Owner owner = new Owner();
        assertTrue(ControlCenterNativeMode.isEditing(owner));
        owner.mainPanelController.value.mode = "NORMAL";
        assertFalse(ControlCenterNativeMode.isEditing(owner));
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SystemUiHooks.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(null, args);
    }
    public static class Tile extends FrameLayout {
        public String mode;
        public int containerHeight = 100;
        int queued;
        Tile() { super(RuntimeEnvironment.getApplication()); }
        @Override public boolean post(Runnable action) { queued++; return true; }
        @Override public boolean postDelayed(Runnable action, long delay) { queued++; return true; }
    }
    public static class Holder { public View itemView; Holder(View view) { itemView = view; } }
    public static class Adapter {
        public String mode = "EDIT";
        public int span = 4;
        public final Manager layoutManager = new Manager();
    }
    public static class Manager { int calls; public void setSpanCount(int value) { calls++; } }
    public static class Owner { public Provider mainPanelController = new Provider(); }
    public static class Provider { public Adapter value = new Adapter(); public Adapter get() { return value; } }
}
