package com.xtjm.fusionstatusbar;

import java.util.ArrayList;

/** Independent RenderNode effects which survive Drawable/outline replacement. */
final class ControlCenterNativeEffects {
    enum Api {
        BLOOM("setMiBloomStroke", float[].class),
        SHADOW("setMiShadow", int.class, float.class, float.class, float.class, float.class),
        SHADOW_WITH_CLIP("setMiShadow", int.class, float.class, float.class, float.class, float.class, boolean.class),
        MIX("setMixEffectEnabled", boolean.class),
        CLEAR_BLEND("clearMiBackgroundBlendColor");

        final String method;
        final Class<?>[] parameters;
        Api(String method, Class<?>... parameters) { this.method = method; this.parameters = parameters; }
    }

    private ControlCenterNativeEffects() { }

    /** Static two-light bloom for OEM RenderNodes which expose the 21-float API. */
    static float[] daylightBloom(float density, int strength) {
        float opacity = Math.max(0, Math.min(100, strength)) / 100f;
        return new float[] {0.8f * density, 180f, 1f, 1f, 1f, 0.05f * opacity,
                2.6f * density, .5f, .5f, -.5f, 1f, 1f, 1f, .6f * opacity,
                .5f, .95f, -.5f, 1f, 1f, 1f, .35f * opacity};
    }

    static boolean decoration(String operation) {
        return operation.equals("setMiBloomStroke") || operation.equals("setMiShadow");
    }

    static boolean composition(String operation) {
        return operation.equals("setMiViewBlurMode") || operation.equals("setMiBackgroundBlendColors")
                || operation.equals("setMixEffectEnabled") || operation.equals("setMiBackgroundBlurMode")
                || operation.equals("setMiBackgroundBlurRadius");
    }

    static Object[] arguments(String operation, Object[] original, boolean customDecoration,
            boolean softwareSurface) {
        if (customDecoration && operation.equals("setMiBloomStroke") && original.length == 1)
            return new Object[] {new float[21]};
        if (customDecoration && operation.equals("setMiShadow") && original.length >= 5) {
            Object[] cleared = original.clone();
            cleared[0] = 0;
            cleared[1] = cleared[2] = cleared[3] = 0f;
            cleared[4] = 1f;
            return cleared;
        }
        if (softwareSurface && original.length == 1) {
            if (operation.equals("setMiViewBlurMode")) return new Object[] {0};
            if (operation.equals("setMiBackgroundBlurMode")
                    || operation.equals("setMiBackgroundBlurRadius")) return new Object[] {0};
            if (operation.equals("setMixEffectEnabled")) return new Object[] {false};
            if (operation.equals("setMiBackgroundBlendColors")) return new Object[] {new ArrayList<>()};
        }
        return original;
    }
}
