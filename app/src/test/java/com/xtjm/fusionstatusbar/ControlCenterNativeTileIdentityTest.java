package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterNativeTileIdentityTest {
    @Test public void modesAndBothDoNotDisturbTilesKeepTheirNativeFactoriesThroughLayoutRoundTrip() {
        var mode = new ControlCenterLayoutPlan.Mode(4, List.of(
                ControlCenterLayoutPlan.Item.tile("dnd", 0, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("quietmode", 1, 0, 1, 1),
                ControlCenterLayoutPlan.Item.tile("modes_dnd", 2, 0, 1, 1)));
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(
                ControlCenterLayoutPlan.of(mode, mode).encode());
        assertNotNull(plan);
        assertEquals(List.of("dnd", "quietmode", "modes_dnd"),
                plan.regular.items.stream().map(item -> item.firstSpec).toList());

        LinkedHashMap<String, Object> available = new LinkedHashMap<>();
        LinkedHashMap<String, ControlCenterAppTileSource.Lease> pending = new LinkedHashMap<>();
        ArrayList<String> created = new ArrayList<>();
        ControlCenterAppTileSource.acquire(plan.regular.items, available, java.util.Map.of(), pending,
                () -> { fail("Native system tiles must not depend on application discovery"); return List.of(); },
                spec -> {
                    created.add(spec);
                    return new ControlCenterAppTileSource.Lease(new Object(), () -> { });
                });
        assertEquals(List.of("dnd", "quietmode", "modes_dnd"), created);
        assertEquals(java.util.Set.of("dnd", "quietmode", "modes_dnd"), available.keySet());
        ControlCenterAppTileSource.release(pending);
    }

    @Test public void hidingModesDoesNotHideEitherExistingDoNotDisturbTile() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withOrder("dnd,quietmode,modes_dnd").withHidden("dnd");

        assertEquals(List.of("dnd", "quietmode", "modes_dnd"), new ArrayList<>(settings.orderedSpecs()));
        assertTrue(settings.isHidden("dnd"));
        assertFalse(settings.isHidden("quietmode"));
        assertFalse(settings.isHidden("modes_dnd"));
        assertTrue(ControlCenterConfig.defaults().withHidden("quietmode").isHidden("quietmode"));
        assertFalse(ControlCenterConfig.defaults().withHidden("quietmode").isHidden("dnd"));
    }
}
