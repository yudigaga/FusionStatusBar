package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenter5GRuntimeTest {
    @Test public void runtimeGridCreatesAndReleasesModuleOwnedTileWithoutNativeRecord() throws Exception {
        ControlCenterRuntimeAvailabilityTest.Adapter adapter =
                new ControlCenterRuntimeAvailabilityTest.Adapter();
        adapter.redistribute(false);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.FIVE_G, 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withLayoutPlan(ControlCenterLayoutPlan.blank(4)
                        .withMode(false, new ControlCenterLayoutPlan.Mode(4, List.of(item)))
                        .forPublication(false).encode());

        assertTrue(ControlCenterRuntimeGrid.apply(adapter, settings, getClass().getClassLoader()));
        Object state = ControlCenterRuntimeGrid.retain(adapter);
        assertNotNull(state);
        Field outerHolder = state.getClass().getDeclaredField("outerHolder");
        outerHolder.setAccessible(true);
        outerHolder.set(state, new GridHolder(new FrameLayout(RuntimeEnvironment.getApplication())));
        Method bind = state.getClass().getDeclaredMethod("bind");
        bind.setAccessible(true);
        bind.invoke(state);

        Field gridField = state.getClass().getDeclaredField("grid");
        gridField.setAccessible(true);
        ControlCenterRuntimeGrid.GridView grid = (ControlCenterRuntimeGrid.GridView) gridField.get(state);
        assertNotNull(grid);
        assertEquals(1, grid.getChildCount());
        assertTrue(grid.getChildAt(0) instanceof ControlCenter5GTileView);
        assertTrue(adapter.qsListController.addedTiles.stream()
                .noneMatch(record -> "custom_5g".equals(record.getSpec())));

        ControlCenter5GTileView tile = (ControlCenter5GTileView) grid.getChildAt(0);
        assertTrue(tile.hasOnClickListeners());
        ControlCenterRuntimeGrid.clear(adapter, false);
        assertFalse(tile.hasOnClickListeners());
    }

    @Test public void disabledFiveGComponentIsOmittedFromTheRuntimeGrid() throws Exception {
        ControlCenterRuntimeAvailabilityTest.Adapter adapter =
                new ControlCenterRuntimeAvailabilityTest.Adapter();
        adapter.redistribute(false);
        ControlCenterLayoutPlan.Item fiveG = ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.FIVE_G, 1, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
        ControlCenterLayoutPlan.Item wifi = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withHidden(ControlCenterComponentSpec.FIVE_G)
                .withLayoutPlan(ControlCenterLayoutPlan.blank(4)
                        .withMode(false, new ControlCenterLayoutPlan.Mode(4, List.of(wifi, fiveG)))
                        .forPublication(false).encode());

        assertTrue(ControlCenterRuntimeGrid.apply(adapter, settings, getClass().getClassLoader()));
        Object state = ControlCenterRuntimeGrid.retain(adapter);
        assertNotNull(state);
        @SuppressWarnings("unchecked")
        List<ControlCenterLayoutPlan.Item> installed = (List<ControlCenterLayoutPlan.Item>)
                ControlCenterRuntimeGrid.readField(state, "items");
        assertEquals(1, installed.size());
        assertEquals("wifi", installed.get(0).firstSpec);
        assertFalse(installed.stream().anyMatch(item ->
                ControlCenterComponentSpec.FIVE_G.equals(item.firstSpec)));
    }

    public static final class GridHolder {
        public final FrameLayout itemView;
        GridHolder(FrameLayout itemView) { this.itemView = itemView; }
        public boolean getAttached$miui_controlcenter_release() { return false; }
    }
}
