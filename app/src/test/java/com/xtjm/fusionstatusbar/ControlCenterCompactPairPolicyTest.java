package com.xtjm.fusionstatusbar;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ControlCenterCompactPairPolicyTest {
    private static ControlCenterConfig configured() {
        ControlCenterLayoutPlan.Mode regular = new ControlCenterLayoutPlan.Mode(4,
                List.of(ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1)));
        ControlCenterLayoutPlan.Mode compact = new ControlCenterLayoutPlan.Mode(3, List.of(
                ControlCenterLayoutPlan.Item.pair("bt", "airplane",
                        ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1),
                ControlCenterLayoutPlan.Item.pair("wifi", "cell",
                        ControlCenterLayoutPlan.Direction.VERTICAL, 2, 0, 1, 2)));
        return ControlCenterConfig.defaults().withEnabled(true).withLayoutPlan(
                ControlCenterLayoutPlan.of(regular, compact).encode());
    }

    @Test
    public void onlyFirstConfiguredPairCanUseNativeCompactCard() {
        ControlCenterConfig config = configured();
        assertNull(ControlCenterCompactPairPolicy.firstAvailablePair(config,
                List.of("wifi", "cell")));
        ControlCenterLayoutPlan.Item selected =
                ControlCenterCompactPairPolicy.firstAvailablePair(config,
                        List.of("WIFI", "BT", "AIRPLANE", "cell"));
        assertEquals("bt", selected.firstSpec);
        assertEquals("airplane", selected.secondSpec);
        assertNull(ControlCenterCompactPairPolicy.firstAvailablePair(
                config.withHidden("bt"), List.of("bt", "airplane")));
        assertNull(ControlCenterCompactPairPolicy.firstAvailablePair(
                config.withEnabled(false), List.of("bt", "airplane")));
    }

    @Test
    public void removesOnlyMembersOfActuallyCreatedPair() {
        ControlCenterLayoutPlan.Item pair = ControlCenterCompactPairPolicy.firstAvailablePair(
                configured(), List.of("bt", "airplane"));
        List<String> original = Arrays.asList("wifi", "bt", "cell", "airplane");
        List<String> filtered = ControlCenterCompactPairPolicy.withoutPair(original, pair,
                value -> value);
        assertEquals(List.of("wifi", "cell"), filtered);
        assertEquals(4, original.size());
        assertSame(original.get(0), filtered.get(0));
        assertEquals(original, ControlCenterCompactPairPolicy.withoutPair(original, null,
                value -> value));
        assertEquals(true, ControlCenterCompactPairPolicy.hasMembers(pair,
                "BT", "airplane"));
    }

    @Test
    public void clearedPlanStopsUsingPreviouslyCreatedCompactPair() {
        ControlCenterConfig config = configured();
        ControlCenterLayoutPlan.Item created =
                ControlCenterCompactPairPolicy.firstAvailablePair(config,
                        List.of("bt", "airplane"));

        assertEquals(created.id,
                ControlCenterCompactPairPolicy.activePair(config, created).id);
        assertNull(ControlCenterCompactPairPolicy.activePair(
                config.clearCustomization(), created));
        assertEquals(true, ControlCenterCompactPairPolicy.needsRebuild(
                config.clearCustomization(), created));
        assertEquals(false, ControlCenterCompactPairPolicy.needsRebuild(
                config, created));
        assertEquals(true, ControlCenterCompactPairPolicy.needsRebuild(
                config, null));
        ControlCenterLayoutPlan changed = ControlCenterLayoutPlan.decode(config.layoutPlan)
                .removeItem(true, created.id)
                .withPair(true, "wifi", "cell",
                        ControlCenterLayoutPlan.Direction.HORIZONTAL, 0, 0, 2, 1);
        assertNull(ControlCenterCompactPairPolicy.activePair(
                config.withLayoutPlan(changed.encode()), created));
    }
}
