package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves and declares Control Center plugin hooks for one actual plugin ClassLoader. */
final class ControlCenterPluginHookInstaller {
    static final String COMPACT_QS_LIST_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.qs.CompactQSListController";
    private static final String QS_LIST_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.qs.QSListController";
    private static final String QS_RECORD_CLASS =
            "miui.systemui.controlcenter.panel.main.qs.QSRecord";
    private static final String QS_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.qs.QSController";
    private static final String MAIN_PANEL_DISTRIBUTOR_CLASS =
            "miui.systemui.controlcenter.panel.main.MainPanelContentDistributor";
    private static final String MAIN_PANEL_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.MainPanelController";
    private static final String MAIN_PANEL_ADAPTER_CLASS =
            "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter";
    private static final String MEDIA_PLAYER_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.media.MediaPlayerController";
    private static final String BRIGHTNESS_SLIDER_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.brightness.BrightnessSliderController";
    private static final String VOLUME_SLIDER_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.volume.VolumeSliderController";
    private static final String DEVICE_CENTER_ENTRY_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryController";
    private static final String DEVICE_CONTROLS_ENTRY_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.devicecontrol.DeviceControlsEntryController";
    static final String COMPACT_QS_CARD_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.panel.main.qs.CompactQSCardController";
    private static final String WINDOW_VIEW_CONTROLLER_CLASS =
            "miui.systemui.controlcenter.windowview.ControlCenterWindowViewController";
    private static final String TILE_ICON_VIEW_CLASS =
            "miui.systemui.controlcenter.qs.tileview.QSTileItemIconView";
    private static final String TILE_ITEM_VIEW_CLASS =
            "miui.systemui.controlcenter.qs.tileview.QSTileItemView";
    private static final String CARD_ITEM_VIEW_CLASS =
            "miui.systemui.controlcenter.qs.tileview.QSCardItemView";

    enum Group {
        COMPACT,
        LIST,
        SHAPE,
        BIND,
        DISTRIBUTOR,
        COMPACT_SPECS,
        COMPACT_PAIR,
        VISUAL,
        GRID,
        GRID_CONTENT,
        ADAPTER_NOTIFY,
        COMPONENT_BIND,
        COMPONENT_SHAPE,
        COMPONENT_LAYOUT
    }

    enum Callback {
        COMPACT_TILES,
        DISTRIBUTE_TILES,
        TILE_SPAN,
        TILE_BIND,
        DISTRIBUTOR_NOTIFY,
        DISTRIBUTE_PANELS,
        SEPARATED_PANELS,
        COMPACT_SPECS,
        COMPACT_CARD_SPECS,
        COMPACT_CARD_CREATED,
        COMPACT_CARD_DESTROYED,
        WINDOW_BLUR_RATIO,
        ICON_SIZE,
        BACKGROUND,
        CORNER_RADIUS,
        CONTAINER_HEIGHT,
        SET_SPAN,
        DISTRIBUTE_CONTENT,
        GRID_REFRESH,
        ADAPTER_NOTIFY,
        COMPONENT_BIND,
        COMPONENT_SPAN,
        COMPONENT_LAYOUT_BIND
    }

    interface ClassResolver {
        Class<?> resolve(String className, ClassLoader targetLoader);
    }

    /** Returns true when a matching hook is installed or was already installed. */
    interface Registrar {
        boolean install(String id, Method method, XposedInterface.Hooker hooker);
    }

    interface Hookers {
        XposedInterface.Hooker get(Callback callback);
    }

    private enum Selector { PARAMETER_COUNT, EXACT_PARAMETERS }

    private static final class Spec {
        final Group group;
        final String className;
        final String methodName;
        final Selector selector;
        final int parameterCount;
        final Class<?> parameterType;
        final Class<?> returnType;
        final Class<?>[] parameterTypes;
        final Callback callback;

        private Spec(Group group, String className, String methodName, Selector selector,
                int parameterCount, Class<?> parameterType, Class<?> returnType,
                Class<?>[] parameterTypes, Callback callback) {
            this.group = group;
            this.className = className;
            this.methodName = methodName;
            this.selector = selector;
            this.parameterCount = parameterCount;
            this.parameterType = parameterType;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.callback = callback;
        }

        static Spec count(Group group, String className, String methodName, int parameterCount,
                Class<?> parameterType, Class<?> returnType, Callback callback) {
            return new Spec(group, className, methodName, Selector.PARAMETER_COUNT, parameterCount,
                    parameterType, returnType, null, callback);
        }

        static Spec exact(Group group, String className, String methodName,
                Class<?>[] parameterTypes, Class<?> returnType, Callback callback) {
            return new Spec(group, className, methodName, Selector.EXACT_PARAMETERS,
                    parameterTypes.length, null, returnType, parameterTypes.clone(), callback);
        }

        String hookId() {
            return className.substring(className.lastIndexOf('.') + 1) + "#" + methodName
                    + "/" + parameterCount;
        }

        boolean matches(Method method) {
            return selector == Selector.PARAMETER_COUNT
                    ? ControlCenterHookPolicy.matchesMethod(method, methodName, parameterCount,
                            parameterType, returnType)
                    : ControlCenterHookPolicy.matchesMethod(method, methodName,
                            parameterTypes, returnType);
        }
    }

    static final class Result {
        final boolean layoutAvailable;
        final List<String> missingClasses;
        final List<String> missingMethods;
        private final Map<Group, Integer> counts;

        Result(Map<Group, Integer> counts, List<String> missingClasses,
                List<String> missingMethods) {
            this.counts = Collections.unmodifiableMap(new EnumMap<>(counts));
            this.layoutAvailable = count(Group.GRID_CONTENT) > 0
                    && count(Group.COMPONENT_BIND) > 0
                    && count(Group.LIST) > 0 && count(Group.BIND) > 0;
            this.missingClasses = Collections.unmodifiableList(new ArrayList<>(missingClasses));
            this.missingMethods = Collections.unmodifiableList(new ArrayList<>(missingMethods));
        }

        int count(Group group) { return counts.getOrDefault(group, 0); }
    }

    private final ClassResolver classResolver;
    private final Registrar registrar;
    private final Hookers hookers;
    private final Runnable beforeVisualHooks;

    ControlCenterPluginHookInstaller(ClassResolver classResolver, Registrar registrar,
            Hookers hookers, Runnable beforeVisualHooks) {
        if (classResolver == null || registrar == null || hookers == null
                || beforeVisualHooks == null)
            throw new IllegalArgumentException("missing_control_center_plugin_installer_dependency");
        this.classResolver = classResolver;
        this.registrar = registrar;
        this.hookers = hookers;
        this.beforeVisualHooks = beforeVisualHooks;
    }

    Result install(ClassLoader targetLoader) {
        if (targetLoader == null) return emptyResult();
        Map<Group, Integer> counts = new EnumMap<>(Group.class);
        List<String> missingClasses = new ArrayList<>();
        List<String> missingMethods = new ArrayList<>();
        boolean visualBoundaryPassed = false;
        for (Spec spec : specs()) {
            if (!visualBoundaryPassed && spec.group == Group.VISUAL) {
                beforeVisualHooks.run();
                visualBoundaryPassed = true;
            }
            Class<?> type = classResolver.resolve(spec.className, targetLoader);
            if (type == null) {
                missingClasses.add(spec.className);
                continue;
            }
            boolean matched = false;
            for (Method method : type.getDeclaredMethods()) {
                if (!spec.matches(method)) continue;
                matched = true;
                method.setAccessible(true);
                if (registrar.install(spec.hookId(), method, hookers.get(spec.callback))) {
                    counts.merge(spec.group, 1, Integer::sum);
                }
            }
            if (!matched) missingMethods.add(spec.className + "#" + spec.methodName);
        }
        return new Result(counts, missingClasses, missingMethods);
    }

    private static Result emptyResult() {
        return new Result(new EnumMap<>(Group.class), List.of(), List.of());
    }

    private static List<Spec> specs() {
        List<Spec> specs = new ArrayList<>();
        specs.add(Spec.count(Group.COMPACT, COMPACT_QS_LIST_CONTROLLER_CLASS,
                "getPrepareShowList", 0, null, null, Callback.COMPACT_TILES));
        specs.add(Spec.count(Group.LIST, QS_LIST_CONTROLLER_CLASS,
                "distributeTiles", 1, boolean.class, null, Callback.DISTRIBUTE_TILES));
        specs.add(Spec.count(Group.SHAPE, QS_RECORD_CLASS,
                "getSpanSize", 0, null, int.class, Callback.TILE_SPAN));
        specs.add(Spec.count(Group.BIND, QS_LIST_CONTROLLER_CLASS,
                "onBindViewHolder", 2, null, null, Callback.TILE_BIND));
        specs.add(Spec.count(Group.DISTRIBUTOR, MAIN_PANEL_DISTRIBUTOR_CLASS,
                "notifyChanged", 1, null, null, Callback.DISTRIBUTOR_NOTIFY));
        specs.add(Spec.count(Group.DISTRIBUTOR, MAIN_PANEL_DISTRIBUTOR_CLASS,
                "distributePanels", 1, boolean.class, null, Callback.DISTRIBUTE_PANELS));
        specs.add(Spec.count(Group.DISTRIBUTOR, MAIN_PANEL_CONTROLLER_CLASS,
                "setUseSeparatedPanels", 1, Boolean.class, null, Callback.SEPARATED_PANELS));
        specs.add(Spec.count(Group.COMPACT_SPECS, QS_CONTROLLER_CLASS,
                "getCompactTileSpecs", 0, null, null, Callback.COMPACT_SPECS));
        specs.add(Spec.count(Group.COMPACT_PAIR, QS_CONTROLLER_CLASS,
                "getCompactCardStyleTileSpecs", 0, null, List.class,
                Callback.COMPACT_CARD_SPECS));
        specs.add(Spec.count(Group.COMPACT_PAIR, COMPACT_QS_CARD_CONTROLLER_CLASS,
                "onCreate", 0, null, void.class, Callback.COMPACT_CARD_CREATED));
        specs.add(Spec.count(Group.COMPACT_PAIR, COMPACT_QS_CARD_CONTROLLER_CLASS,
                "onDestroy", 0, null, void.class, Callback.COMPACT_CARD_DESTROYED));

        specs.add(Spec.count(Group.VISUAL, WINDOW_VIEW_CONTROLLER_CLASS,
                "setBlurRatio", 1, float.class, void.class, Callback.WINDOW_BLUR_RATIO));
        specs.add(Spec.count(Group.VISUAL, TILE_ICON_VIEW_CLASS,
                "updateIconSize", 0, null, void.class, Callback.ICON_SIZE));
        specs.add(Spec.count(Group.VISUAL, TILE_ICON_VIEW_CLASS,
                "setDisabledBg", 1, android.graphics.drawable.Drawable.class, null,
                Callback.BACKGROUND));
        specs.add(Spec.count(Group.VISUAL, TILE_ICON_VIEW_CLASS,
                "setEnabledBg", 1, android.graphics.drawable.Drawable.class, null,
                Callback.BACKGROUND));
        specs.add(Spec.count(Group.VISUAL, TILE_ICON_VIEW_CLASS,
                "getCornerRadius", 0, null, float.class, Callback.CORNER_RADIUS));
        specs.add(Spec.count(Group.VISUAL, TILE_ITEM_VIEW_CLASS,
                "updateContainerHeight", 0, null, void.class, Callback.CONTAINER_HEIGHT));
        specs.add(Spec.count(Group.VISUAL, CARD_ITEM_VIEW_CLASS,
                "setDisabledBg", 1, android.graphics.drawable.Drawable.class, null,
                Callback.BACKGROUND));
        specs.add(Spec.count(Group.VISUAL, CARD_ITEM_VIEW_CLASS,
                "setEnabledBg", 1, android.graphics.drawable.Drawable.class, null,
                Callback.BACKGROUND));
        specs.add(Spec.count(Group.VISUAL, CARD_ITEM_VIEW_CLASS,
                "getCornerRadius", 0, null, float.class, Callback.CORNER_RADIUS));

        specs.add(Spec.count(Group.GRID, MAIN_PANEL_ADAPTER_CLASS,
                "setSpan", 1, int.class, null, Callback.SET_SPAN));
        specs.add(Spec.count(Group.GRID_CONTENT, MAIN_PANEL_ADAPTER_CLASS,
                "distributeContent", 1, boolean.class, null, Callback.DISTRIBUTE_CONTENT));
        specs.add(Spec.count(Group.GRID, MAIN_PANEL_ADAPTER_CLASS,
                "updateSpanCount", 0, null, null, Callback.GRID_REFRESH));
        specs.add(Spec.count(Group.GRID, MAIN_PANEL_ADAPTER_CLASS,
                "onConfigurationChanged", 1, int.class, null, Callback.GRID_REFRESH));
        specs.add(Spec.exact(Group.ADAPTER_NOTIFY, MAIN_PANEL_ADAPTER_CLASS,
                "notifyChanged", new Class<?>[] {boolean.class, boolean.class}, null,
                Callback.ADAPTER_NOTIFY));
        specs.add(Spec.count(Group.COMPONENT_BIND, MAIN_PANEL_ADAPTER_CLASS,
                "onBindViewHolder", 2, null, null, Callback.COMPONENT_BIND));
        for (String componentClass : new String[] {MEDIA_PLAYER_CONTROLLER_CLASS,
                BRIGHTNESS_SLIDER_CONTROLLER_CLASS, VOLUME_SLIDER_CONTROLLER_CLASS,
                DEVICE_CENTER_ENTRY_CONTROLLER_CLASS, DEVICE_CONTROLS_ENTRY_CONTROLLER_CLASS,
                COMPACT_QS_CARD_CONTROLLER_CLASS}) {
            specs.add(Spec.count(Group.COMPONENT_SHAPE, componentClass,
                    "getSpanSize", 0, null, int.class, Callback.COMPONENT_SPAN));
            specs.add(Spec.count(Group.COMPONENT_LAYOUT, componentClass,
                    "onBindViewHolder", 0, null, null, Callback.COMPONENT_LAYOUT_BIND));
        }
        return specs;
    }

}
