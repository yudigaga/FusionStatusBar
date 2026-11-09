package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FusionChargingIconTest {
    private static final String KEY = "fusion_charging_icon";

    @Test public void disabledChoiceSurvivesStorageAndUnrelatedEditsAndRefreshesStatusBar() throws Exception {
        FusionConfig source = configured(false);
        FusionConfig changed = source.withIconScale(125).withRevision(9)
                .withFusionContentPosition(FusionContentConfig.BATTERY, FusionContentConfig.MIDDLE)
                .withFusionContentScale(FusionContentConfig.BATTERY, 85)
                .withFusionWifiOffsetYPercent(-3).withFusionMobileOffsetYPercent(4);
        FusionConfig stored = FusionConfigStore.decode(FusionConfigStore.encode(changed));
        assertFalse(stored.toBundle().getBoolean(KEY, true));
        assertTrue(RuntimeConfigDiff.between(FusionConfig.defaults(), source).statusBar);
        assertFalse(FusionConfig.defaults().fusionContent.sameAs(source.fusionContent));
    }

    @Test public void backupRetainsChoiceAndEarlierCompleteBackupKeepsChargingIcon() throws Exception {
        byte[] bytes = ConfigurationBackupCodec.encode(configured(false));
        assertFalse(ConfigurationBackupCodec.decode(bytes).toBundle().getBoolean(KEY, true));
        JSONObject backup = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        backup.getJSONObject("config").remove(KEY);
        FusionConfig previous = ConfigurationBackupCodec.decode(backup.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(previous.toBundle().getBoolean(KEY, false));
        assertTrue(FusionConfig.fromBundle(new Bundle()).toBundle().getBoolean(KEY, false));
    }

    @Test public void chargingShowsDigitsOnlyWhenSwitchIsOffIncludingFastCharge() {
        for (boolean fast : new boolean[] {false, true}) {
            Bitmap enabled73 = draw(FusionConfig.defaults(), 73, true, fast);
            Bitmap enabled84 = draw(FusionConfig.defaults(), 84, true, fast);
            Bitmap disabled73 = draw(configured(false), 73, true, fast);
            Bitmap disabled84 = draw(configured(false), 84, true, fast);
            Bitmap idle73 = draw(configured(false), 73, false, false);
            assertTrue("enabled charging keeps the same lightning for different percentages",
                    batteryContent(enabled73).sameAs(batteryContent(enabled84)));
            assertFalse("disabled charging must render the actual percentage",
                    batteryContent(disabled73).sameAs(batteryContent(disabled84)));
            assertTrue("disabled charging retains the same digits as idle",
                    batteryContent(disabled73).sameAs(batteryContent(idle73)));
            assertFalse("the switch changes the charging content", enabled73.sameAs(disabled73));
        }
        assertTrue("switch does not affect idle rendering",
                draw(FusionConfig.defaults(), 73, false, false).sameAs(draw(configured(false), 73, false, false)));
    }

    private static FusionConfig configured(boolean enabled) {
        Bundle values = FusionConfig.defaults().toBundle();
        values.putBoolean(KEY, enabled);
        return FusionConfig.fromBundle(values);
    }

    private static Bitmap batteryContent(Bitmap image) {
        return Bitmap.createBitmap(image, 90, 8, 91, 64);
    }

    private static Bitmap draw(FusionConfig config, int percent, boolean charging, boolean fast) {
        FusionIconView view = new FusionIconView(RuntimeEnvironment.getApplication());
        view.showSample(new StatusMonitor.Snapshot(percent, true, "", 4, 3, 4,
                charging, fast, false), Color.WHITE);
        view.setPreviewConfig(config);
        view.layout(0, 0, 271, 282);
        Bitmap image = Bitmap.createBitmap(271, 282, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }
}
