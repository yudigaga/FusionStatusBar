package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterUnknownBlurModuleCanvasTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void ownedCanvasRefreshReleasesUnknownOwnershipWhenTheUserSelectsManualGlass() {
        ControlCenterMaterialsTest.QSTileItemIconView owner = new ControlCenterMaterialsTest.QSTileItemIconView();
        owner.viewMode = 0; owner.backgroundMode = 0; owner.radius = 0;
        ControlCenterMaterials.registerOwnedContainer(owner, automatic(30));
        assertEquals(0, owner.viewMode); assertEquals(0, owner.radius);
        ControlCenterMaterials.refresh(automatic(100).withSurface(ControlCenterSurface.SOFT_GLASS));
        assertEquals("Manual glass retains the existing owned-container mode", 2, owner.viewMode);
        assertEquals(275, owner.radius);
        ControlCenterMaterials.refresh(automatic(30));
        assertEquals(0, owner.viewMode); assertEquals(0, owner.radius);
        ControlCenterMaterials.refresh(automatic(30).withEnabled(false));
        assertEquals(0, owner.viewMode); assertEquals(0, owner.backgroundMode); assertEquals(0, owner.radius);
    }

    @Test public void unknownAutomaticGroupKeepsItsExistingCanvasAndCustomLabelInsteadOfInventingAnOemImage() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.group("group:test",
                ControlCenterGroupData.empty().add("wifi"), 0, 0, 2, 1);
        ControlCenterGroupView group = new ControlCenterGroupView(activity, item, member -> {
            TextView label = new TextView(activity); label.setTextColor(Color.MAGENTA); return label;
        });
        GradientDrawable original = (GradientDrawable) group.getBackground();
        int groupColor = original.getColor().getDefaultColor();
        try {
            for (int amount : new int[] {30, 100, 30}) {
                ControlCenterConfig config = automatic(amount);
                ControlCenterMaterials.registerRoot(group, config); ControlCenterMaterials.refresh(config);
                assertSame(original, group.getBackground());
                assertEquals(groupColor, original.getColor().getDefaultColor());
                assertEquals(255, original.getAlpha());
                assertEquals(Color.MAGENTA, ((TextView) group.getChildAt(0)).getCurrentTextColor());
            }
            ControlCenterMaterials.refresh(automatic(0)); assertEquals(0, original.getAlpha());
            ControlCenterMaterials.refresh(automatic(30)); assertEquals(255, original.getAlpha());
        } finally { activity.finish(); }
    }

    @Test public void unknownAutomaticFiveGKeepsDefaultActiveInactiveColorsAndItsUserGlyph() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FakeRadio radio = new FakeRadio();
        ControlCenter5GTileView tile = new ControlCenter5GTileView(activity, new ControlCenter5GController(radio));
        activity.setContentView(tile);
        TextView label = (TextView) tile.getChildAt(0);
        GradientDrawable original = (GradientDrawable) tile.getBackground();
        try {
            for (boolean active : new boolean[] {false, true, false}) {
                if (radio.enabled != active) assertTrue(tile.performClick());
                ControlCenterConfig config = automatic(30);
                int requestedFill = active ? 0xfff8f9fb : 0xff5e636b;
                int requestedGlyph = active ? 0xff3482f6 : Color.WHITE;
                assertEquals("The module itself must bind the requested network state", active, tile.networkEnabled());
                assertEquals("The module's DEFAULT fill follows its network state before material refresh",
                        requestedFill, original.getColor().getDefaultColor());
                assertEquals("The module's active glyph stays blue and inactive glyph stays white before material refresh",
                        requestedGlyph, label.getCurrentTextColor());
                // Robolectric has no installed Xposed setter hooks; deliver the same raw requests through their real entries.
                original.setColor(ControlCenterMaterials.drawableColor(original, requestedFill, config));
                label.setTextColor(ControlCenterMaterials.textColor(label, requestedGlyph, config));
                ControlCenterMaterials.registerRoot(tile, config); ControlCenterMaterials.refresh(config);
                assertSame(original, tile.getBackground());
                assertEquals(requestedFill, original.getColor().getDefaultColor());
                assertEquals(requestedGlyph, label.getCurrentTextColor());
                assertEquals(255, original.getAlpha());
            }
            label.setTextColor(ControlCenterMaterials.textColor(label, Color.MAGENTA, automatic(30)));
            ControlCenterMaterials.refresh(automatic(100)); assertEquals(Color.MAGENTA, label.getCurrentTextColor());
            ControlCenterMaterials.refresh(automatic(0)); assertEquals(0, original.getAlpha());
            ControlCenterMaterials.refresh(automatic(30).withEnabled(false)); assertEquals(255, original.getAlpha());
            ControlCenterMaterials.refresh(automatic(30)); assertEquals(255, original.getAlpha());
            assertEquals(Color.MAGENTA, label.getCurrentTextColor());
        } finally { tile.release(); activity.finish(); }
    }

    private static ControlCenterConfig automatic(int amount) {
        return ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(false, 1)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(amount, amount, amount);
    }
    private static final class FakeRadio implements ControlCenter5GController.RadioAccess {
        boolean enabled;
        @Override public long supportedNetworkTypes() {
            return TelephonyManager.NETWORK_TYPE_BITMASK_NR | TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        }
        @Override public long allowedNetworkTypes() {
            return TelephonyManager.NETWORK_TYPE_BITMASK_LTE | (enabled ? TelephonyManager.NETWORK_TYPE_BITMASK_NR : 0L);
        }
        @Override public void setAllowedNetworkTypes(long value) { enabled = (value & TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0; }
    }
}
