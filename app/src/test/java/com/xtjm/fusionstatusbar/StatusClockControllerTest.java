package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.util.TypedValue;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusClockControllerTest {
    @Test public void customSizeFollowsNativeUpdatesAndDisableRestoresLatestNativeSize() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView clock = new TextView(activity);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f);
        FusionConfig nativeConfig = FusionConfig.defaults();
        StatusClockController.onNativeUpdate(clock, nativeConfig);
        assertEquals(24f, clock.getTextSize(), 0.01f);

        FusionConfig custom = nativeConfig.withStatusClockSizeSp(20);
        StatusClockController.onConfigChanged(custom);
        float customPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20,
                activity.getResources().getDisplayMetrics());
        assertEquals(customPx, clock.getTextSize(), 0.01f);

        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f);
        StatusClockController.onNativeUpdate(clock, custom);
        assertEquals(customPx, clock.getTextSize(), 0.01f);

        StatusClockController.onConfigChanged(nativeConfig);
        assertEquals(30f, clock.getTextSize(), 0.01f);
        activity.finish();
    }
}
