package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusBarHeightLayoutTest {
    @Test public void configuredTotalHeightSetsPhoneRootLayoutAndMeasuredBounds() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setLayoutParams(new FrameLayout.LayoutParams(400, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY));
        FusionConfig config = FusionConfig.defaults().withDoubleRow(true).withStatusBarHeight(72);

        SystemUiHooks.applyStatusBarHeight(root, config);

        int totalHeightPx = Math.round(72 * activity.getResources().getDisplayMetrics().density);
        assertEquals(totalHeightPx, root.getLayoutParams().height);
        assertEquals(totalHeightPx, root.getMeasuredHeight());
        activity.finish();
    }
}
