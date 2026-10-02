package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.IntConsumer;

/** Copies the rendered panel window. Software View.draw omits OEM hardware-only backgrounds. */
final class ControlCenterWindowCapture {
    private ControlCenterWindowCapture() { }

    static void request(View panel, Bitmap destination, Handler handler, IntConsumer callback) throws ReflectiveOperationException {
        int[] location = new int[2];
        panel.getLocationInWindow(location);
        Rect crop = new Rect(location[0], location[1], location[0] + panel.getWidth(), location[1] + panel.getHeight());
        if (Build.VERSION.SDK_INT >= 34) {
            PixelCopy.request(PixelCopy.Request.Builder.ofWindow(panel).setSourceRect(crop)
                    .setDestinationBitmap(destination).build(), handler::post, result -> callback.accept(result.getStatus()));
        } else {
            // API 33 has no public View-based window source; this runs only inside SystemUI.
            Method getRoot = View.class.getDeclaredMethod("getViewRootImpl");
            getRoot.setAccessible(true);
            Object root = getRoot.invoke(panel);
            if (root == null) throw new IllegalArgumentException("Detached capture panel");
            Field field = root.getClass().getDeclaredField("mSurface");
            field.setAccessible(true);
            Surface surface = (Surface) field.get(root);
            Field attributes = root.getClass().getDeclaredField("mWindowAttributes");
            attributes.setAccessible(true);
            Object attrs = attributes.get(root);
            Field insetsField = attrs.getClass().getDeclaredField("surfaceInsets");
            insetsField.setAccessible(true);
            Rect insets = (Rect) insetsField.get(attrs);
            crop.offset(insets.left, insets.top);
            PixelCopy.request(surface, crop, destination, callback::accept, handler);
        }
    }
}
