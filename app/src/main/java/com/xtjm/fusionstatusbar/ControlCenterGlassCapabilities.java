package com.xtjm.fusionstatusbar;

import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Read-only API inventory. Method presence is not proof of a working glass material. */
final class ControlCenterGlassCapabilities {
    private static final Map<Class<?>, Snapshot> CACHE = new WeakHashMap<>();
    private static final Api[] MODERN_APIS = {
            new Api("setMiGlass", float[].class),
            new Api("setMiGlassBlurRadius", int.class, int.class),
            new Api("setMiGlassClip", float.class, float.class, float.class, float.class),
            new Api("setMiViewMaterialType", int.class),
            new Api("chooseBackgroundBlurContainer", View.class),
            new Api("setPassWindowBlurEnabled", boolean.class)
    };

    private ControlCenterGlassCapabilities() { }

    record Snapshot(int available, List<String> missing) { }
    private record Api(String name, Class<?>... parameters) { }
    private static final Map<Class<?>, Boolean> REPORTED = new WeakHashMap<>();

    static synchronized Snapshot inspect(Class<?> owner) {
        Snapshot cached = CACHE.get(owner);
        if (cached != null) return cached;
        List<String> missing = new ArrayList<>();
        for (Api api : MODERN_APIS) {
            try { owner.getMethod(api.name, api.parameters); }
            catch (NoSuchMethodException | SecurityException error) { missing.add(api.name); }
        }
        Snapshot result = new Snapshot(MODERN_APIS.length - missing.size(), List.copyOf(missing));
        CACHE.put(owner, result);
        return result;
    }

    static void reportCompatibility(View owner) {
        synchronized (ControlCenterGlassCapabilities.class) {
            if (REPORTED.put(owner.getClass(), Boolean.TRUE) != null) return;
        }
        Snapshot snapshot = inspect(owner.getClass());
        Log.i("FusionStatusBar", "Highlight glass compatibility owner=" + owner.getClass().getSimpleName()
                + " backend=canvas_compat fullNativeGlass=false MiGlassAPIs=" + snapshot.available()
                + "/" + MODERN_APIS.length + " missing=" + snapshot.missing()
                + "; host Bionics parameters and compositor rendering are not validated");
    }
}
