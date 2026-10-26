package com.xtjm.fusionstatusbar;

import android.content.res.Configuration;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import com.materialkolor.hct.Hct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class BackGesturePaletteTest {
    @Test public void oneSeedProducesReadablePairedTonesInBothThemes() {
        for (int seed : new int[] {0xFF3267D6, 0xFF26A69A, 0xFF9A5BCC, 0xFFDA4655, 0xFF202124}) {
            BackGesturePalette day = BackGesturePalette.resolve(seed, Color.WHITE, false);
            BackGesturePalette night = BackGesturePalette.resolve(seed, Color.WHITE, true);
            assertTrue(ColorUtils.calculateLuminance(day.background) > .8);
            assertTrue(ColorUtils.calculateLuminance(night.background) < .1);
            assertTrue(ColorUtils.calculateContrast(day.arrow, day.background) >= 7.0);
            assertTrue(ColorUtils.calculateContrast(night.arrow, night.background) >= 7.0);
            assertNotEquals(day.background, night.background);
            assertNotEquals(day.arrow, night.arrow);
        }
    }

    @Test public void chromaticSeedKeepsItsHueAcrossSystemModes() {
        for (int seed : new int[] {0xFF3267D6, 0xFF26A69A, 0xFF9A5BCC, 0xFFDA4655}) {
            double hue = Hct.Companion.fromInt(seed).getHue();
            for (boolean night : new boolean[] {false, true}) {
                BackGesturePalette palette = BackGesturePalette.resolve(seed, Color.WHITE, night);
                assertTrue(hueDifference(hue, Hct.Companion.fromInt(palette.background).getHue()) < 8);
                assertTrue(hueDifference(hue, Hct.Companion.fromInt(palette.arrow).getHue()) < 8);
            }
        }
    }

    @Test public void independentArrowSeedAndBothOpacitiesArePreserved() {
        int background = 0x803267D6;
        int arrow = 0xC8D04655;
        double arrowHue = Hct.Companion.fromInt(arrow | 0xFF000000).getHue();
        for (boolean night : new boolean[] {false, true}) {
            BackGesturePalette palette = BackGesturePalette.resolve(background, arrow, night);
            assertEquals(0x80, Color.alpha(palette.background));
            assertEquals(0xC8, Color.alpha(palette.arrow));
            assertTrue(hueDifference(arrowHue, Hct.Companion.fromInt(palette.arrow).getHue()) < 8);
        }
        BackGesturePalette transparent = BackGesturePalette.resolve(0x003267D6, 0x00D04655, false);
        assertEquals(0, Color.alpha(transparent.background));
        assertEquals(0, Color.alpha(transparent.arrow));
        assertEquals(0, Color.alpha(transparent.outline));
    }

    @Test public void theSameSeedRoundTripsDayNightDayWithoutChangingStoredConfig() {
        DesktopConfig config = DesktopConfig.defaults().withBackGesturePreset(DesktopConfig.BACK_STYLE_NEON);
        BackGesturePalette first = BackGesturePalette.resolve(config.backGestureBackgroundColor,
                config.backGestureArrowColor, false);
        BackGesturePalette.resolve(config.backGestureBackgroundColor, config.backGestureArrowColor, true);
        BackGesturePalette again = BackGesturePalette.resolve(config.backGestureBackgroundColor,
                config.backGestureArrowColor, false);
        assertEquals(first.background, again.background);
        assertEquals(first.arrow, again.arrow);
        assertEquals(config, DesktopConfig.fromBundle(config.toBundle()));
    }

    @Test @Config(qualifiers = "night") public void nightModeComesFromCurrentResources() {
        assertTrue(BackGesturePalette.isNight(RuntimeEnvironment.getApplication().getResources()));
        Configuration day = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        assertFalse(BackGesturePalette.isNight(RuntimeEnvironment.getApplication()
                .createConfigurationContext(day).getResources()));
    }

    private static double hueDifference(double first, double second) {
        double delta = Math.abs(first - second) % 360;
        return Math.min(delta, 360 - delta);
    }
}
