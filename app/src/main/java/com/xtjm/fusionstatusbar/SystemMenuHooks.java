package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedInterface;

/** Keeps native menu actions and gestures while applying the published shared appearance. */
final class SystemMenuHooks {
    static final String POWER_UTIL = "com.android.systemui.miui.globalactions.Util";
    static final String POWER_DIALOG = "com.android.systemui.miui.globalactions.MiuiGlobalActionsDialog";
    static final String POWER_SLIDER = "com.android.systemui.miui.globalactions.SliderView";
    static final String VOLUME_CONTROLLER = "com.android.systemui.miui.volume.VolumePanelViewController";
    static final String VOLUME_COLUMN = "com.android.systemui.miui.volume.VolumeColumn";
    static final String VOLUME_COLUMN_RES = "com.android.systemui.miui.volume.VolumeColumnRes";
    static final String VOLUME_DIALOG_RES = "com.android.systemui.miui.volume.MiuiVolumeDialogRes";
    private static final String VOLUME_MOTION = "com.android.systemui.miui.volume.MiuiVolumeDialogMotion";
    private static final String RINGER_BUTTON = "com.android.systemui.miui.volume.MiuiRingerModeLayout$RingerButtonHelper";
    private static final String TIMER = "com.android.systemui.miui.volume.TimerItem";
    private static final String TAG = "FusionStatusBar";
    private static final int COMPACT_HEIGHT_DP = 142;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Boolean> POWER_MENUS = new WeakHashMap<>();
    private static final Map<Object, String> POWER_DIAGNOSTICS = new WeakHashMap<>();
    private static final Map<Object, Boolean> VOLUME_MENUS = new WeakHashMap<>();
    private static final Map<View, PowerGeometry> POWER_GEOMETRY = new WeakHashMap<>();
    private static final Set<View> VOLUME_SURFACES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> REFRESHING = new ThreadLocal<>();
    private static volatile ControlCenterConfig config = ControlCenterConfig.defaults();
    private static Consumer<Context> bootstrap = ignored -> { };
    private static Supplier<ControlCenterConfig> current = () -> config;

    interface Registrar { boolean install(String id, Method method, XposedInterface.Hooker hooker); }
    interface ClassResolver { Class<?> resolve(String name, ClassLoader loader); }

    private SystemMenuHooks() { }

    static int install(ClassLoader loader, Registrar registrar) {
        return install(loader, registrar, (name, target) -> {
            try { return Class.forName(name, false, target); }
            catch (Throwable ignored) { return null; }
        });
    }

    static int install(ClassLoader loader, Registrar registrar, Consumer<Context> ensureConfig) {
        if (ensureConfig != null) bootstrap = ensureConfig;
        return install(loader, registrar);
    }

    static int install(ClassLoader loader, Registrar registrar, Supplier<ControlCenterConfig> supplier,
            Consumer<Context> ensureConfig) {
        if (supplier != null) current = supplier;
        return install(loader, registrar, ensureConfig);
    }

    static int install(ClassLoader loader, Registrar registrar, ClassResolver resolver) {
        if (loader == null || registrar == null || resolver == null) return 0;
        int count = 0;
        count += hook(loader, registrar, resolver, POWER_UTIL, "isExtendedPowerMenu",
                new Class<?>[] {Context.class}, chain -> {
                    bootstrapContext(chain.getArgs().get(0));
                    Object nativeResult = chain.proceed();
                    return config.systemSurfaces.powerMenu ? config.systemSurfaces.extendedPowerMenu : nativeResult;
                });
        for (String name : new String[] {"initViews", "showDialog", "showSliderViews"}) {
            count += hook(loader, registrar, resolver, POWER_DIALOG, name, new Class<?>[0],
                    chain -> afterPower(chain));
        }
        count += hook(loader, registrar, resolver, POWER_DIALOG, "initDialog", new Class<?>[0],
                SystemMenuHooks::afterPower);
        count += hook(loader, registrar, resolver, POWER_DIALOG, "onConfigChanged",
                new Class<?>[] {android.content.res.Configuration.class}, SystemMenuHooks::afterPower);
        count += hook(loader, registrar, resolver, POWER_SLIDER, "onDraw",
                new Class<?>[] {Canvas.class}, chain -> chain.getThisObject() instanceof View view
                        && config.surface != ControlCenterSurface.DEFAULT
                        && SystemSurfaceStyle.isApplied(view)
                        ? null : chain.proceed());
        count += hook(loader, registrar, resolver, POWER_DIALOG, "action",
                new Class<?>[] {int.class, boolean.class}, SystemMenuHooks::powerAction);
        for (String name : new String[] {"initPanelView", "updateVolumePanelSize"}) {
            count += hook(loader, registrar, resolver, VOLUME_CONTROLLER, name,
                    new Class<?>[0], SystemMenuHooks::afterVolume);
        }
        count += hook(loader, registrar, resolver, VOLUME_CONTROLLER, "showH",
                new Class<?>[] {int.class}, SystemMenuHooks::afterVolume);
        count += hook(loader, registrar, resolver, VOLUME_CONTROLLER, "updateExpandedH",
                new Class<?>[] {boolean.class, boolean.class, boolean.class}, SystemMenuHooks::afterVolume);
        count += hook(loader, registrar, resolver, VOLUME_COLUMN_RES, "getHeight",
                new Class<?>[] {Context.class, boolean.class, boolean.class}, chain -> {
                    int nativeHeight = (Integer) chain.proceed();
                    return columnHeight(nativeHeight, (Context) chain.getArgs().get(0),
                            (Boolean) chain.getArgs().get(1), (Boolean) chain.getArgs().get(2), config);
                });
        count += hook(loader, registrar, resolver, VOLUME_DIALOG_RES, "getHeight",
                new Class<?>[] {Context.class, boolean.class, boolean.class}, chain -> {
                    int nativeHeight = (Integer) chain.proceed();
                    Context context = (Context) chain.getArgs().get(0);
                    return compactPanelHeight(nativeHeight, context,
                            (Boolean) chain.getArgs().get(1), (Boolean) chain.getArgs().get(2));
                });
        count += hook(loader, registrar, resolver, VOLUME_DIALOG_RES, "getShadowHeight",
                new Class<?>[] {Context.class, boolean.class, boolean.class, int.class, boolean.class}, chain -> {
                    int nativeHeight = (Integer) chain.proceed();
                    return compactPanelHeight(nativeHeight, (Context) chain.getArgs().get(0),
                            true, (Boolean) chain.getArgs().get(2));
                });
        for (String name : new String[] {"setSliderResource", "setSliderBlendColor"}) {
            count += hook(loader, registrar, resolver, VOLUME_COLUMN, name,
                    new Class<?>[] {boolean.class}, chain -> {
                        Object result = chain.proceed();
                        safely(() -> applyColumn(chain.getThisObject()), "volume column");
                        return result;
                    });
        }
        for (String name : new String[] {"updateExpandBgState", "updateExpandBgSize"}) {
            count += hook(loader, registrar, resolver, VOLUME_MOTION, name, new Class<?>[0], chain -> {
                Object result = chain.proceed();
                Object motion = chain.getThisObject();
                if (booleanField(motion, "mNeedShowDialog", false))
                    safely(() -> style(viewField(motion, "mExpandBgView"),
                            config.systemSurfaces.volumeDialog, false), "volume backdrop");
                return result;
            });
        }
        count += hook(loader, registrar, resolver, RINGER_BUTTON, "updateState", new Class<?>[0], chain -> {
            Object result = chain.proceed();
            safely(() -> {
                Object helper = chain.getThisObject();
                Object owner = field(helper, "this$0");
                if (booleanField(owner, "mNeedShowDialog", false))
                    style(viewField(helper, "mBlurView"), config.systemSurfaces.volumeDialog, false);
            }, "ringer state");
            return result;
        });
        count += hook(loader, registrar, resolver, TIMER, "updateTimerSeekbar", new Class<?>[0], chain -> {
            Object result = chain.proceed();
            safely(() -> {
                Object timer = chain.getThisObject();
                if (booleanField(timer, "mNeedShowDialog", false)) {
                    style(viewField(timer, "mTimer"), config.systemSurfaces.volumeDialog, true);
                    style(viewField(timer, "mProgressViewBg"), config.systemSurfaces.volumeDialog, true);
                }
            }, "volume timer");
            return result;
        });
        Class<?> powerType = resolver.resolve(POWER_DIALOG, loader);
        if (powerType != null) Log.i(TAG, "power menu capability singleton="
                + (RuntimeReflection.field(powerType, "mSliderView") != null)
                + " array=" + (RuntimeReflection.field(powerType, "mSliderViews") != null)
                + " moduleSideTracks=true");
        return count;
    }

    private static int hook(ClassLoader loader, Registrar registrar, ClassResolver resolver,
            String className, String name, Class<?>[] parameters, XposedInterface.Hooker hooker) {
        Class<?> type = resolver.resolve(className, loader);
        if (type == null) return 0;
        try {
            Method method = type.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return registrar.install("system-menu-" + className + "#" + name
                    + "/" + parameters.length, method, hooker) ? 1 : 0;
        } catch (NoSuchMethodException ignored) { return 0; }
        catch (Throwable error) {
            Log.w(TAG, "system menu hook unavailable " + className + "#" + name, error);
            return 0;
        }
    }

    static void updateConfig(ControlCenterConfig next) {
        if (next == null) return;
        ControlCenterConfig previous = config;
        config = next;
        if (!previous.systemSurfaces.equals(next.systemSurfaces)) Log.i(TAG,
                "system menu settings power=" + next.systemSurfaces.powerMenu
                + " threeColumns=" + next.systemSurfaces.extendedPowerMenu
                + " volume=" + next.systemSurfaces.volumeDialog
                + " compactVolume=" + next.systemSurfaces.compactVolume);
        Runnable refresh = () -> {
            for (Object dialog : new ArrayList<>(POWER_MENUS.keySet())) {
                safely(() -> applyPower(dialog), "power refresh");
            }
            for (Object controller : new ArrayList<>(VOLUME_MENUS.keySet())) {
                safely(() -> {
                    if (previous.systemSurfaces.volumeDialog != next.systemSurfaces.volumeDialog
                            || previous.systemSurfaces.compactVolume != next.systemSurfaces.compactVolume) {
                        refreshVolumeSize(controller);
                    }
                    applyVolume(controller);
                }, "volume refresh");
            }
            if (!next.systemSurfaces.volumeDialog) {
                for (View view : new ArrayList<>(VOLUME_SURFACES)) SystemSurfaceStyle.restore(view);
                VOLUME_SURFACES.clear();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run();
        else MAIN.post(refresh);
    }

    private static Object afterPower(XposedInterface.Chain chain) throws Throwable {
        bootstrapContext(chain.getThisObject());
        Object result = chain.proceed();
        Object dialog = chain.getThisObject();
        POWER_MENUS.put(dialog, Boolean.TRUE);
        safely(() -> applyPower(dialog), "power appearance");
        return result;
    }

    private static Object afterVolume(XposedInterface.Chain chain) throws Throwable {
        bootstrapContext(chain.getThisObject());
        Object result = chain.proceed();
        Object controller = chain.getThisObject();
        if (booleanField(controller, "mNeedShowDialog", true)) {
            VOLUME_MENUS.put(controller, Boolean.TRUE);
            safely(() -> applyVolume(controller), "volume appearance");
        }
        return result;
    }

    static int columnHeight(int nativeHeight, Context context, boolean dialog, boolean expanded,
            ControlCenterConfig settings) {
        if (!settings.systemSurfaces.volumeDialog || !settings.systemSurfaces.compactVolume || !dialog || expanded
                || context == null || nativeHeight <= 0) return nativeHeight;
        return Math.min(nativeHeight, dp(context, COMPACT_HEIGHT_DP));
    }

    private static int compactPanelHeight(int nativeHeight, Context context, boolean dialog, boolean expanded) {
        if (!config.systemSurfaces.volumeDialog || !config.systemSurfaces.compactVolume || !dialog || expanded)
            return nativeHeight;
        int fullHeight = dimension(context, "miui_volume_column_height", dp(context, 172));
        int shortHeight = columnHeight(fullHeight, context, true, false, config);
        return Math.max(1, nativeHeight - Math.max(0, fullHeight - shortHeight));
    }

    private static void applyPower(Object dialog) throws Exception {
        Object value = field(dialog, "mSliderViews");
        SparseArray<View> sliders = new SparseArray<>();
        if (value instanceof SparseArray<?> nativeSliders) {
            for (int i = 0; i < nativeSliders.size(); i++)
                if (nativeSliders.valueAt(i) instanceof View track) sliders.put(nativeSliders.keyAt(i), track);
        }
        View center = viewField(dialog, "mSliderView");
        if (center != null && sliders.indexOfValue(center) < 0) sliders.put(0, center);
        if (center == null) center = sliders.get(0);
        View root = viewField(dialog, "mRoot");
        boolean enabled = config.systemSurfaces.powerMenu;
        for (int i = 0; i < sliders.size(); i++)
            POWER_GEOMETRY.computeIfAbsent(sliders.valueAt(i), PowerGeometry::new);
        if (center != null && sliders.size() == 1 && enabled && config.systemSurfaces.extendedPowerMenu)
            POWER_GEOMETRY.get(center).restore(center);
        boolean added = PowerMenuExtension.update(dialog, center, sliders.size(), config);
        String diagnostic = "power=" + enabled + " threeColumns=" + config.systemSurfaces.extendedPowerMenu
                + " nativeTracks=" + sliders.size() + " moduleTracks=" + (added ? 2 : 0);
        if (!diagnostic.equals(POWER_DIAGNOSTICS.put(dialog, diagnostic)))
            Log.i(TAG, "power menu mounted " + diagnostic);
        Object windowValue = field(dialog, "mWindow");
        if (root != null && windowValue instanceof Window window)
            SystemSurfaceStyle.applyBackdrop(root, window, config, enabled);
        for (int i = 0; i < sliders.size(); i++) {
            View slider = sliders.valueAt(i);
            PowerGeometry geometry = POWER_GEOMETRY.get(slider);
            if (!enabled) {
                SystemSurfaceStyle.restore(slider);
                SystemSurfaceStyle.applyContent(slider, config, false);
                if (geometry != null) geometry.restore(slider);
                continue;
            }
            if (geometry == null) {
                geometry = new PowerGeometry(slider);
                POWER_GEOMETRY.put(slider, geometry);
            }
            SystemSurfaceStyle.applyTrack(slider, config, true);
            SystemSurfaceStyle.applyContent(slider, config.withTileScale(100), true);
            if (!added) geometry.apply(slider, root, sliders.keyAt(i), sliders.size(), config);
        }
    }

    private static Object powerAction(XposedInterface.Chain chain) throws Throwable {
        if (!config.systemSurfaces.powerMenu || !config.systemSurfaces.extendedPowerMenu
                || !Integer.valueOf(5).equals(chain.getArgs().get(0))) return chain.proceed();
        Object dialog = chain.getThisObject();
        Context context = context(dialog);
        if (context == null) return chain.proceed();
        Class<?> util;
        try { util = Class.forName("com.android.systemui.miui.volume.VolumeUtil", false,
                dialog.getClass().getClassLoader()); }
        catch (ClassNotFoundException unavailable) { return chain.proceed(); }
        Method read = RuntimeReflection.exact(util, "isSilentMode", new Class<?>[] {Context.class}, false);
        Method write = RuntimeReflection.exact(util, "setSilenceMode",
                new Class<?>[] {Context.class, boolean.class}, false);
        if (read == null || write == null) return chain.proceed();
        try {
            boolean silent = (Boolean) read.invoke(null, context);
            write.invoke(null, context, !silent);
            Method dismiss = RuntimeReflection.exact(dialog.getClass(), "dismiss", new Class<?>[] {int.class}, false);
            if (dismiss != null) dismiss.invoke(dialog, 3);
        } catch (Throwable error) { Log.w(TAG, "power menu silent toggle failed", error); }
        return null;
    }

    private static void applyVolume(Object controller) throws Exception {
        if (!booleanField(controller, "mNeedShowDialog", true)) return;
        boolean enabled = config.systemSurfaces.volumeDialog;
        style(viewField(controller, "mExpandBgView"), enabled, false);
        Object dialogController = field(controller, "mVolumePanelDialogController");
        View panel = viewField(controller, "mVolumePanelView");
        SystemSurfaceStyle.applyContent(panel, config, enabled);
        Object window = field(dialogController, "mWindow");
        if (panel != null && window instanceof Window value)
            SystemSurfaceStyle.applyBackdrop(panel, value, config, enabled);
        View volume = viewField(controller, "mVolumeView");
        if (volume != null && volume.getRootView() != panel && window instanceof Window value)
            SystemSurfaceStyle.applyBackdrop(volume.getRootView(), value, config, enabled);
        Object columns = field(controller, "mColumns");
        if (columns instanceof Iterable<?> iterable) {
            for (Object column : iterable) applyColumn(column);
        }
        applyColumn(field(controller, "mTempColumn"));
        View ringer = viewField(controller, "mRingerModeLayout");
        applyRingerSurfaces(ringer, enabled, 0, new int[1]);
    }

    private static void applyColumn(Object column) throws Exception {
        if (column == null || !booleanField(column, "isNeedShowDialog", true)) return;
        boolean enabled = config.systemSurfaces.volumeDialog;
        style(viewField(column, "slider"), enabled, true);
        style(viewField(column, "progressViewBg"), enabled, true);
    }

    private static void style(View view, boolean enabled, boolean track) {
        if (view == null) return;
        if (!enabled) { SystemSurfaceStyle.restore(view); VOLUME_SURFACES.remove(view); return; }
        if (track) SystemSurfaceStyle.applyTrack(view, config, true);
        else SystemSurfaceStyle.apply(view, config, true);
        VOLUME_SURFACES.add(view);
    }

    private static void applyRingerSurfaces(View view, boolean enabled, int depth, int[] visited) {
        if (view == null || depth > 8 || visited[0]++ >= 96) return;
        String name = resourceName(view);
        if ("bg_blur".equals(name)) style(view, enabled, false);
        if ("timer_seekbar".equals(name) || "volume_timer_view".equals(name))
            style(view, enabled, true);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++)
                applyRingerSurfaces(group.getChildAt(i), enabled, depth + 1, visited);
        }
    }

    private static void refreshVolumeSize(Object controller) throws Exception {
        if (Boolean.TRUE.equals(REFRESHING.get())) return;
        REFRESHING.set(true);
        try {
            Method update = RuntimeReflection.exact(controller.getClass(), "updateVolumePanelSize", new Class<?>[0], false);
            if (update != null) update.invoke(controller);
            Object temporary = field(controller, "mTempColumn");
            if (temporary != null) {
                Method size = RuntimeReflection.exact(temporary.getClass(), "setSize",
                        new Class<?>[] {boolean.class, boolean.class}, false);
                if (size != null) size.invoke(temporary, booleanField(controller, "mExpanded", false),
                        booleanField(controller, "mIsNotifySingle", false));
            }
        } finally { REFRESHING.remove(); }
    }

    private static Context context(Object owner) throws Exception {
        if (owner instanceof Context value) return value;
        if (owner instanceof View view) return view.getContext();
        for (String name : new String[] {"mSystemUIContext", "mSysUIContext"}) {
            Object system = field(owner, name);
            if (system instanceof Context value) return value;
        }
        Object context = field(owner, "mContext");
        return context instanceof Context value ? value : null;
    }

    private static void bootstrapContext(Object owner) {
        safely(() -> {
            Context context = context(owner);
            if (context != null) {
                bootstrap.accept(context);
                ControlCenterConfig latest = current.get();
                if (latest != null) config = latest;
            }
        }, "config bootstrap");
    }

    private static Object field(Object owner, String name) throws Exception {
        if (owner == null) return null;
        Field field = RuntimeReflection.field(owner.getClass(), name);
        return field == null ? null : field.get(owner);
    }

    private static View viewField(Object owner, String name) throws Exception {
        Object value = field(owner, name);
        return value instanceof View view ? view : null;
    }

    private static boolean booleanField(Object owner, String name, boolean fallback) {
        try { Object value = field(owner, name); return value instanceof Boolean result ? result : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static int dimension(Context context, String name, int fallback) {
        for (String pkg : new String[] {context.getPackageName(), "miui.systemui.plugin", "com.android.systemui"}) {
            int id = context.getResources().getIdentifier(name, "dimen", pkg);
            if (id != 0) return context.getResources().getDimensionPixelSize(id);
        }
        return fallback;
    }

    private static String resourceName(View view) {
        try { return view.getId() == View.NO_ID ? "" : view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    private interface Operation { void run() throws Exception; }
    private static void safely(Operation operation, String operationName) {
        try { operation.run(); }
        catch (Throwable error) { Log.w(TAG, "system menu " + operationName + " failed", error); }
    }

    private static final class PowerGeometry {
        final float scaleX, scaleY;
        final int leftMargin, rightMargin;
        PowerGeometry(View view) {
            scaleX = view.getScaleX(); scaleY = view.getScaleY();
            FrameLayout.LayoutParams params = view.getLayoutParams() instanceof FrameLayout.LayoutParams value ? value : null;
            leftMargin = params == null ? 0 : params.leftMargin;
            rightMargin = params == null ? 0 : params.rightMargin;
        }
        void apply(View view, View root, int position, int columns, ControlCenterConfig settings) {
            float scale = settings.tileScale / 100f;
            int width = view.getLayoutParams() == null ? view.getWidth() : view.getLayoutParams().width;
            int height = view.getLayoutParams() == null ? view.getHeight() : view.getLayoutParams().height;
            int availableWidth = root != null && root.getWidth() > 0 ? root.getWidth()
                    : view.getResources().getDisplayMetrics().widthPixels;
            int availableHeight = root != null && root.getHeight() > 0 ? root.getHeight()
                    : view.getResources().getDisplayMetrics().heightPixels;
            if (width > 0 && height > 0) {
                scale = Math.min(scale, Math.max(0.5f, (availableHeight - dp(view.getContext(), 160)) / (float) height));
                if (columns == 3) scale = Math.min(scale, Math.max(0.5f,
                        (availableWidth - dp(view.getContext(), 48)) / (3f * width)));
            }
            view.setScaleX(scaleX * scale); view.setScaleY(scaleY * scale);
            if (columns == 3 && width > 0 && view.getLayoutParams() instanceof FrameLayout.LayoutParams params) {
                int desired = Math.round(width * scale + dp(view.getContext(), 20 + settings.spacing));
                int room = Math.max(0, Math.round((availableWidth - width * scale) / 2f) - dp(view.getContext(), 12));
                int offset = Math.min(desired, room) * 2;
                int nextLeft = position > 0 ? offset : 0;
                int nextRight = position < 0 ? offset : 0;
                if (params.leftMargin != nextLeft || params.rightMargin != nextRight) {
                    params.leftMargin = nextLeft; params.rightMargin = nextRight;
                    view.setLayoutParams(params);
                }
            }
        }
        void restore(View view) {
            view.setScaleX(scaleX); view.setScaleY(scaleY);
            if (view.getLayoutParams() instanceof FrameLayout.LayoutParams params
                    && (params.leftMargin != leftMargin || params.rightMargin != rightMargin)) {
                params.leftMargin = leftMargin; params.rightMargin = rightMargin;
                view.setLayoutParams(params);
            }
        }
    }
}
