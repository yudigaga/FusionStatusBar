package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import miui.systemui.controlcenter.panel.main.qs.QSCardsController;
import miui.systemui.controlcenter.panel.main.qs.QSListController;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterHostAdapterTest {
    @Test public void captureProvidesAnIndependentNativeSnapshot() throws Exception {
        Adapter adapter = new Adapter();
        Object nativeRecord = adapter.qsListController.addedTiles.get(0);
        ControlCenterHostAdapter.State state = ControlCenterHostAdapter.readState(adapter);
        ControlCenterHostAdapter.Panel panel = ControlCenterHostAdapter.capture(
                state, getClass().getClassLoader());

        assertEquals("NORMAL", state.mode);
        assertEquals("VERTICAL", state.style);
        assertSame(state.controller, panel.state.controller);
        assertSame(adapter.contentMap, panel.nativeMap);
        assertSame(nativeRecord, panel.original.get(adapter.qsListController).get(0));
        assertNotSame(adapter.contentMap.get(adapter.qsListController),
                panel.original.get(adapter.qsListController));

        adapter.qsListController.addedTiles.add(new QSListController.QSRecord("bt"));
        adapter.contentMap.get(adapter.qsListController).clear();
        assertEquals("A later native distribution cannot mutate the recovery snapshot",
                1, panel.original.get(adapter.qsListController).size());
    }

    @Test public void unsupportedPluginShapeReturnsTheCapturedNativePanel() throws Exception {
        Adapter adapter = new Adapter();
        Object nativeRecord = adapter.contentMap.get(adapter.qsListController).get(0);
        ControlCenterHostAdapter.State state = ControlCenterHostAdapter.readState(adapter);
        ClassLoader incompatibleLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (name.equals("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        try {
            ControlCenterHostAdapter.capture(state, incompatibleLoader);
            fail("An incompatible plugin interface must reject this host snapshot");
        } catch (ControlCenterHostAdapter.Unavailable unavailable) {
            assertTrue(unavailable.original.containsKey(adapter.qsListController));
            assertSame(nativeRecord,
                    unavailable.original.get(adapter.qsListController).get(0));
        }
    }

    @Test public void horizontalCaptureUsesNativeCardInventoryAndAdapterIdentity() throws Exception {
        Adapter adapter = new Adapter();
        QSCardsController cards = new QSCardsController();
        QSListController.QSRecord brightness = new QSListController.QSRecord("brightness");
        cards.cards.add(brightness);
        adapter.mainPanelController.controller.style = "HORIZONTAL";
        adapter.mainPanelController.controller.distributor = new Distributor(adapter, cards);
        adapter.contentMap.put(cards, new ArrayList<>());

        ControlCenterHostAdapter.Panel panel = ControlCenterHostAdapter.capture(
                ControlCenterHostAdapter.readState(adapter), getClass().getClassLoader());

        assertTrue(panel.horizontal);
        assertSame(brightness, panel.sources.get(cards).get(0));
        assertTrue(panel.isRightAdapter());
        assertTrue(panel.rightTileSpecs().contains("brightness"));
    }

    @Test public void panelProjectionAndRestoreStayOwnedByTheCapturedHost() throws Exception {
        Adapter adapter = new Adapter();
        QSCardsController cards = new QSCardsController();
        Object component = new Object();
        Object retained = new Object();
        Object otherOwner = new Object();
        Object otherRecord = new Object();
        adapter.contentMap.put(cards, new ArrayList<>(List.of(component, retained)));
        adapter.contentMap.put(otherOwner, new ArrayList<>(List.of(otherRecord)));
        ControlCenterHostAdapter.Panel panel = ControlCenterHostAdapter.capture(
                ControlCenterHostAdapter.readState(adapter), getClass().getClassLoader());
        Object gridItem = new Object();
        java.util.Set<Object> relocated = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        relocated.add(component);

        panel.project(gridItem, relocated);

        assertEquals(List.of(adapter.qsListController, cards, otherOwner),
                new ArrayList<>(adapter.contentMap.keySet()));
        assertEquals(List.of(gridItem), adapter.contentMap.get(adapter.qsListController));
        assertTrue(adapter.contentMap.get(cards).isEmpty());
        assertSame(otherRecord, adapter.contentMap.get(otherOwner).get(0));
        assertEquals(ControlCenterHostAdapter.RestoreResult.RESTORED,
                panel.restoreProxy(gridItem, true));
        assertSame(component, adapter.contentMap.get(cards).get(0));
        assertSame(retained, adapter.contentMap.get(cards).get(1));
        assertEquals(1, adapter.notifications);
    }

    @Test public void oldProxyRestoreKeepsANewerNativeDistribution() throws Exception {
        Adapter adapter = new Adapter();
        ControlCenterHostAdapter.Panel panel = ControlCenterHostAdapter.capture(
                ControlCenterHostAdapter.readState(adapter), getClass().getClassLoader());
        Object gridItem = new Object();
        panel.project(gridItem, java.util.Collections.emptySet());
        Object newerOwner = new Object();
        Object newerRecord = new Object();
        adapter.contentMap.clear();
        adapter.contentMap.put(newerOwner, new ArrayList<>(List.of(newerRecord)));

        assertEquals(ControlCenterHostAdapter.RestoreResult.SUPERSEDED,
                panel.restoreProxy(gridItem, true));

        assertEquals(List.of(newerOwner), new ArrayList<>(adapter.contentMap.keySet()));
        assertSame(newerRecord, adapter.contentMap.get(newerOwner).get(0));
        assertEquals(0, adapter.notifications);
    }

    @Test public void panelOwnsRecordFactoryAndReleaseCallbacks() throws Exception {
        Adapter adapter = new Adapter();
        ControlCenterHostAdapter.Panel panel = ControlCenterHostAdapter.capture(
                ControlCenterHostAdapter.readState(adapter), getClass().getClassLoader());

        Object created = panel.createRecord("bt", true);

        assertTrue(created instanceof QSListController.QSRecord);
        QSListController.QSRecord record = (QSListController.QSRecord) created;
        assertEquals("bt", record.getSpec());
        assertTrue(record.added);
        panel.setRecordListening(record, false);
        panel.removeRecordCallback(record);
        assertFalse(record.listening);
        assertEquals(1, record.removedCallbacks);
    }

    @Test public void panelCreatesAndBindsNativeTileHolder() throws Exception {
        Adapter adapter = new Adapter();
        adapter.qsListController.bindViews = true;
        ControlCenterHostAdapter.Panel panel = ControlCenterHostAdapter.capture(
                ControlCenterHostAdapter.readState(adapter), getClass().getClassLoader());
        Object record = panel.createRecord("bt", false);
        FrameLayout parent = new FrameLayout(org.robolectric.RuntimeEnvironment.getApplication());

        ControlCenterHostAdapter.HolderBinding binding = panel.bindTile(parent, record, false);

        assertSame(adapter.qsListController, binding.owner);
        assertSame(record, binding.record);
        assertTrue(binding.view instanceof FrameLayout);
        assertTrue(binding.holder instanceof QSListController.Holder);
        QSListController.Holder holder = (QSListController.Holder) binding.holder;
        assertSame(adapter.qsListController, holder.owner);
        assertSame(record, holder.item);
        assertSame(holder, ((QSListController.QSRecord) record).getHolder());
    }

    public static final class Adapter {
        public final Provider mainPanelController = new Provider();
        public final QSListController qsListController = new QSListController();
        public final LinkedHashMap<Object, List<Object>> contentMap = new LinkedHashMap<>();
        int notifications;

        Adapter() {
            qsListController.addedTiles.add(new QSListController.QSRecord("wifi"));
            contentMap.put(qsListController, new ArrayList<>(qsListController.addedTiles));
        }

        public void notifyDataSetChanged() { notifications++; }
    }

    public static final class Provider {
        private final Controller controller = new Controller();
        public Controller get() { return controller; }
    }

    public static final class Controller {
        String style = "VERTICAL";
        Distributor distributor;
        public String getMode() { return "NORMAL"; }
        public String getStyle() { return style; }
    }

    public static final class Distributor {
        public final List<Object> leftPanelContent = new ArrayList<>();
        public final List<Object> rightPanelContent = new ArrayList<>();
        private final Object rightAdapter;

        Distributor(Object rightAdapter, Object rightOwner) {
            this.rightAdapter = rightAdapter;
            rightPanelContent.add(rightOwner);
        }

        public Object getRightAdapter() { return rightAdapter; }
        public Object getLeftAdapter() { return null; }
    }
}
