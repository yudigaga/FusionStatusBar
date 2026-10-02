package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves and installs notification-center hooks for one SystemUI ClassLoader. */
final class NotificationHookInstaller {
    static final String HEADER_CLASS =
            "com.android.systemui.qs.MiuiNotificationHeaderView";
    static final String EXPAND_CLASS =
            "com.android.systemui.controlcenter.shade.NotificationHeaderExpandController";
    static final String EXPAND_CALLBACK_CLASS = EXPAND_CLASS + "$notificationCallback$1";
    static final String STACK_CLASS =
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    static final String NETWORK_SPEED_CLASS =
            "com.android.systemui.statusbar.views.NetworkSpeedView";

    enum Callback {
        HEADER,
        EXPAND_SIZE,
        EXPANSION_CHANGED,
        TOP_PADDING,
        STACK_LAYOUT,
        SPEED_VISIBILITY
    }

    interface ClassResolver {
        Class<?> resolve(String name, ClassLoader targetLoader);
    }

    interface MemberResolver {
        Field field(Class<?> type, String name);
        Method exact(Class<?> type, String name, Class<?>[] parameters, boolean searchSuper);
        Method[] declaredMethods(Class<?> type);
    }

    interface Registrar {
        boolean install(String id, Method method, XposedInterface.Hooker hooker);
    }

    interface Hookers {
        XposedInterface.Hooker get(Callback callback);
    }

    static final class Result {
        final Class<?> headerClass;
        final Field bigTimeSizeField;
        final Field expandContextField;
        final Field expandProgressField;
        final Field stackStateField;
        final Field stackAmbientStateField;
        final Field ambientTopPaddingField;
        final Field qsExpansionFractionField;
        final Field stackInjectorField;
        final Field controllerInjectorField;
        final Method useControlCenterMethod;
        final Method topPaddingMethod;
        final List<String> missingClasses;
        final List<String> missingMembers;
        final int installed;

        Result(Class<?> headerClass, Field bigTimeSizeField, Field expandContextField,
                Field expandProgressField, Field stackStateField, Field stackAmbientStateField,
                Field ambientTopPaddingField, Field qsExpansionFractionField,
                Field stackInjectorField, Field controllerInjectorField,
                Method useControlCenterMethod, Method topPaddingMethod,
                List<String> missingClasses, List<String> missingMembers, int installed) {
            this.headerClass = headerClass;
            this.bigTimeSizeField = bigTimeSizeField;
            this.expandContextField = expandContextField;
            this.expandProgressField = expandProgressField;
            this.stackStateField = stackStateField;
            this.stackAmbientStateField = stackAmbientStateField;
            this.ambientTopPaddingField = ambientTopPaddingField;
            this.qsExpansionFractionField = qsExpansionFractionField;
            this.stackInjectorField = stackInjectorField;
            this.controllerInjectorField = controllerInjectorField;
            this.useControlCenterMethod = useControlCenterMethod;
            this.topPaddingMethod = topPaddingMethod;
            this.missingClasses = Collections.unmodifiableList(new ArrayList<>(missingClasses));
            this.missingMembers = Collections.unmodifiableList(new ArrayList<>(missingMembers));
            this.installed = installed;
        }
    }

    private final ClassResolver classes;
    private final MemberResolver members;
    private final Registrar registrar;
    private final Hookers hookers;

    NotificationHookInstaller(ClassResolver classes, MemberResolver members,
            Registrar registrar, Hookers hookers) {
        if (classes == null || members == null || registrar == null || hookers == null)
            throw new IllegalArgumentException("missing_notification_installer_dependency");
        this.classes = classes;
        this.members = members;
        this.registrar = registrar;
        this.hookers = hookers;
    }

    Result install(ClassLoader targetLoader) {
        List<String> missingClasses = new ArrayList<>();
        List<String> missingMembers = new ArrayList<>();
        int installed = 0;

        Class<?> header = resolve(HEADER_CLASS, targetLoader, missingClasses);
        if (header != null) {
            int found = 0;
            for (String name : new String[] {"onFinishInflate", "onAttachedToWindow",
                    "updateHeaderResources", "updateLayout", "updateFlipResources",
                    "updateResources$8"}) {
                int count = installDeclared(header, name, Callback.HEADER);
                found += count;
                if (count == 0 && !"updateResources".equals(name)) {
                    missingMembers.add(header.getSimpleName() + "#" + name);
                }
            }
            installed += found;
        }

        Class<?> expand = resolve(EXPAND_CLASS, targetLoader, missingClasses);
        Field bigTimeSize = null;
        Field expandContext = null;
        Field expandProgress = null;
        if (expand != null) {
            bigTimeSize = members.field(expand, "bigTimeSize");
            expandContext = members.field(expand, "context");
            expandProgress = members.field(expand, "progress");
            Method updateTranslation = members.exact(expand, "updateTranslationY", new Class<?>[0], false);
            if (bigTimeSize != null && expandContext != null && updateTranslation != null) {
                if (register("NotificationHeaderExpandController#updateTranslationY",
                        updateTranslation, Callback.EXPAND_SIZE)) installed++;
            } else {
                missingMembers.add(EXPAND_CLASS + "#updateTranslationY");
            }
        }

        Class<?> expandCallback = resolve(EXPAND_CALLBACK_CLASS, targetLoader, missingClasses);
        if (expandCallback != null) {
            Method changed = members.exact(expandCallback, "onExpansionChanged",
                    new Class<?>[] {float.class}, false);
            if (changed != null) {
                if (register("NotificationHeaderExpandController#onExpansionChanged",
                        changed, Callback.EXPANSION_CHANGED)) installed++;
            } else {
                missingMembers.add(EXPAND_CALLBACK_CLASS + "#onExpansionChanged");
            }
        }

        Class<?> stack = resolve(STACK_CLASS, targetLoader, missingClasses);
        Field stackState = null;
        Field stackAmbient = null;
        Field ambientPadding = null;
        Field qsFraction = null;
        Field stackInjector = null;
        Field controllerInjector = null;
        Method useControlCenter = null;
        Method topPadding = null;
        if (stack != null) {
            stackState = members.field(stack, "mStatusBarState");
            stackAmbient = members.field(stack, "mAmbientState");
            qsFraction = members.field(stack, "mQsExpansionFraction");
            if (stackAmbient != null) ambientPadding = members.field(stackAmbient.getType(), "mTopPadding");
            stackInjector = members.field(stack, "mNsslInjector");
            if (stackInjector != null) {
                controllerInjector = members.field(stackInjector.getType(), "nsslControllerInjector");
                if (controllerInjector != null) {
                    useControlCenter = members.exact(controllerInjector.getType(),
                            "isUseControlCenter", new Class<?>[0], true);
                }
            }
            topPadding = members.exact(stack, "updateTopPadding",
                    new Class<?>[] {float.class, boolean.class}, false);
            if (topPadding != null && register("NotificationStackScrollLayout#updateTopPadding",
                    topPadding, Callback.TOP_PADDING)) installed++;
            else missingMembers.add(STACK_CLASS + "#updateTopPadding");
            Method layout = members.exact(stack, "onLayout",
                    new Class<?>[] {boolean.class, int.class, int.class, int.class, int.class}, true);
            if (layout != null && register("NotificationStackScrollLayout#onLayout",
                    layout, Callback.STACK_LAYOUT)) installed++;
            else missingMembers.add(STACK_CLASS + "#onLayout");
        }

        Class<?> speed = resolve(NETWORK_SPEED_CLASS, targetLoader, missingClasses);
        if (speed != null) {
            Method visibility = members.exact(speed, "setVisibilityByController",
                    new Class<?>[] {boolean.class}, false);
            if (visibility != null && register("NetworkSpeedView#setVisibilityByController",
                    visibility, Callback.SPEED_VISIBILITY)) installed++;
            else missingMembers.add(NETWORK_SPEED_CLASS + "#setVisibilityByController");
        }
        return new Result(header, bigTimeSize, expandContext, expandProgress, stackState,
                stackAmbient, ambientPadding, qsFraction, stackInjector, controllerInjector,
                useControlCenter, topPadding, missingClasses, missingMembers, installed);
    }

    private Class<?> resolve(String name, ClassLoader targetLoader, List<String> missingClasses) {
        Class<?> type = classes.resolve(name, targetLoader);
        if (type == null) missingClasses.add(name);
        return type;
    }

    private int installDeclared(Class<?> type, String methodName, Callback callback) {
        int count = 0;
        try {
            for (Method method : members.declaredMethods(type)) {
                if (!methodName.equals(method.getName())) continue;
                method.setAccessible(true);
                if (register(type.getSimpleName() + "#" + methodName, method, callback)) count++;
            }
        } catch (Throwable ignored) {
            return count;
        }
        return count;
    }

    private boolean register(String id, Method method, Callback callback) {
        return registrar.install(id, method, hookers.get(callback));
    }
}
