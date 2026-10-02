package com.xtjm.fusionstatusbar;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlCenterGroupDataTest {
    private ControlCenterLayoutPlan.Item item(ControlCenterGroupData data) {
        return ControlCenterLayoutPlan.Item.group("group:test", data, 0, 0, 2, 2);
    }
    private ControlCenterLayoutPlan plan(ControlCenterLayoutPlan.Item item) {
        return ControlCenterLayoutPlan.blank(4).withItem(false, item);
    }
    private ControlCenterGroupData seven() {
        ControlCenterGroupData data = ControlCenterGroupData.empty();
        for (String spec : List.of("wifi", "bt", "cell", "rotation", "mute", "nfc", "airplane")) data = data.add(spec);
        return data;
    }

    @Test public void blankContainersSurviveSaveResolveAndEveryItemMutation() {
        var empty = item(ControlCenterGroupData.empty());
        var changed = empty.withPosition(1, 2).withSize(2, 3).withDirection(ControlCenterLayoutPlan.Direction.VERTICAL)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 32).withZIndex(4).withLocked(true);
        var saved = ControlCenterLayoutPlan.decode(plan(changed).forPublication(false).encode());
        assertNotNull(saved);
        var resolved = saved.resolve(false, List.of("wifi"));
        assertEquals(1, resolved.items.size());
        var result = resolved.items.get(0);
        assertEquals("group:test", result.id);
        assertTrue(result.group.members.isEmpty());
        assertTrue(result.locked);
        assertEquals(32, result.cornerRadius);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        assertEquals(3, result.height);
        assertEquals(ControlCenterLayoutPlan.RuntimeLayout.REGULAR, saved.runtimeLayout);
    }

    @Test public void sevenMembersAndAllArrangementsRoundTripWithoutGeometryLoss() {
        for (ControlCenterGroupData.Arrangement arrangement : ControlCenterGroupData.Arrangement.values()) {
            var data = seven().arrange(arrangement).withAppearance(ControlCenterGroupData.Surface.CLEAR, false);
            var saved = ControlCenterLayoutPlan.decode(plan(item(data)).encode());
            assertNotNull(saved);
            assertEquals(data, saved.regular.items.get(0).group);
            assertTrue(saved.regular.validate().isEmpty());
        }
        var mixed = seven().arrange(ControlCenterGroupData.Arrangement.MIXED);
        assertEquals(6, mixed.members.get(2).width);
        assertEquals(3, mixed.members.get(3).width);
        assertEquals(9, mixed.members.get(6).x);
        assertEquals(9, mixed.members.get(6).y);
    }

    @Test public void unavailableRuntimeMembersDoNotDeleteContainerOrMoveRemainingSlots() {
        var data = seven().arrange(ControlCenterGroupData.Arrangement.MIXED);
        var resolved = plan(item(data)).resolve(false, List.of("wifi", "airplane"));
        assertEquals(1, resolved.items.size());
        assertEquals(2, resolved.items.get(0).group.members.size());
        assertEquals(data.members.get(6), resolved.items.get(0).group.members.get(1));
        assertEquals(5, resolved.unavailableSpecs.size());
        assertEquals(1, plan(item(data)).resolve(false, List.of()).items.size());
    }

    @Test public void movingStandaloneIntoGroupAndSplittingDoesNotDuplicateTiles() {
        var base = plan(item(ControlCenterGroupData.empty())).withItem(false, ControlCenterLayoutPlan.Item.tile("wifi", 3, 0, 1, 1));
        var grouped = base.withItem(false, item(ControlCenterGroupData.empty().add("wifi").add("bt")));
        assertEquals(1, grouped.regular.items.size());
        assertTrue(grouped.regular.validate().isEmpty());
        var split = ControlCenterLayoutPlan.decode(grouped.unpair(false, "group:test").encode());
        assertEquals(2, split.regular.items.size());
        assertTrue(split.regular.items.stream().allMatch(i -> i.type == ControlCenterLayoutPlan.Type.TILE));
        assertTrue(split.regular.validate().isEmpty());
    }

    @Test public void customMembersRejectOverlapOutOfBoundsComponentsAndDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> ControlCenterGroupData.empty().add("control:media"));
        assertThrows(IllegalArgumentException.class, () -> ControlCenterGroupData.empty().add("wifi").add("wifi"));
        assertThrows(IllegalArgumentException.class, () -> seven().add("flashlight"));
        assertThrows(IllegalArgumentException.class, () -> new ControlCenterGroupData.Member("wifi", 11, 0, 2, 1, false));
        var data = ControlCenterGroupData.empty().add("wifi").add("bt");
        assertThrows(IllegalArgumentException.class, () -> data.edit(new ControlCenterGroupData.Member("bt", 0, 0, 12, 6, false)));
    }

    @Test public void explicitCustomEditOrderAndAppearanceStayIndependent() {
        var data = ControlCenterGroupData.empty().add("wifi").add("bt");
        var custom = data.edit(new ControlCenterGroupData.Member("wifi", 0, 0, 6, 6, false));
        assertEquals(ControlCenterGroupData.Arrangement.CUSTOM, custom.arrangement);
        var changed = custom.withAppearance(ControlCenterGroupData.Surface.DARK, true);
        assertEquals(custom.members, changed.members);
        var reordered = changed.move(0, 1);
        assertEquals("bt", reordered.members.get(0).spec);
        assertEquals(6, reordered.members.get(0).width);
        assertEquals(1, reordered.remove("wifi").members.size());
    }

    @Test public void previousVersionFourPacketRemainsReadable() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(0x43434c50); out.writeByte(4);
        out.writeByte(4); out.writeShort(1);
        out.writeByte(0); out.writeByte(0); out.writeByte(2); out.writeByte(0);
        out.writeByte(0); out.writeShort(0); out.writeShort(0); out.writeShort(0);
        out.writeByte(1); out.writeByte(1); out.writeUTF("wifi"); out.writeUTF("");
        out.writeByte(3); out.writeShort(0); out.writeByte(1);
        var decoded = ControlCenterLayoutPlan.decode("CCLP2:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()));
        assertNotNull(decoded);
        assertEquals("wifi", decoded.regular.items.get(0).id);
        assertNull(decoded.regular.items.get(0).group);
        assertEquals(ControlCenterLayoutPlan.RuntimeLayout.REGULAR, decoded.runtimeLayout);
        assertNotNull(ControlCenterLayoutPlan.decode(decoded.encode()));
    }

    @Test public void legacyPerTileSizeDoesNotResizeTheContainingGroup() {
        var group = item(ControlCenterGroupData.empty().add("wifi"));
        var config = ControlCenterConfig.defaults().withLayoutPlan(plan(group).encode()).withTileShape("wifi", 4, 1);
        var saved = ControlCenterLayoutPlan.decode(config.layoutPlan).regular.items.get(0);
        assertEquals(2, saved.width);
        assertEquals(2, saved.height);
        assertEquals(group.group, saved.group);
    }

    @Test public void addingToFreeLayoutPreservesManualSlotsAndRejectsFullContainers() {
        var data = ControlCenterGroupData.empty().add("wifi")
                .edit(new ControlCenterGroupData.Member("wifi", 0, 0, 6, 6, true));
        var added = data.add("bt");
        assertEquals(data.members.get(0), added.members.get(0));
        assertEquals(ControlCenterGroupData.Arrangement.CUSTOM, added.arrangement);
        assertEquals(6, added.members.get(1).x);
        var full = ControlCenterGroupData.empty().add("wifi").arrange(ControlCenterGroupData.Arrangement.CUSTOM);
        assertThrows(IllegalArgumentException.class, () -> full.add("bt"));
    }
}
