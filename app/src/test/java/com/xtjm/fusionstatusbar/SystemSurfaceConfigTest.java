package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SystemSurfaceConfigTest {
    @Test public void olderSnapshotsEnableAppearanceOwnershipWithoutChangingMenuLayouts() {
        SystemSurfaceConfig defaults = SystemSurfaceConfig.defaults();
        assertTrue(defaults.powerMenu);
        assertTrue(defaults.volumeDialog);
        assertTrue(defaults.notifications);
        assertTrue(defaults.headsUp);
        assertFalse(defaults.extendedPowerMenu);
        assertFalse(defaults.compactVolume);
        assertEquals(defaults, ControlCenterConfig.fromBundle(new Bundle()).systemSurfaces);
        assertEquals(defaults, SystemSurfaceConfig.fromBundle(null));
        assertEquals(defaults, SystemSurfaceConfig.read(null));
        assertEquals(defaults, ControlCenterConfig.defaults().withSystemSurfaces(null).systemSurfaces);
    }

    @Test public void ownershipAndMenuChoicesSurviveCopiesAndSettingsTransports() {
        SystemSurfaceConfig settings = changed();
        ControlCenterConfig source = ControlCenterConfig.defaults().withSystemSurfaces(settings);
        ControlCenterConfig copied = source.withEnabled(true).withOrder("wifi").withHidden("bt")
                .withColumns(5).withTileScale(110).withCornerRadius(22).withSpacing(8).withStyle(1)
                .withTileLayout("").withLayoutPlan(ControlCenterLayoutPlan.blank(4).encode())
                .withComponentOrder("control:media").withComponentSide("control:media", "left")
                .withBlur(12, 34, 56).withTileShape("wifi", 2, 1).withoutTileShape("wifi")
                .withSurface(ControlCenterSurface.DARK).withGlobalBorder(true, 3);
        assertEquals(settings, copied.systemSurfaces);
        assertEquals(settings, copied.clearCustomization().systemSurfaces);

        Bundle bundle = new Bundle();
        copied.writeTo(bundle);
        assertEquals(settings, ControlCenterConfig.fromBundle(bundle).systemSurfaces);
        var preferences = RuntimeEnvironment.getApplication().getSharedPreferences("system-surfaces", 0);
        var editor = preferences.edit();
        copied.writeTo(editor);
        assertTrue(editor.commit());
        assertEquals(settings, ControlCenterConfig.read(preferences).systemSurfaces);
    }

    @Test public void completeSnapshotPersistenceKeepsTheNewKeyPrefix() {
        FusionConfig source = FusionConfig.defaults().withControlCenter(
                ControlCenterConfig.defaults().withSystemSurfaces(changed()));
        FusionConfigStore.WriteResult result = FusionConfigStore.write(
                RuntimeEnvironment.getApplication(), source);
        assertTrue(result.success);
        assertEquals(changed(), FusionConfigStore.read(RuntimeEnvironment.getApplication())
                .controlCenter.systemSurfaces);
    }

    @Test public void draftHistoryRestoresMenuChoicesWithoutPublishingThem() {
        ControlCenterConfig baseline = ControlCenterConfig.defaults().withLayoutPlan(
                ControlCenterLayoutPlan.blank(4).forPublication(false).encode());
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline);
        assertTrue(session.edit(baseline.withSystemSurfaces(changed())));
        assertTrue(session.isDirty());
        assertTrue(session.undo());
        assertEquals(SystemSurfaceConfig.defaults(), session.draft().systemSurfaces);
        ControlCenterEditorSession restored = ControlCenterEditorSession.restore(
                baseline, session.serialize());
        assertTrue(restored.redo());
        assertEquals(changed(), restored.draft().systemSurfaces);
        assertEquals(SystemSurfaceConfig.defaults(), restored.baseline().systemSurfaces);
    }

    private static SystemSurfaceConfig changed() {
        return SystemSurfaceConfig.defaults().withPowerMenu(false).withVolumeDialog(false)
                .withNotifications(false).withHeadsUp(false).withExtendedPowerMenu(true)
                .withCompactVolume(true);
    }
}
