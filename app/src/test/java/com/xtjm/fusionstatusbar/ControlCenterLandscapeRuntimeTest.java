package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.robolectric.RuntimeEnvironment;
import miui.systemui.controlcenter.panel.main.qs.QSCardsController;
import miui.systemui.controlcenter.panel.main.qs.QSListController;
import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterLandscapeRuntimeTest {
    private final Controller controller = new Controller();
    private final QSListController qs = new QSListController();
    private final QSCardsController cards = new QSCardsController();
    private final ComponentOwner media = new ComponentOwner(63342, true);
    private final ComponentOwner brightness = new ComponentOwner(274442, true);
    private final ComponentOwner volume = new ComponentOwner(865269, true);
    private final ComponentOwner devices = new ComponentOwner(338423, false);
    private final ComponentOwner header = new ComponentOwner(-1, false);
    private final Adapter left = new Adapter(controller, qs);
    private final Adapter right = new Adapter(controller, qs);
    private final Distributor distributor = new Distributor(left, right);

    public ControlCenterLandscapeRuntimeTest() {
        controller.distributor = distributor;
        for (String spec : List.of("bt", "5g", "rotation", "camera", "search", "mute")) {
            qs.addedTiles.add(new QSListController.QSRecord(spec));
        }
        cards.cards.add(new QSListController.QSRecord("wifi"));
        cards.cards.add(new QSListController.QSRecord("cell"));
        distributor.leftPanelContent.addAll(List.of(header, devices, qs));
        distributor.rightPanelContent.addAll(List.of(cards, media, brightness, volume));
        distribute();
    }

    @After public void cleanup() {
        ControlCenterRuntimeGrid.clear(left, false);
        ControlCenterRuntimeGrid.clear(right, false);
    }

    @Test public void bothNativeAdaptersInstallTheScreenshotCustomizationWithoutDuplicates() {
        ControlCenterConfig settings = screenshotConfig();
        String saved = settings.layoutPlan;
        assertTrue(apply(right, settings));
        assertTrue(apply(left, settings));
        var leftItems = items(left);
        var rightItems = items(right);
        assertEquals(Set.of("bt", "5g", "group:tools", ControlCenterComponentSpec.DEVICE_CENTER), ids(leftItems));
        assertEquals(Set.of("wifi", "cell", ControlCenterComponentSpec.MEDIA,
                ControlCenterComponentSpec.BRIGHTNESS, ControlCenterComponentSpec.VOLUME), ids(rightItems));
        var bluetooth = leftItems.stream().filter(i -> i.id.equals("bt")).findFirst().orElseThrow();
        var group = leftItems.stream().filter(i -> i.id.equals("group:tools")).findFirst().orElseThrow();
        assertEquals(2, bluetooth.width);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, bluetooth.shape);
        assertEquals(List.of("rotation", "camera", "search"), group.specs());
        assertEquals(3, group.height);
        assertEquals(1, group.y);
        assertEquals(List.of(header.item), left.contentMap.get(header));
        assertTrue(right.contentMap.get(cards).isEmpty());
        assertTrue(right.contentMap.get(media).isEmpty());
        assertEquals(Set.of("bt", "5g", "rotation", "camera", "search"), records(left));
        assertEquals(Set.of("wifi", "cell"), records(right));
        assertEquals(saved, settings.layoutPlan);
        assertEquals(4, left.span);
        assertEquals(4, right.span);
    }

    @Test public void crossPanelGroupAndExplicitComponentSideUseCombinedNativeInventories() {
        var group = ControlCenterLayoutPlan.Item.group("group:network",
                ControlCenterGroupData.empty().add("wifi").add("bt"), 0, 3, 2, 2);
        var mode = new ControlCenterLayoutPlan.Mode(4, List.of(group,
                ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA, 2, 3, 2, 2)));
        var settings = config(mode).withComponentSide(ControlCenterComponentSpec.MEDIA, "left");
        assertTrue(apply(right, settings));
        assertTrue(apply(left, settings));
        assertEquals(Set.of(group.id, ControlCenterComponentSpec.MEDIA), ids(items(left)));
        assertEquals(group.group, items(left).stream().filter(i -> i.id.equals(group.id)).findFirst().orElseThrow().group);
        assertTrue(items(right).isEmpty());
        assertEquals(Set.of("wifi", "bt"), records(left));
        assertEquals(Set.of(), records(right));
        assertTrue(right.contentMap.get(cards).isEmpty());
        assertTrue(right.contentMap.get(media).isEmpty());
    }

    @Test public void rotationRestoresOriginalSavedCoordinatesAndCanReapplyInEitherOrder() {
        var settings = screenshotConfig();
        assertTrue(apply(right, settings));
        assertTrue(apply(left, settings));
        ControlCenterRuntimeGrid.clear(left, false);
        controller.style = "VERTICAL";
        right.contentMap.clear();
        for (Object owner : List.of(cards, media, brightness, volume, devices, qs)) {
            right.contentMap.put(owner, inventory(owner));
        }
        assertTrue(apply(right, settings));
        assertEquals(4, items(right).stream().filter(i -> i.id.equals("bt")).findFirst().orElseThrow().y);
        controller.style = "HORIZONTAL";
        distribute();
        assertTrue(apply(left, settings));
        assertTrue(apply(right, settings));
        assertEquals(1, items(left).stream().filter(i -> i.id.equals("bt")).findFirst().orElseThrow().y);
        assertEquals(0, items(right).stream().filter(i -> i.id.equals("wifi")).findFirst().orElseThrow().y);
    }

    @Test public void disablingAfterLandscapeRestoresBothCurrentNativeMaps() {
        var settings = screenshotConfig();
        assertTrue(apply(left, settings));
        assertTrue(apply(right, settings));
        assertFalse(apply(right, settings.withEnabled(false)));
        assertFalse(apply(left, settings.withEnabled(false)));
        assertEquals(qs.addedTiles, left.contentMap.get(qs));
        assertEquals(cards.cards, right.contentMap.get(cards));
        assertEquals(List.of(media.item), right.contentMap.get(media));
    }

    @Test public void mountedPanelsRenderWideCardAndAllGroupMembersAndCaptureWholeCanvas() throws Exception {
        var settings = screenshotConfig();
        qs.bindViews = true;
        qs.qsCardsController = cards;
        assertTrue(apply(right, settings));
        assertTrue(apply(left, settings));
        var rightGrid = bind(right);
        var leftGrid = bind(left);
        assertEquals(3, cards.createdHolders); // Wi-Fi, cell and the wide Bluetooth card.
        var group = findGroup(leftGrid);
        assertNotNull(group);
        assertEquals(3, group.getChildCount());
        assertEquals(1, group.item().width);
        assertEquals(3, group.item().height);
        assertTrue(group.getMeasuredHeight() > group.getMeasuredWidth());
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            assertTrue(child.getMeasuredWidth() > 0);
            assertTrue(child.getMeasuredHeight() > 0);
            assertTrue(child.getBottom() <= group.getHeight());
        }
        assertEquals(3, components(right).size());
        assertEquals(1, components(left).size());
        var saved = ControlCenterLayoutPlan.decode(settings.layoutPlan).regular;
        assertEquals(ids(saved.items), ids(leftGrid.captureMode().items));
        assertEquals(ids(saved.items), ids(rightGrid.captureMode().items));
        assertEquals(4, leftGrid.captureMode().items.stream().filter(i -> i.id.equals("bt")).findFirst().orElseThrow().y);
        assertEquals(FusionActivationStatus.APPLIED, ControlCenterRuntimeGrid.applicationState(settings));
    }

    @Test public void oldPanelReleaseCannotUnbindTheComponentAlreadyMovedToNewPanel() throws Exception {
        qs.bindViews = true;
        qs.qsCardsController = cards;
        var mode = new ControlCenterLayoutPlan.Mode(4, List.of(
                ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA, 0, 0, 2, 2)));
        var settings = config(mode);
        assertTrue(apply(right, settings));
        bind(right);
        Object oldHolder = media.item.getHolder();
        assertNotNull(oldHolder);
        var moved = settings.withComponentSide(ControlCenterComponentSpec.MEDIA, "left");
        assertTrue(apply(left, moved));
        bind(left);
        Object newHolder = media.item.getHolder();
        assertNotSame(oldHolder, newHolder);
        int unbinds = media.item.unbinds;
        ControlCenterRuntimeGrid.clear(right, false);
        assertSame(newHolder, media.item.getHolder());
        assertEquals(unbinds, media.item.unbinds);
        assertFalse(((QSListController.Holder) newHolder).recycled);
        assertTrue(((QSListController.Holder) oldHolder).recycled);
    }

    private ControlCenterGroupView findGroup(ViewGroup grid) {
        for (int i = 0; i < grid.getChildCount(); i++) {
            if (grid.getChildAt(i) instanceof ControlCenterGroupView group) return group;
        }
        return null;
    }
    private ControlCenterRuntimeGrid.GridView bind(Adapter adapter) throws Exception {
        Configuration landscape = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        landscape.orientation = Configuration.ORIENTATION_LANDSCAPE;
        var holder = new QSListController.Holder(RuntimeEnvironment.getApplication().createConfigurationContext(landscape));
        holder.itemView.setLayoutParams(new FrameLayout.LayoutParams(600, 600));
        Object state = ControlCenterRuntimeGrid.retain(adapter);
        var field = state.getClass().getDeclaredField("outerHolder");
        field.setAccessible(true); field.set(state, holder);
        var bind = state.getClass().getDeclaredMethod("bind");
        bind.setAccessible(true); bind.invoke(state);
        assertFalse("Proxy must mount every configured holder", (Boolean) ControlCenterRuntimeGrid.readField(state, "failed"));
        var grid = (ControlCenterRuntimeGrid.GridView) ControlCenterRuntimeGrid.readField(state, "grid");
        assertNotNull(grid);
        grid.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
        grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        return grid;
    }
    private Map<?, ?> components(Adapter adapter) {
        return (Map<?, ?>) ControlCenterRuntimeGrid.readField(ControlCenterRuntimeGrid.retain(adapter), "sourceComponents");
    }

    private ControlCenterConfig screenshotConfig() {
        var group = ControlCenterLayoutPlan.Item.group("group:tools", ControlCenterGroupData.empty()
                .add("rotation").add("camera").add("search"), 3, 4, 1, 3);
        return config(new ControlCenterLayoutPlan.Mode(4, List.of(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1),
                ControlCenterLayoutPlan.Item.tile("cell", 2, 0, 2, 1),
                ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA, 0, 1, 2, 2),
                ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.BRIGHTNESS, 2, 1, 1, 2),
                ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.VOLUME, 3, 1, 1, 2),
                ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.DEVICE_CENTER, 0, 3, 4, 1),
                ControlCenterLayoutPlan.Item.tile("bt", 0, 4, 2, 1).withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 24),
                ControlCenterLayoutPlan.Item.tile("5g", 2, 4, 1, 1), group,
                ControlCenterLayoutPlan.Item.tile("mute", 0, 5, 1, 1).withHidden(true))));
    }
    private ControlCenterConfig config(ControlCenterLayoutPlan.Mode mode) {
        return ControlCenterConfig.defaults().withEnabled(true)
                .withLayoutPlan(ControlCenterLayoutPlan.of(mode, mode).forPublication(false).encode());
    }
    private boolean apply(Adapter adapter, ControlCenterConfig settings) {
        return ControlCenterRuntimeGrid.apply(adapter, settings, getClass().getClassLoader());
    }
    @SuppressWarnings("unchecked") private List<ControlCenterLayoutPlan.Item> items(Adapter adapter) {
        Object state = ControlCenterRuntimeGrid.retain(adapter);
        assertNotNull(state);
        return (List<ControlCenterLayoutPlan.Item>) ControlCenterRuntimeGrid.readField(state, "items");
    }
    private Set<String> ids(List<ControlCenterLayoutPlan.Item> items) {
        return items.stream().map(i -> i.id).collect(Collectors.toSet());
    }
    private Set<?> records(Adapter adapter) {
        return ((Map<?, ?>) ControlCenterRuntimeGrid.readField(ControlCenterRuntimeGrid.retain(adapter), "records")).keySet();
    }
    private void distribute() {
        left.contentMap.clear(); right.contentMap.clear();
        for (Object owner : distributor.leftPanelContent) left.contentMap.put(owner, inventory(owner));
        for (Object owner : distributor.rightPanelContent) right.contentMap.put(owner, inventory(owner));
    }
    private List<Object> inventory(Object owner) {
        if (owner == qs) return new ArrayList<>(qs.addedTiles);
        if (owner == cards) return new ArrayList<>(cards.cards);
        return new ArrayList<>(((ComponentOwner) owner).getListItems());
    }
    public static final class Controller {
        String style = "HORIZONTAL";
        public Distributor distributor;
        public String getMode() { return "NORMAL"; }
        public String getStyle() { return style; }
    }
    public static final class Provider {
        final Controller value;
        Provider(Controller value) { this.value = value; }
        public Controller get() { return value; }
    }
    public static final class Adapter {
        public final Provider mainPanelController;
        public final QSListController qsListController;
        public final LinkedHashMap<Object, List<Object>> contentMap = new LinkedHashMap<>();
        public int span = 4;
        Adapter(Controller c, QSListController qs) { mainPanelController = new Provider(c); qsListController = qs; }
        public void notifyDataSetChanged() { }
    }
    public static final class Distributor {
        public final List<Object> leftPanelContent = new ArrayList<>(), rightPanelContent = new ArrayList<>();
        final Adapter left, right;
        Distributor(Adapter left, Adapter right) { this.left = left; this.right = right; }
        public Adapter getLeftAdapter() { return left; }
        public Adapter getRightAdapter() { return right; }
    }
    public static final class ComponentOwner {
        final Component item;
        final boolean right;
        ComponentOwner(int type, boolean right) { item = new Component(type); this.right = right; }
        public boolean getRightOrLeft() { return right; }
        public List<Component> getListItems() { return List.of(item); }
        public Object createViewHolder(ViewGroup parent, int type) { return new QSListController.Holder(parent.getContext()); }
    }
    public static final class Component implements MainPanelListItem {
        final int type;
        private Object holder;
        int unbinds;
        Component(int type) { this.type = type; }
        public int getType() { return type; }
        public Object getHolder() { return holder; }
        public void setHolder(Object holder) { this.holder = holder; }
        public void onBindViewHolder() { }
        public void onUnbindViewHolder() { unbinds++; holder = null; }
    }
}
