package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds two module-owned tracks alongside a stock singleton; the central OEM view stays in place. */
final class PowerMenuExtension {
    static final String LEFT_TAG = "fusion-power-left";
    static final String RIGHT_TAG = "fusion-power-right";
    private static final Map<Object, WeakReference<Mount>> MOUNTS = new WeakHashMap<>();

    private PowerMenuExtension() { }

    static boolean update(Object dialog, View center, int nativeCount, ControlCenterConfig settings) {
        Mount mount = current(dialog);
        boolean requested = settings.systemSurfaces.powerMenu && settings.systemSurfaces.extendedPowerMenu
                && nativeCount == 1 && center != null && !restricted(dialog, center);
        if (!requested || mount != null && mount.center != center) {
            if (mount != null) mount.remove();
            MOUNTS.remove(dialog);
            mount = null;
        }
        if (!requested) return false;
        if (!(center.getParent() instanceof FrameLayout parent)) {
            Log.w("FusionStatusBar", "power menu extension unavailable: non-frame slider parent");
            return false;
        }
        if (mount == null) {
            mount = new Mount(dialog, parent, center);
            MOUNTS.put(dialog, new WeakReference<>(mount));
            mount.attach();
        }
        mount.settings = settings;
        mount.left.applyAppearance(settings.withTileScale(100));
        mount.right.applyAppearance(settings.withTileScale(100));
        mount.position();
        return true;
    }

    private static Mount current(Object dialog) {
        WeakReference<Mount> reference = MOUNTS.get(dialog);
        return reference == null ? null : reference.get();
    }

    static boolean restricted(Object dialog, View center) {
        if (Boolean.TRUE.equals(read(dialog, "mShutDownPasswordEnabled"))
                || Boolean.TRUE.equals(read(center, "mShutDownPasswordEnabled"))) return true;
        try {
            Context context = context(dialog, center);
            ClassLoader loader = dialog.getClass().getClassLoader();
            try {
                Class<?> type = Class.forName("com.android.systemui.miui.globalactions.ShutDownPasswordUtils", false, loader);
                Method password = RuntimeReflection.exact(type, "isShutDownPasswordEnabled",
                        new Class<?>[] {Context.class}, false);
                if (password != null && Boolean.TRUE.equals(password.invoke(null, context))) return true;
            } catch (ClassNotFoundException ignored) { }
            return PowerMenuActions.restricted(context);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w("FusionStatusBar", "power menu lock state unavailable", error);
            return true;
        }
    }

    private static Context context(Object owner, View center) {
        for (String name : new String[] {"mSystemUIContext", "mSysUIContext", "mContext"}) {
            Object value = read(owner, name);
            if (value instanceof Context context) return context;
        }
        return center == null ? null : center.getContext();
    }

    private static Object read(Object owner, String name) {
        if (owner == null) return null;
        try {
            Field field = RuntimeReflection.field(owner.getClass(), name);
            return field == null ? null : field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static final class Mount implements View.OnLayoutChangeListener,
            ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<Object> dialog;
        final FrameLayout parent;
        final View center;
        final PowerMenuSideSlider left, right;
        final float originalScaleX, originalScaleY;
        final boolean clipChildren, clipPadding;
        ControlCenterConfig settings = ControlCenterConfig.defaults();
        boolean removed;

        Mount(Object owner, FrameLayout parent, View center) {
            dialog = new WeakReference<>(owner);
            this.parent = parent;
            this.center = center;
            originalScaleX = center.getScaleX();
            originalScaleY = center.getScaleY();
            clipChildren = parent.getClipChildren();
            clipPadding = parent.getClipToPadding();
            Context context = center.getContext();
            left = new PowerMenuSideSlider(context, "线刷模式", R.drawable.cc_icon_usb,
                    "卡刷模式", R.drawable.cc_icon_settings_backup_restore,
                    direction -> action(direction < 0 ? PowerMenuActions.Action.BOOTLOADER : PowerMenuActions.Action.RECOVERY));
            right = new PowerMenuSideSlider(context, "飞行模式", R.drawable.cc_icon_airplanemode_active,
                    "静音模式", R.drawable.cc_icon_notifications_off,
                    direction -> action(direction < 0 ? PowerMenuActions.Action.AIRPLANE : PowerMenuActions.Action.SILENT));
            left.setTag(LEFT_TAG);
            right.setTag(RIGHT_TAG);
        }

        void attach() {
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
            parent.addView(left, new FrameLayout.LayoutParams(1, 1));
            parent.addView(right, new FrameLayout.LayoutParams(1, 1));
            parent.addOnLayoutChangeListener(this);
            center.addOnLayoutChangeListener(this);
            parent.addOnAttachStateChangeListener(this);
            if (parent.isAttachedToWindow()) onViewAttachedToWindow(parent);
        }

        void position() {
            if (removed) return;
            float density = center.getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams params = center.getLayoutParams();
            int width = center.getWidth() > 0 ? center.getWidth()
                    : params != null && params.width > 0 ? params.width : Math.round(64 * density);
            int height = center.getHeight() > 0 ? center.getHeight()
                    : params != null && params.height > 0 ? params.height : Math.round(288 * density);
            int availableWidth = parent.getWidth() > 0 ? parent.getWidth()
                    : center.getResources().getDisplayMetrics().widthPixels;
            int availableHeight = parent.getHeight() > 0 ? parent.getHeight()
                    : center.getResources().getDisplayMetrics().heightPixels;
            float gap = (20 + settings.spacing) * density;
            float scale = Math.min(settings.tileScale / 100f,
                    Math.max(.1f, (availableWidth - 24 * density - gap * 2) / (width * 3f)));
            scale = Math.min(scale, Math.max(.1f, (availableHeight - 120 * density) / height));
            center.setScaleX(originalScaleX * scale);
            center.setScaleY(originalScaleY * scale);
            float cx = center.getWidth() > 0 ? center.getLeft() + width / 2f : availableWidth / 2f;
            float cy = center.getHeight() > 0 ? center.getTop() + height / 2f : availableHeight / 2f;
            float offset = width * originalScaleX * scale + gap;
            int sideHeight = height + Math.round(72 * density);
            place(left, Math.round(cx - offset - width / 2f), Math.round(cy - sideHeight / 2f), width, sideHeight, scale);
            place(right, Math.round(cx + offset - width / 2f), Math.round(cy - sideHeight / 2f), width, sideHeight, scale);
        }

        private void place(View view, int x, int y, int width, int height, float scale) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            if (params.leftMargin != x || params.topMargin != y || params.width != width || params.height != height
                    || params.gravity != (Gravity.TOP | Gravity.LEFT)) {
                params.width = width;
                params.height = height;
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.leftMargin = x;
                params.topMargin = y;
                view.setLayoutParams(params);
            }
            view.setScaleX(originalScaleX * scale);
            view.setScaleY(originalScaleY * scale);
        }

        private void action(PowerMenuActions.Action action) {
            Object owner = dialog.get();
            if (removed || owner == null || !settings.systemSurfaces.powerMenu
                    || !settings.systemSurfaces.extendedPowerMenu || restricted(owner, center)) return;
            Context context = context(owner, center);
            try {
                PowerMenuActions.execute(action, PowerMenuActions.platform(context));
                Log.i("FusionStatusBar", "power menu side action=" + action);
                Method dismiss = RuntimeReflection.exact(owner.getClass(), "dismiss", new Class<?>[] {int.class}, false);
                if (dismiss != null) dismiss.invoke(owner, 3);
                else {
                    dismiss = RuntimeReflection.exact(owner.getClass(), "dismiss", new Class<?>[0], false);
                    if (dismiss != null) dismiss.invoke(owner);
                }
            } catch (Exception error) {
                Log.w("FusionStatusBar", "power menu side action failed: " + action, error);
                Toast.makeText(context, "操作未完成，请重试", Toast.LENGTH_SHORT).show();
            }
        }

        void remove() {
            removed = true;
            onViewDetachedFromWindow(parent);
            parent.removeOnLayoutChangeListener(this);
            center.removeOnLayoutChangeListener(this);
            parent.removeOnAttachStateChangeListener(this);
            parent.removeView(left);
            parent.removeView(right);
            parent.setClipChildren(clipChildren);
            parent.setClipToPadding(clipPadding);
            center.setScaleX(originalScaleX);
            center.setScaleY(originalScaleY);
        }

        @Override public void onLayoutChange(View view, int l, int t, int r, int b,
                int ol, int ot, int or, int ob) { position(); }

        @Override public boolean onPreDraw() {
            if (!removed) {
                left.setVisibility(center.getVisibility());
                right.setVisibility(center.getVisibility());
                left.setAlpha(center.getAlpha());
                right.setAlpha(center.getAlpha());
                left.setTranslationY(center.getTranslationY());
                right.setTranslationY(center.getTranslationY());
            }
            return true;
        }

        @Override public void onViewAttachedToWindow(View view) {
            parent.getViewTreeObserver().removeOnPreDrawListener(this);
            parent.getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (parent.getViewTreeObserver().isAlive()) parent.getViewTreeObserver().removeOnPreDrawListener(this);
        }
    }
}
