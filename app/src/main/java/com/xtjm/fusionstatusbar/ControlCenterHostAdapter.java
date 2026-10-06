package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reads the MIUI panel shape needed by the runtime grid and preserves its native fallback. */
final class ControlCenterHostAdapter {
    static final String QS_LIST =
            "miui.systemui.controlcenter.panel.main.qs.QSListController";
    static final String QS_CARDS =
            "miui.systemui.controlcenter.panel.main.qs.QSCardsController";
    static final String COMPACT_CARD =
            "miui.systemui.controlcenter.panel.main.qs.CompactQSCardController";
    static final String COMPACT_LIST =
            "miui.systemui.controlcenter.panel.main.qs.CompactQSListController";
    private static final String LIST_ITEM =
            "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem";

    private ControlCenterHostAdapter() { }

    /** Compatibility reflection seam used by both the host adapter and legacy grid callers. */
    static Object readField(Object target, String name) {
        if (target == null) return null;
        try {
            Field field = RuntimeReflection.field(target.getClass(), name);
            return field == null ? null : field.get(target);
        } catch (IllegalAccessException | RuntimeException error) {
            return null;
        }
    }

    static Method method(Class<?> type, String name, int count) {
        return RuntimeReflection.withCount(type, name, count);
    }

    static Object call(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        if (target == null) throw new NoSuchMethodException(name + " on null");
        Method candidate = RuntimeReflection.compatible(target.getClass(), name, args);
        if (candidate != null) {
            try {
                return candidate.invoke(target, args);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof ReflectiveOperationException reflection) throw reflection;
                throw new IllegalStateException(cause);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + '#' + name);
    }

    static Object callOptional(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        try {
            return call(target, name, args);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    static State readState(Object adapter) throws ReflectiveOperationException {
        Object provider = readField(adapter, "mainPanelController");
        Object controller = provider == null ? null
                : call(provider, "get");
        Object mode = controller == null ? null
                : call(controller, "getMode");
        Object style = controller == null ? null
                : call(controller, "getStyle");
        return new State(adapter, controller, mode, style);
    }

    static Panel capture(State state, ClassLoader pluginLoader) throws Unavailable {
        Object value = readField(state.adapter, "contentMap");
        if (!(value instanceof LinkedHashMap<?, ?> nativeMap)) throw new Unavailable(null, null);
        LinkedHashMap<Object, List<Object>> original;
        try {
            original = copyContent(nativeMap);
        } catch (RuntimeException error) {
            throw new Unavailable(null, error);
        }

        try {
            Object listOwner = readField(state.adapter, "qsListController");
            if (listOwner == null || !QS_LIST.equals(listOwner.getClass().getName())) {
                throw new IllegalStateException("Native QS list controller unavailable");
            }
            Class<?> itemInterface = Class.forName(LIST_ITEM, false, pluginLoader);
            if (!itemInterface.isInterface()
                    || method(listOwner.getClass(), "createViewHolder", 2) == null
                    || method(listOwner.getClass(), "onBindViewHolder", 2) == null) {
                throw new IllegalStateException("Native QS list binding methods unavailable");
            }
            Object factory = readField(listOwner, "recordFactory");
            if (factory == null
                    || method(factory.getClass(), "create", 2) == null) {
                throw new IllegalStateException("Native QS record factory unavailable");
            }

            boolean horizontal = "HORIZONTAL".equals(String.valueOf(state.style));
            boolean compact = "COMPACT".equals(String.valueOf(state.style));
            if (!original.containsKey(listOwner) && !horizontal
                    && !(compact && hasOwner(original, COMPACT_CARD))) {
                throw new IllegalStateException("Native QS list is not in the current panel");
            }
            LinkedHashMap<Object, List<Object>> sources = horizontal
                    ? horizontalSources(state.controller, original) : original;
            return new Panel(state, nativeMap, original, listOwner, factory, itemInterface,
                    horizontal, compact, sources);
        } catch (Throwable error) {
            if (error instanceof Unavailable unavailable) throw unavailable;
            throw new Unavailable(original, error);
        }
    }

    private static LinkedHashMap<Object, List<Object>> copyContent(Map<?, ?> source) {
        LinkedHashMap<Object, List<Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) {
                throw new IllegalStateException("Unexpected panel content");
            }
            copy.put(entry.getKey(), new ArrayList<>(list));
        }
        return copy;
    }

    private static boolean hasOwner(Map<?, ?> content, String className) {
        for (Object owner : content.keySet()) {
            if (owner != null && className.equals(owner.getClass().getName())) return true;
        }
        return false;
    }

    private static Object providerValue(Object value) throws ReflectiveOperationException {
        Object provided = value == null ? null : callOptional(value, "get");
        return provided == null ? value : provided;
    }

    private static LinkedHashMap<Object, List<Object>> horizontalSources(Object controller,
            LinkedHashMap<Object, List<Object>> current) throws ReflectiveOperationException {
        LinkedHashMap<Object, List<Object>> sources = new LinkedHashMap<>();
        Object distributor = providerValue(readField(controller, "distributor"));
        // Native inventories are stable even after the opposite adapter installs its proxy.
        for (String field : new String[] {"leftPanelContent", "rightPanelContent"}) {
            Object owners = readField(distributor, field);
            if (!(owners instanceof Iterable<?> list)) continue;
            for (Object owner : list) {
                Object records = owner == null ? null
                        : callOptional(owner, "getListItems");
                if (records instanceof List<?> items) sources.put(owner, new ArrayList<>(items));
            }
        }
        for (Map.Entry<Object, List<Object>> entry : current.entrySet()) {
            sources.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return sources;
    }

    static String componentSpec(Object item) throws ReflectiveOperationException {
        try {
            Object type = call(item, "getType");
            if (type instanceof Number) {
                String spec = ControlCenterComponentSpec.fromType(((Number) type).intValue());
                if (ControlCenterComponentSpec.isSpecial(spec)) return spec.toLowerCase(Locale.ROOT);
            }
        } catch (NoSuchMethodException ignored) {
            // Some plugin revisions expose the component type only in the controller name.
        }
        String spec = ControlCenterComponentSpec.fromControllerName(item.getClass().getName());
        return ControlCenterComponentSpec.isSpecial(spec)
                ? spec.toLowerCase(Locale.ROOT) : "";
    }

    static final class State {
        final Object adapter;
        final Object controller;
        final Object mode;
        final Object style;

        State(Object adapter, Object controller, Object mode, Object style) {
            this.adapter = adapter;
            this.controller = controller;
            this.mode = mode;
            this.style = style;
        }
    }

    static final class Panel {
        final State state;
        final LinkedHashMap<?, ?> nativeMap;
        final LinkedHashMap<Object, List<Object>> original;
        final Object listOwner;
        final Object factory;
        final Class<?> itemInterface;
        final Object cardsOwner;
        final boolean horizontal;
        final boolean compact;
        final LinkedHashMap<Object, List<Object>> sources;

        Panel(State state, LinkedHashMap<?, ?> nativeMap,
                LinkedHashMap<Object, List<Object>> original, Object listOwner, Object factory,
                Class<?> itemInterface, boolean horizontal, boolean compact,
                LinkedHashMap<Object, List<Object>> sources) throws ReflectiveOperationException {
            this.state = state;
            this.nativeMap = nativeMap;
            this.original = original;
            this.listOwner = listOwner;
            this.factory = factory;
            this.itemInterface = itemInterface;
            Object cardsProvider = readField(listOwner, "qsCardsController");
            Object cardsController = cardsProvider;
            if (cardsProvider != null) {
                try {
                    Object candidate = call(cardsProvider, "get");
                    if (candidate != null && method(candidate.getClass(), "createViewHolder", 2) != null)
                        cardsController = candidate;
                } catch (ReflectiveOperationException ignored) {
                    // Older plugin revisions expose the controller directly.
                }
            }
            this.cardsOwner = cardsController != null
                    && method(cardsController.getClass(), "createViewHolder", 2) != null
                    ? cardsController : null;
            this.horizontal = horizontal;
            this.compact = compact;
            this.sources = sources;
        }

        void project(Object gridItem, Set<Object> relocatedComponents) {
            LinkedHashMap<Object, List<Object>> ordered = new LinkedHashMap<>();
            boolean inserted = false;
            for (Map.Entry<Object, List<Object>> entry : original.entrySet()) {
                Object owner = entry.getKey();
                String name = owner.getClass().getName();
                if (QS_LIST.equals(name) || QS_CARDS.equals(name)
                        || COMPACT_CARD.equals(name) || COMPACT_LIST.equals(name)) {
                    if (!inserted) {
                        ordered.put(listOwner, Collections.singletonList(gridItem));
                        inserted = true;
                    }
                    if (owner != listOwner) ordered.put(owner, Collections.emptyList());
                } else {
                    ArrayList<Object> retained = new ArrayList<>();
                    for (Object contentItem : entry.getValue()) {
                        if (!relocatedComponents.contains(contentItem)) retained.add(contentItem);
                    }
                    if (!inserted && retained.size() != entry.getValue().size()) {
                        ordered.put(listOwner, Collections.singletonList(gridItem));
                        inserted = true;
                    }
                    ordered.put(owner, retained);
                }
            }
            if (!inserted) throw new IllegalStateException("QS list owner disappeared");
            @SuppressWarnings({"rawtypes", "unchecked"}) Map raw = nativeMap;
            raw.clear();
            raw.putAll(ordered);
        }

        Object createRecord(Object tile, boolean card) throws ReflectiveOperationException {
            Object record = call(factory, "create", tile, card);
            if (record == null || !"QSRecord".equals(record.getClass().getSimpleName())) {
                throw new IllegalStateException("QSRecord factory returned no record");
            }
            call(record, "setAdded", true);
            return record;
        }

        boolean hasCardsOwner() { return cardsOwner != null; }

        HolderBinding bindTile(ViewGroup parent, Object record, boolean card)
                throws ReflectiveOperationException {
            Object owner = card && cardsOwner != null ? cardsOwner : listOwner;
            Object holder = call(owner, "createViewHolder", parent, call(record, "getType"));
            Object value = readField(holder, "itemView");
            if (!(value instanceof View view)) throw new IllegalStateException("Native tile view missing");
            if (view.getParent() instanceof ViewGroup parentView) parentView.removeView(view);
            call(holder, "init");
            call(holder, "setOwner", owner);
            call(holder, "setItem$miui_controlcenter_release", record);
            call(record, "setHolder", holder);
            if (owner == listOwner) call(listOwner, "onBindViewHolder", holder, record);
            else call(record, "onBindViewHolder");
            return new HolderBinding(owner, record, holder, view);
        }

        HolderBinding bindComponent(ViewGroup parent, Object owner, Object item)
                throws ReflectiveOperationException {
            Object existingHolder = callOptional(item, "getHolder");
            if (existingHolder != null) callOptional(item, "onUnbindViewHolder");
            Object type = call(item, "getType");
            if (!(type instanceof Number)) throw new IllegalStateException("Component type unavailable");
            Object holder = call(owner, "createViewHolder", parent, ((Number) type).intValue());
            if (holder == null) throw new IllegalStateException("Component owner returned no holder");
            Object value = readField(holder, "itemView");
            if (!(value instanceof View view)) throw new IllegalStateException("Native component view missing");
            if (view.getParent() instanceof ViewGroup parentView) parentView.removeView(view);
            call(holder, "init");
            call(holder, "setOwner", owner);
            call(holder, "setItem$miui_controlcenter_release", item);
            call(item, "setHolder", holder);
            callOptional(item, "onBindViewHolder");
            return new HolderBinding(owner, item, holder, view);
        }

        void setRecordListening(Object record, boolean listening)
                throws ReflectiveOperationException {
            call(record, "setListening", listening);
        }

        void removeRecordCallback(Object record) throws ReflectiveOperationException {
            call(record, "removeCallback");
        }

        RestoreResult restoreSnapshot(LinkedHashMap<Object, List<Object>> snapshot,
                boolean notify) {
            Map<?, ?> target = contentMap();
            if (target == null || snapshot == null) return RestoreResult.MAP_UNAVAILABLE;
            replace(target, snapshot);
            return notifyAdapter(notify);
        }

        RestoreResult restoreProxy(Object gridItem, boolean notify) {
            Map<?, ?> target = contentMap();
            if (target == null) return RestoreResult.MAP_UNAVAILABLE;
            boolean active = false;
            for (Object items : target.values()) {
                if (items instanceof List<?> list && list.contains(gridItem)) {
                    active = true;
                    break;
                }
            }
            if (!active && !target.isEmpty()) return RestoreResult.SUPERSEDED;
            replace(target, original);
            return notifyAdapter(notify);
        }

        private Map<?, ?> contentMap() {
            Object value = readField(state.adapter, "contentMap");
            return value instanceof Map<?, ?> map ? map : null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void replace(Map<?, ?> target, LinkedHashMap<Object, List<Object>> snapshot) {
            Map raw = target;
            raw.clear();
            raw.putAll(snapshot);
        }

        private RestoreResult notifyAdapter(boolean notify) {
            if (!notify) return RestoreResult.RESTORED;
            try {
                call(state.adapter, "notifyDataSetChanged");
                return RestoreResult.RESTORED;
            } catch (ReflectiveOperationException error) {
                return RestoreResult.RESTORED_WITHOUT_NOTIFY;
            }
        }

        boolean isRightAdapter() throws ReflectiveOperationException {
            Object distributor = providerValue(
                    readField(state.controller, "distributor"));
            if (distributor != null) {
                if (callOptional(distributor, "getRightAdapter")
                        == state.adapter) return true;
                if (callOptional(distributor, "getLeftAdapter")
                        == state.adapter) return false;
            }
            return !original.containsKey(listOwner) && hasOwner(original, QS_CARDS);
        }

        Set<String> rightTileSpecs() throws ReflectiveOperationException {
            Set<String> specs = new java.util.LinkedHashSet<>();
            specs.add("wifi");
            specs.add("cell");
            for (Map.Entry<Object, List<Object>> entry : sources.entrySet()) {
                if (entry.getKey() == null
                        || !QS_CARDS.equals(entry.getKey().getClass().getName())) continue;
                for (Object record : entry.getValue()) {
                    Object value = callOptional(record, "getSpec");
                    if (value instanceof String spec) {
                        specs.add(ControlCenterConfig.canonicalSpec(spec.toLowerCase(Locale.ROOT)));
                    }
                }
            }
            return specs;
        }

        Map<String, Boolean> componentSides() throws ReflectiveOperationException {
            Map<String, Boolean> sides = new LinkedHashMap<>();
            for (Map.Entry<Object, List<Object>> entry : sources.entrySet()) {
                Object side = callOptional(entry.getKey(), "getRightOrLeft");
                if (!(side instanceof Boolean right)) continue;
                for (Object item : entry.getValue()) {
                    if (item == null || !itemInterface.isInstance(item)) continue;
                    String spec = componentSpec(item);
                    if (!spec.isEmpty()) sides.put(spec, right);
                }
            }
            return sides;
        }
    }

    static final class HolderBinding {
        final Object owner;
        final Object record;
        final Object holder;
        final View view;

        private HolderBinding(Object owner, Object record, Object holder, View view) {
            this.owner = owner;
            this.record = record;
            this.holder = holder;
            this.view = view;
        }
    }

    enum RestoreResult { RESTORED, RESTORED_WITHOUT_NOTIFY, SUPERSEDED, MAP_UNAVAILABLE }

    static final class Unavailable extends Exception {
        final LinkedHashMap<Object, List<Object>> original;

        private Unavailable(LinkedHashMap<Object, List<Object>> original, Throwable cause) {
            super("MIUI control center host shape is unavailable", cause);
            this.original = original;
        }
    }
}
