package com.xtjm.fusionstatusbar;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Replaces the separate status-bar signal and battery icons with one fused icon. */
public final class SystemUiHooks {
    private static final String BATTERY_CLASS = StatusBarHookInstaller.BATTERY_CLASS;
    private static final String CONTAINER_CLASS = StatusBarHookInstaller.CONTAINER_CLASS;
    private static final String BATTERY_CONTAINER_CLASS = StatusBarHookInstaller.BATTERY_CONTAINER_CLASS;
    private static final String PHONE_STATUS_BAR_CLASS = StatusBarHookInstaller.PHONE_CLASS;
    private static final String PHONE_STATUS_BAR_BASE_CLASS = StatusBarHookInstaller.PHONE_BASE_CLASS;
    private static final String KEYGUARD_STATUS_BAR_CLASS = StatusBarHookInstaller.KEYGUARD_CLASS;
    private static final String CLOCK_CLASS = "com.android.systemui.statusbar.views.MiuiClock";
    private static final String NOTIFICATION_HEADER_CLASS = NotificationHookInstaller.HEADER_CLASS;
    private static final String NOTIFICATION_EXPAND_CLASS = NotificationHookInstaller.EXPAND_CLASS;
    private static final String NOTIFICATION_EXPAND_CALLBACK_CLASS =
            NotificationHookInstaller.EXPAND_CALLBACK_CLASS;
    private static final String NOTIFICATION_STACK_CLASS = NotificationHookInstaller.STACK_CLASS;
    private static final String NETWORK_SPEED_CLASS = NotificationHookInstaller.NETWORK_SPEED_CLASS;
    private static final String MIUI_PAGED_TILE_LAYOUT_CLASS =
            "com.android.systemui.qs.MiuiPagedTileLayout";
    private static final String PAGED_TILE_LAYOUT_CLASS =
            "com.android.systemui.qs.PagedTileLayout";
    private static final String CURRENT_TILES_INTERACTOR_CLASS =
            "com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractorImpl";
    private static final String CURRENT_TILES_INTERACTOR_API_CLASS =
            "com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractor";
    private static final String MIUI_QS_HOST_ADAPTER_CLASS =
            "com.android.systemui.qs.pipeline.domain.adapter.MiuiQSHostAdapter";
    private static final String PLUGIN_FACTORY_CLASS =
            "com.android.systemui.shared.plugins.PluginInstance$PluginFactory";
    private static final String PLUGIN_CLASS_LOADER_FACTORY_CLASS =
            "com.android.systemui.shared.plugins.PluginInstance$Factory$$ExternalSyntheticLambda0";
    private static final String TAG = "FusionStatusBar";
    private static final AtomicBoolean BATTERY_READY = new AtomicBoolean(false);
    private static final AtomicBoolean MEASURE_FAILED = new AtomicBoolean(false);
    private static final AtomicBoolean SLOTS_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean TINT_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FIELD_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SPAN_MEASURE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NOTIFICATION_SIZE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NOTIFICATION_OFFSET_FAILED = new AtomicBoolean(false);
    private static final AtomicBoolean NOTIFICATION_LIST_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CONTROL_CENTER_TILES_LOGGED = new AtomicBoolean(false);
    private static volatile String CONTROL_CENTER_TILE_CATALOG_LAST = "";
    private static final AtomicBoolean CONTROL_CENTER_DISPLAY_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CONTROL_CENTER_HOST_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CONTROL_CENTER_SHAPE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CONTROL_CENTER_HEIGHT_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CONTROL_CENTER_LAYOUT_REBIND_LOGGED =
            new AtomicBoolean(false);
    private static final Set<String> CONTROL_CENTER_SHAPES_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> CONTROL_CENTER_VISUAL_SHAPES_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> CONTROL_CENTER_COMPONENTS_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean CONTROL_CENTER_PLUGIN_HOOKED = new AtomicBoolean(false);
    private static final Map<ClassLoader, Boolean> CONTROL_CENTER_PLUGIN_LOADERS =
            new WeakHashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final BoundedHookRetry INSTALLATION_RETRY = new BoundedHookRetry(
            (task, delay) -> MAIN_HANDLER.postDelayed(task, delay), 3000L, 4);
    private static final BoundedHookRetry CONTROL_CENTER_PLUGIN_RETRY = new BoundedHookRetry(
            (task, delay) -> MAIN_HANDLER.postDelayed(task, delay), 3000L, 8);
    private static final AtomicBoolean CONTROL_CENTER_ADAPTER_REFRESH_POSTED =
            new AtomicBoolean(false);
    private static final RuntimeHookRegistry<XposedInterface.HookHandle> HANDLES =
            new RuntimeHookRegistry<>();
    private static final ThreadLocal<Boolean> CONTAINER_GUARD = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BATTERY_CONTAINER_GUARD = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> NOTIFICATION_PADDING_REFRESH = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CONTROL_CENTER_RAW_HOST_READ = new ThreadLocal<>();
    private static XposedModule module;
    private static ClassLoader loader;
    private static Class<?> batteryClass;
    private static Class<?> batteryContainerClass;
    private static Method setMeasuredDimension;
    private static Method dispatcherTint;
    private static Method notificationTopPaddingMethod;
    private static Field notificationStackStateField;
    private static Field notificationStackAmbientStateField;
    private static Field notificationAmbientTopPaddingField;
    private static Field notificationQsExpansionFractionField;
    private static Field notificationStackInjectorField;
    private static Field notificationControllerInjectorField;
    private static Method notificationUseControlCenterMethod;
    private static Field notificationBigTimeSizeField;
    private static Field notificationExpandContextField;
    private static Field notificationExpandProgressField;
    private static volatile FusionConfig currentConfig = FusionConfig.defaults();
    private static WeakReference<View> notificationStackRef = new WeakReference<>(null);
    private static float nativeNotificationTopPadding;
    private static volatile StatusBarLayoutPlan currentPlan =
            StatusBarLayoutPlan.from(currentConfig);
    private static volatile RuntimeConfigLifecycle configLifecycle;
    private static WeakReference<ViewGroup> currentStatusRoot = new WeakReference<>(null);
    private static final Map<View, Integer> NATIVE_SPEED_VISIBILITY = new WeakHashMap<>();
    private static final Map<ViewGroup, Boolean> NATIVE_ICON_LAYOUT_LOGGED = new WeakHashMap<>();
    private static final Map<Class<?>, Method> SLOT_METHOD_CACHE = new WeakHashMap<>();
    private static final Map<Class<?>, Field> IGNORED_SLOT_FIELD_CACHE = new WeakHashMap<>();
    private static final int[] ICON_LOCATION = new int[2];
    private static final int[] STATUS_LOCATION = new int[2];
    private static final int[] CONTAINER_LOCATION = new int[2];
    private static WeakReference<View> nativeSpeedRef = new WeakReference<>(null);
    private static WeakReference<ViewGroup> nativeSpeedRoot = new WeakReference<>(null);
    private static WeakReference<Object> controlCenterHostRef = new WeakReference<>(null);
    private static WeakReference<Object> controlCenterCompactRef = new WeakReference<>(null);
    private static WeakReference<Object> controlCenterDistributorRef = new WeakReference<>(null);
    private static WeakReference<Object> controlCenterCompactCardRef = new WeakReference<>(null);
    private static WeakReference<Object> controlCenterListRef = new WeakReference<>(null);
    private static volatile ControlCenterLayoutPlan.Item controlCenterSelectedCompactPair;
    private static volatile ControlCenterLayoutPlan.Item controlCenterActiveCompactPair;
    private static WeakReference<View> controlCenterPreviewRootRef = new WeakReference<>(null);
    private static ControlCenterConfig nativeControlCenterAppliedSettings;
    private static final Map<View, Integer> CONTROL_CENTER_BASE_HEIGHTS = new WeakHashMap<>();
    private static final Map<View, Integer> CONTROL_CENTER_BASE_ICON_WIDTHS =
            new WeakHashMap<>();
    private static final Map<View, IconDimensionBaseline> CONTROL_CENTER_ICON_DIMENSIONS =
            new WeakHashMap<>();
    private static final Map<LinearLayout, CompactCardBaseline> CONTROL_CENTER_COMPACT_LAYOUTS =
            new WeakHashMap<>();
    private static final Map<View, TileVisualBaseline> CONTROL_CENTER_TILE_VISUALS =
            new WeakHashMap<>();
    private static final Map<ViewGroup, boolean[]> CONTROL_CENTER_ROOT_CLIPPING =
            new WeakHashMap<>();
    private static final Map<ViewGroup, Integer> CONTROL_CENTER_ROOT_COLUMNS =
            new WeakHashMap<>();
    private static final Map<View, Integer> CONTROL_CENTER_BASE_ICON_CONTAINER_WIDTHS =
            new WeakHashMap<>();
    private static final Map<View, String> CONTROL_CENTER_CAPTURE_SPECS = new WeakHashMap<>();
    private static final Map<Object, Integer> CONTROL_CENTER_NATIVE_SPANS = new WeakHashMap<>();
    private static final Map<View, Integer> CONTROL_CENTER_CAPTURE_NATIVE_SPANS =
            new WeakHashMap<>();
    private static final ControlCenterPreviewCapture PREVIEW_CAPTURE = new ControlCenterPreviewCapture(
            MAIN_HANDLER, () -> controlCenterPreviewRootRef.get(), SystemUiHooks::serializeControlCenterLayout);
    private static long nativeSpeedScanAt;
    private static final LatestRootRefreshScheduler<ViewGroup> STATUS_BAR_REFRESH =
            new LatestRootRefreshScheduler<>(MAIN_HANDLER::post,
                    SystemUiHooks::reapplyStatusBarViews);
    private static final Map<String, Long> RECENT_DIAGNOSTICS = new java.util.LinkedHashMap<>();

    private SystemUiHooks() {
    }

    public static synchronized void install(XposedModule source, ClassLoader classLoader) {
        module = source;
        loader = classLoader;
        if (classLoader == null) {
            log(Log.ERROR, "systemui class loader missing", null);
            return;
        }
        StatusBarHookInstaller statusBarInstaller = statusBarHookInstaller();
        StatusBarHookInstaller.Targets statusBarTargets = statusBarInstaller.resolve(classLoader);
        hookPluginClassLoaderFactory();
        int controlCenterHooks = hookControlCenterTiles();
        if (statusBarTargets.battery == null) {
            log(Log.ERROR, "battery view missing; original icons kept", null);
        }
        if (statusBarTargets.container == null) {
            log(Log.ERROR, "icon container missing; signal slots stay visible", null);
        }
        batteryClass = statusBarTargets.battery;
        batteryContainerClass = statusBarTargets.batteryContainer;
        StatusBarHookInstaller.Result statusBar = statusBarInstaller.install(statusBarTargets);
        for (String missingMethod : statusBar.missingMethods) {
            log(Log.ERROR, "method missing " + missingMethod, null);
        }
        int batteryHooks = statusBar.batteryHooks;
        int containerHooks = statusBar.containerHooks;
        int batteryContainerHooks = statusBar.batteryContainerHooks;
        int layoutHooks = statusBar.layoutHooks;
        Class<?> clock = findClass(CLOCK_CLASS);
        if (clock != null) {
            hookDeclaredClockMethod(clock, "updateTime");
            hookDeclaredClockMethod(clock, "onAttachedToWindow");
        } else {
            log(Log.WARN, "MiuiClock missing; custom time unavailable", null);
        }
        NotificationHookInstaller.Result notification = notificationHookInstaller().install(classLoader);
        notificationBigTimeSizeField = notification.bigTimeSizeField;
        notificationExpandContextField = notification.expandContextField;
        notificationExpandProgressField = notification.expandProgressField;
        notificationStackStateField = notification.stackStateField;
        notificationStackAmbientStateField = notification.stackAmbientStateField;
        notificationAmbientTopPaddingField = notification.ambientTopPaddingField;
        notificationQsExpansionFractionField = notification.qsExpansionFractionField;
        notificationStackInjectorField = notification.stackInjectorField;
        notificationControllerInjectorField = notification.controllerInjectorField;
        notificationUseControlCenterMethod = notification.useControlCenterMethod;
        notificationTopPaddingMethod = notification.topPaddingMethod;
        for (String missing : notification.missingClasses) {
            log(Log.WARN, "notification hook class missing " + missing, null);
        }
        if (notification.headerClass == null) {
            log(Log.WARN,
                    "MiuiNotificationHeaderView missing; notification-center time unavailable",
                    null);
        }
        for (String missing : notification.missingMembers) {
            log(Log.WARN, "notification hook member missing " + missing, null);
        }
        log(Log.INFO, "hooks installed battery=" + batteryHooks + " container="
                + containerHooks + " batteryContainer=" + batteryContainerHooks
                + " layout=" + layoutHooks + " controlCenter=" + controlCenterHooks, null);
        INSTALLATION_RETRY.scheduleIfNeeded(HANDLES::hasFailures,
                () -> install(source, classLoader));
    }

    private static int hookPluginClassLoaderFactory() {
        int before = HANDLES.size();
        Class<?> factory = findClassQuiet(PLUGIN_FACTORY_CLASS, loader);
        if (factory != null) {
            for (Method method : factory.getDeclaredMethods()) {
                if ("createPlugin".equals(method.getName())) {
                    method.setAccessible(true);
                    hookMethod("PluginFactory#createPlugin/" + method.getParameterCount(), method,
                            SystemUiHooks::pluginFactoryCreatePlugin);
                } else if ("createPluginContext".equals(method.getName())) {
                    method.setAccessible(true);
                    hookMethod("PluginFactory#createPluginContext/" + method.getParameterCount(),
                            method, SystemUiHooks::pluginFactoryCreatePluginContext);
                }
            }
        } else {
            log(Log.WARN, "plugin factory missing; plugin hooks will use package-ready fallback",
                    null);
        }

        // Older HyperOS builds expose the class-loader supplier as a generated lambda.
        Class<?> supplier = findClassQuiet(PLUGIN_CLASS_LOADER_FACTORY_CLASS, loader);
        if (supplier != null) {
            for (Method method : supplier.getDeclaredMethods()) {
                if ("get".equals(method.getName()) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    hookMethod("PluginInstanceFactory#get", method,
                            SystemUiHooks::pluginClassLoaderFactoryGet);
                }
            }
        }
        int count = HANDLES.size() - before;
        log(Log.INFO, "plugin classloader factory hooks=" + count
                + " factoryClass=" + (factory != null), null);
        return count;
    }

    private static Object pluginFactoryCreatePlugin(XposedInterface.Chain chain)
            throws Throwable {
        capturePluginClassLoader(chain.getThisObject(), null);
        return chain.proceed();
    }

    private static Object pluginFactoryCreatePluginContext(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        ClassLoader classLoader = null;
        if (result instanceof Context context) {
            classLoader = context.getClassLoader();
        }
        capturePluginClassLoader(chain.getThisObject(), classLoader);
        return result;
    }

    private static Object pluginClassLoaderFactoryGet(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (!(result instanceof ClassLoader classLoader)) return result;
        capturePluginClassLoader(chain.getThisObject(), classLoader);
        return result;
    }

    private static void capturePluginClassLoader(Object factory, ClassLoader knownLoader) {
        ComponentName componentName = pluginComponentName(factory);
        if (componentName == null
                || !"miui.systemui.plugin".equals(componentName.getPackageName())) {
            return;
        }
        ClassLoader classLoader = knownLoader;
        if (classLoader == null) {
            classLoader = pluginClassLoaderFromFactory(factory);
        }
        if (classLoader == null) {
            log(Log.WARN, "plugin component found but classloader unavailable component="
                    + componentName.flattenToShortString(), null);
            return;
        }
        log(Log.INFO, "plugin classloader captured component="
                + componentName.flattenToShortString(), null);
        installPluginHooks(module, classLoader);
    }

    private static ComponentName pluginComponentName(Object target) {
        if (target == null) return null;
        for (String fieldName : new String[] {"mComponentName", "componentName"}) {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) continue;
            try {
                Object value = field.get(target);
                if (value instanceof ComponentName componentName) return componentName;
            } catch (Throwable ignored) {
                // Try the alternate field name used by newer HyperOS builds.
            }
        }
        return null;
    }

    private static ClassLoader pluginClassLoaderFromFactory(Object target) {
        if (target == null) return null;
        for (String fieldName : new String[] {"mClassLoaderFactory", "classLoaderFactory"}) {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) continue;
            try {
                Object value = field.get(target);
                if (value instanceof ClassLoader classLoader) return classLoader;
                Method get = value == null ? null
                        : findMethod(value.getClass(), "get", new Class<?>[] {});
                Object result = get == null ? null : get.invoke(value);
                if (result instanceof ClassLoader classLoader) return classLoader;
            } catch (Throwable ignored) {
                // The factory implementation varies between HyperOS releases.
            }
        }
        return null;
    }

    private static String pluginPackageName(Object target) {
        if (target == null) return null;
        ComponentName componentName = pluginComponentName(target);
        if (componentName != null) return componentName.getPackageName();
        try {
            for (Field field : target.getClass().getDeclaredFields()) {
                if (!ApplicationInfo.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof ApplicationInfo info) return info.packageName;
            }
        } catch (Throwable ignored) {
            // The synthetic lambda field names vary across SystemUI builds.
        }
        return null;
    }

    public static void installPluginHooks(XposedModule source, ClassLoader classLoader) {
        if (source == null || classLoader == null) return;
        if (module == null) module = source;
        int hooks = hookControlCenterPlugin(classLoader);
        scheduleControlCenterPluginRetry();
        log(Log.INFO, "plugin control-center hooks=" + hooks
                + " loader=" + classLoader.getClass().getName(), null);
    }

    private static StatusBarHookInstaller statusBarHookInstaller() {
        return new StatusBarHookInstaller(
                (name, targetLoader) -> findClass(name, targetLoader),
                (id, method, hooker) -> {
                    if (HANDLES.hasHandle(method, id)) return 0;
                    return hookMethod(id, method, hooker) && HANDLES.hasHandle(method, id) ? 1 : 0;
                },
                SystemUiHooks::statusBarHooker);
    }

    private static XposedInterface.Hooker statusBarHooker(StatusBarHookInstaller.Callback callback) {
        return switch (callback) {
            case BATTERY_MEASURE -> SystemUiHooks::batteryMeasure;
            case BATTERY_LAYOUT -> SystemUiHooks::batteryLayout;
            case BATTERY_FINISH_INFLATE -> SystemUiHooks::batteryFinishInflate;
            case BATTERY_ATTACHED -> SystemUiHooks::batteryAttached;
            case AFTER_UPDATE -> chain -> afterUpdate(chain);
            case AFTER_TINT -> SystemUiHooks::afterTint;
            case INTERCEPT_CHILD_VISIBILITY -> SystemUiHooks::interceptChildVisibility;
            case CONTAINER_MEASURE -> chain -> containerPass(chain, true);
            case CONTAINER_LAYOUT -> chain -> containerPass(chain, false);
            case AFTER_SET_IGNORED -> SystemUiHooks::afterSetIgnored;
            case AFTER_VIEW_ADDED -> SystemUiHooks::afterViewAdded;
            case BATTERY_CONTAINER_MEASURE -> chain -> batteryContainerPass(chain, true);
            case BATTERY_CONTAINER_LAYOUT -> chain -> batteryContainerPass(chain, false);
            case PHONE_FINISH_INFLATE -> SystemUiHooks::phoneFinishInflate;
            case PHONE_ATTACHED -> SystemUiHooks::phoneAttached;
            case PHONE_LAYOUT -> SystemUiHooks::phoneLayout;
            case PHONE_CUTOUT_UPDATE -> SystemUiHooks::phoneCutoutUpdate;
            case PHONE_MEASURE -> SystemUiHooks::phoneMeasure;
            case KEYGUARD_ATTACHED -> SystemUiHooks::keyguardAttach;
            case KEYGUARD_LAYOUT -> SystemUiHooks::keyguardLayout;
        };
    }

    private static int hookControlCenterTiles() {
        int before = HANDLES.size();
        int interactorHooks = hookCurrentTilesInteractor();
        int hostHooks = hookMiuiQsHostAdapter();
        int pluginHooks = hookControlCenterPlugin();
        if (pluginHooks == 0) {
            log(Log.INFO, "control-center plugin hooks deferred until PluginFactory loads it",
                    null);
        }
        Class<?> miuiType = findClass(MIUI_PAGED_TILE_LAYOUT_CLASS);
        Class<?> baseType = findClass(PAGED_TILE_LAYOUT_CLASS);
        int layoutHooks = 0;
        if (miuiType != null) {
            layoutHooks += hookControlCenterClass(miuiType);
        }
        if (baseType != null) {
            layoutHooks += hookControlCenterClass(baseType);
        }
        if (interactorHooks == 0 && hostHooks == 0 && pluginHooks == 0 && layoutHooks == 0) {
            log(Log.WARN, "control-center tile hooks unavailable", null);
        }
        return HANDLES.size() - before;
    }

    private static int hookMiuiQsHostAdapter() {
        int before = HANDLES.size();
        Class<?> type = findClass(MIUI_QS_HOST_ADAPTER_CLASS);
        if (type == null) return 0;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() != 0) continue;
            if ("getTiles".equals(method.getName())
                    && Collection.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                hookMethod("MiuiQSHostAdapter#getTiles", method,
                        SystemUiHooks::controlCenterHostGetTiles);
            } else if ("getSpecs".equals(method.getName())
                    && List.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                hookMethod("MiuiQSHostAdapter#getSpecs", method,
                        SystemUiHooks::controlCenterHostGetSpecs);
            }
        }
        int count = HANDLES.size() - before;
        if (count > 0) {
            log(Log.INFO, "control-center MiuiQSHost hooks installed=" + count, null);
        }
        return count;
    }

    private static int hookControlCenterPlugin() {
        return hookControlCenterPlugin(loader);
    }

    private static int hookControlCenterPlugin(ClassLoader targetLoader) {
        if (targetLoader == null) return 0;
        int before = HANDLES.size();
        ControlCenterPluginHookInstaller installer = controlCenterPluginHookInstaller();
        ControlCenterPluginHookInstaller.Result result = installer.install(targetLoader);
        int count = HANDLES.size() - before;
        boolean layoutAvailable = result.layoutAvailable;
        synchronized (CONTROL_CENTER_PLUGIN_LOADERS) {
            CONTROL_CENTER_PLUGIN_LOADERS.put(targetLoader, layoutAvailable);
        }
        if (layoutAvailable) {
            CONTROL_CENTER_PLUGIN_HOOKED.set(true);
        }
        if (count > 0) {
            log(Log.INFO, "control-center plugin hooks installed=" + count
                    + " compact=" + result.count(ControlCenterPluginHookInstaller.Group.COMPACT)
                    + " list=" + result.count(ControlCenterPluginHookInstaller.Group.LIST)
                    + " distributor=" + result.count(ControlCenterPluginHookInstaller.Group.DISTRIBUTOR)
                    + " compactSpecs=" + result.count(ControlCenterPluginHookInstaller.Group.COMPACT_SPECS)
                    + " compactPair=" + result.count(ControlCenterPluginHookInstaller.Group.COMPACT_PAIR)
                    + " visual=" + result.count(ControlCenterPluginHookInstaller.Group.VISUAL)
                    + " shape=" + result.count(ControlCenterPluginHookInstaller.Group.SHAPE)
                    + " bind=" + result.count(ControlCenterPluginHookInstaller.Group.BIND)
                    + " adapterNotify=" + result.count(ControlCenterPluginHookInstaller.Group.ADAPTER_NOTIFY)
                    + " componentBind=" + result.count(ControlCenterPluginHookInstaller.Group.COMPONENT_BIND)
                    + " componentShape=" + result.count(ControlCenterPluginHookInstaller.Group.COMPONENT_SHAPE)
                    + " componentLayout=" + result.count(ControlCenterPluginHookInstaller.Group.COMPONENT_LAYOUT)
                    + " grid=" + result.count(ControlCenterPluginHookInstaller.Group.GRID)
                    + " gridContent=" + result.count(ControlCenterPluginHookInstaller.Group.GRID_CONTENT)
                    + " layoutAvailable=" + layoutAvailable, null);
        } else if (!layoutAvailable) {
            log(Log.WARN, "control-center plugin classes unavailable in loader="
                    + targetLoader.getClass().getName(), null);
        }
        return count;
    }

    private static ControlCenterPluginHookInstaller controlCenterPluginHookInstaller() {
        return new ControlCenterPluginHookInstaller(
                SystemUiHooks::findClassQuiet,
                (id, method, hooker) -> hookMethod(id, method, hooker),
                SystemUiHooks::controlCenterPluginHooker,
                SystemUiHooks::installControlCenterMaterialHooks);
    }

    private static XposedInterface.Hooker controlCenterPluginHooker(
            ControlCenterPluginHookInstaller.Callback callback) {
        return switch (callback) {
            case COMPACT_TILES -> SystemUiHooks::controlCenterCompactTiles;
            case DISTRIBUTE_TILES -> SystemUiHooks::controlCenterPluginDistributeTiles;
            case TILE_SPAN -> SystemUiHooks::controlCenterPluginTileSpan;
            case TILE_BIND -> SystemUiHooks::controlCenterPluginTileBind;
            case DISTRIBUTOR_NOTIFY -> SystemUiHooks::controlCenterPluginNotifyChanged;
            case DISTRIBUTE_PANELS -> SystemUiHooks::controlCenterPluginDistributePanels;
            case SEPARATED_PANELS -> SystemUiHooks::controlCenterPluginSeparatedPanels;
            case COMPACT_SPECS -> SystemUiHooks::controlCenterPluginCompactSpecs;
            case COMPACT_CARD_SPECS -> SystemUiHooks::controlCenterPluginCompactCardSpecs;
            case COMPACT_CARD_CREATED -> SystemUiHooks::controlCenterPluginCompactCardCreated;
            case COMPACT_CARD_DESTROYED -> SystemUiHooks::controlCenterPluginCompactCardDestroyed;
            case WINDOW_BLUR_RATIO -> SystemUiHooks::controlCenterPluginWindowBlurRatio;
            case ICON_SIZE -> SystemUiHooks::controlCenterPluginIconSize;
            case BACKGROUND -> SystemUiHooks::controlCenterPluginBackground;
            case CORNER_RADIUS -> SystemUiHooks::controlCenterPluginCornerRadius;
            case CONTAINER_HEIGHT -> SystemUiHooks::controlCenterPluginContainerHeight;
            case SET_SPAN -> SystemUiHooks::controlCenterPluginSetSpan;
            case DISTRIBUTE_CONTENT -> SystemUiHooks::controlCenterPluginDistributeContent;
            case GRID_REFRESH -> SystemUiHooks::controlCenterPluginGridRefresh;
            case ADAPTER_NOTIFY -> SystemUiHooks::controlCenterPluginAdapterNotifyChanged;
            case COMPONENT_BIND -> SystemUiHooks::controlCenterPluginComponentBind;
            case COMPONENT_SPAN -> SystemUiHooks::controlCenterPluginComponentSpan;
            case COMPONENT_LAYOUT_BIND -> SystemUiHooks::controlCenterPluginComponentLayoutBind;
        };
    }

    private static Object controlCenterPluginWindowBlurRatio(XposedInterface.Chain chain)
            throws Throwable {
        Object[] args = chain.getArgs().toArray();
        args[0] = ControlCenterMaterials.windowRatio(chain.getThisObject(), (Float) args[0],
                currentConfig.controlCenter, controlCenterNativeEditing(chain.getThisObject()));
        return chain.proceed(args);
    }

    private static void installControlCenterMaterialHooks() {
        int before = HANDLES.size();
        int nativeCount = 0;
        for (String name : new String[] {"setMiBackgroundBlurRadius", "setMiViewBlurMode", "setMiBackgroundBlurMode",
                "setMiBackgroundBlurScaleRatio", "setMiBackgroundBlendColors"}) {
            Class<?> parameter = name.equals("setMiBackgroundBlendColors") ? ArrayList.class
                    : name.equals("setMiBackgroundBlurScaleRatio") ? float.class : int.class;
            try {
                Method method = View.class.getDeclaredMethod(name, parameter);
                if (hookMethod("control-material-" + name, method, chain -> {
                    View view = (View) chain.getThisObject();
                    Object[] args = ControlCenterMaterials.nativeArguments(view, name, chain.getArgs().toArray(),
                            currentConfig.controlCenter, controlCenterNativeEditing(view));
                    Object result = chain.proceed(args);
                    if (name.equals("setMiViewBlurMode") || name.equals("setMiBackgroundBlurMode")) ControlCenterMaterials.updateNativeSurface(view,
                            currentConfig.controlCenter, controlCenterNativeEditing(view));
                    return result;
                })) nativeCount++;
            } catch (NoSuchMethodException ignored) { }
        }
        for (Class<?> type : new Class<?>[] {android.graphics.drawable.GradientDrawable.class,
                android.graphics.drawable.ColorDrawable.class, android.graphics.drawable.LayerDrawable.class,
                android.graphics.drawable.BitmapDrawable.class}) {
            try {
                hookMethod("control-surface-alpha-" + type.getSimpleName(), type.getDeclaredMethod("setAlpha", int.class), chain -> {
                    Object[] args = chain.getArgs().toArray();
                    args[0] = ControlCenterMaterials.drawableAlpha((Drawable) chain.getThisObject(), (Integer) args[0], currentConfig.controlCenter);
                    return chain.proceed(args);
                });
            } catch (NoSuchMethodException ignored) { }
        }
        if (HANDLES.size() != before)
            log(Log.INFO, "control-center native material APIs=" + nativeCount + "/5", null);
    }

    private static void scheduleControlCenterPluginRetry() {
        CONTROL_CENTER_PLUGIN_RETRY.scheduleIfNeeded(
                () -> !CONTROL_CENTER_PLUGIN_HOOKED.get() || HANDLES.hasFailures(), () -> {
            ArrayList<ClassLoader> targets;
            synchronized (CONTROL_CENTER_PLUGIN_LOADERS) {
                targets = new ArrayList<>(CONTROL_CENTER_PLUGIN_LOADERS.keySet());
            }
            if (!targets.contains(loader)) targets.add(loader);
            for (ClassLoader target : targets) hookControlCenterPlugin(target);
        });
    }

    private static int hookCurrentTilesInteractor() {
        int before = HANDLES.size();
        Class<?> implementation = findClass(CURRENT_TILES_INTERACTOR_CLASS);
        if (implementation != null) {
            for (Method method : implementation.getDeclaredMethods()) {
                if (!"setTiles".equals(method.getName())
                        || method.getParameterCount() != 1
                        || !List.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    continue;
                }
                method.setAccessible(true);
                // The native tile repository is user-owned. Do not rewrite it from
                // the module; display overlays are applied by the read/distribution
                // hooks below.
            }
        }
        Class<?> api = findClass(CURRENT_TILES_INTERACTOR_API_CLASS);
        if (api != null) {
            for (Method method : api.getDeclaredMethods()) {
                if (!"getCurrentQSTiles".equals(method.getName())
                        || method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                hookMethod("CurrentTilesInteractor#getCurrentQSTiles", method,
                        SystemUiHooks::controlCenterCurrentTiles);
            }
        }
        int count = HANDLES.size() - before;
        if (count == 0) {
            log(Log.WARN, "CurrentTilesInteractor methods missing", null);
        } else {
            log(Log.INFO, "control-center data hooks installed=" + count, null);
        }
        return count;
    }

    private static int hookControlCenterClass(Class<?> type) {
        if (type == null) return 0;
        for (Method method : type.getDeclaredMethods()) {
            if ("onAttachedToWindow".equals(method.getName())
                    || "updateResources".equals(method.getName())
                    || "onMeasure".equals(method.getName())
                    || "onLayout".equals(method.getName())) {
                method.setAccessible(true);
                hookMethod(type.getSimpleName() + "#" + method.getName(), method,
                        SystemUiHooks::controlCenterLayoutRefresh);
            }
        }
        return 0;
    }

    private static Object controlCenterCurrentTiles(XposedInterface.Chain chain)
            throws Throwable {
        ensureConfigForTarget(chain.getThisObject());
        Object result = chain.proceed();
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasTileOrderOverride()
                || !(result instanceof List<?> source)) {
            return result;
        }
        List<?> visible = ControlCenterTileOrder.forDisplay(
                new ArrayList<>(source), settings, SystemUiHooks::tileSpec);
        if (!source.isEmpty() && CONTROL_CENTER_DISPLAY_LOGGED.compareAndSet(false, true)) {
            log(Log.INFO, "control-center current tiles overlay applied source=" + source.size()
                    + " visible=" + visible.size() + " specs=" + tileSpecs(visible), null);
        }
        return visible;
    }

    private static Object controlCenterHostGetTiles(XposedInterface.Chain chain)
            throws Throwable {
        controlCenterHostRef = new WeakReference<>(chain.getThisObject());
        ensureConfigForTarget(chain.getThisObject());
        Object result = chain.proceed();
        return reorderControlCenterResult(result, true, "MiuiQSHost#getTiles");
    }

    private static Object controlCenterHostGetSpecs(XposedInterface.Chain chain)
            throws Throwable {
        ensureConfigForTarget(chain.getThisObject());
        Object result = chain.proceed();
        return reorderControlCenterResult(result, true, "MiuiQSHost#getSpecs");
    }

    private static Object reorderControlCenterResult(Object result, boolean filterHidden,
            String sourceName) {
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasTileOrderOverride()
                || !(result instanceof Collection<?> source)) {
            return result;
        }
        List<?> reordered = ControlCenterTileOrder.forDisplay(
                new ArrayList<>(source), settings, SystemUiHooks::tileSpec);
        if (!source.isEmpty() && CONTROL_CENTER_HOST_LOGGED.compareAndSet(false, true)) {
            log(Log.INFO, "control-center plugin source=" + sourceName + " source="
                    + source.size() + " visible=" + reordered.size()
                    + " specs=" + tileSpecs(reordered), null);
        }
        return reordered;
    }

    private static String tileSpecs(Collection<?> values) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (Object value : values) {
            if (count++ > 0) result.append(',');
            if (count > 24) {
                result.append("...");
                break;
            }
            result.append(tileSpec(value));
        }
        return result.toString();
    }

    private static Object controlCenterCompactTiles(XposedInterface.Chain chain)
            throws Throwable {
        controlCenterCompactRef = new WeakReference<>(chain.getThisObject());
        Object result = chain.proceed();
        ensureConfigForTarget(chain.getThisObject());
        ControlCenterConfig settings = currentConfig.controlCenter;
        ControlCenterLayoutPlan.Item activePair = activeCompactPair();
        if ((!settings.enabled || !settings.hasTileOrderOverride()) && activePair == null) {
            return result;
        }
        try {
            Object target = chain.getThisObject();
            Field field = findField(target.getClass(), "prepareShowQSList");
            Object value = field == null ? null : field.get(target);
            if (value instanceof List<?> source) {
                List<?> reordered = activePair == null ? new ArrayList<>(source)
                        : ControlCenterCompactPairPolicy.withoutPair(
                                new ArrayList<>(source), activePair, SystemUiHooks::tileSpec);
                int pairRemoved = source.size() - reordered.size();
                if (settings.enabled && settings.hasTileOrderOverride()) {
                    reordered = ControlCenterTileOrder.forDisplay(
                            reordered, settings, SystemUiHooks::tileSpec);
                }
                int originalSize = source.size();
                source.clear();
                ((List<Object>) source).addAll(reordered);
                if (activePair != null && pairRemoved > 0
                        && CONTROL_CENTER_COMPONENTS_LOGGED.add(
                                "compact-pair-filter:" + activePair.id)) {
                    log(Log.INFO, "control-center compact pair duplicate tiles removed count="
                            + pairRemoved, null);
                }
                if (CONTROL_CENTER_HOST_LOGGED.compareAndSet(false, true)) {
                    log(Log.INFO, "control-center compact source=sysui_flip_qs_tiles source="
                            + originalSize + " visible=" + reordered.size(), null);
                }
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center compact tile overlay failed", error);
        }
        return result;
    }

    private static Object controlCenterPluginDistributeTiles(XposedInterface.Chain chain)
            throws Throwable {
        controlCenterListRef = new WeakReference<>(chain.getThisObject());
        rememberControlCenterPreviewRoot(chain.getThisObject());
        Object result = chain.proceed();
        ensureConfigForTarget(chain.getThisObject());
        applyPluginTileOrder(chain.getThisObject(), "QSListController#distributeTiles");
        requestControlCenterPreviewCapture(contextFor(chain.getThisObject()));
        return result;
    }

    private static Object controlCenterPluginTileSpan(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ensureConfigForTarget(chain.getThisObject());
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasShapeOverride()) return result;
        String spec = tileSpec(chain.getThisObject());
        if (spec.isEmpty() || !ControlCenterTileLayout.hasShape(settings.layout, spec)) {
            return result;
        }
        int width = settings.tileWidth(spec);
        String shapeKey = spec + "=" + width + "x" + settings.tileHeight(spec);
        if (width != 1 && CONTROL_CENTER_SHAPES_LOGGED.add(shapeKey)) {
            log(Log.INFO, "control-center shape span applied spec=" + spec
                    + " width=" + width + " height=" + settings.tileHeight(spec), null);
        }
        return Integer.valueOf(width);
    }

    private static Object controlCenterPluginComponentSpan(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ensureConfigForTarget(chain.getThisObject());
        String spec = ControlCenterComponentSpec.fromControllerName(
                chain.getThisObject().getClass().getName());
        if (!spec.isEmpty() && result instanceof Number) {
            int nativeSpan = ((Number) result).intValue();
            if (nativeSpan > 0) CONTROL_CENTER_NATIVE_SPANS.put(
                    chain.getThisObject(), nativeSpan);
        }
        ControlCenterConfig settings = currentConfig.controlCenter;
        ControlCenterLayoutPlan.Item compactPair = ControlCenterComponentSpec.QS_CARD.equals(spec)
                ? activeCompactPair() : null;
        if (compactPair != null) return Integer.valueOf(compactPair.width);
        if (spec.isEmpty() || !settings.enabled
                || !ControlCenterTileLayout.hasShape(settings.layout, spec)) return result;
        int width = settings.tileWidth(spec);
        String key = spec + "=" + width + "x" + settings.tileHeight(spec);
        if (CONTROL_CENTER_SHAPES_LOGGED.add(key)) {
            log(Log.INFO, "control-center component span applied spec=" + spec
                    + " width=" + width + " height=" + settings.tileHeight(spec), null);
        }
        return Integer.valueOf(width);
    }

    private static Object controlCenterPluginComponentLayoutBind(XposedInterface.Chain chain)
            throws Throwable {
        if (controlCenterNativeEditing(chain.getThisObject())) return chain.proceed();
        boolean compactCard = ControlCenterPluginHookInstaller.COMPACT_QS_CARD_CONTROLLER_CLASS
                .equals(
                chain.getThisObject().getClass().getName());
        if (compactCard) captureCompactCardBaseline(chain.getThisObject());
        Object result = chain.proceed();
        try {
            Method getHolder = findMethod(chain.getThisObject().getClass(), "getHolder",
                    new Class<?>[] {});
            Object holder = getHolder == null ? null : getHolder.invoke(chain.getThisObject());
            applyPluginTileHeight(holder, chain.getThisObject());
            if (compactCard) {
                applyCompactPairLayout(holder, activeCompactPair());
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center component post-bind shape failed", error);
        }
        return result;
    }

    private static void captureCompactCardBaseline(Object card) {
        try {
            Method getHolder = findMethod(card.getClass(), "getHolder", new Class<?>[] {});
            Object holder = getHolder == null ? null : getHolder.invoke(card);
            Field itemView = holder == null ? null : findField(holder.getClass(), "itemView");
            Object value = itemView == null ? null : itemView.get(holder);
            if (!(value instanceof LinearLayout root) || root.getChildCount() != 2
                    || root.getLayoutParams() == null) return;
            CONTROL_CENTER_COMPACT_LAYOUTS.putIfAbsent(root,
                    new CompactCardBaseline(root, root.getLayoutParams().height));
        } catch (Throwable error) {
            log(Log.WARN, "control-center compact card baseline unavailable", error);
        }
    }

    private static void applyCompactPairLayout(Object holder,
            ControlCenterLayoutPlan.Item pair) {
        if (holder == null) return;
        Field itemViewField = findField(holder.getClass(), "itemView");
        try {
            Object value = itemViewField == null ? null : itemViewField.get(holder);
            if (!(value instanceof LinearLayout root) || root.getChildCount() != 2) return;
            if (pair == null) {
                CompactCardBaseline baseline = CONTROL_CENTER_COMPACT_LAYOUTS.remove(root);
                if (baseline != null) baseline.restore(root);
                return;
            }
            ViewGroup.LayoutParams rootParams = root.getLayoutParams();
            if (rootParams == null) return;
            CONTROL_CENTER_COMPACT_LAYOUTS.putIfAbsent(root,
                    new CompactCardBaseline(root, rootParams.height));
            boolean horizontal = pair.direction == ControlCenterLayoutPlan.Direction.HORIZONTAL;
            root.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            int rowHeight = controlCenterRowHeight(root, Math.min(pair.height, 4));
            if (pair.height > 4) {
                rowHeight += (pair.height - 4) * controlCenterRowHeight(root, 1);
            }
            if (rowHeight <= 0) return;
            if (rootParams.height != rowHeight) {
                rootParams.height = rowHeight;
                root.setLayoutParams(rootParams);
            }
            for (int index = 0; index < 2; index++) {
                View child = root.getChildAt(index);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        horizontal ? 0 : ViewGroup.LayoutParams.MATCH_PARENT,
                        horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                        1f);
                child.setLayoutParams(params);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center compact pair layout failed", error);
        }
    }

    private static final class CompactCardBaseline {
        final int orientation;
        final int height;
        final LinearLayout.LayoutParams[] children = new LinearLayout.LayoutParams[2];

        CompactCardBaseline(LinearLayout root, int currentHeight) {
            orientation = root.getOrientation();
            height = currentHeight;
            for (int index = 0; index < children.length; index++) {
                ViewGroup.LayoutParams params = root.getChildAt(index).getLayoutParams();
                if (params instanceof LinearLayout.LayoutParams linearParams) {
                    children[index] = new LinearLayout.LayoutParams(linearParams);
                }
            }
        }

        void restore(LinearLayout root) {
            root.setOrientation(orientation);
            ViewGroup.LayoutParams params = root.getLayoutParams();
            if (params != null && params.height != height) {
                params.height = height;
                root.setLayoutParams(params);
            }
            for (int index = 0; index < children.length; index++) {
                if (children[index] != null) {
                    root.getChildAt(index).setLayoutParams(
                            new LinearLayout.LayoutParams(children[index]));
                }
            }
        }
    }

    private static Object controlCenterPluginComponentBind(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        Object[] args = chain.getArgs().toArray();
        if (args.length < 2 || !(args[1] instanceof Integer)) return result;
        try {
            Method getItem = findMethod(args[0].getClass(),
                    "getItem$miui_controlcenter_release", new Class<?>[] {});
            Object item = getItem == null ? null : getItem.invoke(args[0]);
            Field itemViewField = findField(args[0].getClass(), "itemView");
            Object value = itemViewField == null ? null : itemViewField.get(args[0]);
            if (!(value instanceof View view) || item == null) return result;
            String spec = tileSpec(item);
            if (spec.isEmpty()) {
                spec = ControlCenterComponentSpec.fromControllerName(item.getClass().getName());
            }
            if (spec.isEmpty()) return result;
            ControlCenterMaterials.registerRoot(view, currentConfig.controlCenter);
            CONTROL_CENTER_CAPTURE_SPECS.put(view, spec);
            Integer nativeSpan = CONTROL_CENTER_NATIVE_SPANS.get(item);
            CONTROL_CENTER_CAPTURE_NATIVE_SPANS.put(view, nativeSpan == null
                    ? ControlCenterComponentSpec.defaultSpan(spec) : Math.max(1, nativeSpan));
            if (CONTROL_CENTER_COMPONENTS_LOGGED.add("bind:" + spec)) {
                log(Log.INFO, "control-center component bound spec=" + spec
                        + " view=" + view.getClass().getName(), null);
            }
            if (ControlCenterComponentSpec.isSpecial(spec)) {
                applyPluginTileHeight(args[0], item);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center component bind failed", error);
        }
        return result;
    }

    private static Object controlCenterPluginTileBind(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ensureConfigForTarget(chain.getThisObject());
        try {
            Object[] args = chain.getArgs().toArray();
            if (args.length >= 2) {
                applyPluginTileHeight(args[0], args[1]);
                Field itemViewField = findField(args[0].getClass(), "itemView");
                Object view = itemViewField == null ? null : itemViewField.get(args[0]);
                if (view instanceof View tile && tile.getParent() != null
                        && currentConfig.controlCenter.enabled
                        && !currentConfig.controlCenter.hasLayoutPlanOverride()) {
                    nativeControlCenterAppliedSettings = currentConfig.controlCenter;
                    reportControlCenterApplication(currentConfig.controlCenter,
                            FusionActivationStatus.APPLIED, "native_control_center_bound");
                }
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin tile shape failed", error);
            reportControlCenterApplication(currentConfig.controlCenter,
                    FusionActivationStatus.DEGRADED, "native_control_center_bind_failed");
        }
        return result;
    }

    private static void applyPluginTileHeight(Object holder, Object item) {
        if (holder == null || item == null) return;
        String spec = tileSpec(item);
        Field itemViewField = findField(holder.getClass(), "itemView");
        if (itemViewField == null) return;
        try {
            Object value = itemViewField.get(holder);
            if (!(value instanceof View itemView)) return;
            if (controlCenterNativeEditing(itemView)) return;
            applyPluginTileVisualShape(value, spec);
            itemView.post(() -> applyPluginTileHeightNow(itemView, spec));
            itemView.postDelayed(() -> applyPluginTileHeightNow(itemView, spec), 48L);
            applyPluginTileHeightNow(itemView, spec);
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin tile height failed", error);
        }
    }

    private static void applyPluginTileHeightNow(View itemView, String spec) {
        if (itemView == null || spec == null || spec.isEmpty()) return;
        if (controlCenterNativeEditing(itemView)) return;
        ControlCenterConfig settings = currentConfig.controlCenter;
        boolean component = ControlCenterComponentSpec.isSpecial(spec);
        boolean hasShape = settings.enabled
                && ControlCenterTileLayout.hasShape(settings.layout, spec);
        if (ControlCenterComponentSpec.QS_CARD.equals(spec)
                && (activeCompactPair() != null || !hasShape)) return;
        int rows = hasShape ? settings.tileHeight(spec)
                : ControlCenterComponentSpec.defaultRows(spec);
        try {
            applyPluginTileVisualShape(itemView, spec);
            if (component && !hasShape && !CONTROL_CENTER_BASE_HEIGHTS.containsKey(itemView)) {
                return;
            }
            if (!component && !settings.hasShapeOverride()
                    && !CONTROL_CENTER_BASE_HEIGHTS.containsKey(itemView)) return;
            ViewGroup.LayoutParams params = itemView.getLayoutParams();
            if (params == null) return;
            Integer baseHeight = CONTROL_CENTER_BASE_HEIGHTS.get(itemView);
            if (baseHeight == null || baseHeight <= 0) {
                baseHeight = readIntField(itemView, "containerHeight");
                if (baseHeight == null || baseHeight <= 0) baseHeight = params.height;
                if (baseHeight == null || baseHeight <= 0) baseHeight = itemView.getMeasuredHeight();
                if (baseHeight != null && baseHeight > 0) {
                    CONTROL_CENTER_BASE_HEIGHTS.put(itemView, baseHeight);
                }
            }
            if (baseHeight == null || baseHeight <= 0) return;
            int target = component ? controlCenterRowHeight(itemView, rows)
                    : baseHeight * rows;
            if (target <= 0) {
                target = baseHeight * rows;
                if (rows > 1) target += dp(itemView, settings.spacing) * (rows - 1);
            }
            if (params.height != target) {
                params.height = target;
                itemView.setLayoutParams(params);
            }
            if (rows > 1 && CONTROL_CENTER_HEIGHT_LOGGED.compareAndSet(false, true)) {
                log(Log.INFO, "control-center shape height applied spec=" + spec
                        + " rows=" + rows + " base=" + baseHeight + " target=" + target, null);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin tile height failed", error);
        }
    }

    private static int controlCenterRowHeight(View view, int rows) {
        String name;
        switch (Math.max(1, Math.min(4, rows))) {
            case 2:
                name = "control_center_universal_2_rows_size";
                break;
            case 3:
                name = "control_center_universal_3_rows_with_margin_size";
                break;
            case 4:
                name = "control_center_universal_4_rows_size";
                break;
            default:
                name = "control_center_universal_1_row_size";
                break;
        }
        try {
            int id = view.getResources().getIdentifier(name, "dimen",
                    "miui.systemui.controlcenter");
            if (id == 0) {
                id = view.getResources().getIdentifier(name, "dimen",
                        view.getContext().getPackageName());
            }
            return id == 0 ? 0 : view.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void applyPluginTileVisualShape(Object itemView, String spec) {
        if (!(itemView instanceof ViewGroup tileView) || spec == null || spec.isEmpty()) return;
        if (controlCenterNativeEditing(tileView)) return;
        ControlCenterConfig settings = currentConfig.controlCenter;
        int shapeWidth = settings.enabled && settings.hasShapeOverride()
                && ControlCenterTileLayout.hasShape(settings.layout, spec)
                ? settings.tileWidth(spec) : 1;
        Field iconField = findField(tileView.getClass(), "icon");
        Object iconValue;
        try {
            iconValue = iconField == null ? null : iconField.get(tileView);
        } catch (Throwable error) {
            return;
        }
        if (!(iconValue instanceof ViewGroup iconContainer)) return;
        Field imageField = findField(iconContainer.getClass(), "icon");
        Object imageValue;
        try {
            imageValue = imageField == null ? null : imageField.get(iconContainer);
        } catch (Throwable error) {
            return;
        }
        if (!(imageValue instanceof View image)) return;
        ViewGroup.LayoutParams imageParams = image.getLayoutParams();
        ViewGroup.LayoutParams containerParams = iconContainer.getLayoutParams();
        if (imageParams == null || containerParams == null) return;
        Integer baseImageWidth = CONTROL_CENTER_BASE_ICON_WIDTHS.get(image);
        if (baseImageWidth == null || baseImageWidth <= 0) {
            baseImageWidth = imageParams.width > 0 ? imageParams.width : image.getMeasuredWidth();
            if (baseImageWidth == null || baseImageWidth <= 0) return;
            CONTROL_CENTER_BASE_ICON_WIDTHS.put(image, baseImageWidth);
        }
        if (!CONTROL_CENTER_BASE_ICON_CONTAINER_WIDTHS.containsKey(iconContainer)) {
            CONTROL_CENTER_BASE_ICON_CONTAINER_WIDTHS.put(iconContainer, containerParams.width);
        }
        int targetWidth = baseImageWidth;
        if (shapeWidth > 1) {
            targetWidth = tileView.getWidth() > 0 ? tileView.getWidth() : tileView.getMeasuredWidth();
            if (targetWidth <= 0) return;
            targetWidth = Math.max(baseImageWidth, targetWidth);
        }
        Integer baseContainerWidth = CONTROL_CENTER_BASE_ICON_CONTAINER_WIDTHS.get(iconContainer);
        int containerWidth = shapeWidth > 1 ? targetWidth
                : (baseContainerWidth == null ? containerParams.width : baseContainerWidth);
        boolean changed = imageParams.width != targetWidth || containerParams.width != containerWidth;
        if (imageParams.width != targetWidth) {
            imageParams.width = targetWidth;
            image.setLayoutParams(imageParams);
        }
        if (containerParams.width != containerWidth) {
            containerParams.width = containerWidth;
            iconContainer.setLayoutParams(containerParams);
        }
        if (changed && shapeWidth > 1) {
            String key = spec + "=" + shapeWidth + "x" + settings.tileHeight(spec);
            if (CONTROL_CENTER_VISUAL_SHAPES_LOGGED.add(key)) {
                log(Log.INFO, "control-center visual shape applied spec=" + spec
                        + " width=" + shapeWidth + " visualWidth=" + targetWidth, null);
            }
        }
    }

    private static Integer readIntField(Object target, String name) {
        Field field = findField(target.getClass(), name);
        if (field == null || field.getType() != int.class) return null;
        try {
            return field.getInt(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object controlCenterPluginNotifyChanged(XposedInterface.Chain chain)
            throws Throwable {
        controlCenterDistributorRef = new WeakReference<>(chain.getThisObject());
        rememberControlCenterPreviewRoot(chain.getThisObject());
        try {
            Object target = chain.getThisObject();
            Field field = findField(target.getClass(), "qsListController");
            Object list = field == null ? null : field.get(target);
            applyPluginTileOrder(list, "MainPanelContentDistributor#notifyChanged");
            // Keep the OEM component split intact when the custom tile grid is active.
            // Reordering these lists independently can leave the grid with an empty
            // measured region while media/sliders remain visible.
            if (!currentConfig.controlCenter.hasLayoutPlanOverride()) {
                sortControlCenterComponents(target);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center distributor pre-refresh failed", error);
        }
        Object result = chain.proceed();
        rememberControlCenterPreviewRoot(chain.getThisObject());
        requestControlCenterPreviewCapture(contextFor(chain.getThisObject()));
        return result;
    }

    private static Object controlCenterPluginDistributePanels(XposedInterface.Chain chain)
            throws Throwable {
        controlCenterDistributorRef = new WeakReference<>(chain.getThisObject());
        Object result = chain.proceed();
        if (!currentConfig.controlCenter.hasLayoutPlanOverride()) {
            sortControlCenterComponents(chain.getThisObject());
        }
        return result;
    }

    private static Object controlCenterPluginSeparatedPanels(XposedInterface.Chain chain)
            throws Throwable {
        if (controlCenterNativeEditing(chain.getThisObject())) return chain.proceed();
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (settings.enabled && settings.hasLayoutPlanOverride()) {
            log(Log.INFO, "control-center custom plan forcing single panel", null);
            Object[] args = chain.getArgs().toArray();
            if (args.length == 1 && (args[0] == null || args[0] instanceof Boolean)) {
                args[0] = Boolean.FALSE;
                return chain.proceed(args);
            }
        }
        return chain.proceed();
    }

    private static void sortControlCenterComponents(Object distributor) {
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (distributor == null || !settings.enabled) return;
        try {
            List<Object> left = panelContentList(distributor, "leftPanelContent");
            List<Object> right = panelContentList(distributor, "rightPanelContent");
            if (left == null || right == null) return;
            ArrayList<Object> all = new ArrayList<>(left);
            all.addAll(right);
            boolean compactListRight = true;
            for (Object item : all) {
                if (ControlCenterPluginHookInstaller.COMPACT_QS_LIST_CONTROLLER_CLASS
                        .equals(item.getClass().getName())) {
                    compactListRight = right.contains(item);
                    break;
                }
            }
            for (Object item : all) {
                String spec = ControlCenterComponentSpec.fromControllerName(
                        item.getClass().getName());
                String side = settings.hasLayoutPlanOverride()
                        && ControlCenterComponentSpec.QS_CARD.equals(spec)
                        ? (compactListRight ? "right" : "left")
                        : settings.componentSide(spec);
                if (side.isEmpty()) continue;
                List<Object> target = "left".equals(side) ? left : right;
                List<Object> other = "left".equals(side) ? right : left;
                if (other.remove(item) && !target.contains(item)) target.add(item);
            }
            if (!settings.componentOrder.isEmpty()) {
                ControlCenterComponentSpec.reorder(left, settings.componentOrder,
                        item -> ControlCenterComponentSpec.fromControllerName(
                                item.getClass().getName()));
                ControlCenterComponentSpec.reorder(right, settings.componentOrder,
                        item -> ControlCenterComponentSpec.fromControllerName(
                                item.getClass().getName()));
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center component placement failed", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> panelContentList(Object distributor, String fieldName)
            throws IllegalAccessException {
        Field field = findField(distributor.getClass(), fieldName);
        Object value = field == null ? null : field.get(distributor);
        return value instanceof List<?> ? (List<Object>) value : null;
    }

    private static Object controlCenterPluginAdapterNotifyChanged(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || (!settings.hasGridOverride() && !settings.hasShapeOverride()
                && !settings.hasLayoutPlanOverride())) {
            return result;
        }
        try {
            boolean refreshed = invalidateMainPanelAdapter(chain.getThisObject(), null, false);
            if (refreshed && CONTROL_CENTER_LAYOUT_REBIND_LOGGED.compareAndSet(false, true)) {
                log(Log.INFO,
                        "control-center adapter notifyChanged completed; layout caches invalidated",
                        null);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center adapter post-refresh failed", error);
        }
        return result;
    }

    private static Object controlCenterPluginCompactSpecs(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasTileOrderOverride()
                || !(result instanceof List<?> source)) return result;
        List<?> reordered = ControlCenterTileOrder.forDisplay(
                new ArrayList<>(source), settings, SystemUiHooks::tileSpec);
        if (CONTROL_CENTER_DISPLAY_LOGGED.compareAndSet(false, true)) {
            log(Log.INFO, "control-center plugin compact specs source=" + source.size()
                    + " visible=" + reordered.size(), null);
        }
        return reordered;
    }

    private static Object controlCenterPluginCompactCardSpecs(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        ensureConfigForTarget(chain.getThisObject());
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasLayoutPlanOverride()) {
            controlCenterSelectedCompactPair = null;
            return result;
        }
        ControlCenterLayoutPlan.Item pair = ControlCenterCompactPairPolicy.firstAvailablePair(
                settings, compactPairHostSpecs(chain.getThisObject()));
        controlCenterSelectedCompactPair = pair;
        if (pair == null) return result;
        if (CONTROL_CENTER_COMPONENTS_LOGGED.add("compact-pair:" + pair.id)) {
            log(Log.INFO, "control-center compact pair selected first=" + pair.firstSpec
                    + " second=" + pair.secondSpec, null);
        }
        return java.util.Arrays.asList(pair.firstSpec, pair.secondSpec);
    }

    private static List<String> compactPairHostSpecs(Object qsController) {
        try {
            Field hostField = findField(qsController.getClass(), "host");
            Object host = hostField == null ? null : hostField.get(qsController);
            if (host == null) host = controlCenterHostRef.get();
            if (host == null) return null;
            for (String methodName : new String[] {"getSpecs", "getTiles"}) {
                Method method = findMethod(host.getClass(), methodName, new Class<?>[] {});
                if (method == null) continue;
                try {
                    Boolean previous = CONTROL_CENTER_RAW_HOST_READ.get();
                    Object value;
                    try {
                        CONTROL_CENTER_RAW_HOST_READ.set(true);
                        value = method.invoke(host);
                    } finally {
                        if (previous == null) CONTROL_CENTER_RAW_HOST_READ.remove();
                        else CONTROL_CENTER_RAW_HOST_READ.set(previous);
                    }
                    if (value instanceof Collection<?> source && !source.isEmpty()) {
                        ArrayList<String> specs = new ArrayList<>();
                        for (Object tile : source) {
                            String spec = tileSpec(tile);
                            if (!spec.isEmpty()) specs.add(spec);
                        }
                        if (!specs.isEmpty()) return specs;
                    }
                } catch (Throwable error) {
                    log(Log.WARN, "control-center compact pair " + methodName
                            + " unavailable", error);
                }
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center compact pair host read failed", error);
        }
        return null;
    }

    private static Object controlCenterPluginCompactCardCreated(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        Object card = chain.getThisObject();
        controlCenterCompactCardRef = new WeakReference<>(card);
        ControlCenterLayoutPlan.Item pair = controlCenterSelectedCompactPair;
        controlCenterActiveCompactPair = null;
        try {
            Field recordsField = findField(card.getClass(), "qsRecords");
            Object value = recordsField == null ? null : recordsField.get(card);
            if (value instanceof List<?> records && records.size() >= 2
                    && ControlCenterCompactPairPolicy.hasMembers(pair,
                            tileSpec(records.get(0)), tileSpec(records.get(1)))) {
                controlCenterActiveCompactPair = pair;
                log(Log.INFO, "control-center compact pair active first=" + pair.firstSpec
                        + " second=" + pair.secondSpec + " direction=" + pair.direction
                        + " size=" + pair.width + "x" + pair.height, null);
                Object compact = controlCenterCompactRef.get();
                if (compact != null) MAIN_HANDLER.post(() -> refreshActiveCompactList(compact));
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center compact pair verification failed", error);
        }
        return result;
    }

    private static Object controlCenterPluginCompactCardDestroyed(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterCompactCardRef.get() == chain.getThisObject()) {
            controlCenterCompactCardRef = new WeakReference<>(null);
            controlCenterActiveCompactPair = null;
        }
        return result;
    }

    private static ControlCenterLayoutPlan.Item activeCompactPair() {
        return controlCenterCompactCardRef.get() == null ? null
                : ControlCenterCompactPairPolicy.activePair(currentConfig.controlCenter,
                        controlCenterActiveCompactPair);
    }

    private static void recreateCompactCardIfNeeded(ControlCenterConfig previous,
            ControlCenterConfig next) {
        if (previous.enabled == next.enabled
                && previous.layoutPlan.equals(next.layoutPlan)) return;
        Object card = controlCenterCompactCardRef.get();
        if (card == null || !ControlCenterCompactPairPolicy.needsRebuild(next,
                controlCenterActiveCompactPair)) return;
        try {
            Method getListening = findMethod(card.getClass(), "getListening", new Class<?>[] {});
            Method setListening = findMethod(card.getClass(), "setListening",
                    new Class<?>[] {boolean.class});
            Method unbind = findMethod(card.getClass(), "onUnbindViewHolder",
                    new Class<?>[] {});
            Method destroy = findMethod(card.getClass(), "onDestroy", new Class<?>[] {});
            Method create = findMethod(card.getClass(), "onCreate", new Class<?>[] {});
            Method bind = findMethod(card.getClass(), "onBindViewHolder", new Class<?>[] {});
            Method getHolder = findMethod(card.getClass(), "getHolder", new Class<?>[] {});
            if (setListening == null || unbind == null || destroy == null || create == null
                    || bind == null || getHolder == null) {
                log(Log.WARN, "control-center compact card rebuild methods missing", null);
                return;
            }
            boolean wasListening = getListening != null
                    && Boolean.TRUE.equals(getListening.invoke(card));
            unbind.invoke(card);
            setListening.invoke(card, false);
            destroy.invoke(card);
            create.invoke(card);
            if (wasListening) setListening.invoke(card, true);
            if (getHolder.invoke(card) != null) bind.invoke(card);
            log(Log.INFO, "control-center compact card recreated for layout change", null);
        } catch (Throwable error) {
            controlCenterActiveCompactPair = null;
            log(Log.WARN, "control-center compact card rebuild failed", error);
        }
    }

    private static void refreshActiveCompactList(Object compact) {
        if (compact != controlCenterCompactRef.get() || activeCompactPair() == null) return;
        try {
            Method method = findMethod(compact.getClass(), "distributeTiles",
                    new Class<?>[] {boolean.class});
            if (method != null) method.invoke(compact, false);
        } catch (Throwable error) {
            log(Log.WARN, "control-center compact pair tile refresh failed", error);
        }
    }

    private static void applyPluginTileOrder(Object target, String sourceName) {
        if (target == null) return;
        if (controlCenterNativeEditing(target)) return;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasTileOrderOverride()) return;
        try {
            Field field = findField(target.getClass(), "addedTiles");
            Object value = field == null ? null : field.get(target);
            if (!(value instanceof List<?> source)) return;
            int originalSize = source.size();
            List<?> reordered = ControlCenterTileOrder.forDisplay(
                    new ArrayList<>(source), settings, SystemUiHooks::tileSpec);
            source.clear();
            ((List<Object>) source).addAll(reordered);
            if (CONTROL_CENTER_TILES_LOGGED.compareAndSet(false, true)) {
                log(Log.INFO, "control-center plugin tile order applied source=" + sourceName
                        + " source=" + originalSize
                        + " visible=" + reordered.size(), null);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin tile order failed source=" + sourceName, error);
        }
    }

    private static Object controlCenterPluginIconSize(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ensureConfigForTarget(chain.getThisObject());
        applyPluginIconSize(chain.getThisObject());
        return result;
    }

    private static void applyPluginIconSize(Object target) {
        if (!(target instanceof ViewGroup)) return;
        if (controlCenterNativeEditing(target)) return;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || settings.tileScale == 100) {
            restoreControlCenterIconSizes();
            return;
        }
        try {
            Field cardField = findField(target.getClass(), "card");
            if (cardField != null && cardField.getBoolean(target)) return;
            Field iconField = findField(target.getClass(), "icon");
            Object iconValue = iconField == null ? null : iconField.get(target);
            if (!(iconValue instanceof View icon)) return;
            ViewGroup.LayoutParams params = icon.getLayoutParams();
            if (params == null) return;
            IconDimensionBaseline base = CONTROL_CENTER_ICON_DIMENSIONS.get(icon);
            if (base == null) {
                base = new IconDimensionBaseline(params.width, params.height,
                        params.width > 0 ? params.width : icon.getMeasuredWidth(),
                        params.height > 0 ? params.height : icon.getMeasuredHeight());
                if (base.width <= 0 || base.height <= 0) return;
                CONTROL_CENTER_ICON_DIMENSIONS.put(icon, base);
            }
            int width = Math.max(1, Math.round(base.width * settings.tileScale / 100f));
            int height = Math.max(1, Math.round(base.height * settings.tileScale / 100f));
            if (params.width != width || params.height != height) {
                params.width = width;
                params.height = height;
                icon.setLayoutParams(params);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin icon size failed", error);
        }
    }

    private static void restoreControlCenterIconSizes() {
        for (Map.Entry<View, IconDimensionBaseline> entry :
                new ArrayList<>(CONTROL_CENTER_ICON_DIMENSIONS.entrySet())) {
            View icon = entry.getKey();
            if (icon == null) continue;
            ViewGroup.LayoutParams params = icon.getLayoutParams();
            if (params == null) continue;
            IconDimensionBaseline base = entry.getValue();
            params.width = base.originalWidth;
            params.height = base.originalHeight;
            RuntimeCleanup.run("restore icon dimensions", () -> icon.setLayoutParams(params));
        }
        CONTROL_CENTER_ICON_DIMENSIONS.clear();
    }

    private static final class IconDimensionBaseline {
        final int originalWidth;
        final int originalHeight;
        final int width;
        final int height;

        IconDimensionBaseline(int originalWidth, int originalHeight, int width, int height) {
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.width = width;
            this.height = height;
        }
    }

    private static Object controlCenterPluginBackground(XposedInterface.Chain chain)
            throws Throwable {
        if (chain.getThisObject() instanceof View owner && chain.getArg(0) instanceof Drawable drawable) {
            ControlCenterMaterials.register(owner, drawable, ControlCenterMaterials.layer(owner), currentConfig.controlCenter);
        }
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (settings.enabled && settings.cornerRadius > 0
                && chain.getArg(0) instanceof GradientDrawable drawable) {
            drawable.setCornerRadius(dp((View) chain.getThisObject(), settings.cornerRadius));
        }
        return result;
    }

    private static Object controlCenterPluginCornerRadius(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || settings.cornerRadius <= 0
                || !(chain.getThisObject() instanceof View view)) return result;
        return Float.valueOf(dp(view, settings.cornerRadius));
    }

    private static Object controlCenterPluginContainerHeight(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (controlCenterNativeEditing(chain.getThisObject())) return result;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || settings.tileScale == 100) return result;
        try {
            Field field = findField(chain.getThisObject().getClass(), "containerHeight");
            if (field != null && field.getType() == int.class) {
                int current = field.getInt(chain.getThisObject());
                if (chain.getThisObject() instanceof View view) {
                    int resourceId = view.getResources().getIdentifier(
                            "control_center_universal_1_row_with_margin_size", "dimen",
                            "miui.systemui.plugin");
                    if (resourceId != 0) {
                        current = view.getResources().getDimensionPixelSize(resourceId);
                    }
                }
                field.setInt(chain.getThisObject(), Math.max(1,
                        Math.round(current * settings.tileScale / 100f)));
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin tile height failed", error);
        }
        return result;
    }

    private static Object controlCenterPluginSetSpan(XposedInterface.Chain chain)
            throws Throwable {
        ControlCenterConfig settings = currentConfig.controlCenter;
        Object[] args = chain.getArgs().toArray();
        if (settings.enabled && (settings.hasGridOverride()
                || settings.hasLayoutPlanOverride())
                && args.length == 1 && args[0] instanceof Integer && !controlCenterNativeEditing(chain.getThisObject())) {
            args[0] = controlCenterColumnsForAdapter(chain.getThisObject(),
                    (Integer) args[0]);
        }
        return chain.proceed(args);
    }

    private static Object controlCenterPluginDistributeContent(XposedInterface.Chain chain)
            throws Throwable {
        Object adapter = chain.getThisObject();
        Object retained = ControlCenterRuntimeGrid.retain(adapter);
        try {
            Object result = chain.proceed();
            try {
                ensureConfigForTarget(adapter);
                if (isHiddenSecondaryAdapter(adapter)) {
                    ControlCenterRuntimeGrid.clear(adapter, false);
                    log(Log.INFO, "control-center runtime grid skipped hidden secondary adapter", null);
                    return result;
                }
                boolean active = ControlCenterRuntimeGrid.apply(adapter,
                        currentConfig.controlCenter, adapter.getClass().getClassLoader());
                if (active && CONTROL_CENTER_COMPONENTS_LOGGED.add("runtime-grid-active")) {
                    log(Log.INFO, "control-center runtime grid active", null);
                }
            } catch (Throwable error) {
                ControlCenterRuntimeGrid.clear(adapter);
                log(Log.WARN, "control-center runtime grid unavailable", error);
            }
            return result;
        } finally {
            java.lang.ref.Reference.reachabilityFence(retained);
        }
    }

    private static boolean isHiddenSecondaryAdapter(Object adapter) {
        if (adapter == null || !currentConfig.controlCenter.enabled
                || !currentConfig.controlCenter.hasLayoutPlanOverride()) return false;
        try {
            Object provider = ControlCenterRuntimeGrid.readField(adapter, "mainPanelController");
            Object controller = unwrapProvider(provider);
            Method method = controller == null ? null
                    : findMethod(controller.getClass(), "getStyle", new Class<?>[] {});
            Object style = method == null ? null : method.invoke(controller);
            return "HORIZONTAL".equals(String.valueOf(style));
        } catch (Throwable ignored) { return false; }
    }

    private static Object controlCenterPluginGridRefresh(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        applyPluginAdapterSpan(chain.getThisObject());
        return result;
    }

    private static void applyPluginAdapterSpan(Object target) {
        if (target == null) return;
        if (controlCenterNativeEditing(target)) return;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || (!settings.hasGridOverride() && !settings.hasShapeOverride()
                && !settings.hasLayoutPlanOverride())) return;
        try {
            int columns = controlCenterColumnsForAdapter(target, settings.columns);
            Field span = findField(target.getClass(), "span");
            if (span != null && span.getType() == int.class) {
                span.setInt(target, columns);
            }
            Field layoutManager = findField(target.getClass(), "layoutManager");
            Object value = layoutManager == null ? null : layoutManager.get(target);
            Method setSpanCount = value == null ? null
                    : findMethod(value.getClass(), "setSpanCount", new Class<?>[] {int.class});
            if (setSpanCount != null) setSpanCount.invoke(value, columns);
            invalidateMainPanelAdapter(target, value, false);
        } catch (Throwable error) {
            log(Log.WARN, "control-center plugin grid refresh failed", error);
        }
    }

    private static int controlCenterColumnsForAdapter(Object adapter, int fallback) {
        ControlCenterConfig settings = currentConfig.controlCenter;
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        if (plan == null || adapter == null) return settings.columns;
        try {
            Field style = findField(adapter.getClass(), "style");
            Object value = style == null ? null : style.get(adapter);
            boolean compact = "COMPACT".equals(String.valueOf(value))
                    || (value == null && fallback == 3);
            return plan.runtimeMode(compact).columns;
        } catch (Throwable ignored) {
            return plan.runtimeMode(fallback == 3).columns;
        }
    }

    private static boolean invalidateMainPanelAdapter(Object adapter, Object layoutManager) {
        return invalidateMainPanelAdapter(adapter, layoutManager, true);
    }

    private static boolean invalidateMainPanelAdapter(Object adapter, Object layoutManager,
            boolean notifyAdapter) {
        if (adapter == null) return false;
        boolean changed = false;
        try {
            Object manager = layoutManager;
            if (manager == null) {
                Field field = findField(adapter.getClass(), "layoutManager");
                manager = field == null ? null : field.get(adapter);
            }
            if (manager != null) {
                Method getLookup = findMethod(manager.getClass(), "getSpanSizeLookup",
                        new Class<?>[] {});
                Object lookup = getLookup == null ? null : getLookup.invoke(manager);
                Method invalidateIndex = lookup == null ? null
                        : findMethod(lookup.getClass(), "invalidateSpanIndexCache",
                                new Class<?>[] {});
                if (invalidateIndex != null) {
                    invalidateIndex.invoke(lookup);
                    changed = true;
                }
                Method invalidateGroup = lookup == null ? null
                        : findMethod(lookup.getClass(), "invalidateSpanGroupIndexCache",
                                new Class<?>[] {});
                if (invalidateGroup != null) {
                    invalidateGroup.invoke(lookup);
                    changed = true;
                }
                Method requestLayout = findMethod(manager.getClass(), "requestLayout",
                        new Class<?>[] {});
                if (requestLayout != null) {
                    requestLayout.invoke(manager);
                    changed = true;
                }
            }
            Field recycler = findField(adapter.getClass(), "recyclerView");
            Object value = recycler == null ? null : recycler.get(adapter);
            if (notifyAdapter) {
                Method recycle = value instanceof View
                        ? findMethod(value.getClass(), "removeAndRecycleViews",
                        new Class<?>[] {}) : null;
                if (recycle != null) {
                    try {
                        recycle.invoke(value);
                        changed = true;
                    } catch (Throwable error) {
                        log(Log.WARN, "control-center visible holders recycle failed", error);
                    }
                }
                Method notify = findMethod(adapter.getClass(), "notifyDataSetChanged",
                        new Class<?>[] {});
                if (notify != null) {
                    notify.invoke(adapter);
                    changed = true;
                }
            }
            if (value instanceof View view) {
                view.requestLayout();
                view.invalidate();
                changed = true;
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center adapter rebind failed", error);
        }
        return changed;
    }

    private static Object unwrapProvider(Object value) {
        if (value == null) return null;
        try {
            Method get = findMethod(value.getClass(), "get", new Class<?>[] {});
            if (get != null) {
                Object result = get.invoke(value);
                if (result != null) return result;
            }
        } catch (Throwable ignored) {
            // Use the value itself when the provider is already unwrapped.
        }
        return value;
    }

    private static int refreshControlCenterAdapters(Object list) {
        return refreshControlCenterAdapters(list, true);
    }

    private static int refreshControlCenterAdapters(Object list, boolean notifyAdapter) {
        if (list == null) return 0;
        if (controlCenterNativeEditing(list)) return 0;
        int refreshed = 0;
        try {
            Field controllerField = findField(list.getClass(), "mainPanelController");
            Object controller = controllerField == null ? null :
                    unwrapProvider(controllerField.get(list));
            Field distributorField = controller == null ? null
                    : findField(controller.getClass(), "distributor");
            Object distributor = distributorField == null ? null
                    : unwrapProvider(distributorField.get(controller));
            if (distributor == null) return 0;
            updateControlCenterPanelSeparation(controller, distributor);
            controlCenterDistributorRef = new WeakReference<>(distributor);
            if (!currentConfig.controlCenter.hasLayoutPlanOverride()) {
                sortControlCenterComponents(distributor);
            }
            boolean singlePanel = currentConfig.controlCenter.enabled
                    && currentConfig.controlCenter.hasLayoutPlanOverride();
            for (String name : singlePanel
                    ? new String[] {"getLeftAdapter"}
                    : new String[] {"getLeftAdapter", "getRightAdapter"}) {
                Method getter = findMethod(distributor.getClass(), name, new Class<?>[] {});
                Object adapter = getter == null ? null : getter.invoke(distributor);
                if (adapter == null) continue;
                if (!notifyAdapter && ControlCenterRuntimeGrid.isActive(adapter)) {
                    // The immediate refresh already installed and notified this
                    // proxy. A deferred pass is only for adapters created after
                    // that pass; rebuilding an active proxy without notify would
                    // leave the visible RecyclerView bound to stale holders.
                    if (invalidateMainPanelAdapter(adapter, null, true)) {
                        refreshed++;
                    }
                    log(Log.INFO, "control-center runtime grid deferred refresh rebound;"
                            + " adapter already active", null);
                    continue;
                }
                // Keep this refresh atomic. Restoring the native map with an
                // immediate notify produces the visible flash and can leave
                // the RecyclerView holding the old native holders.
                ControlCenterRuntimeGrid.clear(adapter, false);
                Method notify = findMethod(adapter.getClass(), "notifyChanged",
                        new Class<?>[] {boolean.class, boolean.class});
                Method distribute = findMethod(adapter.getClass(), "distributeContent",
                        new Class<?>[] {boolean.class});
                boolean rebound = false;
                boolean lifecycleNotify = notify != null;
                if (lifecycleNotify) {
                    // MainPanelAdapter.notifyChanged performs the OEM lifecycle:
                    // distribution, mode/style updates, DiffUtil and holder
                    // rebinding. Its distributeContent call is hooked and will
                    // install the custom grid after rebuilding the native map.
                    notify.invoke(adapter, false, false);
                    rebound = true;
                } else if (distribute != null) {
                    // Older plugin revisions may not expose notifyChanged.
                    distribute.invoke(adapter, false);
                    rebound = true;
                }
                // A reflective call does not reliably re-enter the LibXposed
                // hook on every plugin class-loader revision. Apply explicitly
                // after the native map has been rebuilt so the current adapter
                // cannot silently fall back to the OEM list.
                boolean gridApplied;
                if (ControlCenterRuntimeGrid.isActive(adapter)) {
                    // The direct call re-entered the hooked method and already
                    // installed the proxy. Applying a second time would snapshot
                    // that proxy as if it were the native map.
                    gridApplied = true;
                } else {
                    gridApplied = ControlCenterRuntimeGrid.apply(adapter,
                            currentConfig.controlCenter, adapter.getClass().getClassLoader());
                }
                log(Log.INFO, "control-center runtime grid refresh adapter="
                        + name + " adapter=" + adapter.getClass().getName()
                        + " applied=" + gridApplied
                        + " lifecycleNotify=" + lifecycleNotify
                        + " distributed=" + (distribute != null), null);
                // The native notifyChanged path updates contentMap and DiffUtil, but
                // it can leave the currently visible holder attached to the old
                // native item. The grid proxy is a replacement item, so force one
                // holder recycle and a full adapter rebind after it is installed.
                if (invalidateMainPanelAdapter(adapter, null, true)) {
                    rebound = true;
                }
                if (rebound) {
                    refreshed++;
                }
            }
            if (refreshed > 0) {
                log(Log.INFO, "control-center adapters rebound for shape changes count="
                        + refreshed, null);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center adapter lookup failed", error);
        }
        return refreshed;
    }

    private static void updateControlCenterPanelSeparation(Object controller,
            Object distributor) {
        if (controller == null) return;
        if (controlCenterNativeEditing(controller)) return;
        try {
            ControlCenterConfig settings = currentConfig.controlCenter;
            Method update = findMethod(controller.getClass(), "updateUseSeparatedPanels",
                    new Class<?>[] {});
            Method set = findMethod(controller.getClass(), "setUseSeparatedPanels",
                    new Class<?>[] {Boolean.class});
            if (settings.enabled && settings.hasLayoutPlanOverride() && set != null) {
                Method distribute = distributor == null ? null
                        : findMethod(distributor.getClass(), "distributePanels",
                                new Class<?>[] {boolean.class});
                if (distribute != null) distribute.invoke(distributor, false);
                set.invoke(controller, Boolean.FALSE);
                log(Log.INFO, "control-center custom plan forced single panel and redistributed",
                        null);
            } else if (update != null) {
                update.invoke(controller);
            }
        } catch (Throwable error) {
            log(Log.WARN, "control-center panel separation update failed", error);
        }
    }

    private static void scheduleControlCenterAdapterRefresh() {
        if (!CONTROL_CENTER_ADAPTER_REFRESH_POSTED.compareAndSet(false, true)) return;
        MAIN_HANDLER.post(() -> {
            CONTROL_CENTER_ADAPTER_REFRESH_POSTED.set(false);
            Object list = controlCenterListRef.get();
            int refreshed = refreshControlCenterAdapters(list, false);
            if (refreshed > 0) {
                log(Log.INFO, "control-center deferred layout refresh completed count="
                        + refreshed, null);
            }
        });
    }

    private static boolean controlCenterNativeEditing(Object target) {
        return ControlCenterNativeMode.isEditing(target) || ControlCenterNativeMode.isEditing(controlCenterListRef.get());
    }

    private static void ensureConfigForTarget(Object target) {
        if (target == null) return;
        try {
            Method method = findMethod(target.getClass(), "getContext", new Class<?>[] {});
            Object value = method == null ? null : method.invoke(target);
            if (!(value instanceof Context)) {
                for (String fieldName : new String[] {"context", "mContext", "sysUIContext"}) {
                    Field field = findField(target.getClass(), fieldName);
                    if (field == null) continue;
                    Object fieldValue = field.get(target);
                    if (fieldValue instanceof Context) {
                        value = fieldValue;
                        break;
                    }
                }
            }
            if (value instanceof Context context) {
                ensureConfigObserver(context);
            }
        } catch (Throwable ignored) {
            // The plugin context is optional; the SystemUI host hook remains authoritative.
        }
    }

    private static Context contextFor(Object target) {
        if (target == null) return configLifecycle == null ? null : configLifecycle.context();
        try {
            Method method = findMethod(target.getClass(), "getContext", new Class<?>[] {});
            Object value = method == null ? null : method.invoke(target);
            if (value instanceof Context context) return context;
            for (String fieldName : new String[] {"context", "mContext", "sysUIContext"}) {
                Field field = findField(target.getClass(), fieldName);
                Object fieldValue = field == null ? null : field.get(target);
                if (fieldValue instanceof Context context) return context;
            }
        } catch (Throwable ignored) {
            // The target context is optional for preview capture.
        }
        return configLifecycle == null ? null : configLifecycle.context();
    }

    private static void rememberControlCenterPreviewRoot(Object target) {
        if (target == null) return;
        try {
            Method method = findMethod(target.getClass(), "getView", new Class<?>[] {});
            Object value = method == null ? null : method.invoke(target);
            if (value instanceof View view) {
                controlCenterPreviewRootRef = new WeakReference<>(view);
                requestControlCenterPreviewCapture(view.getContext());
            }
        } catch (Throwable ignored) {
            // Some controller wrappers do not expose their view during startup.
        }
    }

    private static void requestControlCenterPreviewCapture(Context context) {
        PREVIEW_CAPTURE.request(context);
    }

    private static ControlCenterPreviewCapture.Layout serializeControlCenterLayout(View root) {
        StringBuilder result = new StringBuilder("v2|")
                .append(root.getWidth()).append('|').append(root.getHeight());
        org.json.JSONObject styles = new org.json.JSONObject();
        int[] rowHeights = new int[4];
        for (int rows = 1; rows <= 4; rows++) {
            rowHeights[rows - 1] = controlCenterRowHeight(root, rows);
            result.append('|').append(rowHeights[rows - 1]);
        }
        Set<String> seen = new LinkedHashSet<>();
        int[] rootLocation = new int[2];
        try {
            root.getLocationOnScreen(rootLocation);
        } catch (Throwable ignored) {
            rootLocation[0] = 0;
            rootLocation[1] = 0;
        }
        collectControlCenterTiles(root, root, rootLocation, rowHeights, seen, result, styles);
        try {
            org.json.JSONObject packet = new org.json.JSONObject().put("version", 3)
                    .put("bounds", result.toString()).put("styles", styles)
                    .put("iconSampling", 2)
                    .put("density", root.getResources().getDisplayMetrics().density);
            ControlCenterRuntimeGrid.GridView grid = findCaptureGrid(root);
            if (grid != null) {
                packet.put("editablePlan", ControlCenterLayoutPlan.of(grid.captureMode(), grid.captureMode()).encode());
                packet.put("spacingDp", grid.captureSpacing());
            }
            return new ControlCenterPreviewCapture.Layout(packet.toString(), seen.size(),
                    nativeControlCenterTileCatalog());
        } catch (org.json.JSONException error) {
            throw new IllegalStateException("Cannot serialize native capture", error);
        }
    }

    private static ControlCenterRuntimeGrid.GridView findCaptureGrid(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof ControlCenterRuntimeGrid.GridView grid) return grid;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            ControlCenterRuntimeGrid.GridView grid = findCaptureGrid(group.getChildAt(i));
            if (grid != null) return grid;
        }
        return null;
    }

    /** Returns every native record known to the editor, including unadded system and app tiles. */
    private static String nativeControlCenterTileCatalog() {
        Object list = controlCenterListRef.get();
        if (list == null) return "";
        LinkedHashSet<String> specs = new LinkedHashSet<>();
        for (String fieldName : new String[] {"addedTiles", "systemTiles", "packageTiles"}) {
            Field field = findField(list.getClass(), fieldName);
            if (field == null) continue;
            try {
                appendNativeTileCatalogValue(field.get(list), specs);
            } catch (Throwable error) {
                log(Log.WARN, "control-center tile catalog field unavailable " + fieldName, error);
            }
        }
        for (String methodName : new String[] {"getAddedTiles", "getSystemTiles",
                "getPackageTiles", "getListItems", "getTiles"}) {
            try {
                Method method = findMethod(list.getClass(), methodName, new Class<?>[] {});
                if (method != null) appendNativeTileCatalogValue(method.invoke(list), specs);
            } catch (Throwable error) {
                // Accessors differ across MIUI plugin revisions; fields remain the primary path.
            }
        }
        String catalog = String.join(",", specs);
        if (!catalog.equals(CONTROL_CENTER_TILE_CATALOG_LAST)) {
            CONTROL_CENTER_TILE_CATALOG_LAST = catalog;
            log(Log.INFO, "control-center tile catalog count=" + specs.size()
                    + " specs=" + catalog, null);
        }
        return catalog;
    }

    private static void appendNativeTileCatalogValue(Object value, Set<String> specs) {
        if (value instanceof Iterable<?> records) {
            for (Object record : records) appendNativeTileCatalogRecord(record, specs);
        } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                appendNativeTileCatalogRecord(java.lang.reflect.Array.get(value, i), specs);
            }
        }
    }

    private static void appendNativeTileCatalogRecord(Object record, Set<String> specs) {
        String spec = nativeRecordSpec(record);
        if (!spec.isEmpty()) {
            String normalized = ControlCenterConfig.canonicalSpec(
                    spec.toLowerCase(java.util.Locale.ROOT));
            if (!normalized.isEmpty()) specs.add(normalized);
        }
    }

    private static String nativeRecordSpec(Object record) {
        if (record == null) return "";
        if (record instanceof String value) return value.trim();
        try {
            for (String methodName : new String[] {"getSpec", "getTileSpec", "getKey"}) {
                Method method = findMethod(record.getClass(), methodName, new Class<?>[] {});
                Object value = method == null ? null : method.invoke(record);
                if (value instanceof String string && !string.trim().isEmpty()) return string.trim();
            }
            for (String fieldName : new String[] {"spec", "tileSpec", "key"}) {
                Field field = findField(record.getClass(), fieldName);
                Object value = field == null ? null : field.get(record);
                if (value instanceof String string && !string.trim().isEmpty()) return string.trim();
            }
            return "";
        } catch (Throwable ignored) { return ""; }
    }

    private static void collectControlCenterTiles(View root, View view, int[] rootLocation,
            int[] rowHeights, Set<String> seen, StringBuilder result, org.json.JSONObject styles) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        View surface = isControlCenterRecordView(view) ? ControlCenterStyleSampler.surfaceView(view) : view;
        int[] location = new int[2];
        try {
            surface.getLocationOnScreen(location);
        } catch (Throwable ignored) {
            return;
        }
        int left = location[0] - rootLocation[0];
        int top = location[1] - rootLocation[1];
        int width = Math.round(surface.getWidth() * Math.abs(surface.getScaleX()));
        int height = Math.round(surface.getHeight() * Math.abs(surface.getScaleY()));
        if (isControlCenterRecordView(view)) {
            String spec = previewTileSpec(view);
            if (!spec.isEmpty() && width > 0 && height > 0 && left < root.getWidth() && top < root.getHeight()
                    && left + width > 0 && top + height > 0 && seen.add(spec)) {
                try {
                    ControlCenterCapturedStyle style = ControlCenterStyleSampler.read(view, spec);
                    styles.put(spec.toLowerCase(java.util.Locale.ROOT), style.toJson());
                    if (style.icon != null) style.icon.recycle();
                    if (view instanceof ControlCenterGroupView group) group.visitMemberViews((memberSpec, nativeView) -> {
                        ControlCenterCapturedStyle memberStyle = ControlCenterStyleSampler.read(nativeView, memberSpec);
                        try {
                            styles.put(memberSpec, memberStyle.toJson());
                        } catch (org.json.JSONException error) {
                            log(Log.WARN, "control-center group style unavailable", error);
                        } finally { if (memberStyle.icon != null) memberStyle.icon.recycle(); }
                    });
                } catch (RuntimeException | org.json.JSONException error) {
                    log(Log.WARN, "control-center native style unavailable spec=" + spec, error);
                }
                if (ControlCenterComponentSpec.isSpecial(spec)
                        && CONTROL_CENTER_COMPONENTS_LOGGED.add("capture:" + spec)) {
                    log(Log.INFO, "control-center preview component captured spec=" + spec
                            + " bounds=" + left + "," + top + ","
                            + width + "x" + height, null);
                }
                String encoded = Base64.encodeToString(spec.getBytes(StandardCharsets.UTF_8),
                        Base64.NO_WRAP);
                result.append('|').append(encoded).append(',').append(left).append(',')
                        .append(top).append(',').append(width).append(',').append(height);
                if (ControlCenterComponentSpec.isSpecial(spec)) {
                    Integer nativeSpan = CONTROL_CENTER_CAPTURE_NATIVE_SPANS.get(view);
                    result.append(',').append(nativeSpan == null
                            ? ControlCenterComponentSpec.defaultSpan(spec) : nativeSpan)
                            .append(',').append(inferControlCenterRows(height, rowHeights, spec));
                }
            }
            if (!spec.isEmpty() && width > 0 && height > 0) return;
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                collectControlCenterTiles(root, group.getChildAt(index), rootLocation,
                        rowHeights, seen, result, styles);
            }
        }
    }

    private static int inferControlCenterRows(int height, int[] rowHeights, String spec) {
        int fallback = ControlCenterComponentSpec.defaultRows(spec);
        int bestRows = fallback;
        int bestDelta = Integer.MAX_VALUE;
        for (int rows = 1; rows <= rowHeights.length; rows++) {
            int rowHeight = rowHeights[rows - 1];
            if (rowHeight <= 0) continue;
            int delta = Math.abs(height - rowHeight);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestRows = rows;
            }
        }
        return bestRows;
    }

    private static boolean isControlCenterRecordView(View view) {
        if (view instanceof ControlCenterGroupView) return true;
        if (CONTROL_CENTER_CAPTURE_SPECS.containsKey(view)) return true;
        String name = view.getClass().getName();
        return name.contains("QSTileItemView") || name.contains("QSCardItemView")
                || !ControlCenterComponentSpec.fromViewName(name).isEmpty()
                && !name.contains("ToggleSliderView");
    }

    private static String previewTileSpec(View view) {
        if (view instanceof ControlCenterGroupView group) return group.item().id;
        String boundSpec = CONTROL_CENTER_CAPTURE_SPECS.get(view);
        if (boundSpec != null && !boundSpec.isEmpty()) return boundSpec;
        String componentSpec = ControlCenterComponentSpec.fromViewName(
                view.getClass().getName());
        if (!componentSpec.isEmpty() && !ControlCenterComponentSpec.SLIDER.equals(componentSpec)) {
            return componentSpec;
        }
        try {
            Field stateField = findField(view.getClass(), "state");
            Object state = stateField == null ? null : stateField.get(view);
            String spec = stringField(state, "spec");
            if (!spec.isEmpty()) return spec;
            Field iconField = findField(view.getClass(), "icon");
            Object icon = iconField == null ? null : iconField.get(view);
            Field iconStateField = icon == null ? null : findField(icon.getClass(), "state");
            return stringField(iconStateField == null ? null : iconStateField.get(icon), "spec");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String stringField(Object target, String name) {
        if (target == null) return "";
        try {
            Field field = findField(target.getClass(), name);
            Object value = field == null ? null : field.get(target);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Object controlCenterLayoutRefresh(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        applyControlCenterLayout(chain.getThisObject());
        return result;
    }

    private static String tileSpec(Object value) {
        return tileSpec(value, 0);
    }

    private static String tileSpec(Object value, int depth) {
        if (value == null || depth > 2) return "";
        if (value instanceof String text) return text;
        for (String methodName : new String[] {"getTileSpec", "getSpec"}) {
            try {
                Method method = findMethod(value.getClass(), methodName, new Class<?>[] {});
                if (method != null) {
                    Object result = method.invoke(value);
                    if (result instanceof String text) return text;
                }
            } catch (Throwable ignored) {
                // Try the next OEM representation.
            }
        }
        String componentSpec = componentSpecFromType(value);
        if (!componentSpec.isEmpty()) return componentSpec;
        for (String fieldName : new String[] {"mTileSpec", "tileSpec", "mSpec", "spec"}) {
            Field field = findField(value.getClass(), fieldName);
            if (field != null) {
                try {
                    Object result = field.get(value);
                    if (result instanceof String text) return text;
                } catch (Throwable ignored) {
                    // Continue with wrapper fields.
                }
            }
        }
        for (String fieldName : new String[] {"state", "customizeState", "tileState"}) {
            Field field = findField(value.getClass(), fieldName);
            if (field == null) continue;
            try {
                Object nested = field.get(value);
                String result = tileSpec(nested, depth + 1);
                if (!result.isEmpty()) return result;
            } catch (Throwable ignored) {
                // Some tile views expose state only during binding.
            }
        }
        Field tileField = findField(value.getClass(), "tile");
        if (tileField != null) {
            try {
                return tileSpec(tileField.get(value), depth + 1);
            } catch (Throwable ignored) {
                // Unknown tile record shape.
            }
        }
        return "";
    }

    private static String componentSpecFromType(Object value) {
        if (value == null) return "";
        for (String methodName : new String[] {"getType", "getItemViewType"}) {
            try {
                Method method = findMethod(value.getClass(), methodName, new Class<?>[] {});
                if (method == null) continue;
                Object type = method.invoke(value);
                if (type instanceof Number) {
                    String spec = ControlCenterComponentSpec.fromType(
                            ((Number) type).intValue());
                    if (!spec.isEmpty()) return spec;
                }
            } catch (Throwable ignored) {
                // Some list items expose the type only through a field.
            }
        }
        for (String fieldName : new String[] {"type", "mType", "itemViewType"}) {
            try {
                Field field = findField(value.getClass(), fieldName);
                if (field == null) continue;
                Object type = field.get(value);
                if (type instanceof Number) {
                    String spec = ControlCenterComponentSpec.fromType(
                            ((Number) type).intValue());
                    if (!spec.isEmpty()) return spec;
                }
            } catch (Throwable ignored) {
                // Continue with the other type representations.
            }
        }
        return "";
    }

    private static void applyControlCenterLayout(Object target) {
        if (!(target instanceof ViewGroup group)) return;
        ControlCenterConfig settings = currentConfig.controlCenter;
        if (!settings.enabled || !settings.hasVisualOverride()) {
            restoreControlCenterVisuals();
            return;
        }
        try {
            Method columns = findMethod(target.getClass(), "setMaxColumns",
                    new Class<?>[] {int.class});
            if (columns == null) {
                columns = findMethod(target.getClass(), "setNumColumns",
                        new Class<?>[] {int.class});
            }
            if (columns != null) {
                Integer original = readIntField(target, "mMaxColumns");
                if (original == null) original = readIntField(target, "mNumColumns");
                if (original != null && original > 0) {
                    CONTROL_CENTER_ROOT_COLUMNS.putIfAbsent(group, original);
                }
                columns.invoke(target, settings.columns);
            }
            CONTROL_CENTER_ROOT_CLIPPING.putIfAbsent(group,
                    new boolean[] {group.getClipChildren(), group.getClipToPadding()});
            group.setClipChildren(false);
            group.setClipToPadding(false);
            applyTileVisuals(group, settings);
        } catch (Throwable error) {
            log(Log.WARN, "control-center visual customization failed", error);
        }
    }

    private static void applyTileVisuals(ViewGroup root, ControlCenterConfig settings) {
        float scale = settings.tileScale / 100f;
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (isTileView(child)) {
                CONTROL_CENTER_TILE_VISUALS.putIfAbsent(child,
                        new TileVisualBaseline(child));
                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setClipToOutline(settings.cornerRadius > 0);
                if (settings.cornerRadius > 0) {
                    final float radius = child.getResources().getDisplayMetrics().density
                            * settings.cornerRadius;
                    child.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                        }
                    });
                }
            }
            if (child instanceof ViewGroup group) applyTileVisuals(group, settings);
        }
    }

    private static void restoreControlCenterVisuals() {
        for (Map.Entry<View, TileVisualBaseline> entry :
                new ArrayList<>(CONTROL_CENTER_TILE_VISUALS.entrySet())) {
            View view = entry.getKey();
            if (view != null) RuntimeCleanup.run("restore tile visuals", () -> entry.getValue().restore(view));
        }
        CONTROL_CENTER_TILE_VISUALS.clear();
        for (Map.Entry<ViewGroup, boolean[]> entry :
                new ArrayList<>(CONTROL_CENTER_ROOT_CLIPPING.entrySet())) {
            ViewGroup root = entry.getKey();
            if (root != null) {
                RuntimeCleanup.run("restore child clipping", () -> root.setClipChildren(entry.getValue()[0]));
                RuntimeCleanup.run("restore padding clipping", () -> root.setClipToPadding(entry.getValue()[1]));
            }
        }
        CONTROL_CENTER_ROOT_CLIPPING.clear();
        for (Map.Entry<ViewGroup, Integer> entry :
                new ArrayList<>(CONTROL_CENTER_ROOT_COLUMNS.entrySet())) {
            ViewGroup root = entry.getKey();
            if (root == null) continue;
            try {
                Method columns = findMethod(root.getClass(), "setMaxColumns",
                        new Class<?>[] {int.class});
                if (columns == null) {
                    columns = findMethod(root.getClass(), "setNumColumns",
                            new Class<?>[] {int.class});
                }
                if (columns != null) columns.invoke(root, entry.getValue());
            } catch (Throwable error) {
                log(Log.WARN, "control-center native column restore failed", error);
            }
        }
        CONTROL_CENTER_ROOT_COLUMNS.clear();
    }

    private static final class TileVisualBaseline {
        final float scaleX;
        final float scaleY;
        final boolean clipToOutline;
        final ViewOutlineProvider outlineProvider;

        TileVisualBaseline(View view) {
            scaleX = view.getScaleX();
            scaleY = view.getScaleY();
            clipToOutline = view.getClipToOutline();
            outlineProvider = view.getOutlineProvider();
        }

        void restore(View view) {
            RuntimeCleanup.run("restore tile scale X", () -> view.setScaleX(scaleX));
            RuntimeCleanup.run("restore tile scale Y", () -> view.setScaleY(scaleY));
            RuntimeCleanup.run("restore outline clipping", () -> view.setClipToOutline(clipToOutline));
            RuntimeCleanup.run("restore outline provider", () -> view.setOutlineProvider(outlineProvider));
        }
    }

    private static boolean isTileView(View view) {
        String name = view.getClass().getName();
        return name.contains("QSTileView") || name.contains("TileView")
                || name.contains("QSTileBaseView");
    }

    private static Object keyguardAttach(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (chain.getThisObject() instanceof ViewGroup root) {
            applyKeyguardStatusBar(root);
        }
        return result;
    }

    private static Object keyguardLayout(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (chain.getThisObject() instanceof ViewGroup root) {
            applyKeyguardStatusBar(root);
        }
        return result;
    }

    private static void applyKeyguardStatusBar(ViewGroup root) {
        try {
            ensureConfigObserver(root.getContext());
            View battery = findSystemView(root, "battery");
            if (battery != null) {
                ensureFusionIcon(battery);
                if (battery instanceof ViewGroup group) {
                    layoutIcon(group);
                }
            }
            View statusIcons = findSystemView(root, "statusIcons");
            if (signalsSuppressed() && statusIcons instanceof ViewGroup group) {
                restoreVisibleSlots(group);
                addMissingSlots(group);
                hideSignalChildren(group);
            }
            if (findSystemView(root, "phone_status_bar_left_container") != null
                    && findSystemView(root, "system_icon_area") != null) {
                DualRowStatusBarLayout.apply(root, currentConfig);
                applyStatusBarHeight(root);
            }
        } catch (Throwable error) {
            log(Log.WARN, "keyguard status-bar refresh failed", error);
        }
    }

    private static Object phoneFinishInflate(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup root) {
                ensureConfigObserver(root.getContext());
                applyRootPlacement(root);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "phone status-bar setup failed", error);
        }
        return result;
    }

    private static Object phoneAttached(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup root) {
                ensureConfigObserver(root.getContext());
                applyRootPlacement(root);
                log(Log.INFO, "phone status-bar attached; initial placement applied", null);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "phone status-bar attach setup failed", error);
        }
        return result;
    }

    private static Object phoneCutoutUpdate(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup root) {
                DualRowStatusBarLayout.apply(root, currentConfig);
            }
        } catch (Throwable error) {
            log(Log.WARN, "status-bar column restore failed", error);
        }
        return result;
    }

    private static Object phoneMeasure(XposedInterface.Chain chain) throws Throwable {
        if (!(chain.getThisObject() instanceof ViewGroup root)
                || !isPhoneStatusBar(root)) {
            return chain.proceed();
        }
        ensureConfigObserver(root.getContext());
        applyRootPlacement(root);
        Object[] args = chain.getArgs().toArray();
        if (args.length > 1 && args[1] instanceof Integer heightSpec) {
            int mode = View.MeasureSpec.getMode(heightSpec);
            int size = View.MeasureSpec.getSize(heightSpec);
            if (size > 0) {
                long expanded = dp(root, currentConfig.statusBarHeight);
                int maxSize = (1 << 30) - 1;
                args[1] = View.MeasureSpec.makeMeasureSpec(
                        (int) Math.min(expanded, maxSize),
                        mode == View.MeasureSpec.UNSPECIFIED ? mode : View.MeasureSpec.EXACTLY);
            }
        }
        Object result = chain.proceed(args);
        try {
            applyStatusBarHeight(root);
        } catch (Throwable error) {
            log(Log.ERROR, "phone status-bar measure hook failed", error);
        }
        return result;
    }

    private static void applyStatusBarHeight(View root) {
        int desiredHeight = dp(root, currentConfig.statusBarHeight);
        if (root.getMeasuredWidth() > 0 && root.getMeasuredHeight() != desiredHeight) {
            overwriteMeasured(root, root.getMeasuredWidth(), desiredHeight);
        }
        setMeasuredHeight(findSystemView(root, "status_bar_icons"), desiredHeight);
        View contents = findSystemView(root, "status_bar_contents");
        if (contents instanceof ViewGroup contentGroup) {
            if (contentGroup.getMeasuredWidth() > 0
                    && contentGroup.getMeasuredHeight() != desiredHeight) {
                overwriteMeasured(contentGroup, contentGroup.getMeasuredWidth(), desiredHeight);
            }
        }
        setMeasuredHeight(findSystemView(root, "phone_status_bar_left_container"), desiredHeight);
        setMeasuredHeight(findSystemView(root, "system_icon_area"), desiredHeight);
        View startSide = findSystemView(root, "status_bar_start_side_content");
        View endSide = findSystemView(root, "status_bar_end_side_content");
        if (startSide != null && startSide.getMeasuredWidth() > 0
                && startSide.getMeasuredHeight() != desiredHeight) {
            overwriteMeasured(startSide, startSide.getMeasuredWidth(), desiredHeight);
        }
        if (endSide != null && endSide.getMeasuredWidth() > 0
                && endSide.getMeasuredHeight() != desiredHeight) {
            overwriteMeasured(endSide, endSide.getMeasuredWidth(), desiredHeight);
        }
    }

    private static void setMeasuredHeight(View view, int height) {
        if (view == null || view.getMeasuredWidth() <= 0
                || view.getMeasuredHeight() == height) {
            return;
        }
        overwriteMeasured(view, view.getMeasuredWidth(), height);
    }

    private static Object phoneLayout(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (isPhoneStatusBar(chain.getThisObject())
                    && chain.getThisObject() instanceof ViewGroup root) {
                applyStatusBarHeight(root);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "phone status-bar layout hook failed", error);
        }
        return result;
    }

    private static Object batteryContainerPass(XposedInterface.Chain chain, boolean measure)
            throws Throwable {
        if (!(chain.getThisObject() instanceof ViewGroup container)
                || !isStatusBatteryContainer(container)) {
            return chain.proceed();
        }
        if (Boolean.TRUE.equals(BATTERY_CONTAINER_GUARD.get())) {
            return chain.proceed();
        }
        BATTERY_CONTAINER_GUARD.set(Boolean.TRUE);
        try {
            Object result = chain.proceed();
            if (!measure && currentPlan.doubleRow) {
                alignNativeStatusIcons(container);
            }
            return result;
        } finally {
            BATTERY_CONTAINER_GUARD.set(Boolean.FALSE);
        }
    }

    private static void alignNativeStatusIcons(ViewGroup container) {
        View battery = directChild(container, batteryClass);
        Class<?> statusClass = findClass(CONTAINER_CLASS);
        ViewGroup statusIcons = statusClass == null
                ? null : findDescendant(container, statusClass);
        FusionIconView fusionIcon = battery instanceof ViewGroup batteryGroup
                ? findIcon(batteryGroup) : null;
        if (battery == null || statusIcons == null || fusionIcon == null
                || statusIcons.getWidth() <= 0 || fusionIcon.getWidth() <= 0) {
            return;
        }
        unclipAncestors(statusIcons, container);
        int gap = dp(battery, 4);
        fusionIcon.getLocationOnScreen(ICON_LOCATION);
        statusIcons.getLocationOnScreen(STATUS_LOCATION);
        container.getLocationOnScreen(CONTAINER_LOCATION);
        int iconLeft = ICON_LOCATION[0];
        int iconRight = iconLeft + fusionIcon.getWidth();
        int width = statusIcons.getWidth();
        int containerLeft = CONTAINER_LOCATION[0] + container.getPaddingLeft();
        int containerRight = CONTAINER_LOCATION[0] + container.getWidth()
                - container.getPaddingRight();
        int desiredLeft;
        if (currentPlan.systemSide == currentPlan.fusionSide) {
            desiredLeft = currentPlan.fusionSide == FusionConfig.SIDE_RIGHT
                    ? iconLeft - gap - width : iconRight + gap;
        } else {
            desiredLeft = currentPlan.systemSide == FusionConfig.SIDE_RIGHT
                    ? containerRight - width : containerLeft;
        }
        desiredLeft = Math.max(containerLeft, Math.min(
                Math.max(containerLeft, containerRight - width), desiredLeft));
        int shift = desiredLeft - STATUS_LOCATION[0];
        if (shift != 0) {
            statusIcons.setTranslationX(statusIcons.getTranslationX() + shift);
        }
        if (!NATIVE_ICON_LAYOUT_LOGGED.containsKey(container)) {
            NATIVE_ICON_LAYOUT_LOGGED.put(container, Boolean.TRUE);
            log(Log.INFO, "native status icons aligned: iconLeft=" + iconLeft
                    + " iconRight=" + iconRight + " statusLeft=" + STATUS_LOCATION[0]
                    + " targetLeft=" + desiredLeft + " shift=" + shift
                    + " gap=" + gap + " side=" + currentPlan.systemSide
                    + " parent=" + statusIcons.getParent().getClass().getName(), null);
        }
    }

    private static ViewGroup findDescendant(ViewGroup root, Class<?> type) {
        if (root == null || type == null) {
            return null;
        }
        if (type.isInstance(root)) {
            return root;
        }
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof ViewGroup group) {
                ViewGroup found = findDescendant(group, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void unclipAncestors(View view, ViewGroup stopAt) {
        ViewParent parent = view == null ? null : view.getParent();
        while (parent instanceof ViewGroup group && group != stopAt) {
            group.setClipChildren(false);
            group.setClipToPadding(false);
            parent = group.getParent();
        }
        if (stopAt != null) {
            stopAt.setClipChildren(false);
            stopAt.setClipToPadding(false);
        }
    }

    private static void ensureConfigObserver(Context context) {
        if (context == null) return;
        synchronized (SystemUiHooks.class) {
            if (configLifecycle == null) {
                configLifecycle = new RuntimeConfigLifecycle(MAIN_HANDLER,
                        new RuntimeConfigLifecycle.Host() {
                    @Override public void apply(FusionConfig loaded) { applyLoadedConfig(loaded); }

                    @Override public void onApplyFailure(FusionConfig loaded, Throwable error) {
                        log(Log.ERROR, "config application failed", error);
                        for (String feature : new String[] {FusionActivationStatus.FEATURE_STATUS_BAR,
                                FusionActivationStatus.FEATURE_CONTROL_CENTER,
                                FusionActivationStatus.FEATURE_NOTIFICATION_CLOCK}) {
                            configLifecycle.report(loaded.revision, feature,
                                    FusionActivationStatus.FAILED, "config_application_failed");
                        }
                    }

                    @Override public void onRecovered() {
                        log(Log.INFO, "config recovered after startup", null);
                    }

                    @Override public void requestPreview(Context source) {
                        requestControlCenterPreviewCapture(source);
                    }
                        });
            }
            configLifecycle.ensureStarted(context);
        }
    }

    private static void applyLoadedConfig(FusionConfig loaded) {
        ControlCenterConfig previousControlCenter = currentConfig.controlCenter;
        boolean controlCenterChanged = controlCenterChanged(previousControlCenter,
                loaded.controlCenter);
        boolean layoutPlanChanged = previousControlCenter == null
                || !previousControlCenter.layoutPlan.equals(loaded.controlCenter.layoutPlan);
        boolean restoreNativePanels = (previousControlCenter.hasLayoutPlanOverride()
                && !loaded.controlCenter.hasLayoutPlanOverride())
                || (previousControlCenter.hasVisualOverride()
                && !loaded.controlCenter.hasVisualOverride())
                || (!previousControlCenter.componentPlacement.isEmpty()
                && loaded.controlCenter.componentPlacement.isEmpty())
                || (!previousControlCenter.componentOrder.isEmpty()
                && loaded.controlCenter.componentOrder.isEmpty())
                || (previousControlCenter.enabled && !loaded.controlCenter.enabled);
        // Reset generations from older builds are ignored. The module no longer
        // owns the native tile repository and must not rewrite it while restoring
        // its own overlay.
        if (controlCenterChanged) {
            CONTROL_CENTER_SHAPES_LOGGED.clear();
            CONTROL_CENTER_VISUAL_SHAPES_LOGGED.clear();
            CONTROL_CENTER_HEIGHT_LOGGED.set(false);
            CONTROL_CENTER_LAYOUT_REBIND_LOGGED.set(false);
        }
        if (layoutPlanChanged) {
            int reset = ControlCenterRuntimeGrid.resetAll("layout-plan-changed");
            log(Log.INFO, "control-center runtime grid invalidated planChanged=true"
                    + " reset=" + reset
                    + " regular=" + controlCenterPlanSummary(loaded.controlCenter, false)
                    + " compact=" + controlCenterPlanSummary(loaded.controlCenter, true), null);
        }
        currentConfig = loaded;
        currentPlan = StatusBarLayoutPlan.from(currentConfig);
        reportFeature(FusionActivationStatus.FEATURE_STATUS_BAR, FusionActivationStatus.WAITING,
                "status_bar_refresh_pending");
        reportFeature(FusionActivationStatus.FEATURE_CONTROL_CENTER,
                FusionActivationStatus.WAITING, "control_center_mount_pending");
        if (controlCenterChanged) ControlCenterMaterials.refresh(loaded.controlCenter);
        if (controlCenterChanged) {
            ControlCenterLayoutPlan nextPlan = ControlCenterLayoutPlan.decode(
                    loaded.controlCenter.layoutPlan);
            int regularCount = nextPlan == null ? 0 : nextPlan.regular.items.size();
            int compactCount = nextPlan == null ? 0 : nextPlan.compact.items.size();
            log(Log.INFO, "control-center layout changed plan="
                    + (loaded.controlCenter.layoutPlan.isEmpty() ? "empty" : "present")
                    + " regularItems=" + regularCount
                    + " compactItems=" + compactCount
                    + " regularColumns=" + (nextPlan == null ? loaded.controlCenter.columns
                    : nextPlan.regular.columns)
                    + " compactColumns=" + (nextPlan == null ? 0 : nextPlan.compact.columns),
                    null);
        }
        if (previousControlCenter.hasVisualOverride()
                && (!loaded.controlCenter.enabled
                || !loaded.controlCenter.hasVisualOverride())) {
            restoreControlCenterVisuals();
        }
        if (previousControlCenter.tileScale != 100
                && (!loaded.controlCenter.enabled
                || loaded.controlCenter.tileScale == 100)) {
            restoreControlCenterIconSizes();
        }
        try {
            StatusClockController.onConfigChanged(currentConfig);
        } catch (Throwable error) {
            log(Log.WARN, "custom clock config refresh failed", error);
            reportFeature(FusionActivationStatus.FEATURE_STATUS_BAR,
                    FusionActivationStatus.DEGRADED, "status_clock_refresh_failed");
        }
        try {
            NotificationCenterClockController.onConfigChanged(currentConfig);
            reportNotificationApplication();
        } catch (Throwable error) {
            log(Log.WARN, "notification clock config refresh failed", error);
            reportFeature(FusionActivationStatus.FEATURE_NOTIFICATION_CLOCK,
                    FusionActivationStatus.DEGRADED, "notification_clock_refresh_failed");
        }
        refreshNotificationTopPadding();
        SPAN_MEASURE_LOGGED.set(false);
        scheduleStatusBarRefresh(currentStatusRoot.get());
        if (currentStatusRoot.get() == null) reportStatusBarApplication();
        if (controlCenterChanged) {
            recreateCompactCardIfNeeded(previousControlCenter, loaded.controlCenter);
            refreshControlCenterTiles();
            if (restoreNativePanels) redistributeNativeControlCenterPanels();
        }
        if (!loaded.controlCenter.enabled) {
            reportFeature(RuntimeApplicationPolicy.controlCenterAvailability(false,
                    CONTROL_CENTER_PLUGIN_HOOKED.get()));
        } else if (!CONTROL_CENTER_PLUGIN_HOOKED.get()) {
            reportFeature(RuntimeApplicationPolicy.controlCenterAvailability(true, false));
        } else if (!controlCenterChanged) {
            if (loaded.controlCenter.hasLayoutPlanOverride()) {
                ControlCenterRuntimeGrid.refreshApplication(loaded.controlCenter);
            } else if (nativeControlCenterAppliedSettings == previousControlCenter) {
                nativeControlCenterAppliedSettings = loaded.controlCenter;
                reportControlCenterApplication(loaded.controlCenter,
                        FusionActivationStatus.APPLIED, "native_control_center_bound");
            }
        }
    }

    private static void reportFeature(String feature, String state, String reason) {
        if (configLifecycle != null)
            configLifecycle.report(currentConfig.revision, feature, state, reason);
    }

    private static void reportFeature(RuntimeApplicationPolicy.Result result) {
        reportFeature(result.feature(), result.state(), result.reason());
    }

    private static void reportStatusBarApplication() {
        ViewGroup root = currentStatusRoot.get();
        reportFeature(RuntimeApplicationPolicy.statusBar(
                batteryClass != null && HANDLES.hasHook(batteryClass, "onAttachedToWindow"),
                root != null && root.isAttachedToWindow(), currentConfig.customClock,
                StatusClockController.hasAttachedViews()));
    }

    private static void reportNotificationApplication() {
        boolean enabled = currentConfig.notificationClock.enabled
                || currentConfig.notificationClock.dateEnabled;
        boolean available = HANDLES.hasHook(findClassQuiet(NOTIFICATION_HEADER_CLASS, loader), "onFinishInflate")
                || HANDLES.hasHook(findClassQuiet(CLOCK_CLASS, loader), "updateTime");
        reportFeature(RuntimeApplicationPolicy.notificationClock(enabled, available,
                NotificationCenterClockController.hasAttachedViews()));
    }

    static void reportControlCenterApplication(ControlCenterConfig settings, String state, String reason) {
        if (settings == currentConfig.controlCenter)
            reportFeature(FusionActivationStatus.FEATURE_CONTROL_CENTER, state, reason);
    }

    private static void redistributeNativeControlCenterPanels() {
        Object distributor = controlCenterDistributorRef.get();
        if (distributor == null) return;
        try {
            Method defaultDistribution = findMethod(distributor.getClass(),
                    "distributePanels$default", new Class<?>[] {distributor.getClass(),
                            boolean.class, int.class, Object.class});
            if (defaultDistribution != null) {
                defaultDistribution.invoke(null, distributor, false, 1, null);
            } else {
                Method distribute = findMethod(distributor.getClass(), "distributePanels",
                        new Class<?>[] {boolean.class});
                Field leftField = findField(distributor.getClass(), "leftPanelContent");
                Object left = leftField == null ? null : leftField.get(distributor);
                boolean split = left instanceof List<?> items && !items.isEmpty();
                if (distribute == null) return;
                distribute.invoke(distributor, split);
            }
            Method notify = findMethod(distributor.getClass(), "handleNotifyChanged",
                    new Class<?>[] {boolean.class, boolean.class});
            if (notify != null) notify.invoke(distributor, false, false);
            log(Log.INFO, "control-center native panels redistributed", null);
        } catch (Throwable error) {
            log(Log.WARN, "control-center native panel restore failed", error);
        }
    }

    private static boolean controlCenterChanged(ControlCenterConfig oldSettings,
            ControlCenterConfig newSettings) {
        if (oldSettings == null || newSettings == null) return oldSettings != newSettings;
        return oldSettings.enabled != newSettings.enabled
                || !oldSettings.order.equals(newSettings.order)
                || !oldSettings.hidden.equals(newSettings.hidden)
                || oldSettings.columns != newSettings.columns
                || oldSettings.tileScale != newSettings.tileScale
                || oldSettings.cornerRadius != newSettings.cornerRadius
                || oldSettings.spacing != newSettings.spacing
                || oldSettings.style != newSettings.style
                || oldSettings.backgroundBlur != newSettings.backgroundBlur
                || oldSettings.cardBlur != newSettings.cardBlur
                || oldSettings.tileBlur != newSettings.tileBlur
                || !oldSettings.layout.equals(newSettings.layout)
                || !oldSettings.layoutPlan.equals(newSettings.layoutPlan)
                || !oldSettings.componentOrder.equals(newSettings.componentOrder)
                || !oldSettings.componentPlacement.equals(newSettings.componentPlacement);
    }

    private static int refreshControlCenterTiles() {
        int refreshed = 0;
        Object host = controlCenterHostRef.get();
        if (host != null) {
            try {
                // The host tile list is never rewritten. Ordering and hiding are applied
                // by display hooks and the runtime grid.
                Field callbacksField = findField(host.getClass(), "callbacksMap");
                Object value = callbacksField == null ? null : callbacksField.get(host);
                if (value instanceof Map<?, ?> callbacks) {
                    for (Object callback : new ArrayList<>(callbacks.keySet())) {
                        if (callback == null) continue;
                        Method method = findMethod(callback.getClass(), "onTilesChanged",
                                new Class<?>[] {});
                        if (method == null) {
                            try {
                                method = callback.getClass().getMethod("onTilesChanged");
                                method.setAccessible(true);
                            } catch (Throwable ignored) {
                                method = null;
                            }
                        }
                        if (method != null) {
                            method.invoke(callback);
                            refreshed++;
                        }
                    }
                }
            } catch (Throwable error) {
                log(Log.WARN, "control-center host refresh failed", error);
            }
        }
        Object list = controlCenterListRef.get();
        if (list != null) {
            try {
                Method method = findMethod(list.getClass(), "distributeTiles",
                        new Class<?>[] {boolean.class});
                if (method != null) {
                    method.invoke(list, false);
                    refreshed++;
                }
            } catch (Throwable error) {
                log(Log.WARN, "control-center plugin list refresh failed", error);
            }
            refreshed += refreshControlCenterAdapters(list);
            scheduleControlCenterAdapterRefresh();
        }
        Object compact = controlCenterCompactRef.get();
        if (compact != null) {
            try {
                Method method = findMethod(compact.getClass(), "distributeTiles",
                        new Class<?>[] {boolean.class});
                if (method != null) {
                    method.invoke(compact, false);
                    refreshed++;
                }
            } catch (Throwable error) {
                log(Log.WARN, "control-center compact refresh failed", error);
            }
        }
        log(Log.INFO, "control-center config refresh requested callbacks=" + refreshed
                + " regular=" + controlCenterPlanSummary(currentConfig.controlCenter, false)
                + " compact=" + controlCenterPlanSummary(currentConfig.controlCenter, true)
                + " order=" + currentConfig.controlCenter.order, null);
        return refreshed;
    }

    private static String controlCenterPlanSummary(ControlCenterConfig settings,
            boolean compact) {
        if (settings == null || settings.layoutPlan.isEmpty()) return "empty";
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        if (plan == null) return "invalid";
        ControlCenterLayoutPlan.Mode mode = plan.mode(compact);
        StringBuilder summary = new StringBuilder();
        int count = mode.items.size();
        int limit = Math.min(count, 24);
        for (int i = 0; i < limit; i++) {
            ControlCenterLayoutPlan.Item item = mode.items.get(i);
            if (summary.length() > 0) summary.append(';');
            summary.append(item.id).append('@').append(item.x).append(',')
                    .append(item.y).append('+').append(item.width).append('x')
                    .append(item.height);
        }
        if (count > limit) summary.append(";...");
        return mode.columns + "c/" + count + "i/" + summary;
    }

    private static Object afterClockPass(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof TextView clock
                    && NotificationCenterClockController.isNotificationView(clock)) {
                ensureConfigObserver(clock.getContext());
                NotificationCenterClockController.onNativeUpdate(clock, currentConfig);
                reportNotificationApplication();
            } else if (chain.getThisObject() instanceof TextView clock
                    && isViewNamed(clock, "clock")) {
                ensureConfigObserver(clock.getContext());
                StatusClockController.onNativeUpdate(clock, currentConfig);
                reportStatusBarApplication();
            }
        } catch (Throwable error) {
            log(Log.WARN, "status clock update failed", error);
        }
        return result;
    }

    private static Object notificationHeaderPass(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup header) {
                ensureConfigObserver(header.getContext());
                NotificationCenterClockController.onHeader(header, currentConfig);
                reportNotificationApplication();
            }
        } catch (Throwable error) {
            log(Log.WARN, "notification-center clock update failed", error);
        }
        return result;
    }

    private static Object prepareNotificationTimeSize(XposedInterface.Chain chain) throws Throwable {
        int targetPixels = 0;
        boolean expanded = false;
        try {
            NotificationClockConfig settings = currentConfig.notificationClock;
            if (settings.enabled && settings.sizeSp > 0) {
                Object controller = chain.getThisObject();
                Context context = (Context) notificationExpandContextField.get(controller);
                targetPixels = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                        settings.sizeSp, context.getResources().getDisplayMetrics()));
                if (notificationBigTimeSizeField.getInt(controller) != targetPixels) {
                    notificationBigTimeSizeField.setInt(controller, targetPixels);
                    if (NOTIFICATION_SIZE_LOGGED.compareAndSet(false, true)) {
                        log(Log.INFO, "notification expanded time size synced sp="
                                + settings.sizeSp + " px=" + targetPixels, null);
                    }
                }
                expanded = notificationExpandProgressField != null
                        && notificationExpandProgressField.getFloat(controller) >= 0.5f;
            }
        } catch (Throwable error) {
            if (NOTIFICATION_SIZE_LOGGED.compareAndSet(false, true)) {
                log(Log.WARN, "notification expanded time size sync failed", error);
            }
        }
        Object result = chain.proceed();
        if (expanded) {
            try {
                NotificationCenterClockController.enforceExpandedTimeSize(targetPixels);
            } catch (Throwable error) {
                log(Log.WARN, "notification expanded time size restore failed", error);
            }
        }
        return result;
    }

    private static Object afterNotificationExpansion(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getArgs().size() == 1 && chain.getArgs().get(0) instanceof Float progress) {
                NotificationCenterClockController.onExpansionChanged(progress);
            }
        } catch (Throwable error) {
            if (NOTIFICATION_OFFSET_FAILED.compareAndSet(false, true)) {
                log(Log.WARN, "notification expansion offset failed", error);
            }
        }
        return result;
    }

    private static Object adjustNotificationTopPadding(XposedInterface.Chain chain) throws Throwable {
        if (Boolean.TRUE.equals(NOTIFICATION_PADDING_REFRESH.get())) {
            return chain.proceed();
        }
        if (!(chain.getThisObject() instanceof View stack)) {
            return chain.proceed();
        }
        Object[] args = chain.getArgs().toArray();
        if (args.length != 2 || !(args[0] instanceof Float padding)) {
            return chain.proceed();
        }
        try {
            if (isNotificationShadeStack(stack)) {
                View known = notificationStackRef.get();
                int offsetDp = currentConfig.notificationClock.listOffsetYDp;
                if (known != stack) {
                    notificationStackRef = new WeakReference<>(stack);
                    nativeNotificationTopPadding = padding;
                } else if (offsetDp != 0
                        && Math.abs(padding - (nativeNotificationTopPadding
                                + dp(stack, offsetDp))) < 0.5f) {
                    return chain.proceed(args);
                } else {
                    nativeNotificationTopPadding = padding;
                }
                if (offsetDp != 0) {
                    args[0] = padding + dp(stack, offsetDp);
                    if (NOTIFICATION_LIST_LOGGED.compareAndSet(false, true)) {
                        log(Log.INFO, "notification list top padding adjusted dp=" + offsetDp, null);
                    }
                }
            }
        } catch (Throwable error) {
            if (NOTIFICATION_LIST_LOGGED.compareAndSet(false, true)) {
                log(Log.WARN, "notification list padding adjustment failed", error);
            }
        }
        return chain.proceed(args);
    }

    private static Object afterNotificationStackLayout(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (Boolean.TRUE.equals(NOTIFICATION_PADDING_REFRESH.get())) return result;
        if (!(chain.getThisObject() instanceof View stack)
                || !isNotificationShadeStack(stack)) return result;
        try {
            View known = notificationStackRef.get();
            if (known != stack) {
                notificationStackRef = new WeakReference<>(stack);
                nativeNotificationTopPadding = readNotificationTopPadding(stack);
            }
            if (currentConfig.notificationClock.listOffsetYDp != 0
                    && notificationTopPaddingMethod != null) {
                NOTIFICATION_PADDING_REFRESH.set(Boolean.TRUE);
                notificationTopPaddingMethod.invoke(stack,
                        nativeNotificationTopPadding
                                + dp(stack, currentConfig.notificationClock.listOffsetYDp), false);
                if (NOTIFICATION_LIST_LOGGED.compareAndSet(false, true)) {
                    log(Log.INFO, "notification list layout offset applied dp="
                            + currentConfig.notificationClock.listOffsetYDp, null);
                }
            }
        } catch (Throwable error) {
            log(Log.WARN, "notification list layout offset failed", error);
        } finally {
            NOTIFICATION_PADDING_REFRESH.remove();
        }
        return result;
    }

    private static void refreshNotificationTopPadding() {
        View stack = notificationStackRef.get();
        if (stack == null || !stack.isAttachedToWindow() || notificationTopPaddingMethod == null) {
            return;
        }
        try {
            int offsetDp = isNotificationShadeStack(stack)
                    ? currentConfig.notificationClock.listOffsetYDp : 0;
            NOTIFICATION_PADDING_REFRESH.set(Boolean.TRUE);
            notificationTopPaddingMethod.invoke(stack,
                    nativeNotificationTopPadding + dp(stack, offsetDp), false);
        } catch (Throwable error) {
            log(Log.WARN, "notification list padding refresh failed", error);
        } finally {
            NOTIFICATION_PADDING_REFRESH.remove();
        }
    }

    /** Returns false for the shared notification stack while it is owned by Control Center. */
    private static boolean isNotificationShadeStack(View stack) {
        try {
            if (notificationQsExpansionFractionField != null
                    && notificationQsExpansionFractionField.getFloat(stack) > 0.01f) {
                return false;
            }
            if (notificationStackStateField == null) return true;
            int state = notificationStackStateField.getInt(stack);
            return state == 0 || state == 2;
        } catch (Throwable error) {
            int state = notificationStackStateField == null
                    ? 0 : safeIntField(notificationStackStateField, stack);
            return state == 0 || state == 2;
        }
    }

    private static float readNotificationTopPadding(View stack) {
        try {
            if (notificationStackAmbientStateField == null
                    || notificationAmbientTopPaddingField == null) return 0f;
            Object ambient = notificationStackAmbientStateField.get(stack);
            Object value = ambient == null ? null : notificationAmbientTopPaddingField.get(ambient);
            return value instanceof Number ? ((Number) value).floatValue() : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static int safeIntField(Field field, Object target) {
        try {
            return field.getInt(target);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Object afterNativeSpeedVisibility(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof View speed
                    && currentConfig.telemetry.enabled(TelemetryConfig.NET_SPEED)
                    && isDescendantOf(speed, currentStatusRoot.get())) {
                NATIVE_SPEED_VISIBILITY.put(speed, speed.getVisibility());
                speed.setVisibility(View.GONE);
            }
        } catch (Throwable error) {
            log(Log.WARN, "native network speed hide failed", error);
        }
        return result;
    }

    private static void hookDeclaredClockMethod(Class<?> clockClass, String name) {
        try {
            Method method = clockClass.getDeclaredMethod(name);
            hookMethod(clockClass.getSimpleName() + "#" + name, method,
                    SystemUiHooks::afterClockPass);
        } catch (NoSuchMethodException error) {
            log(Log.WARN, "clock method missing " + name, null);
        } catch (Throwable error) {
            log(Log.WARN, "clock method hook failed " + name, error);
        }
    }

    private static NotificationHookInstaller notificationHookInstaller() {
        return new NotificationHookInstaller(
                SystemUiHooks::findClassQuiet,
                new NotificationHookInstaller.MemberResolver() {
                    @Override public java.lang.reflect.Field field(Class<?> type, String name) {
                        return findField(type, name);
                    }

                    @Override public Method exact(Class<?> type, String name, Class<?>[] parameters,
                            boolean searchSuper) {
                        if (searchSuper) return findMethod(type, name, parameters);
                        try {
                            Method method = type.getDeclaredMethod(name, parameters);
                            method.setAccessible(true);
                            return method;
                        } catch (Throwable ignored) {
                            return null;
                        }
                    }

                    @Override public Method[] declaredMethods(Class<?> type) {
                        return type.getDeclaredMethods();
                    }
                },
                (id, method, hooker) -> hookMethod(id, method, hooker),
                SystemUiHooks::notificationHooker);
    }

    private static XposedInterface.Hooker notificationHooker(NotificationHookInstaller.Callback callback) {
        return switch (callback) {
            case HEADER -> SystemUiHooks::notificationHeaderPass;
            case EXPAND_SIZE -> SystemUiHooks::prepareNotificationTimeSize;
            case EXPANSION_CHANGED -> SystemUiHooks::afterNotificationExpansion;
            case TOP_PADDING -> SystemUiHooks::adjustNotificationTopPadding;
            case STACK_LAYOUT -> SystemUiHooks::afterNotificationStackLayout;
            case SPEED_VISIBILITY -> SystemUiHooks::afterNativeSpeedVisibility;
        };
    }

    private static void scheduleStatusBarRefresh(ViewGroup root) {
        STATUS_BAR_REFRESH.request(root);
    }

    private static void reapplyStatusBarViews(ViewGroup root) {
        try {
            if (root == null) return;
            ensureConfigObserver(root.getContext());
            applyRootPlacement(root);
            applyStatusBarHeight(root);
            root.requestLayout();
            reportStatusBarApplication();
        } catch (Throwable error) {
            log(Log.WARN, "status layout refresh failed", error);
            reportFeature(FusionActivationStatus.FEATURE_STATUS_BAR,
                    FusionActivationStatus.DEGRADED, "status_bar_layout_failed");
        }
    }

    private static boolean isPhoneStatusBar(Object value) {
        return value != null && PHONE_STATUS_BAR_CLASS.equals(value.getClass().getName());
    }

    private static boolean isViewNamed(View view, String name) {
        if (view == null || name == null) {
            return false;
        }
        try {
            int id = view.getResources().getIdentifier(name, "id", "com.android.systemui");
            return id != 0 && view.getId() == id;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isStatusBatteryContainer(ViewGroup container) {
        return isViewNamed(container, "system_icons");
    }

    private static boolean isStatusIconContainer(ViewGroup container) {
        if (!isViewNamed(container, "statusIcons")) {
            return false;
        }
        ViewParent parent = container.getParent();
        while (parent instanceof View view) {
            if (isViewNamed(view, "system_icons")) {
                return true;
            }
            if (KEYGUARD_STATUS_BAR_CLASS.equals(view.getClass().getName())) {
                return true;
            }
            parent = view.getParent();
        }
        return false;
    }

    private static boolean isStatusBarBattery(View battery) {
        if (battery == null || !isViewNamed(battery, "battery")) {
            return false;
        }
        ViewParent parent = battery.getParent();
        while (parent instanceof View view) {
            if (isViewNamed(view, "system_icons")
                    || (batteryContainerClass != null && batteryContainerClass.isInstance(view))
                    || KEYGUARD_STATUS_BAR_CLASS.equals(view.getClass().getName())) {
                return true;
            }
            parent = view.getParent();
        }
        return false;
    }

    private static void applyRootPlacement(ViewGroup root) {
        if (root == null) {
            return;
        }
        currentStatusRoot = new WeakReference<>(root);
        DualRowStatusBarLayout.apply(root, currentConfig);
        syncNativeSpeed(root);
        View battery = findSystemView(root, "battery");
        hideNativeBatteryArtifacts(root, battery);
        suppressOriginalSignals(root);
        if (battery != null) {
            ensureFusionIcon(battery);
        }
    }

    private static void syncNativeSpeed(ViewGroup root) {
        View speed = nativeSpeedRef.get();
        ViewGroup knownRoot = nativeSpeedRoot.get();
        long now = SystemClock.uptimeMillis();
        if (knownRoot == root && speed == null
                && now - nativeSpeedScanAt < 5_000L) {
            return;
        }
        if (knownRoot != root || speed == null || !isDescendantOf(speed, root)) {
            speed = findNativeSpeed(root);
            nativeSpeedRoot = new WeakReference<>(root);
            nativeSpeedRef = new WeakReference<>(speed);
            nativeSpeedScanAt = now;
        }
        if (speed == null) return;
        if (currentConfig.telemetry.enabled(TelemetryConfig.NET_SPEED)) {
            if (!NATIVE_SPEED_VISIBILITY.containsKey(speed)) {
                NATIVE_SPEED_VISIBILITY.put(speed, speed.getVisibility());
            }
            if (speed.getVisibility() != View.GONE) speed.setVisibility(View.GONE);
        } else {
            Integer original = NATIVE_SPEED_VISIBILITY.remove(speed);
            if (original != null && speed.getVisibility() != original) {
                speed.setVisibility(original);
            }
        }
    }

    private static View findNativeSpeed(View node) {
        if (NETWORK_SPEED_CLASS.equals(node.getClass().getName())) return node;
        if (node instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findNativeSpeed(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void suppressOriginalSignals(View root) {
        if (!signalsSuppressed()) {
            return;
        }
        View view = findSystemView(root, "statusIcons");
        if (view instanceof ViewGroup group) {
            restoreVisibleSlots(group);
            addMissingSlots(group);
            hideSignalChildren(group);
        }
    }

    private static void restoreVisibleSlots(ViewGroup container) {
        List<?> ignored = ignoredSlots(container);
        if (ignored == null) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) ignored;
            mutable.removeIf(value -> value instanceof String
                    && SlotFilter.shouldForceShow((String) value));
        } catch (UnsupportedOperationException ignoredError) {
            log(Log.WARN, "ignored slot list is immutable", null);
        } catch (Throwable error) {
            log(Log.WARN, "restore visible slots failed", error);
        }
    }

    private static void hideNativeBatteryArtifacts(View root, View battery) {
        if (!signalsSuppressed()) {
            return;
        }
        for (String name : new String[] {
                "battery_indicator", "battery_view", "battery_meter_view",
                "battery_meter_composable_view", "battery_remaining_icon"}) {
            View candidate = findSystemView(root, name);
            if (candidate == null || candidate == battery || isDescendantOf(candidate, battery)) {
                continue;
            }
            candidate.setAlpha(0f);
            candidate.setVisibility(View.GONE);
            candidate.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    private static void ensureFusionIcon(View battery) {
        if (batteryClass == null || !isStatusBarBattery(battery) || !(battery instanceof ViewGroup batteryGroup)) {
            return;
        }
        FusionIconView icon = findIcon(batteryGroup);
        if (icon == null) {
            ensureIcon(batteryGroup);
            icon = findIcon(batteryGroup);
        }
        if (icon != null) {
            icon.setDisplayConfig(currentConfig);
            icon.setIconTint(resolveTint(battery));
        }
        battery.setBackground(null);
        battery.setForeground(null);
        batteryGroup.setClipChildren(false);
        batteryGroup.setClipToPadding(false);
        if (currentPlan.spanFusion && battery.getParent() instanceof ViewGroup parent) {
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
        }
        battery.setVisibility(View.VISIBLE);
        battery.setAlpha(1f);
    }

    private static boolean isDescendantOf(View child, View ancestor) {
        if (child == null || ancestor == null) {
            return false;
        }
        ViewParent parent = child.getParent();
        while (parent instanceof View) {
            if (parent == ancestor) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static View findSystemView(View root, String name) {
        if (root == null) {
            return null;
        }
        try {
            int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
            return id == 0 ? null : root.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View directChild(ViewGroup parent, Class<?> type) {
        if (parent == null || type == null) {
            return null;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (type.isInstance(child)) {
                return child;
            }
        }
        return null;
    }

    private static void arrangeBatteryContainer(ViewGroup container, boolean measure) {
        View battery = directChild(container, batteryClass);
        ViewGroup statusIcons = (ViewGroup) directChild(container, findClass(CONTAINER_CLASS));
        if (battery == null || statusIcons == null) {
            return;
        }
        int totalHeight = measure ? container.getMeasuredHeight() : container.getHeight();
        int totalWidth = measure ? container.getMeasuredWidth() : container.getWidth();
        if (totalHeight <= 0 || totalWidth <= 0) {
            return;
        }
        int rowHeight = Math.max(1, totalHeight / 2);
        if (measure) {
            int availableWidth = Math.max(1, totalWidth
                    - container.getPaddingLeft() - container.getPaddingRight());
            int batteryHeight = Math.max(
                    battery.getMeasuredHeight(), dp(battery, currentPlan.doubleRow ? 48 : 40));
            battery.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(batteryHeight, View.MeasureSpec.EXACTLY));
            statusIcons.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY));
            int desiredWidth = battery.getMeasuredWidth() + container.getPaddingLeft()
                    + container.getPaddingRight();
            overwriteMeasured(container, Math.max(1, desiredWidth), totalHeight);
            return;
        }
        int left = container.getPaddingLeft();
        int right = container.getWidth() - container.getPaddingRight();
        boolean rtl = container.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int statusWidth = statusIcons.getMeasuredWidth();
        int statusLeft = rtl ? Math.max(left, right - statusWidth) : left;
        statusIcons.layout(statusLeft, rowHeight,
                Math.min(right, statusLeft + statusWidth),
                rowHeight + statusIcons.getMeasuredHeight());
        battery.layout(left, 0, right, battery.getMeasuredHeight());
        arrangeStatusIcons(statusIcons, false);
    }

    private static void arrangeStatusIcons(ViewGroup container, boolean measure) {
        if (container == null || !currentPlan.doubleRow) {
            return;
        }
        container.setClipChildren(false);
        container.setClipToPadding(false);
        int height = measure ? container.getMeasuredHeight() : container.getHeight();
        int width = measure ? container.getMeasuredWidth() : container.getWidth();
        if (height <= 0 || width <= 0) {
            return;
        }
        boolean rtl = container.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int rowHeight = Math.max(1, height / 2);
        List<View> children = visibleStatusChildren(container);
        if (measure) {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(
                    Math.max(1, width), View.MeasureSpec.AT_MOST);
            for (int i = 0; i < children.size(); i++) {
                View child = children.get(i);
                boolean fullHeight = currentPlan.spanFusion || child instanceof FusionIconView;
                int childHeight = fullHeight ? height : rowHeight;
                child.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(
                        childHeight, View.MeasureSpec.EXACTLY));
            }
            int desired = desiredStatusWidth(children,
                    container.getPaddingLeft(), container.getPaddingRight(),
                    currentPlan.spanFusion ? 1 : 2);
            overwriteMeasured(container, Math.max(width, desired), height);
            return;
        }
        if (currentPlan.spanFusion) {
            layoutStatusRow(container, children, 0, height, rtl);
            return;
        }
        List<View> first = new ArrayList<>();
        List<View> second = new ArrayList<>();
        int row = 0;
        View fusionIcon = null;
        List<View> rightFirst = new ArrayList<>();
        List<View> rightSecond = new ArrayList<>();
        int fusionSide = currentPlan.fusionSide;
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child instanceof FusionIconView) {
                fusionIcon = child;
            } else {
                boolean toTop = row++ % 2 == 0;
                if (fusionSide == FusionConfig.SIDE_LEFT) {
                    (toTop ? first : second).add(child);
                } else {
                    (toTop ? rightFirst : rightSecond).add(child);
                }
            }
        }
        int y = 0;
        int x;
        if (fusionSide == FusionConfig.SIDE_LEFT) {
            x = rtl ? container.getWidth() - container.getPaddingRight()
                            - (fusionIcon == null ? 0 : fusionIcon.getMeasuredWidth())
                    : container.getPaddingLeft();
        } else {
            x = rtl ? container.getPaddingLeft()
                    : container.getWidth() - container.getPaddingRight()
                            - (fusionIcon == null ? 0 : fusionIcon.getMeasuredWidth());
        }
        if (fusionIcon != null) {
            fusionIcon.layout(x, y, x + fusionIcon.getMeasuredWidth(),
                    y + fusionIcon.getMeasuredHeight());
        }
        if (fusionSide == FusionConfig.SIDE_LEFT) {
            int firstStart = rtl ? container.getPaddingLeft()
                    : x + (fusionIcon == null ? 0 : fusionIcon.getMeasuredWidth());
            int firstEnd = rtl ? x : container.getWidth() - container.getPaddingRight();
            layoutStatusRowInBounds(container, first, 0, rowHeight, rtl, firstStart, firstEnd);
            layoutStatusRow(container, second, rowHeight, rowHeight, rtl);
        } else {
            layoutStatusRow(container, rightFirst, 0, rowHeight, rtl);
            int secondStart = rtl ? container.getPaddingLeft()
                    : x + (fusionIcon == null ? 0 : fusionIcon.getMeasuredWidth());
            int secondEnd = rtl ? x : container.getWidth() - container.getPaddingRight();
            layoutStatusRowInBounds(container, rightSecond, rowHeight, rowHeight,
                    rtl, secondStart, secondEnd);
        }
    }

    private static List<View> visibleStatusChildren(ViewGroup container) {
        List<View> result = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getVisibility() == View.GONE || SlotFilter.shouldHide(slotOf(child))) {
                continue;
            }
            result.add(child);
        }
        return result;
    }

    private static int desiredStatusWidth(
            List<View> children, int paddingLeft, int paddingRight, int rowCount) {
        int[] widths = new int[Math.max(1, rowCount)];
        for (int i = 0; i < children.size(); i++) {
            int row = i % widths.length;
            widths[row] += children.get(i).getMeasuredWidth();
        }
        int max = 0;
        for (int width : widths) {
            max = Math.max(max, width);
        }
        return max + paddingLeft + paddingRight;
    }

    private static void layoutStatusRow(
            ViewGroup container, List<View> children, int top, int rowHeight, boolean rtl) {
        int left = container.getPaddingLeft();
        int right = container.getWidth() - container.getPaddingRight();
        layoutStatusRowInBounds(container, children, top, rowHeight, rtl, left, right);
    }

    private static void layoutStatusRowInBounds(
            ViewGroup container, List<View> children, int top, int rowHeight,
            boolean rtl, int left, int right) {
        int cursor = rtl ? right : left;
        for (View child : children) {
            int childWidth = child.getMeasuredWidth();
            int childHeight = rowHeight;
            if (rtl) {
                int childLeft = Math.max(left, cursor - childWidth);
                child.layout(childLeft, top, cursor, top + childHeight);
                cursor = childLeft;
            } else {
                int childRight = Math.min(right, cursor + childWidth);
                child.layout(cursor, top, childRight, top + childHeight);
                cursor = childRight;
            }
        }
    }

    private static void hook(Class<?> type, String name, Class<?>[] params, XposedInterface.Hooker hooker) {
        Method method = findMethod(type, name, params);
        if (method == null) {
            log(Log.ERROR, "method missing " + type.getSimpleName() + "#" + name, null);
            return;
        }
        hookMethod(type.getSimpleName() + "#" + name, method, hooker);
    }

    private static boolean hookMethod(String id, Method method, XposedInterface.Hooker hooker) {
        if (module == null || method == null) {
            return false;
        }
        try {
            return HANDLES.install(method, id, () -> {
                try {
                    module.deoptimize(method);
                } catch (Throwable error) {
                    log(Log.WARN, "deoptimize failed " + id, error);
                }
                return module.hook(method)
                    .setId("fusion-statusbar:" + id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
            });
        } catch (Throwable error) {
            log(Log.ERROR, "hook failed " + id, error);
            return false;
        }
    }

    private static Object batteryMeasure(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (!(chain.getThisObject() instanceof ViewGroup battery)
                    || !isStatusBarBattery(battery)) {
                return result;
            }
            FusionIconView icon = findIcon(battery);
            if (icon == null) {
                return result;
            }
            int heightSpec = (Integer) chain.getArg(1);
            icon.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), heightSpec);
            if (currentPlan.spanFusion
                    && SPAN_MEASURE_LOGGED.compareAndSet(false, true)) {
                log(Log.INFO, "span measure icon=" + icon.getMeasuredHeight()
                        + " battery=" + battery.getMeasuredHeight()
                        + " parentSpec=" + View.MeasureSpec.getSize(heightSpec)
                        + " target=" + dp(battery, currentConfig.statusBarHeight), null);
            }
            int width = icon.getMeasuredWidth() + battery.getPaddingLeft() + battery.getPaddingRight();
            int height = icon.getMeasuredHeight() + battery.getPaddingTop() + battery.getPaddingBottom();
            if (width <= 0 || height <= 0) {
                return result;
            }
            if (overwriteMeasured(battery, width, height)) {
                markReady(battery);
                if (BATTERY_READY.get()) {
                    hideBatteryChildren(battery);
                }
                expandBatteryContainer(battery);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "battery measure hook failed", error);
        }
        return result;
    }

    private static Object batteryLayout(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup battery
                    && isStatusBarBattery(battery)) {
                layoutIcon(battery);
                if (BATTERY_READY.get()) {
                    hideBatteryChildren(battery);
                }
            }
        } catch (Throwable error) {
            log(Log.ERROR, "battery layout hook failed", error);
        }
        return result;
    }

    private static Object batteryFinishInflate(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup battery
                    && isStatusBarBattery(battery)) {
                ensureConfigObserver(battery.getContext());
                ensureIcon(battery);
                applyTint(battery);
                if (BATTERY_READY.get()) {
                    hideBatteryChildren(battery);
                }
            }
        } catch (Throwable error) {
            log(Log.ERROR, "battery inflate hook failed", error);
        }
        return result;
    }

    private static Object batteryAttached(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup battery
                    && isStatusBarBattery(battery)) {
                ensureIcon(battery);
                applyTint(battery);
                if (BATTERY_READY.get()) {
                    hideBatteryChildren(battery);
                    conceal(battery);
                }
            }
        } catch (Throwable error) {
            log(Log.ERROR, "battery attach hook failed", error);
        }
        return result;
    }

    private static Object afterUpdate(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof ViewGroup battery
                    && isStatusBarBattery(battery)) {
                if (BATTERY_READY.get()) {
                    conceal(battery);
                    applyTint(battery);
                }
            }
        } catch (Throwable error) {
            log(Log.ERROR, "battery update hook failed", error);
        }
        return result;
    }

    private static Object afterTint(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (chain.getThisObject() instanceof View battery
                    && isStatusBarBattery(battery)) {
                applyTint(battery);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "battery tint hook failed", error);
        }
        return result;
    }

    private static Object interceptChildVisibility(XposedInterface.Chain chain) throws Throwable {
        View child = chain.getArg(1) instanceof View ? (View) chain.getArg(1) : null;
        if (BATTERY_READY.get() && childOfBattery(child)) {
            hideBatteryChild(child);
            return null;
        }
        return chain.proceed();
    }

    private static boolean signalsSuppressed() {
        return BATTERY_READY.get() && !MEASURE_FAILED.get();
    }

    private static Object containerPass(XposedInterface.Chain chain, boolean measure) throws Throwable {
        if (!(chain.getThisObject() instanceof ViewGroup container)
                || !isStatusIconContainer(container)) {
            return chain.proceed();
        }
        if (Boolean.TRUE.equals(CONTAINER_GUARD.get())) {
            return chain.proceed();
        }
        CONTAINER_GUARD.set(Boolean.TRUE);
        try {
            if (signalsSuppressed()) {
                hideSignalChildren(container);
                if (measure) {
                    addMissingSlots(container);
                }
            }
            Object result = chain.proceed();
            if (signalsSuppressed()) {
                restoreVisibleSlots(container);
                hideSignalChildren(container);
            }
            return result;
        } finally {
            CONTAINER_GUARD.set(Boolean.FALSE);
        }
    }

    private static Object afterSetIgnored(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (signalsSuppressed() && chain.getThisObject() instanceof ViewGroup container
                    && isStatusIconContainer(container)) {
                restoreVisibleSlots(container);
                addMissingSlots(container);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "ignored slot restore failed", error);
        }
        return result;
    }

    private static Object afterViewAdded(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (signalsSuppressed() && chain.getThisObject() instanceof ViewGroup container
                    && isStatusIconContainer(container)) {
                restoreVisibleSlots(container);
                addMissingSlots(container);
                hideSignalChildren(container);
            }
        } catch (Throwable error) {
            log(Log.ERROR, "icon add hook failed", error);
        }
        return result;
    }

    private static void ensureIcon(ViewGroup battery) {
        battery.setBackground(null);
        battery.setForeground(null);
        if (findIcon(battery) != null) {
            return;
        }
        FusionIconView icon = new FusionIconView(battery.getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        icon.setLayoutParams(params);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        battery.addView(icon);
        icon.bringToFront();
    }

    private static void layoutIcon(ViewGroup battery) {
        FusionIconView icon = findIcon(battery);
        if (icon == null) {
            return;
        }
        int width = icon.getMeasuredWidth();
        int height = icon.getMeasuredHeight();
        if (width <= 0 || height <= 0) {
            icon.measure(
                    View.MeasureSpec.makeMeasureSpec(Math.max(battery.getWidth(), 0), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(Math.max(battery.getHeight(), 0), View.MeasureSpec.AT_MOST));
            width = icon.getMeasuredWidth();
            height = icon.getMeasuredHeight();
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        int contentWidth = Math.max(0, battery.getWidth() - battery.getPaddingLeft() - battery.getPaddingRight());
        int contentHeight = Math.max(0, battery.getHeight() - battery.getPaddingTop() - battery.getPaddingBottom());
        int left = battery.getPaddingLeft() + Math.max(0, (contentWidth - width) / 2);
        int top = battery.getPaddingTop() + Math.max(0, (contentHeight - height) / 2);
        if (currentPlan.spanFusion) {
            top = Math.max(0,
                    (dp(battery, currentConfig.statusBarHeight) - height) / 2
                            - battery.getTop());
        }
        icon.layout(left, top, left + width, top + height);
        int offsetX = 0;
        int offsetY = 0;
        if (currentPlan.spanFusion) {
            offsetX = dp(battery, currentConfig.spanOffsetX);
            offsetY = dp(battery, currentConfig.spanOffsetY);
        }
        if (icon.getTranslationX() != offsetX) {
            icon.setTranslationX(offsetX);
        }
        if (icon.getTranslationY() != offsetY) {
            icon.setTranslationY(offsetY);
        }
    }

    private static void expandBatteryContainer(View battery) {
        if (!currentPlan.doubleRow || battery == null) {
            return;
        }
        View current = battery;
        while (current.getParent() instanceof View parent) {
            if (batteryContainerClass != null && batteryContainerClass.isInstance(parent)) {
                int width = parent.getMeasuredWidth();
                int height = Math.max(parent.getMeasuredHeight(), dp(battery, 48));
                if (width > 0 && height > 0) {
                    overwriteMeasured(parent, width, height);
                }
                return;
            }
            current = parent;
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static void conceal(ViewGroup battery) {
        if (!BATTERY_READY.get()) {
            return;
        }
        hideBatteryChildren(battery);
        battery.post(() -> hideBatteryChildren(battery));
    }

    private static void hideBatteryChildren(ViewGroup battery) {
        int count = battery.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = battery.getChildAt(i);
            if (child instanceof FusionIconView) {
                if (child.getVisibility() != View.VISIBLE) {
                    child.setVisibility(View.VISIBLE);
                }
                child.setAlpha(1f);
                continue;
            }
            hideBatteryChild(child);
        }
    }

    private static void hideBatteryChild(View child) {
        if (child == null) {
            return;
        }
        if (child.getVisibility() != View.GONE) {
            child.setVisibility(View.GONE);
        }
        child.setAlpha(0f);
        child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private static void hideSignalChildren(ViewGroup container) {
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (!SlotFilter.shouldHide(slotOf(child))) {
                continue;
            }
            child.setAlpha(0f);
            if (child.getVisibility() != View.GONE) {
                child.setVisibility(View.GONE);
            }
        }
    }

    private static void addMissingSlots(ViewGroup container) {
        List<String> wanted = hiddenSlots(container);
        if (wanted.isEmpty()) {
            return;
        }
        if (SLOTS_LOGGED.compareAndSet(false, true)) {
            log(Log.INFO, "hiding slots " + wanted, null);
        }
        List<?> current = ignoredSlots(container);
        if (current == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String slot : wanted) {
            if (!current.contains(slot)) {
                missing.add(slot);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        try {
            Method method = container.getClass().getMethod("addIgnoredSlots", List.class);
            method.invoke(container, missing);
        } catch (Throwable error) {
            log(Log.ERROR, "addIgnoredSlots failed", error);
        }
    }

    private static List<String> hiddenSlots(ViewGroup container) {
        List<String> slots = new ArrayList<>();
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            String slot = slotOf(container.getChildAt(i));
            if (SlotFilter.shouldHide(slot) && !slots.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static String slotOf(View child) {
        if (child == null) {
            return null;
        }
        Method method = slotMethod(child.getClass());
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(child);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> ignoredSlots(View container) {
        Field field = ignoredSlotField(container.getClass());
        if (field != null) {
            try {
                Object value = field.get(container);
                if (value instanceof List) {
                    return (List<?>) value;
                }
            } catch (Throwable error) {
                log(Log.WARN, "ignored slot field failed", error);
            }
        }
        if (FIELD_LOGGED.compareAndSet(false, true)) {
            log(Log.ERROR, "ignoredSlots field missing; signal slots are only hidden visually", null);
        }
        return null;
    }

    private static void markReady(View battery) {
        if (!BATTERY_READY.compareAndSet(false, true)) {
            return;
        }
        log(Log.INFO, "fused icon sized; original signal and battery icons suppressed", null);
        battery.post(() -> {
            View root = battery;
            while (root.getParent() instanceof View parent) {
                root = parent;
            }
            root.requestLayout();
        });
    }

    private static boolean overwriteMeasured(View view, int width, int height) {
        if (MEASURE_FAILED.get()) {
            return false;
        }
        try {
            if (setMeasuredDimension == null) {
                Method method = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                method.setAccessible(true);
                setMeasuredDimension = method;
            }
            setMeasuredDimension.invoke(view, width, height);
            return true;
        } catch (Throwable error) {
            MEASURE_FAILED.set(true);
            log(Log.ERROR, "setMeasuredDimension failed; original icons kept", error);
            return false;
        }
    }

    private static void applyTint(View battery) {
        if (!(battery instanceof ViewGroup group)) {
            return;
        }
        FusionIconView icon = findIcon(group);
        if (icon == null) {
            return;
        }
        icon.setIconTint(resolveTint(battery));
    }

    private static int resolveTint(View battery) {
        try {
            boolean useTint = booleanField(battery, "mUseTint");
            if (useTint) {
                Object areas = field(battery, "mTintAreas");
                ArrayList<?> list = areas instanceof ArrayList ? (ArrayList<?>) areas : new ArrayList<>();
                int fallback = intField(battery, "mTintColor");
                return dispatcherTint(list, battery, fallback);
            }
            float intensity = floatField(battery, "mDarkIntensity");
            int dark = intField(battery, "mDarkColor");
            int light = intField(battery, "mLightColor");
            int color = intensity > 0f ? dark : light;
            return Color.alpha(color) == 0 ? Color.WHITE : color;
        } catch (Throwable error) {
            if (TINT_LOGGED.compareAndSet(false, true)) {
                log(Log.WARN, "tint fields unavailable; using white", error);
            }
            return Color.WHITE;
        }
    }

    private static int dispatcherTint(ArrayList<?> areas, View view, int fallback) {
        try {
            if (dispatcherTint == null && loader != null) {
                Class<?> type = Class.forName(
                        "com.android.systemui.plugins.DarkIconDispatcher", false, loader);
                for (Method method : type.getMethods()) {
                    if (!"getTint".equals(method.getName()) || method.getParameterCount() != 3) {
                        continue;
                    }
                    if (!Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    method.setAccessible(true);
                    dispatcherTint = method;
                    break;
                }
            }
            if (dispatcherTint == null) {
                return Color.alpha(fallback) == 0 ? Color.WHITE : fallback;
            }
            Object value = dispatcherTint.invoke(null, areas, view, fallback);
            if (value instanceof Integer color) {
                return Color.alpha(color) == 0 ? Color.WHITE : color;
            }
        } catch (Throwable error) {
            if (TINT_LOGGED.compareAndSet(false, true)) {
                log(Log.WARN, "DarkIconDispatcher.getTint failed", error);
            }
        }
        return Color.alpha(fallback) == 0 ? Color.WHITE : fallback;
    }

    private static FusionIconView findIcon(ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child instanceof FusionIconView icon) {
                return icon;
            }
        }
        return null;
    }

    private static boolean childOfBattery(View child) {
        if (child == null || batteryClass == null) {
            return false;
        }
        return child.getParent() instanceof View battery
                && batteryClass.isInstance(battery)
                && isStatusBarBattery(battery);
    }

    private static boolean booleanField(Object target, String name) throws ReflectiveOperationException {
        Object value = field(target, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static int intField(Object target, String name) throws ReflectiveOperationException {
        Object value = field(target, name);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static float floatField(Object target, String name) throws ReflectiveOperationException {
        Object value = field(target, name);
        return value instanceof Float ? (Float) value : 0f;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field found = findField(target.getClass(), name);
        if (found == null) {
            throw new NoSuchFieldException(name);
        }
        return found.get(target);
    }

    private static Field findField(Class<?> type, String name) {
        return RuntimeReflection.field(type, name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] params) {
        return RuntimeReflection.exact(type, name, params, true);
    }

    private static Method slotMethod(Class<?> type) {
        synchronized (SLOT_METHOD_CACHE) {
            if (SLOT_METHOD_CACHE.containsKey(type)) {
                return SLOT_METHOD_CACHE.get(type);
            }
            Method method = null;
            try {
                Method candidate = type.getMethod("getSlot");
                if (candidate.getReturnType() == String.class) {
                    candidate.setAccessible(true);
                    method = candidate;
                }
            } catch (Throwable ignored) {
                // Some SystemUI icon implementations do not expose a slot method.
            }
            SLOT_METHOD_CACHE.put(type, method);
            return method;
        }
    }

    private static Field ignoredSlotField(Class<?> type) {
        synchronized (IGNORED_SLOT_FIELD_CACHE) {
            if (IGNORED_SLOT_FIELD_CACHE.containsKey(type)) {
                return IGNORED_SLOT_FIELD_CACHE.get(type);
            }
            Field field = findField(type, "ignoredSlots");
            if (field == null) {
                field = findField(type, "mIgnoredSlots");
            }
            IGNORED_SLOT_FIELD_CACHE.put(type, field);
            return field;
        }
    }

    private static Method findStaticMethod(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> findClass(String name) {
        return findClass(name, loader);
    }

    private static Class<?> findClass(String name, ClassLoader targetLoader) {
        try {
            return Class.forName(name, false, targetLoader);
        } catch (Throwable error) {
            log(Log.ERROR, "class missing " + name, error);
            return null;
        }
    }

    private static Class<?> findClassQuiet(String name) {
        return findClassQuiet(name, loader);
    }

    private static Class<?> findClassQuiet(String name, ClassLoader targetLoader) {
        try {
            return Class.forName(name, false, targetLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(int priority, String message, Throwable error) {
        if (priority <= Log.INFO && error == null) {
            synchronized (RECENT_DIAGNOSTICS) {
                long now = SystemClock.elapsedRealtime();
                Long previous = RECENT_DIAGNOSTICS.get(message);
                if (previous != null && now - previous < 60_000L) return;
                if (RECENT_DIAGNOSTICS.size() >= 64)
                    RECENT_DIAGNOSTICS.remove(RECENT_DIAGNOSTICS.keySet().iterator().next());
                RECENT_DIAGNOSTICS.put(message, now);
            }
        }
        if (error == null) {
            Log.println(priority, TAG, message);
        } else if (priority >= Log.ERROR) {
            Log.e(TAG, message, error);
        } else {
            Log.w(TAG, message, error);
        }
        if (module == null) {
            return;
        }
        try {
            if (error == null) {
                module.log(priority, TAG, message);
            } else {
                module.log(priority, TAG, message, error);
            }
        } catch (Throwable ignored) {
            // The framework logger must not take down SystemUI.
        }
    }

    /** Package-private bridge for runtime-grid diagnostics emitted from the helper class. */
    static void runtimeGridLog(int priority, String message, Throwable error) {
        log(priority, message, error);
    }
}
