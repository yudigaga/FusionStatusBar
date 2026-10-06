package com.xtjm.fusionstatusbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class RuntimeConfigDiffTest {
    @Test public void identicalConfigDoesNotInvalidateAnySurface() {
        RuntimeConfigDiff diff = RuntimeConfigDiff.between(FusionConfig.defaults(),
                FusionConfig.defaults().withRevision(19));
        assertFalse(diff.statusBar);
        assertFalse(diff.statusClock);
        assertFalse(diff.notificationClock);
        assertFalse(diff.controlCenter());
        assertFalse(diff.systemMenus);
        assertFalse(diff.notificationSurfaces);
    }

    @Test public void statusOnlyChangeDoesNotRefreshControlCenterOrMenus() {
        RuntimeConfigDiff diff = RuntimeConfigDiff.between(FusionConfig.defaults(),
                FusionConfig.defaults().withIconScale(115));
        assertTrue(diff.statusBar);
        assertFalse(diff.statusClock);
        assertFalse(diff.notificationClock);
        assertFalse(diff.controlCenter());
        assertFalse(diff.systemMenus);
        assertFalse(diff.notificationSurfaces);
    }

    @Test public void statusClockSizeOnlyRefreshesTheStatusClock() {
        RuntimeConfigDiff diff = RuntimeConfigDiff.between(FusionConfig.defaults(),
                FusionConfig.defaults().withStatusClockSizeSp(18));
        assertTrue(diff.statusClock);
        assertFalse(diff.statusBar);
        assertFalse(diff.notificationClock);
        assertFalse(diff.controlCenter());
        assertFalse(diff.systemMenus);
        assertFalse(diff.notificationSurfaces);
    }

    @Test public void surfaceChangeIsScopedToTheAffectedSurface() {
        ControlCenterConfig oldSettings = ControlCenterConfig.defaults();
        ControlCenterConfig next = oldSettings.withSystemSurfaces(
                oldSettings.systemSurfaces.withNotifications(false));
        RuntimeConfigDiff diff = RuntimeConfigDiff.between(
                FusionConfig.defaults().withControlCenter(oldSettings),
                FusionConfig.defaults().withControlCenter(next));
        assertFalse(diff.statusBar);
        assertTrue(diff.notificationSurfaces);
        assertFalse(diff.systemMenus);
        assertFalse(diff.controlCenterLayout);
    }

    @Test public void controlCenterHeaderVisibilityChangeDoesNotRebuildTheTileGrid() {
        ControlCenterConfig oldSettings = ControlCenterConfig.defaults();
        ControlCenterConfig next = oldSettings.withHideDate(true).withHideCarrier(true);

        RuntimeConfigDiff diff = RuntimeConfigDiff.between(
                FusionConfig.defaults().withControlCenter(oldSettings),
                FusionConfig.defaults().withControlCenter(next));

        assertTrue(diff.controlCenterHeader);
        assertFalse(diff.controlCenterLayout);
        assertFalse(diff.controlCenterMaterials);
        assertFalse(diff.controlCenter());
        assertFalse(diff.systemMenus);
    }
}
