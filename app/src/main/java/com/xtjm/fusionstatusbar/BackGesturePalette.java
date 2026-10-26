package com.xtjm.fusionstatusbar;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;

import com.materialkolor.hct.Hct;

/** The selected colors are seeds; paired tones keep their color family in either system theme. */
final class BackGesturePalette {
    final int background;
    final int arrow;
    final int outline;
    final int shadow;

    private BackGesturePalette(int background, int arrow, int outline, int shadow) {
        this.background = background;
        this.arrow = arrow;
        this.outline = outline;
        this.shadow = shadow;
    }

    static boolean isNight(Resources resources) {
        return (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static BackGesturePalette resolve(int backgroundSeed, int arrowSeed, boolean night) {
        Hct body = Hct.Companion.fromInt(backgroundSeed | 0xFF000000);
        Hct glyph = Hct.Companion.fromInt(arrowSeed | 0xFF000000);
        double hue = body.getHue();
        double chroma = Math.min(body.getChroma(), 36.0);
        boolean neutralArrow = glyph.getChroma() < 8.0;
        double arrowHue = neutralArrow ? hue : glyph.getHue();
        double arrowChroma = neutralArrow ? Math.min(chroma, 32.0)
                : Math.min(glyph.getChroma(), 48.0);
        int background = tone(hue, Math.min(chroma, night ? 26.0 : 24.0),
                night ? 22.0 : 94.0, Color.alpha(backgroundSeed));
        int arrow = tone(arrowHue, arrowChroma, night ? 90.0 : 22.0,
                Color.alpha(arrowSeed));
        int outline = tone(hue, Math.min(chroma, 28.0), night ? 70.0 : 46.0,
                Math.round(Color.alpha(backgroundSeed) * (night ? .38f : .3f)));
        return new BackGesturePalette(background, arrow, outline,
                night ? 0x28000000 : 0x18000000);
    }

    static int[] rainbow(int seed, boolean night, boolean outline) {
        Hct base = Hct.Companion.fromInt(seed | 0xFF000000);
        int[] colors = new int[7];
        int alpha = Math.round(Color.alpha(seed) * (outline ? .86f : 1f));
        double tone = outline ? (night ? 72.0 : 58.0) : (night ? 26.0 : 90.0);
        for (int index = 0; index < colors.length - 1; index++) {
            colors[index] = tone((base.getHue() + index * 60.0) % 360.0,
                    outline ? 48.0 : 40.0, tone, alpha);
        }
        colors[colors.length - 1] = colors[0];
        return colors;
    }

    private static int tone(double hue, double chroma, double tone, int alpha) {
        return (Hct.Companion.from(hue, chroma, tone).toInt() & 0x00FFFFFF) | (alpha << 24);
    }
}
