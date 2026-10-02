package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ControlCenterLayoutPlanTest {
    @Test
    public void pushedCompactCanvasWinsOverNativeVerticalStyleAfterPersistence() {
        ControlCenterLayoutPlan draft = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1),
                ControlCenterLayoutPlan.Item.component("control:media", 2, 3, 2, 2))
                .withItem(true, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1))
                .withItem(true, ControlCenterLayoutPlan.Item.component(
                        "control:media", 0, 5, 2, 2));
        ControlCenterConfig saved = ControlCenterConfig.defaults()
                .withLayoutPlan(draft.forPublication(true).encode());
        ControlCenterLayoutPlan loaded = ControlCenterLayoutPlan.fromConfig(saved, null);
        ControlCenterLayoutPlan.Resolved rendered = loaded.resolveForRuntime(false, null);

        assertEquals(3, rendered.mode.columns);
        assertEquals(2, bySpec(rendered.items, "wifi").width);
        assertEquals(0, bySpec(rendered.items, "control:media").x);
        assertEquals(5, bySpec(rendered.items, "control:media").y);
        assertEquals(1, bySpec(loaded.regular.items, "wifi").width);
    }

    @Test
    public void publishingStandardAgainChangesOnlyTheRuntimeSelection() {
        ControlCenterLayoutPlan draft = plan(ControlCenterLayoutPlan.Item.tile(
                "wifi", 0, 0, 1, 1)).withItem(true,
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1));
        ControlCenterLayoutPlan loaded = ControlCenterLayoutPlan.decode(
                draft.forPublication(true).forPublication(false).encode());

        assertEquals(4, loaded.resolveForRuntime(true, null).mode.columns);
        assertEquals(1, loaded.resolveForRuntime(true, null).items.get(0).width);
        assertEquals(2, loaded.compact.items.get(0).width);
    }

    @Test
    public void editingPublishedPlanPreservesSelectionAndHiddenRuntimeFiltering() {
        ControlCenterLayoutPlan published = plan(ControlCenterLayoutPlan.Item.tile(
                "wifi", 0, 0, 1, 1)).forPublication(true)
                .withItem(true, ControlCenterLayoutPlan.Item.tile("bt", 1, 1, 1, 1))
                .withItem(true, ControlCenterLayoutPlan.Item.tile("mute", 0, 0, 1, 1)
                        .withHidden(true));
        ControlCenterLayoutPlan loaded = ControlCenterLayoutPlan.decode(published.encode());

        assertTrue(loaded.runtimeUsesCompact(false));
        assertEquals(List.of("bt"), loaded.resolveForRuntime(false, null).items.stream()
                .map(item -> item.firstSpec).toList());
    }

    @Test
    public void legacyV3KeepsAutomaticSelectionUntilPublished() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x43434c50);
        output.writeByte(3);
        output.writeByte(4);
        output.writeShort(0);
        output.writeByte(3);
        output.writeShort(0);
        ControlCenterLayoutPlan loaded = ControlCenterLayoutPlan.decode("CCLP2:"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()));

        assertNotNull(loaded);
        assertFalse(loaded.runtimeUsesCompact(false));
        assertTrue(loaded.runtimeUsesCompact(true));
    }

    @Test
    public void unknownPublishedCanvasIsRejected() {
        byte[] bytes = Base64.getUrlDecoder().decode(plan().encode().substring(6));
        bytes[bytes.length - 1] = 127;
        assertNull(ControlCenterLayoutPlan.decode("CCLP2:"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)));
    }

    @Test
    public void changingCompactColumnsPreservesRegularDraftAndPublishedSelection() {
        ControlCenterLayoutPlan plan = plan(ControlCenterLayoutPlan.Item.tile(
                "wifi", 0, 0, 2, 1)).withItem(true,
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)).forPublication(true);
        ControlCenterLayoutPlan changed = ControlCenterLayoutPlan.decode(
                plan.withMode(true, plan.compact.withColumns(5)).encode());
        assertEquals(5, changed.runtimeMode(false).columns);
        assertEquals(4, changed.regular.columns);
        assertEquals(2, changed.regular.items.get(0).width);
    }

    private static ControlCenterLayoutPlan plan(ControlCenterLayoutPlan.Item... items) {
        return ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(4, List.of(items)),
                new ControlCenterLayoutPlan.Mode(3, List.of()));
    }

    @Test
    public void pairAndModesRoundTripWithStableId() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair(
                "WiFi", "BT", ControlCenterLayoutPlan.Direction.VERTICAL, 2, 0, 2, 3);
        ControlCenterLayoutPlan original = plan(pair).withItem(true,
                ControlCenterLayoutPlan.Item.tile("wifi", 1, 2, 1, 1));
        String serialized = original.encode();
        ControlCenterLayoutPlan restored = ControlCenterLayoutPlan.decode(serialized);

        assertNotNull(restored);
        assertEquals(serialized, restored.encode());
        assertEquals(2, restored.regular.items.get(0).width);
        assertEquals(3, restored.regular.items.get(0).height);
        assertEquals(ControlCenterLayoutPlan.Direction.VERTICAL,
                restored.regular.items.get(0).direction);
        assertEquals(pair.id, ControlCenterLayoutPlan.Item.pair("bt", "wifi",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1).id);
        assertEquals(1, restored.compact.items.get(0).x);
        assertEquals(3, restored.compact.columns);
    }

    @Test
    public void itemShapeAndRadiusRoundTripWithoutChangingIdentity() {
        ControlCenterLayoutPlan.Item originalItem =
                ControlCenterLayoutPlan.Item.tile("wifi", 1, 2, 2, 2)
                        .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 22);
        ControlCenterLayoutPlan restoredPlan = plan(originalItem);

        ControlCenterLayoutPlan.Item restored = ControlCenterLayoutPlan.decode(
                restoredPlan.encode()).regular.items.get(0);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, restored.shape);
        assertEquals(22, restored.cornerRadius);
        assertEquals(originalItem.id, restored.id);
    }

    @Test
    public void itemEditorStateRoundTripsAndSurvivesGeometryChanges() {
        ControlCenterLayoutPlan.Item originalItem =
                ControlCenterLayoutPlan.Item.tile("wifi", 1, 2, 2, 2)
                        .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 18)
                        .withState(true, true, 17);
        ControlCenterLayoutPlan.Item changed = originalItem
                .withPosition(0, 3)
                .withSize(3, 1)
                .withDirection(ControlCenterLayoutPlan.Direction.VERTICAL)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 24);

        ControlCenterLayoutPlan.Item restored = ControlCenterLayoutPlan.decode(
                plan(changed).encode()).regular.items.get(0);
        assertTrue(restored.locked);
        assertTrue(restored.hidden);
        assertEquals(17, restored.zIndex);
        assertEquals(0, restored.x);
        assertEquals(3, restored.y);
        assertEquals(3, restored.width);
        assertEquals(1, restored.height);
        assertEquals(ControlCenterLayoutPlan.Direction.VERTICAL, restored.direction);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, restored.shape);
        assertEquals(24, restored.cornerRadius);
    }

    @Test
    public void editorStateHelpersChangeOnlyTheirSelectedField() {
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile(
                "wifi", 0, 0, 1, 1).withState(true, true, 9);

        ControlCenterLayoutPlan.Item unlocked = item.withLocked(false);
        assertFalse(unlocked.locked);
        assertTrue(unlocked.hidden);
        assertEquals(9, unlocked.zIndex);

        ControlCenterLayoutPlan.Item visible = item.withHidden(false);
        assertTrue(visible.locked);
        assertFalse(visible.hidden);
        assertEquals(9, visible.zIndex);

        ControlCenterLayoutPlan.Item reordered = item.withZIndex(-4);
        assertTrue(reordered.locked);
        assertTrue(reordered.hidden);
        assertEquals(-4, reordered.zIndex);
    }

    @Test
    public void visibleResolutionReleasesHiddenCells() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                        .withHidden(true),
                ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1));

        ControlCenterLayoutPlan.Resolved visible = original.resolveVisible(false,
                List.of("wifi", "bt"));
        assertEquals(1, visible.items.size());
        assertEquals("bt", visible.items.get(0).firstSpec);
        assertEquals(0, visible.items.get(0).x);
        assertEquals(0, visible.items.get(0).y);
        assertTrue(original.regular.validate().isEmpty());
    }

    @Test
    public void legacyV1PlanRemainsReadableWithDefaultShape() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x43434c50);
        output.writeByte(1);
        writeLegacyMode(output, 4, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1));
        writeLegacyMode(output, 3);
        output.flush();

        ControlCenterLayoutPlan restored = ControlCenterLayoutPlan.decode("CCLP1:"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()));
        assertNotNull(restored);
        assertEquals(ControlCenterLayoutPlan.Shape.RECTANGLE,
                restored.regular.items.get(0).shape);
        assertEquals(0, restored.regular.items.get(0).cornerRadius);
        assertFalse(restored.regular.items.get(0).locked);
        assertFalse(restored.regular.items.get(0).hidden);
        assertEquals(0, restored.regular.items.get(0).zIndex);
    }

    @Test
    public void v2PlanRemainsReadableWithDefaultEditorState() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x43434c50);
        output.writeByte(2);
        writeStyledMode(output, 4, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 12));
        writeStyledMode(output, 3);
        output.flush();

        ControlCenterLayoutPlan restored = ControlCenterLayoutPlan.decode("CCLP2:"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()));
        assertNotNull(restored);
        ControlCenterLayoutPlan.Item item = restored.regular.items.get(0);
        assertEquals(ControlCenterLayoutPlan.Shape.CAPSULE, item.shape);
        assertEquals(12, item.cornerRadius);
        assertFalse(item.locked);
        assertFalse(item.hidden);
        assertEquals(0, item.zIndex);
    }

    @Test
    public void zIndexIsClampedToThePersistedRange() {
        ControlCenterLayoutPlan.Item high = ControlCenterLayoutPlan.Item.tile(
                "wifi", 0, 0, 1, 1).withZIndex(Integer.MAX_VALUE);
        ControlCenterLayoutPlan.Item low = ControlCenterLayoutPlan.Item.tile(
                "bt", 1, 0, 1, 1).withZIndex(Integer.MIN_VALUE);

        assertEquals(Short.MAX_VALUE, high.zIndex);
        assertEquals(Short.MIN_VALUE, low.zIndex);
    }

    @Test
    public void pinnedCoordinatesSurviveAndCollisionsMoveIntoFirstOpenCell() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1),
                ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("cell", 3, 0, 1, 1));
        assertEquals("COLLISION", original.regular.validate().get(0).code);

        ControlCenterLayoutPlan.Resolved resolved = original.resolve(false,
                List.of("wifi", "bt", "cell"));
        assertTrue(resolved.mode.validate().isEmpty());
        assertEquals(0, bySpec(resolved.items, "wifi").x);
        assertEquals(2, bySpec(resolved.items, "bt").x);
        assertEquals(3, bySpec(resolved.items, "cell").x);
        assertEquals(List.of("bt"), resolved.movedIds);
    }

    @Test
    public void pairMembershipIsUniqueAndUnavailableMemberFallsBackToTile() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair(
                "wifi", "bt", ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        ControlCenterLayoutPlan original = plan(pair,
                ControlCenterLayoutPlan.Item.tile("wifi", 2, 0, 1, 1));
        assertEquals("DUPLICATE_MEMBER", original.regular.validate().get(0).code);

        ControlCenterLayoutPlan.Resolved withBoth = original.resolve(false,
                List.of("wifi", "bt", "cell"));
        assertEquals(1, withBoth.items.size());
        assertEquals(ControlCenterLayoutPlan.Type.PAIR, withBoth.items.get(0).type);

        ControlCenterLayoutPlan.Resolved withoutBt = original.resolve(false,
                List.of("wifi", "cell"));
        assertEquals(ControlCenterLayoutPlan.Type.TILE,
                bySpec(withoutBt.items, "wifi").type);
        assertEquals(List.of("bt"), withoutBt.unavailableSpecs);
        assertTrue(withoutBt.mode.validate().isEmpty());
    }

    @Test
    public void pairAndUnpairReplaceMembershipWithoutDiscardingOtherItems() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("bt", 1, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("cell", 2, 0, 1, 1));
        ControlCenterLayoutPlan paired = original.withPair(false, "wifi", "bt",
                ControlCenterLayoutPlan.Direction.VERTICAL, 0, 0, 1, 2);
        assertEquals(2, paired.regular.items.size());
        assertEquals(1, paired.regular.items.stream()
                .filter(item -> item.type == ControlCenterLayoutPlan.Type.PAIR).count());
        String pairId = paired.regular.items.get(1).id;

        ControlCenterLayoutPlan unpaired = paired.unpair(false, pairId);
        assertEquals(3, unpaired.regular.items.size());
        assertTrue(unpaired.regular.validate().isEmpty());
        assertNotNull(bySpec(unpaired.regular.items, "cell"));
    }

    @Test
    public void unpairPreservesPairEditorStateOnBothChildren() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair(
                "wifi", "bt", ControlCenterLayoutPlan.Direction.HORIZONTAL,
                0, 0, 2, 1).withState(true, true, 12);
        ControlCenterLayoutPlan original = plan(pair);

        ControlCenterLayoutPlan unpaired = original.unpair(false, pair.id);
        ControlCenterLayoutPlan.Item first = bySpec(unpaired.regular.items, "wifi");
        ControlCenterLayoutPlan.Item second = bySpec(unpaired.regular.items, "bt");
        assertTrue(first.locked);
        assertTrue(first.hidden);
        assertEquals(12, first.zIndex);
        assertTrue(second.locked);
        assertTrue(second.hidden);
        assertEquals(12, second.zIndex);
    }

    @Test
    public void recomposingPairKeepsFormerPartnerAsTile() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                        ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1),
                ControlCenterLayoutPlan.Item.tile("cell", 2, 0, 1, 1));
        ControlCenterLayoutPlan revised = original.withPair(false, "wifi", "cell",
                ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        ControlCenterLayoutPlan.Resolved resolved = revised.resolve(false,
                List.of("wifi", "bt", "cell"));

        assertEquals(2, resolved.items.size());
        assertEquals(ControlCenterLayoutPlan.Type.TILE, bySpec(resolved.items, "bt").type);
        assertEquals(ControlCenterLayoutPlan.Type.PAIR,
                bySpec(resolved.items, "wifi").type);
        assertTrue(resolved.mode.validate().isEmpty());
    }

    @Test
    public void legacyOrderAndShapesMigrateIntoSeparateGrids() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withOrder("wifi,bt,cell")
                .withHidden("bt")
                .withColumns(5)
                .withTileShape("wifi", 4, 2);
        ControlCenterLayoutPlan migrated = ControlCenterLayoutPlan.fromConfig(settings,
                List.of("cell", "wifi", "bt"));

        assertEquals(5, migrated.regular.columns);
        assertEquals(3, migrated.compact.columns);
        assertEquals("wifi", migrated.regular.items.get(0).firstSpec);
        assertEquals(4, migrated.regular.items.get(0).width);
        assertEquals(3, migrated.compact.items.get(0).width);
        assertEquals(2, migrated.regular.items.size());
        assertTrue(migrated.regular.validate().isEmpty());
        assertTrue(migrated.compact.validate().isEmpty());

        ControlCenterConfig saved = settings.withLayoutPlan(migrated.encode());
        assertEquals(migrated.encode(), saved.layoutPlan);
        assertEquals(migrated.encode(), ControlCenterLayoutPlan.fromConfig(saved,
                List.of("wifi", "cell")).encode());
        assertEquals("", saved.clearCustomization().layoutPlan);
    }

    @Test
    public void defaultConfigStartsWithBlankGrid() {
        ControlCenterLayoutPlan blank = ControlCenterLayoutPlan.fromConfig(
                ControlCenterConfig.defaults(), List.of("wifi", "bt", "cell"));

        assertTrue(blank.regular.items.isEmpty());
        assertTrue(blank.compact.items.isEmpty());
        assertEquals(4, blank.regular.columns);
        assertEquals(4, blank.compact.columns);
    }

    @Test
    public void explicitCaptureReplacesExistingSavedPlan() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withLayoutPlan(plan(ControlCenterLayoutPlan.Item.tile(
                        "wifi", 0, 0, 1, 1)).encode());

        ControlCenterLayoutPlan captured = ControlCenterLayoutPlan.fromCaptured(
                settings, List.of("bt", "cell"));

        assertEquals(List.of("bt", "cell"), captured.regular.items.stream()
                .map(item -> item.firstSpec).toList());
    }

    @Test
    public void columnSettingReflowsRegularGridWithoutChangingCompactGrid() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 2, 0, 2, 1))
                .withItem(true, ControlCenterLayoutPlan.Item.tile("wifi", 1, 0, 2, 1));
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withLayoutPlan(original.encode()).withColumns(3);
        ControlCenterLayoutPlan resized = ControlCenterLayoutPlan.decode(settings.layoutPlan);

        assertNotNull(resized);
        assertEquals(3, resized.regular.columns);
        assertEquals(0, resized.regular.items.get(0).x);
        assertEquals(3, resized.compact.columns);
        assertEquals(1, resized.compact.items.get(0).x);
    }

    @Test
    public void legacyShapeControlsUpdateBothSavedGrids() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("bt", 1, 0, 1, 1))
                .withItem(true, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1));
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withLayoutPlan(original.encode()).withTileShape("wifi", 2, 2);
        ControlCenterLayoutPlan resized = ControlCenterLayoutPlan.decode(settings.layoutPlan);

        assertNotNull(resized);
        assertEquals(2, settings.tileWidth("wifi"));
        assertEquals(2, bySpec(resized.regular.items, "wifi").width);
        assertEquals(2, bySpec(resized.compact.items, "wifi").height);
        assertTrue(resized.regular.validate().isEmpty());

        ControlCenterConfig reset = settings.withoutTileShape("wifi");
        ControlCenterLayoutPlan restored = ControlCenterLayoutPlan.decode(reset.layoutPlan);
        assertNotNull(restored);
        assertEquals(1, bySpec(restored.regular.items, "wifi").width);
        assertEquals(1, bySpec(restored.compact.items, "wifi").height);
    }

    @Test
    public void corruptOrUnknownDataFallsBackToLegacyMigration() {
        assertNull(ControlCenterLayoutPlan.decode("CCLP2:abcd"));
        assertNull(ControlCenterLayoutPlan.decode("CCLP1:abc"));
        assertNull(ControlCenterLayoutPlan.decode("CCLP1:" + "x".repeat(200_000)));
        ControlCenterConfig config = ControlCenterConfig.defaults().withOrder("wifi,bt")
                .withLayoutPlan("CCLP2:abcd");
        assertEquals("", config.layoutPlan);
        assertFalse(ControlCenterLayoutPlan.fromConfig(config,
                List.of("wifi", "bt")).regular.items.isEmpty());
    }

    @Test
    public void extremeCoordinatesAreReportedAndSafelyReflowed() {
        ControlCenterLayoutPlan original = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", Integer.MAX_VALUE,
                        Integer.MAX_VALUE, 2, 1));

        assertEquals("BOUNDS", original.regular.validate().get(0).code);
        ControlCenterLayoutPlan.Resolved resolved = original.resolve(false, List.of("wifi"));
        assertEquals(0, resolved.items.get(0).x);
        assertEquals(0, resolved.items.get(0).y);
        assertTrue(resolved.mode.validate().isEmpty());
    }

    @Test
    public void persistedRowsUseTheRuntimeGridLimit() {
        ControlCenterLayoutPlan valid = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0,
                        ControlCenterLayoutPlan.MAX_ROWS - 1, 1, 1));
        assertNotNull(ControlCenterLayoutPlan.decode(valid.encode()));

        ControlCenterLayoutPlan invalid = plan(
                ControlCenterLayoutPlan.Item.tile("wifi", 0,
                        ControlCenterLayoutPlan.MAX_ROWS, 1, 1));
        assertEquals("BOUNDS", invalid.regular.validate().get(0).code);
        assertEquals(0, invalid.resolve(false, List.of("wifi")).items.get(0).y);
    }

    private static ControlCenterLayoutPlan.Item bySpec(
            List<ControlCenterLayoutPlan.Item> items, String spec) {
        for (ControlCenterLayoutPlan.Item item : items) {
            if (item.firstSpec.equals(spec)) return item;
        }
        throw new AssertionError("Missing item: " + spec);
    }

    private static void writeLegacyMode(DataOutputStream output, int columns,
            ControlCenterLayoutPlan.Item... items) throws Exception {
        output.writeByte(columns);
        output.writeShort(items.length);
        for (ControlCenterLayoutPlan.Item item : items) {
            output.writeByte(item.type.ordinal());
            output.writeByte(item.direction.ordinal());
            output.writeShort(item.x);
            output.writeShort(item.y);
            output.writeByte(item.width);
            output.writeByte(item.height);
            output.writeUTF(item.firstSpec);
            output.writeUTF(item.secondSpec);
        }
    }

    private static void writeStyledMode(DataOutputStream output, int columns,
            ControlCenterLayoutPlan.Item... items) throws Exception {
        output.writeByte(columns);
        output.writeShort(items.length);
        for (ControlCenterLayoutPlan.Item item : items) {
            output.writeByte(item.type.ordinal());
            output.writeByte(item.direction.ordinal());
            output.writeByte(item.shape.ordinal());
            output.writeByte(item.cornerRadius);
            output.writeShort(item.x);
            output.writeShort(item.y);
            output.writeByte(item.width);
            output.writeByte(item.height);
            output.writeUTF(item.firstSpec);
            output.writeUTF(item.secondSpec);
        }
    }
}
