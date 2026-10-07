package com.xtjm.fusionstatusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Retains the native sidebar handle and only dispatches an explicit expansion request. */
final class SidebarHooks {
    static final String ACTION_SHOW_SIDEBAR =
            "io.github.yudigaga.fusionstatusbar.action.SHOW_SIDEBAR";
    static final String SENDER_PERMISSION = "com.android.systemui.permission.SELF";
    private static final String PACKAGE = "com.miui.securitycenter";
    private static final String TAG = "FusionStatusBar";
    private static final String[] SAMPLING_HELPERS = {
            "com.android.systemui.navigationbar.gestural.RegionSamplingHelper",
            "com.android.systemui.shared.navigationbar.RegionSamplingHelper",
            "com.android.wm.shell.shared.handles.RegionSamplingHelper"
    };
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean RECEIVER_REGISTERED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Boolean> TRACKED_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile WeakReference<View> sidebar = new WeakReference<>(null);
    private static final View.OnAttachStateChangeListener ATTACH_LISTENER =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    capture(view);
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    if (sidebar.get() == view) sidebar = new WeakReference<>(null);
                }
            };

    private SidebarHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        if (module == null || loader == null || !INSTALLED.compareAndSet(false, true)) return 0;
        int count = 0;
        for (String className : SAMPLING_HELPERS) {
            Class<?> helper;
            try {
                helper = Class.forName(className, false, loader);
            } catch (ClassNotFoundException ignored) {
                continue;
            }
            for (Constructor<?> constructor : helper.getDeclaredConstructors()) {
                if (!hasViewParameter(constructor.getParameterTypes())) continue;
                try {
                    module.hook(constructor)
                            .setId("fusion-statusbar:sidebar-" + constructor.toGenericString())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                for (Object argument : chain.getArgs()) {
                                    if (argument instanceof ImageView image) track(image);
                                }
                                return result;
                            });
                    count++;
                } catch (Throwable error) {
                    Log.w(TAG, "sidebar sampling hook failed " + className, error);
                }
            }
        }
        if (count == 0) Log.w(TAG, "sidebar sampling helper unavailable");
        return count;
    }

    private static boolean hasViewParameter(Class<?>[] parameters) {
        for (Class<?> parameter : parameters) {
            if (View.class.isAssignableFrom(parameter)) return true;
        }
        return false;
    }

    private static void track(ImageView image) {
        if (!PACKAGE.equals(image.getContext().getPackageName())) return;
        if (TRACKED_VIEWS.put(image, true) == null) image.addOnAttachStateChangeListener(ATTACH_LISTENER);
        capture(image);
        // A native constructor can run before the handle has its parent and size.
        image.post(() -> capture(image));
    }

    private static void capture(View view) {
        if (!isSidebarHandle(view)) return;
        sidebar = new WeakReference<>(view);
        register(view.getContext());
    }

    static boolean isSidebarHandle(View view) {
        if (!(view instanceof ImageView) || !PACKAGE.equals(view.getContext().getPackageName())) {
            return false;
        }
        if (sidebarName(resourceName(view))) return true;
        for (ViewParent parent = view.getParent(); parent instanceof View;
                parent = parent.getParent()) {
            if (sidebarName(parent.getClass().getName()) || sidebarName(resourceName((View) parent))) {
                return true;
            }
        }
        if (!view.isAttachedToWindow()) return false;
        int width = view.getWidth();
        int height = view.getHeight();
        float density = view.getResources().getDisplayMetrics().density;
        int screenWidth = view.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = view.getResources().getDisplayMetrics().heightPixels;
        if (width <= 0 || width > 36 * density || height < width * 2
                || height > screenHeight * .85f) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location[0] <= 24 * density
                || location[0] + width >= screenWidth - 24 * density;
    }

    private static boolean sidebarName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("sidebar") || lower.contains("side_bar")
                || lower.contains("dock_line") || lower.contains("videobox");
    }

    private static String resourceName(View view) {
        try {
            return view.getId() == View.NO_ID ? "" : view.getResources().getResourceEntryName(view.getId());
        } catch (Resources.NotFoundException ignored) {
            return "";
        }
    }

    private static void register(Context source) {
        if (!RECEIVER_REGISTERED.compareAndSet(false, true)) return;
        Context context = source.getApplicationContext();
        if (context == null) context = source;
        try {
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    if (!ACTION_SHOW_SIDEBAR.equals(intent.getAction())) return;
                    View handle = sidebar.get();
                    if (handle == null || !handle.isAttachedToWindow() || !handle.isShown()) {
                        Log.w(TAG, "native sidebar handle unavailable");
                        return;
                    }
                    try {
                        show(handle, dockLocation(receiverContext, handle));
                    } catch (Throwable error) {
                        Log.w(TAG, "native sidebar expansion failed", error);
                    }
                }
            }, new IntentFilter(ACTION_SHOW_SIDEBAR), SENDER_PERMISSION, MAIN,
                    Context.RECEIVER_EXPORTED);
            Log.i(TAG, "native sidebar action receiver registered");
        } catch (Throwable error) {
            RECEIVER_REGISTERED.set(false);
            Log.w(TAG, "sidebar action receiver registration failed", error);
        }
    }

    private static int dockLocation(Context context, View handle) {
        int stored = context.getSharedPreferences("sp_video_box", Context.MODE_PRIVATE)
                .getInt("dock_line_location", -1);
        if (stored == 0 || stored == 1) return stored;
        int[] location = new int[2];
        handle.getLocationOnScreen(location);
        return location[0] + handle.getWidth() / 2
                < handle.getResources().getDisplayMetrics().widthPixels / 2 ? 0 : 1;
    }

    static void show(View handle, int dockLocation) {
        int[] location = new int[2];
        handle.getLocationOnScreen(location);
        float inset = Math.min(4, Math.max(1, handle.getWidth() / 2f));
        float x = dockLocation == 0 ? location[0] + inset
                : location[0] + handle.getWidth() - inset;
        float y = location[1] + Math.max(1, handle.getHeight() / 2f);
        float drag = Math.max(160, 80 * handle.getResources().getDisplayMetrics().density);
        float destination = x + (dockLocation == 0 ? drag : -drag);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent move = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, destination, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 21, MotionEvent.ACTION_UP, destination, y, 0);
        down.setSource(9999);
        move.setSource(9999);
        up.setSource(9999);
        try {
            handle.dispatchTouchEvent(down);
            handle.dispatchTouchEvent(move);
            handle.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            move.recycle();
            up.recycle();
        }
    }
}
