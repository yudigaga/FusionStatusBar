package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import miui.systemui.controlcenter.panel.main.qs.QSListController;
import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterRuntimeAvailabilityTest {
    private final Adapter adapter = new Adapter();
    @After public void cleanup() { ControlCenterRuntimeGrid.clear(adapter, false); }

    private ControlCenterConfig config(List<ControlCenterLayoutPlan.Item> items) {
        return ControlCenterConfig.defaults().withEnabled(true).withLayoutPlan(ControlCenterLayoutPlan.blank(4)
                .withMode(false, new ControlCenterLayoutPlan.Mode(4, items)).forPublication(false).encode());
    }
    private boolean apply(ControlCenterConfig config) {
        return ControlCenterRuntimeGrid.apply(adapter, config, getClass().getClassLoader());
    }
    @SuppressWarnings("unchecked") private List<ControlCenterLayoutPlan.Item> installed() {
        Object state = ControlCenterRuntimeGrid.retain(adapter);
        assertNotNull("A live grid must remain installed", state);
        return (List<ControlCenterLayoutPlan.Item>) ControlCenterRuntimeGrid.readField(state, "items");
    }
    private ControlCenterLayoutPlan.Item tile() { return ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1); }
    private ControlCenterLayoutPlan.Item missing() {
        return ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.DEVICE_CONTROLS, 0, 6, 4, 1);
    }

    @Test public void hiddenAndUnusedNativeTilesDoNotAcquireNewRecords() {
        adapter.qsListController.addedTiles.add(new QSListController.QSRecord("bt"));
        adapter.qsListController.addedTiles.add(new QSListController.QSRecord("flashlight"));
        adapter.redistribute(false);
        assertTrue(apply(config(List.of(tile()))));
        Object retained = ControlCenterRuntimeGrid.retain(adapter);
        java.util.Map<?, ?> records = (java.util.Map<?, ?>) ControlCenterRuntimeGrid.readField(retained, "records");
        assertEquals(java.util.Set.of("wifi"), records.keySet());
        assertEquals(3, adapter.qsListController.addedTiles.size());
    }

    @Test public void installedProxyWaitsForActualMountBeforeAcknowledgingApplication() throws Exception {
        ControlCenterConfig settings = config(List.of(tile()));
        assertTrue(apply(settings));
        assertEquals(FusionActivationStatus.WAITING, ControlCenterRuntimeGrid.applicationState(settings));
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        android.widget.FrameLayout parent = new android.widget.FrameLayout(activity);
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 8, 24, 0, 1);
        parent.addView(grid);
        Object state = ControlCenterRuntimeGrid.retain(adapter);
        java.lang.reflect.Field field = state.getClass().getDeclaredField("grid");
        field.setAccessible(true);
        field.set(state, grid);
        assertEquals(FusionActivationStatus.APPLIED, ControlCenterRuntimeGrid.applicationState(settings));
        parent.removeView(grid);
        assertEquals(FusionActivationStatus.WAITING, ControlCenterRuntimeGrid.applicationState(settings));
        activity.finish();
    }

    @Test public void unavailableOptionalComponentMustNotRejectAnOtherwiseValidPush() {
        var settings = config(List.of(tile(), missing()));
        String saved = settings.layoutPlan;
        assertTrue(apply(settings));
        assertEquals(FusionActivationStatus.DEGRADED, ControlCenterRuntimeGrid.applicationState(settings));
        assertTrue(ControlCenterRuntimeGrid.isActive(adapter));
        assertEquals(List.of("wifi"), installed().stream().map(i -> i.firstSpec).toList());
        assertEquals(2, ControlCenterLayoutPlan.decode(saved).regular.items.size());
        assertEquals(saved, settings.layoutPlan);
    }

    @Test public void hiddenUnsupportedComponentCannotAbortVisibleLayout() {
        assertTrue(apply(config(List.of(tile(), missing().withHidden(true)))));
        assertEquals(1, installed().size());
    }

    @Test public void anExistingGridAndGroupSurviveAddingUnsupportedComponent() {
        var group = ControlCenterLayoutPlan.Item.group("group:test", ControlCenterGroupData.empty().add("wifi"), 1, 2, 2, 2);
        assertTrue(apply(config(List.of(group))));
        adapter.redistribute(false);
        assertTrue(apply(config(List.of(group, missing()))));
        assertEquals(group.id, installed().get(0).id);
        assertEquals(group.group, installed().get(0).group);
        assertEquals(1, installed().get(0).x);
        assertEquals(2, installed().get(0).y);
    }

    @Test public void componentCanAppearAfterInitializationOrStyleChangeWithoutResaving() {
        var settings = config(List.of(tile(), missing()));
        assertTrue(apply(settings));
        assertEquals(1, installed().size());
        adapter.redistribute(true);
        assertTrue(apply(settings));
        assertEquals(2, installed().size());
        assertEquals(6, installed().get(1).y);
        adapter.mainPanelController.controller.style = "HORIZONTAL";
        adapter.redistribute(false);
        assertTrue(apply(settings));
        assertEquals(1, installed().size());
    }

    @Test public void onlyUnavailableItemsKeepTheNativePanelInsteadOfInstallingAnEmptyProxy() {
        Object original = adapter.contentMap.get(adapter.qsListController).get(0);
        assertFalse(apply(config(List.of(missing()))));
        assertFalse(ControlCenterRuntimeGrid.isActive(adapter));
        assertSame(original, adapter.contentMap.get(adapter.qsListController).get(0));
    }

    @Test public void appTileAbsentFromNativeAddedListIsCreatedReusedAndReleased() {
        android.content.pm.ResolveInfo info = new android.content.pm.ResolveInfo();
        info.serviceInfo = new android.content.pm.ServiceInfo();
        info.serviceInfo.packageName = "com.example.shortcut"; info.serviceInfo.name = "com.example.shortcut.AppTile";
        info.serviceInfo.permission = "android.permission.BIND_QUICK_SETTINGS_TILE";
        info.serviceInfo.enabled = true; info.serviceInfo.exported = true;
        info.serviceInfo.applicationInfo = new android.content.pm.ApplicationInfo();
        info.serviceInfo.applicationInfo.packageName = info.serviceInfo.packageName; info.serviceInfo.applicationInfo.enabled = true;
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager())
                .addResolveInfoForIntent(new android.content.Intent(android.service.quicksettings.TileService.ACTION_QS_TILE), info);
        AppController factory = new AppController(); adapter.qsListController.qsController = factory;
        var selected = ControlCenterLayoutPlan.Item.tile("custom(com.example.shortcut/.apptile)", 0, 0, 1, 1);
        var settings = config(List.of(selected));
        assertTrue(apply(settings));
        assertEquals(selected.id, installed().get(0).id);
        assertEquals(1, factory.created);
        assertEquals("custom(com.example.shortcut/.AppTile)", factory.tile.spec);
        adapter.redistribute(false);
        assertTrue(apply(settings));
        assertEquals(1, factory.created);
        assertEquals(0, factory.tile.destroyed);
        ControlCenterRuntimeGrid.clear(adapter, false);
        assertEquals(1, factory.tile.destroyed);
        assertEquals(1, adapter.qsListController.addedTiles.size());
    }

    public static final class AppController {
        int created; AppTile tile;
        public AppTile createTile(String spec) { created++; tile = new AppTile(spec); return tile; }
    }
    public static final class AppTile {
        final String spec; int destroyed;
        AppTile(String spec) { this.spec = spec; }
        public void destroy() { destroyed++; }
        @Override public String toString() { return spec; }
    }

    public static final class Adapter {
        public final Provider mainPanelController = new Provider();
        public final QSListController qsListController = new QSListController();
        public final LinkedHashMap<Object, List<Object>> contentMap = new LinkedHashMap<>();
        private final Object componentOwner = new Object();
        private final Component component = new Component();
        public int span = 4;
        Adapter() { qsListController.addedTiles.add(new QSListController.QSRecord("wifi")); redistribute(false); }
        void redistribute(boolean withComponent) {
            contentMap.clear();
            contentMap.put(qsListController, new ArrayList<>(qsListController.addedTiles));
            if (withComponent) contentMap.put(componentOwner, new ArrayList<>(List.of(component)));
        }
        public void notifyDataSetChanged() { }
    }
    public static final class Provider {
        final Controller controller = new Controller();
        public Controller get() { return controller; }
    }
    public static final class Controller {
        String style = "VERTICAL";
        public String getMode() { return "NORMAL"; }
        public String getStyle() { return style; }
    }
    public static final class Component implements MainPanelListItem {
        public int getType() { return 2668765; }
    }
}
