package com.xtjm.fusionstatusbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ControlCenterImportedConfigRegressionTest {
    private static final String GROUP = "group:2e871f18-cfc0-46f2-8393-0bc1f49b15fe";
    private static final String[] SPECS = {
            "wifi", "cell", "control:media", "control:brightness", "control:volume",
            "control:device-center", "bt", "custom_5g", GROUP, "autobrightness", "papermode",
            "night", "hotspot", "custom(com.miui.mishare.connectivity/.tile.misharetileservice)",
            "custom(com.miui.screenrecorder/.service.quickservice)"
    };
    private static final int[][] POSITIONS = {
            {0, 0, 2, 1}, {2, 0, 2, 1}, {0, 1, 2, 2}, {2, 1, 1, 2}, {3, 1, 1, 2},
            {0, 3, 4, 1}, {0, 4, 2, 1}, {2, 4, 1, 1}, {3, 4, 1, 3}, {0, 5, 1, 1},
            {1, 5, 1, 1}, {2, 5, 1, 1}, {0, 6, 1, 1}, {1, 6, 1, 1}, {2, 6, 1, 1}
    };
    private static final int[] RADII = {0, 24, 28, 24, 24, 24, 0, 0, 0, 0, 0, 0, 36, 0, 0};

    @Test public void suppliedLegacyBackupRetainsEveryItemAndGroupMember() throws Exception {
        ControlCenterConfig settings = importedConfig().controlCenter;
        assertEquals(ControlCenterSurface.DEFAULT, settings.surface);
        assertFalse(settings.borderEnabled);
        assertLayout(settings, false);
    }

    @Test public void capsuleAndGlassEditsSurvivePublicationRestartAndBackup() throws Exception {
        ControlCenterConfig settings = loadedSettings();
        assertEquals(ControlCenterSurface.SOFT_GLASS, settings.surface);
        assertTrue(settings.borderEnabled);
        assertEquals(1, settings.borderWidthDp);
        assertLayout(settings, true);

        ControlCenterConfig exported = ConfigurationBackupCodec.decode(
                ConfigurationBackupCodec.encode(FusionConfig.defaults().withControlCenter(settings)))
                .controlCenter;
        assertEquals(settings.layoutPlan, exported.layoutPlan);
        assertEquals(settings.surface, exported.surface);
        assertEquals(settings.borderEnabled, exported.borderEnabled);
        assertEquals(settings.borderWidthDp, exported.borderWidthDp);
        assertLayout(exported, true);
    }

    static ControlCenterConfig loadedSettings() throws Exception {
        FusionConfig imported = importedConfig();
        ControlCenterConfig settings = imported.controlCenter;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        assertNotNull(plan);
        for (String spec : List.of("wifi", "cell")) {
            ControlCenterLayoutPlan.Item item = item(plan, spec);
            plan = plan.withItem(false, item.withShape(ControlCenterLayoutPlan.Shape.CAPSULE,
                    item.cornerRadius));
        }
        settings = settings.withLayoutPlan(plan.forPublication(false).encode())
                .withSurface(ControlCenterSurface.SOFT_GLASS).withGlobalBorder(true, 1);
        FusionConfig restarted = FusionConfigStore.decode(
                FusionConfigStore.encode(imported.withControlCenter(settings)));
        return FusionConfig.fromBundle(restarted.toBundle()).controlCenter;
    }

    private static FusionConfig importedConfig() throws IOException {
        try (InputStream input = ControlCenterImportedConfigRegressionTest.class.getResourceAsStream(
                "/control-center-config/import-20261004.json")) {
            assertNotNull("The supplied import fixture must be on the test classpath", input);
            return ConfigurationBackupCodec.read(input);
        }
    }

    private static void assertLayout(ControlCenterConfig settings, boolean edited) {
        assertTrue(settings.enabled);
        assertEquals(4, settings.columns);
        assertEquals(14, settings.spacing);
        assertEquals(100, settings.tileScale);
        assertEquals(28, settings.cornerRadius);
        assertEquals(100, settings.backgroundBlur);
        assertEquals(100, settings.cardBlur);
        assertEquals(100, settings.tileBlur);
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        assertNotNull(plan);
        assertEquals(ControlCenterLayoutPlan.RuntimeLayout.REGULAR, plan.runtimeLayout);
        assertEquals(4, plan.regular.columns);
        assertEquals(4, plan.compact.columns);
        assertTrue(plan.compact.items.isEmpty());
        assertTrue(plan.regular.validate().isEmpty());
        assertEquals(SPECS.length, plan.regular.items.size());
        for (int i = 0; i < SPECS.length; i++) {
            ControlCenterLayoutPlan.Item item = plan.regular.items.get(i);
            assertEquals(SPECS[i], item.id);
            assertArrayEquals(SPECS[i], POSITIONS[i],
                    new int[] {item.x, item.y, item.width, item.height});
            ControlCenterLayoutPlan.Shape shape = i < 6 ? ControlCenterLayoutPlan.Shape.RECTANGLE
                    : i == 6 || i == 8 ? ControlCenterLayoutPlan.Shape.CAPSULE
                    : ControlCenterLayoutPlan.Shape.CIRCLE;
            if (edited && i < 2) shape = ControlCenterLayoutPlan.Shape.CAPSULE;
            assertEquals(SPECS[i], shape, item.shape);
            assertEquals(SPECS[i], RADII[i], item.cornerRadius);
            assertFalse(SPECS[i], item.hidden);
            assertFalse(SPECS[i], item.locked);
        }
        ControlCenterGroupData group = item(plan, GROUP).group;
        assertNotNull(group);
        assertEquals(ControlCenterGroupData.Arrangement.CUSTOM, group.arrangement);
        assertEquals(ControlCenterGroupData.Surface.CLEAR, group.surface);
        assertTrue(group.border);
        assertEquals(List.of(
                new ControlCenterGroupData.Member("custom(com.github.surfing/.service.surfingtileservice)",
                        0, 0, 12, 4, false, 100),
                new ControlCenterGroupData.Member("custom(tornaco.apps.shortx/.ui.service.tile.tile2)",
                        0, 4, 12, 4, false, 100),
                new ControlCenterGroupData.Member("custom(com.example.a11ytextfinder/.findertileservice)",
                        0, 8, 12, 4, false, 100)), group.members);
    }

    private static ControlCenterLayoutPlan.Item item(ControlCenterLayoutPlan plan, String spec) {
        return plan.regular.items.stream().filter(candidate -> candidate.id.equals(spec))
                .findFirst().orElseThrow(() -> new AssertionError("Missing imported item: " + spec));
    }
}
