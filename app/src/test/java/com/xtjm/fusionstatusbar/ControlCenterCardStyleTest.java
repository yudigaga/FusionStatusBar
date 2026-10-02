package com.xtjm.fusionstatusbar;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlCenterCardStyleTest {
    @Test public void presetsOnlyChangeFieldsAlreadyUnderstoodByRuntime() {
        ControlCenterLayoutPlan.Item original = ControlCenterLayoutPlan.Item
                .tile("wifi", 1, 2, 2, 1).withState(false, true, 7);
        for (ControlCenterCardStyle style : ControlCenterCardStyle.values()) {
            ControlCenterLayoutPlan.Item item = style.apply(original);
            assertEquals(original.id, item.id);
            assertEquals(original.type, item.type);
            assertEquals(original.x, item.x);
            assertEquals(original.y, item.y);
            assertEquals(original.width, item.width);
            assertEquals(original.height, item.height);
            assertEquals(original.zIndex, item.zIndex);
            assertEquals(original.hidden, item.hidden);
            assertEquals(original.locked, item.locked);
            ControlCenterLayoutPlan.Mode mode = new ControlCenterLayoutPlan.Mode(4, Collections.singletonList(item));
            ControlCenterLayoutPlan decoded = ControlCenterLayoutPlan.decode(ControlCenterLayoutPlan.of(mode, mode).encode());
            assertEquals(item.shape, decoded.regular.items.get(0).shape);
            assertEquals(item.cornerRadius, decoded.regular.items.get(0).cornerRadius);
            if (style != ControlCenterCardStyle.CUSTOM) assertSame(style, ControlCenterCardStyle.of(item));
        }
    }

    @Test public void manualRadiusIsRecognizedWithoutLosingItsValue() {
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 17);
        assertSame(ControlCenterCardStyle.CUSTOM, ControlCenterCardStyle.of(item));
        assertSame(item, ControlCenterCardStyle.CUSTOM.apply(item));
        assertEquals(0, ControlCenterCardStyle.DEFAULT.apply(item).cornerRadius);
    }

    @Test public void pairsKeepBothMembersDirectionAndGeometry() {
        ControlCenterLayoutPlan.Item pair = ControlCenterLayoutPlan.Item.pair("wifi", "bt",
                ControlCenterLayoutPlan.Direction.VERTICAL, 0, 0, 1, 2);
        ControlCenterLayoutPlan.Item changed = ControlCenterCardStyle.CAPSULE.apply(pair);
        assertEquals(pair.id, changed.id);
        assertEquals(pair.secondSpec, changed.secondSpec);
        assertEquals(pair.direction, changed.direction);
        assertEquals(pair.width, changed.width);
        assertEquals(pair.height, changed.height);
    }
}
