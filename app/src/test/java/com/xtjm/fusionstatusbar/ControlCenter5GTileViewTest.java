package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.FrameLayout;
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
public class ControlCenter5GTileViewTest {
    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void runtimeMaterialRefreshPreservesModuleTileStateFillAndGlyph() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FakeRadio radio = new FakeRadio();
        radio.enabled = true;
        ControlCenter5GTileView tile = new ControlCenter5GTileView(activity,
                new ControlCenter5GController(radio));
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                activity, 4, 4, 18, 0, 1);
        grid.addTile(tile, ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.FIVE_G, 0, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
        QSTileItemView nativeCarrier = new QSTileItemView(activity);
        nativeCarrier.addView(grid);
        activity.setContentView(nativeCarrier);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS).withGlobalBorder(true, 1);

        grid.applyMaterials(settings);
        grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
        grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        ControlCenterMaterials.refresh(settings);
        assertEquals(ControlCenterMaterials.Layer.TILE, ControlCenterMaterials.layer(tile));
        assertEquals("Enabled 5G must use the same fill as an enabled system tile",
                ControlCenterSurface.SOFT_GLASS.stateColor(true),
                ((GradientDrawable) tile.getBackground()).getColor().getDefaultColor());
        assertEquals(0xff3482f6, ((TextView) tile.getChildAt(0)).getCurrentTextColor());

        assertTrue(tile.performClick());
        ControlCenterMaterials.refresh(settings);
        assertEquals(ControlCenterSurface.SOFT_GLASS.stateColor(false),
                ((GradientDrawable) tile.getBackground()).getColor().getDefaultColor());
        assertEquals(android.graphics.Color.WHITE,
                ((TextView) tile.getChildAt(0)).getCurrentTextColor());

        assertTrue(tile.performClick());
        grid.applyMaterials(settings);
        ControlCenterMaterials.refresh(settings);
        assertEquals(ControlCenterSurface.SOFT_GLASS.stateColor(true),
                ((GradientDrawable) tile.getBackground()).getColor().getDefaultColor());
        assertEquals(0xff3482f6, ((TextView) tile.getChildAt(0)).getCurrentTextColor());
        grid.releaseMaterials();
        tile.release();
        activity.finish();
    }

    @Test public void moduleTileRendersNetworkStateAndOperatesWithoutNativeQuickSettingsTile() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FakeRadio radio = new FakeRadio();
        ControlCenter5GTileView tile = new ControlCenter5GTileView(activity,
                new ControlCenter5GController(radio));
        activity.setContentView(tile);

        assertEquals("5G 已关闭", tile.getContentDescription());
        assertTrue(tile.performClick());
        assertTrue(radio.enabled);
        assertEquals("5G 已开启", tile.getContentDescription());
        assertEquals(0xfff8f9fb, ((GradientDrawable) tile.getBackground()).getColor().getDefaultColor());
        assertEquals(0xff3482f6, ((android.widget.TextView) tile.getChildAt(0)).getCurrentTextColor());
        ControlCenterCapturedStyle captured = ControlCenterStyleSampler.read(tile,
                ControlCenterComponentSpec.FIVE_G);
        assertEquals(ControlCenterLayoutPlan.Shape.CIRCLE, captured.shape);
        assertEquals(2, captured.state);
        assertEquals(GradientDrawable.OVAL, ((GradientDrawable) tile.getBackground()).getShape());

        assertTrue(tile.performClick());
        assertFalse(radio.enabled);
        assertEquals("5G 已关闭", tile.getContentDescription());
        assertEquals(0xff5e636b, ((GradientDrawable) tile.getBackground()).getColor().getDefaultColor());
        assertEquals(android.graphics.Color.WHITE,
                ((android.widget.TextView) tile.getChildAt(0)).getCurrentTextColor());
        tile.release();
        activity.finish();
    }

    private static final class FakeRadio implements ControlCenter5GController.RadioAccess {
        boolean enabled;
        @Override public long supportedNetworkTypes() {
            return TelephonyManager.NETWORK_TYPE_BITMASK_NR
                    | TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        }
        @Override public long allowedNetworkTypes() {
            return TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                    | (enabled ? TelephonyManager.NETWORK_TYPE_BITMASK_NR : 0L);
        }
        @Override public void setAllowedNetworkTypes(long value) {
            enabled = (value & TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0;
        }
    }

    public static final class QSTileItemView extends FrameLayout {
        QSTileItemView(android.content.Context context) { super(context); }
    }
}
