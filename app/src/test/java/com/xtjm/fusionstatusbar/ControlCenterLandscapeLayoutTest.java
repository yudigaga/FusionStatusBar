package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ControlCenterLandscapeLayoutTest {
    @Test
    public void screenshotLayoutRetainsWideTileAndVerticalGroupInLeftPanel() {
        ControlCenterLayoutPlan.Mode source = screenshotLayout();
        ControlCenterLayoutPlan stored = plan(source);
        String before = stored.encode();
        ControlCenterConfig settings = ControlCenterConfig.defaults();

        ControlCenterLayoutPlan.Mode left = ControlCenterLandscapeLayout.forPanel(
                settings, source, false, Set.of("wifi", "cell"), Map.of());
        ControlCenterLayoutPlan.Mode right = ControlCenterLandscapeLayout.forPanel(
                settings, source, true, Set.of("wifi", "cell"), Map.of());

        assertEquals(4, left.columns);
        assertEquals(4, right.columns);
        assertEquals(0, byId(left, ControlCenterComponentSpec.DEVICE_CENTER).y);
        ControlCenterLayoutPlan.Item bluetooth = byId(left, "bt");
        assertEquals(0, bluetooth.x);
        assertEquals(1, bluetooth.y);
        assertEquals(2, bluetooth.width);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, bluetooth.shape);
        assertEquals(25, bluetooth.cornerRadius);
        assertTrue(bluetooth.locked);
        assertEquals(8, bluetooth.zIndex);

        ControlCenterLayoutPlan.Item group = byId(left, "group:tools");
        assertEquals(3, group.x);
        assertEquals(1, group.y);
        assertEquals(1, group.width);
        assertEquals(3, group.height);
        assertEquals(byId(source, "group:tools").group, group.group);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, group.shape);
        assertEquals(30, group.cornerRadius);
        assertEquals(17, group.zIndex);
        assertEquals(0, byId(right, "wifi").y);
        assertEquals(0, byId(right, "cell").y);
        assertEquals(1, byId(right, ControlCenterComponentSpec.MEDIA).y);
        assertEquals(2, byId(right, ControlCenterComponentSpec.MEDIA).height);
        assertEquals(1, byId(right, ControlCenterComponentSpec.BRIGHTNESS).y);
        assertEquals(1, byId(right, ControlCenterComponentSpec.VOLUME).y);
        assertTrue(left.validate().isEmpty());
        assertTrue(right.validate().isEmpty());
        assertPartitionsVisibleItems(source, left, right);
        assertEquals(before, stored.encode());
        assertEquals(4, byId(source, "group:tools").y);
    }

    @Test
    public void groupPayloadRoundTripsAfterLandscapeProjection() {
        ControlCenterLayoutPlan.Mode left = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), screenshotLayout(), false,
                Set.of("wifi", "cell"), Map.of());
        ControlCenterLayoutPlan restored = ControlCenterLayoutPlan.decode(plan(left).encode());
        assertNotNull(restored);

        ControlCenterLayoutPlan.Item original = byId(left, "group:tools");
        ControlCenterLayoutPlan.Item decoded = byId(restored.regular, "group:tools");
        assertEquals(original.group, decoded.group);
        assertEquals(original.width, decoded.width);
        assertEquals(original.height, decoded.height);
        assertEquals(original.shape, decoded.shape);
        assertEquals(original.cornerRadius, decoded.cornerRadius);
        assertEquals(original.locked, decoded.locked);
        assertEquals(original.zIndex, decoded.zIndex);
        assertEquals(List.of("sail", "camera", "custom(com.example.search/.searchtile)"),
                decoded.specs());
        assertFalse(decoded.group.members.get(0).showLabel);
        assertTrue(decoded.group.members.get(1).showLabel);
        assertEquals(8, decoded.group.members.get(2).y);
        assertEquals(ControlCenterGroupData.Surface.CLEAR, decoded.group.surface);
        assertTrue(decoded.group.border);
    }

    @Test
    public void pairAndGroupStayWholeWhenMembersCrossNativePanels() {
        ControlCenterLayoutPlan.Item rightPair = ControlCenterLayoutPlan.Item.pair(
                "wifi", "cell", ControlCenterLayoutPlan.Direction.VERTICAL, 0, 0, 1, 2)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 19);
        ControlCenterLayoutPlan.Item mixedPair = ControlCenterLayoutPlan.Item.pair(
                "wifi", "bt", ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        ControlCenterLayoutPlan.Item rightGroup = ControlCenterLayoutPlan.Item.group(
                "group:network", ControlCenterGroupData.empty().add("wifi").add("cell"),
                0, 0, 1, 2);
        ControlCenterLayoutPlan.Item mixedGroup = ControlCenterLayoutPlan.Item.group(
                "group:mixed", ControlCenterGroupData.empty().add("wifi").add("camera"),
                0, 0, 1, 2);
        ControlCenterConfig settings = ControlCenterConfig.defaults();

        assertTrue(ControlCenterLandscapeLayout.rightSide(settings, rightPair,
                Set.of("wifi", "cell"), Map.of()));
        assertFalse(ControlCenterLandscapeLayout.rightSide(settings, mixedPair,
                Set.of("wifi", "cell"), Map.of()));
        assertTrue(ControlCenterLandscapeLayout.rightSide(settings, rightGroup,
                Set.of("wifi", "cell"), Map.of()));
        assertFalse(ControlCenterLandscapeLayout.rightSide(settings, mixedGroup,
                Set.of("wifi", "cell"), Map.of()));
        ControlCenterLayoutPlan.Mode source = new ControlCenterLayoutPlan.Mode(
                4, List.of(mixedPair.withPosition(0, 2)));
        ControlCenterLayoutPlan.Mode left = ControlCenterLandscapeLayout.forPanel(
                settings, source, false, Set.of("wifi", "cell"), Map.of());
        assertEquals(1, left.items.size());
        assertEquals(mixedPair.id, left.items.get(0).id);
        assertEquals(List.of("wifi", "bt"), left.items.get(0).specs());
        assertEquals(ControlCenterLayoutPlan.Type.PAIR, left.items.get(0).type);
        assertEquals(0, left.items.get(0).y);
    }

    @Test
    public void emptyGroupIsAVisibleLeftPanelItem() {
        ControlCenterLayoutPlan.Item empty = ControlCenterLayoutPlan.Item.group(
                "group:empty", ControlCenterGroupData.empty(), 2, 5, 1, 2);
        ControlCenterLayoutPlan.Mode source = new ControlCenterLayoutPlan.Mode(4, List.of(empty));

        ControlCenterLayoutPlan.Mode left = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), source, false, Set.of("wifi", "cell"), Map.of());
        ControlCenterLayoutPlan.Mode right = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), source, true, Set.of("wifi", "cell"), Map.of());

        assertEquals(1, left.items.size());
        assertEquals(empty.id, left.items.get(0).id);
        assertEquals(2, left.items.get(0).x);
        assertEquals(0, left.items.get(0).y);
        assertTrue(left.items.get(0).group.members.isEmpty());
        assertTrue(right.items.isEmpty());
    }

    @Test
    public void explicitComponentSideWinsOverNativeSideAndFallback() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withComponentSide(ControlCenterComponentSpec.MEDIA, "left")
                .withComponentSide(ControlCenterComponentSpec.DEVICE_CENTER, "right");
        Map<String, Boolean> nativeSides = Map.of(
                ControlCenterComponentSpec.MEDIA, true,
                ControlCenterComponentSpec.DEVICE_CENTER, false,
                ControlCenterComponentSpec.BRIGHTNESS, false,
                ControlCenterComponentSpec.DEVICE_CONTROLS, true);

        assertFalse(side(settings, ControlCenterComponentSpec.MEDIA, nativeSides));
        assertTrue(side(settings, ControlCenterComponentSpec.DEVICE_CENTER, nativeSides));
        assertFalse(side(settings, ControlCenterComponentSpec.BRIGHTNESS, nativeSides));
        assertTrue(side(settings, ControlCenterComponentSpec.DEVICE_CONTROLS, nativeSides));
        assertTrue(side(settings, ControlCenterComponentSpec.VOLUME, nativeSides));
        assertTrue(side(settings, ControlCenterComponentSpec.QS_CARD, nativeSides));
        assertFalse(side(settings, "control:component:custom", nativeSides));
    }

    @Test
    public void onlyUndiscoveredNativeTilesUseWifiCellFallback() {
        ControlCenterConfig settings = ControlCenterConfig.defaults();
        ControlCenterLayoutPlan.Item wifi = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterLayoutPlan.Item cell = ControlCenterLayoutPlan.Item.tile("cell", 0, 0, 1, 1);
        ControlCenterLayoutPlan.Item custom = ControlCenterLayoutPlan.Item.tile("custom", 0, 0, 1, 1);

        assertTrue(ControlCenterLandscapeLayout.rightSide(settings, wifi, Set.of(), Map.of()));
        assertTrue(ControlCenterLandscapeLayout.rightSide(settings, cell, null, Map.of()));
        assertFalse(ControlCenterLandscapeLayout.rightSide(settings, custom, Set.of(), Map.of()));
        assertTrue(ControlCenterLandscapeLayout.rightSide(settings, custom, Set.of("custom"), Map.of()));
        assertFalse(ControlCenterLandscapeLayout.rightSide(settings, wifi, Set.of("custom"), Map.of()));
        assertFalse(ControlCenterLandscapeLayout.rightSide(settings, cell, Set.of("custom"), Map.of()));
    }

    @Test
    public void hiddenItemsAreOmittedBeforeRowsAreCompacted() {
        ControlCenterLayoutPlan.Mode source = new ControlCenterLayoutPlan.Mode(4, List.of(
                ControlCenterLayoutPlan.Item.tile("mute", 0, 0, 1, 1).withHidden(true),
                ControlCenterLayoutPlan.Item.tile("bt", 2, 4, 2, 1),
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 2, 2, 1).withHidden(true)));

        ControlCenterLayoutPlan.Mode left = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), source, false, Set.of("wifi", "cell"), Map.of());
        ControlCenterLayoutPlan.Mode right = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), source, true, Set.of("wifi", "cell"), Map.of());

        assertEquals(1, left.items.size());
        assertEquals("bt", left.items.get(0).id);
        assertEquals(0, left.items.get(0).y);
        assertTrue(right.items.isEmpty());
        assertTrue(source.items.get(0).hidden);
        assertEquals(4, source.items.get(1).y);
    }

    @Test
    public void rowCompactionKeepsTallOccupiedRowsAndHorizontalHoles() {
        ControlCenterLayoutPlan.Item tall = ControlCenterLayoutPlan.Item.tile("sail", 3, 5, 1, 3)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 21).withState(true, false, 6);
        ControlCenterLayoutPlan.Mode source = new ControlCenterLayoutPlan.Mode(4, List.of(
                tall,
                ControlCenterLayoutPlan.Item.tile("camera", 0, 6, 1, 1),
                ControlCenterLayoutPlan.Item.tile("custom", 2, 11, 2, 1)));

        ControlCenterLayoutPlan.Mode compact = ControlCenterLandscapeLayout.compactRows(source);

        assertEquals(0, byId(compact, "sail").y);
        assertEquals(1, byId(compact, "camera").y);
        assertEquals(3, byId(compact, "custom").y);
        assertEquals(3, byId(compact, "sail").x);
        assertEquals(2, byId(compact, "custom").x);
        assertEquals(2, byId(compact, "custom").width);
        assertEquals(3, byId(compact, "sail").height);
        assertEquals(tall.shape, byId(compact, "sail").shape);
        assertEquals(tall.cornerRadius, byId(compact, "sail").cornerRadius);
        assertEquals(tall.zIndex, byId(compact, "sail").zIndex);
        assertTrue(byId(compact, "sail").locked);
        assertTrue(compact.validate().isEmpty());
        assertEquals(5, tall.y);
    }

    @Test
    public void rowsCompactAgainAfterUnavailableRuntimeItemsAreRemoved() {
        ControlCenterLayoutPlan.Mode projected = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), new ControlCenterLayoutPlan.Mode(4, List.of(
                        ControlCenterLayoutPlan.Item.component(
                                ControlCenterComponentSpec.DEVICE_CENTER, 0, 4, 4, 1),
                        ControlCenterLayoutPlan.Item.tile("bt", 0, 5, 2, 1),
                        ControlCenterLayoutPlan.Item.tile("camera", 3, 8, 1, 2))),
                false, Set.of("wifi", "cell"), Map.of());
        ControlCenterLayoutPlan.Resolved available = plan(projected).resolveVisible(
                false, List.of("bt", "camera"));

        ControlCenterLayoutPlan.Mode compact = ControlCenterLandscapeLayout.compactRows(available.mode);

        assertEquals(0, byId(compact, "bt").y);
        assertEquals(1, byId(compact, "camera").y);
        assertEquals(3, byId(compact, "camera").x);
        assertEquals(2, byId(compact, "camera").height);
        assertTrue(compact.validate().isEmpty());
        assertEquals(List.of(ControlCenterComponentSpec.DEVICE_CENTER), available.unavailableSpecs);
    }

    @Test
    public void publishedCompactCanvasSuppliesLandscapeSourceWithoutMutatingEitherMode() {
        ControlCenterLayoutPlan.Mode regular = new ControlCenterLayoutPlan.Mode(4, List.of(
                ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)));
        ControlCenterLayoutPlan.Mode compact = new ControlCenterLayoutPlan.Mode(3, List.of(
                ControlCenterLayoutPlan.Item.tile("bt", 1, 4, 2, 2)));
        ControlCenterLayoutPlan published = ControlCenterLayoutPlan.decode(
                ControlCenterLayoutPlan.of(regular, compact).forPublication(true).encode());
        assertNotNull(published);
        String before = published.encode();

        ControlCenterLayoutPlan.Mode left = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), published.runtimeMode(false), false,
                Set.of("wifi", "cell"), Map.of());

        assertEquals(3, left.columns);
        assertEquals(1, left.items.get(0).x);
        assertEquals(0, left.items.get(0).y);
        assertEquals(2, left.items.get(0).width);
        assertEquals(2, left.items.get(0).height);
        assertEquals(1, published.regular.items.get(0).width);
        assertEquals(4, published.compact.items.get(0).y);
        assertEquals(before, published.encode());
    }

    @Test
    public void blankProjectionRemainsBlankAndRetainsCanvasColumns() {
        ControlCenterLayoutPlan.Mode empty = new ControlCenterLayoutPlan.Mode(5, List.of());

        ControlCenterLayoutPlan.Mode projected = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), empty, true, Set.of("wifi", "cell"), Map.of());

        assertEquals(5, projected.columns);
        assertTrue(projected.items.isEmpty());
        assertTrue(ControlCenterLandscapeLayout.compactRows(empty).items.isEmpty());
    }

    @Test
    public void unplacedItemsRemainForRuntimeResolution() {
        ControlCenterLayoutPlan.Item unplaced = ControlCenterLayoutPlan.Item.tile("bt",
                ControlCenterLayoutPlan.UNPLACED, ControlCenterLayoutPlan.UNPLACED, 2, 2);
        ControlCenterLayoutPlan.Mode source = new ControlCenterLayoutPlan.Mode(4, List.of(unplaced));

        ControlCenterLayoutPlan.Mode projected = ControlCenterLandscapeLayout.forPanel(
                ControlCenterConfig.defaults(), source, false, Set.of("wifi", "cell"), Map.of());

        assertEquals(1, projected.items.size());
        assertEquals(ControlCenterLayoutPlan.UNPLACED, projected.items.get(0).x);
        assertEquals(ControlCenterLayoutPlan.UNPLACED, projected.items.get(0).y);
        assertEquals(2, projected.items.get(0).width);
        assertEquals(2, projected.items.get(0).height);
    }

    private static boolean side(ControlCenterConfig settings, String spec,
            Map<String, Boolean> nativeSides) {
        return ControlCenterLandscapeLayout.rightSide(settings,
                ControlCenterLayoutPlan.Item.component(spec, 0, 0, 1, 1),
                Set.of("wifi", "cell"), nativeSides);
    }

    private static ControlCenterLayoutPlan.Mode screenshotLayout() {
        ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
        items.add(ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("cell", 2, 0, 2, 1));
        items.add(ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA, 0, 1, 2, 2));
        items.add(ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.BRIGHTNESS, 2, 1, 1, 2));
        items.add(ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.VOLUME, 3, 1, 1, 2));
        items.add(ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.DEVICE_CENTER, 0, 3, 4, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("bt", 0, 4, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 25).withState(true, false, 8));
        items.add(ControlCenterLayoutPlan.Item.tile("5g", 2, 4, 1, 1));
        ControlCenterGroupData tools = new ControlCenterGroupData(
                ControlCenterGroupData.Arrangement.CUSTOM, ControlCenterGroupData.Surface.CLEAR,
                true, List.of(
                        new ControlCenterGroupData.Member("sail", 0, 0, 12, 4, false),
                        new ControlCenterGroupData.Member("camera", 0, 4, 12, 4, true),
                        new ControlCenterGroupData.Member("custom(com.example.search/.SearchTile)",
                                0, 8, 12, 4, false)));
        items.add(ControlCenterLayoutPlan.Item.group("group:tools", tools, 3, 4, 1, 3)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 30).withZIndex(17));
        items.add(ControlCenterLayoutPlan.Item.tile("autobrightness", 0, 5, 1, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("eye", 1, 5, 1, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("darkmode", 2, 5, 1, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("link", 0, 6, 1, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("contact", 1, 6, 1, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("screenrecord", 2, 6, 1, 1));
        items.add(ControlCenterLayoutPlan.Item.tile("mute", 0, 7, 1, 1).withHidden(true));
        items.add(ControlCenterLayoutPlan.Item.tile("custom(com.example.tools/.ToolTile)", 0, 8, 1, 1));
        return new ControlCenterLayoutPlan.Mode(4, items);
    }

    private static ControlCenterLayoutPlan plan(ControlCenterLayoutPlan.Mode mode) {
        return ControlCenterLayoutPlan.of(mode, new ControlCenterLayoutPlan.Mode(3, List.of()));
    }

    private static ControlCenterLayoutPlan.Item byId(ControlCenterLayoutPlan.Mode mode, String id) {
        for (ControlCenterLayoutPlan.Item item : mode.items) {
            if (item.id.equals(id)) return item;
        }
        throw new AssertionError("Missing item: " + id);
    }

    private static void assertPartitionsVisibleItems(ControlCenterLayoutPlan.Mode source,
            ControlCenterLayoutPlan.Mode left, ControlCenterLayoutPlan.Mode right) {
        Set<String> expectedIds = new HashSet<>();
        Set<String> expectedSpecs = new HashSet<>();
        for (ControlCenterLayoutPlan.Item item : source.items) {
            if (item.hidden) continue;
            expectedIds.add(item.id);
            expectedSpecs.addAll(item.specs());
        }
        Set<String> actualIds = new HashSet<>();
        Set<String> actualSpecs = new HashSet<>();
        for (ControlCenterLayoutPlan.Mode mode : List.of(left, right)) {
            for (ControlCenterLayoutPlan.Item item : mode.items) {
                assertTrue("Duplicated item: " + item.id, actualIds.add(item.id));
                for (String spec : item.specs()) assertTrue("Duplicated spec: " + spec,
                        actualSpecs.add(spec));
            }
        }
        assertEquals(expectedIds, actualIds);
        assertEquals(expectedSpecs, actualSpecs);
    }
}
