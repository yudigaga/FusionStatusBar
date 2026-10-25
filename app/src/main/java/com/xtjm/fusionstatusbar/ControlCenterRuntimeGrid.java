package com.xtjm.fusionstatusbar;

import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Places the tile portion of the OEM panel in one full-span, independently bound grid. */
final class ControlCenterRuntimeGrid {
    private static final String TAG = "FusionControlGrid";
    private static final int TILE_VIEW_TYPE = 8453;
    private static final int MAX_RENDER_ROWS = 128;
    private static final Map<Object, WeakReference<GridState>> STATES = new WeakHashMap<>();
    private static final Object MODULE_FIVE_G_COMPONENT = new Object();

    private ControlCenterRuntimeGrid() {
    }

    /** Hold this return value across distributeContent's native map replacement. */
    static Object retain(Object adapter) {
        return adapter == null ? null : stateFor(adapter);
    }

    /** Returns whether this adapter already contains a live custom-grid proxy. */
    static boolean isActive(Object adapter) {
        GridState state = stateFor(adapter);
        return state != null && !state.disposed && !state.failed;
    }

    /** OEM bind, icon and background hooks call this after installing their new state. */
    static void markMaterialsDirty(View source) {
        for (View current = source; current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof GridView grid) {
                grid.markSlotDirty(source);
                return;
            }
        }
    }

    /** Reads the live controller style, which updates before either adapter's cached style. */
    static boolean isHorizontal(Object target) {
        if (target == null) return false;
        try {
            Object controller = target;
            Object provider = readField(target, "mainPanelController");
            if (provider != null) {
                Object value = callOptional(provider, "get");
                if (value != null) controller = value;
            }
            Object style = callOptional(controller, "getStyle");
            return "HORIZONTAL".equals(String.valueOf(style));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void refreshApplication(ControlCenterConfig settings) {
        synchronized (STATES) {
            for (WeakReference<GridState> reference : STATES.values()) {
                GridState state = reference.get();
                if (state != null && state.layoutPlan.equals(settings.layoutPlan)) {
                    state.materialSettings = settings;
                    if (state.grid != null) state.grid.updateDecorations(settings);
                }
            }
        }
        reportApplication(settings);
    }

    static void reportApplication(ControlCenterConfig settings) {
        String state = applicationState(settings);
        SystemUiHooks.reportControlCenterApplication(settings, state,
                FusionActivationStatus.DEGRADED.equals(state) ? "control_center_items_unavailable"
                        : FusionActivationStatus.WAITING.equals(state)
                        ? "control_center_mount_pending" : "control_center_mounted");
    }

    static String applicationState(ControlCenterConfig settings) {
        boolean found = false, pending = false, degraded = false;
        synchronized (STATES) {
            for (WeakReference<GridState> reference : STATES.values()) {
                GridState state = reference.get();
                if (state == null || state.disposed || state.materialSettings != settings) continue;
                found = true;
                pending |= state.grid == null || state.grid.getParent() == null;
                degraded |= state.failed || state.unavailable;
            }
        }
        return degraded ? FusionActivationStatus.DEGRADED : !found || pending
                ? FusionActivationStatus.WAITING : FusionActivationStatus.APPLIED;
    }

    /** Called after MainPanelAdapter.distributeContent has rebuilt its native contentMap. */
    static boolean apply(Object adapter, ControlCenterConfig settings, ClassLoader pluginLoader) {
        if (adapter == null) return false;
        if (settings == null || pluginLoader == null) return abort(adapter);
        ControlCenterHostAdapter.State hostState;
        LinkedHashMap<Object, List<Object>> currentSnapshot = null;
        LinkedHashMap<String, ControlCenterAppTileSource.Lease> pendingAppTiles = new LinkedHashMap<>();
        try {
            hostState = ControlCenterHostAdapter.readState(adapter);
        } catch (ReflectiveOperationException error) {
            return abort(adapter);
        }
        Object currentMode = hostState.mode;
        Object currentStyle = hostState.style;
        boolean horizontal = "HORIZONTAL".equals(String.valueOf(currentStyle));
        if (!settings.enabled || settings.layoutPlan.isEmpty()
                || !"NORMAL".equals(String.valueOf(currentMode))) {
            trace(Log.INFO, "runtime grid bypass mode=" + currentMode
                    + " style=" + currentStyle
                    + " enabled=" + settings.enabled
                    + " plan=" + (settings.layoutPlan.isEmpty() ? "empty" : "present"));
            clear(adapter, false);
            return false;
        }
        try {
            ControlCenterHostAdapter.Panel host;
            try {
                host = ControlCenterHostAdapter.capture(hostState, pluginLoader);
            } catch (ControlCenterHostAdapter.Unavailable unavailable) {
                currentSnapshot = unavailable.original;
                return abort(adapter, currentSnapshot);
            }
            LinkedHashMap<Object, List<Object>> original = host.original;
            currentSnapshot = original;
            Object listOwner = host.listOwner;
            Class<?> itemInterface = host.itemInterface;
            boolean nativeCompact = host.compact;
            LinkedHashMap<Object, List<Object>> sources = host.sources;
            LinkedHashMap<String, Object> sourceTiles = collectTiles(sources, listOwner,
                    settings);
            ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.fromConfig(settings, new ArrayList<>(sourceTiles.keySet()));
            boolean compact = plan.runtimeUsesCompact(nativeCompact);
            ControlCenterLayoutPlan.Mode mode = plan.mode(compact);
            if (horizontal) {
                mode = ControlCenterLandscapeLayout.forPanel(settings, mode,
                        host.isRightAdapter(), host.rightTileSpecs(), host.componentSides());
            }
            GridState previous = stateFor(adapter);
            ControlCenterAppTileSource.acquire(mode.items, sourceTiles,
                    previous == null ? Collections.emptyMap() : previous.appTiles, pendingAppTiles,
                    spec -> !settings.isHidden(spec),
                    () -> {
                        try {
                            Object context = callOptional(listOwner, "getContext");
                            return context instanceof android.content.Context c ? ControlCenterAppTiles.query(c, false) : Collections.emptyList();
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            trace(Log.WARN, "Application tile lookup unavailable", error);
                            return Collections.emptyList();
                        }
                    }, spec -> createApplicationTile(listOwner, spec));
            LinkedHashMap<String, ComponentSource> sourceComponents =
                    collectComponents(sources, itemInterface, settings);
            ArrayList<String> availableSpecs = new ArrayList<>(sourceTiles.keySet());
            for (String spec : sourceComponents.keySet()) {
                if (!availableSpecs.contains(spec)) availableSpecs.add(spec);
            }
            trace(Log.INFO, "runtime grid input mode=" + currentMode
                    + " style=" + currentStyle
                    + " plan=" + (settings.layoutPlan.isEmpty() ? "empty" : "present")
                    + " nativeTiles=" + sourceTiles.size()
                    + " nativeComponents=" + (sourceComponents.size()
                            - (sourceComponents.containsKey(ControlCenterComponentSpec.FIVE_G) ? 1 : 0))
                    + " moduleComponents="
                    + (sourceComponents.containsKey(ControlCenterComponentSpec.FIVE_G) ? 1 : 0));
            // Components can be absent in a particular native style or during startup.
            // Resolve against this adapter's sources without discarding the whole saved plan.
            ControlCenterLayoutPlan.Resolved resolved = horizontal
                    ? ControlCenterLayoutPlan.of(mode, mode).resolveVisible(false, availableSpecs)
                    : plan.resolveForRuntime(nativeCompact, availableSpecs);
            List<ControlCenterLayoutPlan.Item> resolvedItems = horizontal
                    ? ControlCenterLandscapeLayout.compactRows(resolved.mode).items : resolved.items;
            if (!resolved.unavailableSpecs.isEmpty()) {
                trace(Log.WARN, "runtime grid skipping unavailable items=" + resolved.unavailableSpecs
                        + " nativeStyle=" + currentStyle + "; saved plan retained");
            }
            ArrayList<ControlCenterLayoutPlan.Item> items = new ArrayList<>();
            int lastRow = 0;
            for (ControlCenterLayoutPlan.Item item : resolvedItems) {
                if (item.hidden) {
                    continue;
                }
                if (item.type == ControlCenterLayoutPlan.Type.COMPONENT) {
                    if (!sourceComponents.containsKey(item.firstSpec)) {
                        return abort(adapter, original);
                    }
                } else {
                    for (String member : item.specs()) if (!sourceTiles.containsKey(member)) return abort(adapter, original);
                }
                items.add(item);
                lastRow = Math.max(lastRow, item.y + item.height);
            }
            if (lastRow > MAX_RENDER_ROWS) {
                trace(Log.WARN, "Grid rows exceed runtime limit: " + lastRow);
                return abort(adapter, original);
            }
            items.sort(java.util.Comparator.comparingInt((ControlCenterLayoutPlan.Item item)
                    -> item.zIndex).thenComparingInt(item -> item.y)
                    .thenComparingInt(item -> item.x));

            // Keep the native fallback for an entirely empty canvas. An empty
            // projection of a nonempty landscape canvas must instead hide this
            // side's handled native content, or grouped/hidden tiles reappear.
            if (items.isEmpty() && !(horizontal && !plan.mode(compact).items.isEmpty())) {
                trace(Log.INFO, "runtime grid bypass empty resolved plan mode="
                        + (compact ? "COMPACT" : "NORMAL"));
                clear(adapter, false);
                SystemUiHooks.reportControlCenterApplication(settings,
                        resolved.unavailableSpecs.isEmpty() ? FusionActivationStatus.APPLIED : FusionActivationStatus.DEGRADED,
                        resolved.unavailableSpecs.isEmpty() ? "empty_layout_native_retained" : "control_center_items_unavailable");
                return false;
            }

            LinkedHashMap<String, ComponentSource> boundComponents = new LinkedHashMap<>();
            for (ControlCenterLayoutPlan.Item item : items) {
                if (item.type == ControlCenterLayoutPlan.Type.COMPONENT) {
                    boundComponents.put(item.firstSpec, sourceComponents.get(item.firstSpec));
                }
            }
            boolean reused = previous != null && previous.matches(settings, compact,
                    resolved.mode.columns, items, sourceTiles, boundComponents);
            if (!reused) {
                StringBuilder summary = new StringBuilder(160);
                for (ControlCenterLayoutPlan.Item item : items) {
                    if (summary.length() > 0) summary.append(';');
                    summary.append(item.id).append('@').append(item.x).append(',')
                            .append(item.y).append('+').append(item.width).append('x')
                            .append(item.height);
                }
                trace(Log.INFO, "runtime grid resolved mode=" + (compact ? "COMPACT" : "NORMAL")
                        + " selection=" + plan.runtimeLayout + " nativeStyle=" + currentStyle
                        + " columns=" + resolved.mode.columns
                        + " items=" + items.size() + " rows=" + lastRow
                        + " layout=" + summary);
            }
            GridState next = reused ? previous : new GridState(adapter, host, itemInterface,
                            settings, compact, resolved.mode.columns, items, sourceTiles,
                            boundComponents, 0, lastRow);
            next.hostPanel = host;
            next.materialSettings = settings;
            next.unavailable = !resolved.unavailableSpecs.isEmpty();
            try {
                java.util.Set<Object> relocatedComponents = Collections.newSetFromMap(
                        new IdentityHashMap<>());
                for (ComponentSource source : sourceComponents.values()) {
                    relocatedComponents.add(source.item);
                }
                host.project(next.item, relocatedComponents);
            } catch (Throwable error) {
                RuntimeCleanup.run("restore failed grid map", () -> host.restoreSnapshot(original, false));
                if (previous != next) next.dispose();
                throw error;
            }
            synchronized (STATES) {
                STATES.put(adapter, new WeakReference<>(next));
            }
            if (!reused) {
                next.appTiles.putAll(pendingAppTiles);
                pendingAppTiles.clear();
            }
            if (previous != null && previous != next) previous.dispose();
            trace(Log.INFO, "runtime grid applied mode=" + (compact ? "COMPACT" : "NORMAL")
                    + " rebuilt=" + (!reused) + " itemCount=" + items.size());
            reportApplication(settings);
            return true;
        } catch (Throwable error) {
            trace(Log.WARN, "Native panel retained after grid setup failed", error);
            SystemUiHooks.reportControlCenterApplication(settings, FusionActivationStatus.DEGRADED,
                    "control_center_grid_setup_failed");
            return abort(adapter, currentSnapshot);
        } finally {
            ControlCenterAppTileSource.release(pendingAppTiles);
        }
    }

    private static ControlCenterAppTileSource.Lease createApplicationTile(Object listOwner, String spec) {
        try {
            Object provider = readField(listOwner, "qsController");
            Object controller = callOptional(provider, "get");
            if (controller == null) controller = provider;
            Object tile = callOptional(controller, "createTile", spec);
            if (tile == null) return null;
            return new ControlCenterAppTileSource.Lease(tile, () -> {
                try { callOptional(tile, "destroy"); }
                catch (ReflectiveOperationException | RuntimeException error) { trace(Log.WARN, "Application tile release failed", error); }
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            trace(Log.WARN, "Application tile creation failed spec=" + spec, error);
            return null;
        }
    }

    private static boolean abort(Object adapter) {
        return abort(adapter, null);
    }

    private static boolean abort(Object adapter,
            LinkedHashMap<Object, List<Object>> currentSnapshot) {
        GridState previous = stateFor(adapter);
        if (previous != null) {
            if (currentSnapshot == null) {
                RuntimeCleanup.run("restore native panel", () -> previous.restoreNative(true));
            } else {
                RuntimeCleanup.run("restore current panel",
                        () -> previous.restoreSnapshot(currentSnapshot, true));
            }
            synchronized (STATES) {
                STATES.remove(adapter);
            }
            previous.dispose();
        }
        return false;
    }

    static void clear(Object adapter) {
        clear(adapter, true);
    }

    /** Clears a proxy without sending an intermediate adapter notification. */
    static void clear(Object adapter, boolean notify) {
        if (adapter == null) return;
        GridState previous;
        synchronized (STATES) {
            WeakReference<GridState> reference = STATES.remove(adapter);
            previous = reference == null ? null : reference.get();
        }
        if (previous == null) return;
        trace(Log.INFO, "runtime grid cleared mode=" + (previous.compact ? "COMPACT" : "NORMAL")
                + " itemCount=" + previous.items.size());
        RuntimeCleanup.run("restore native panel", () -> previous.restoreNative(notify));
        previous.dispose();
    }

    /**
     * Drops every installed proxy before a new layout plan is distributed.
     *
     * <p>The adapter instances can be replaced by the plugin while a settings
     * notification is being delivered. Clearing the weak map as one operation
     * prevents a stale proxy from being reused by a later distribution.</p>
     */
    static int resetAll(String reason) {
        ArrayList<GridState> active = new ArrayList<>();
        synchronized (STATES) {
            for (WeakReference<GridState> reference : STATES.values()) {
                GridState state = reference == null ? null : reference.get();
                if (state != null && !active.contains(state)) active.add(state);
            }
            STATES.clear();
        }
        for (GridState state : active) {
            RuntimeCleanup.run("restore native panel", () -> state.restoreNative(false));
            state.dispose();
        }
        if (!active.isEmpty()) {
            trace(Log.INFO, "runtime grid reset count=" + active.size()
                + " reason=" + (reason == null ? "unspecified" : reason));
        }
        return active.size();
    }

    private static GridState stateFor(Object adapter) {
        synchronized (STATES) {
            WeakReference<GridState> reference = STATES.get(adapter);
            return reference == null ? null : reference.get();
        }
    }

    private static void trace(int priority, String message) {
        SystemUiHooks.runtimeGridLog(priority, message, null);
    }

    private static void trace(int priority, String message, Throwable error) {
        SystemUiHooks.runtimeGridLog(priority, message, error);
    }

    private static LinkedHashMap<String, Object> collectTiles(
            LinkedHashMap<Object, List<Object>> original, Object listOwner,
            ControlCenterConfig settings) throws ReflectiveOperationException {
        LinkedHashMap<String, Object> tiles = new LinkedHashMap<>();
        for (List<Object> records : original.values()) collectRecords(records, tiles, settings);
        for (String fieldName : new String[] {"addedTiles", "systemTiles", "packageTiles"}) {
            Object records = readField(listOwner, fieldName);
            collectRecordValue(records, tiles, settings);
        }
        for (String methodName : new String[] {"getAddedTiles", "getSystemTiles",
                "getPackageTiles", "getListItems", "getTiles"}) {
            try {
                collectRecordValue(call(listOwner, methodName), tiles, settings);
            } catch (NoSuchMethodException ignored) {
                // MIUI revisions expose different inventory accessors.
            }
        }
        return tiles;
    }

    private static void collectRecordValue(Object value, Map<String, Object> tiles,
            ControlCenterConfig settings) throws ReflectiveOperationException {
        if (value instanceof List<?> records) {
            collectRecords(records, tiles, settings);
        } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            ArrayList<Object> records = new ArrayList<>(length);
            for (int i = 0; i < length; i++) records.add(java.lang.reflect.Array.get(value, i));
            collectRecords(records, tiles, settings);
        }
    }

    /** Collect native non-tile list items without changing the adapter's source lists. */
    private static LinkedHashMap<String, ComponentSource> collectComponents(
            LinkedHashMap<Object, List<Object>> original, Class<?> itemInterface,
            ControlCenterConfig settings)
            throws ReflectiveOperationException {
        LinkedHashMap<String, ComponentSource> components = new LinkedHashMap<>();
        for (Map.Entry<Object, List<Object>> entry : original.entrySet()) {
            Object owner = entry.getKey();
            if (owner == null) continue;
            for (Object item : entry.getValue()) {
                if (item == null || !itemInterface.isInstance(item)) continue;
                String spec = componentSpec(item);
                if (spec.isEmpty()) continue;
                components.putIfAbsent(spec, new ComponentSource(owner, item));
            }
        }
        if (settings.isHidden(ControlCenterComponentSpec.FIVE_G)) {
            components.remove(ControlCenterComponentSpec.FIVE_G);
        } else {
            components.putIfAbsent(ControlCenterComponentSpec.FIVE_G,
                    ComponentSource.moduleOwned());
        }
        return components;
    }

    static String componentSpec(Object item) throws ReflectiveOperationException {
        return ControlCenterHostAdapter.componentSpec(item);
    }

    private static void collectRecords(List<?> records, Map<String, Object> tiles,
            ControlCenterConfig settings) throws ReflectiveOperationException {
        for (Object record : records) {
            if (record == null) continue;
            try {
                Object specValue = call(record, "getSpec");
                Object tile = call(record, "getTile");
                if (!(specValue instanceof String spec) || spec.isEmpty() || tile == null) continue;
                String normalized = ControlCenterConfig.canonicalSpec(
                        spec.trim().toLowerCase(Locale.ROOT));
                if (normalized.isEmpty() || settings.isHidden(normalized)) continue;
                tiles.putIfAbsent(normalized, tile);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Dividers and OEM wrapper items share the native lists but do not expose QS methods.
            }
        }
    }

    private static final class GridState implements InvocationHandler {
        final WeakReference<Object> adapter;
        final int columns;
        final int spacingDp;
        final int radiusDp;
        final boolean compact;
        final boolean horizontal;
        final String layoutPlan;
        final List<ControlCenterLayoutPlan.Item> items;
        final LinkedHashMap<String, Object> sourceTiles;
        final LinkedHashMap<String, ComponentSource> sourceComponents;
        final LinkedHashMap<String, Object> records = new LinkedHashMap<>();
        ControlCenterConfig materialSettings;
        boolean unavailable;
        final LinkedHashMap<String, ControlCenterAppTileSource.Lease> appTiles = new LinkedHashMap<>();
        final int firstRow;
        final int rowCount;
        final Object item;
        ControlCenterHostAdapter.Panel hostPanel;
        Object outerHolder;
        ViewGroup outerView;
        GridView grid;
        ControlCenterScaleViewport gridViewport;
        ControlCenterOuterHeightReconciler outerHeightReconciler;
        View.OnLayoutChangeListener outerLayoutListener;
        final ArrayList<Runnable> mountTasks = new ArrayList<>();
        ArrayList<Child> originalChildren;
        ArrayList<TileBinding> bindings;
        Drawable oldBackground;
        Drawable oldForeground;
        int oldHeight;
        int oldAccessibility;
        boolean oldClickable;
        boolean oldLongClickable;
        int oldMinimumHeight;
        boolean presentationLogged;
        boolean attached;
        boolean disposed;
        boolean failed;

        GridState(Object adapter, ControlCenterHostAdapter.Panel hostPanel,
                Class<?> itemInterface,
                ControlCenterConfig settings, boolean compact, int columns,
                List<ControlCenterLayoutPlan.Item> items, Map<String, Object> sourceTiles,
                Map<String, ComponentSource> sourceComponents, int firstRow, int rowCount)
                throws ReflectiveOperationException {
            this.adapter = new WeakReference<>(adapter);
            this.hostPanel = hostPanel;
            this.materialSettings = settings;
            this.columns = columns;
            this.spacingDp = settings.spacing;
            this.radiusDp = settings.cornerRadius;
            this.compact = compact;
            this.horizontal = isHorizontal(adapter);
            this.layoutPlan = settings.layoutPlan;
            this.items = new ArrayList<>(items);
            this.sourceTiles = new LinkedHashMap<>(sourceTiles);
            this.sourceComponents = new LinkedHashMap<>(sourceComponents);
            this.firstRow = firstRow;
            this.rowCount = rowCount;
            try {
                java.util.Set<String> requiredSpecs = new java.util.LinkedHashSet<>();
                for (ControlCenterLayoutPlan.Item placement : items) {
                    if (placement.type != ControlCenterLayoutPlan.Type.COMPONENT)
                        requiredSpecs.addAll(placement.specs());
                }
                for (String spec : requiredSpecs) {
                    Object tile = sourceTiles.get(spec);
                    if (tile == null) throw new IllegalStateException("Missing grid tile " + spec);
                    boolean card = hostPanel.hasCardsOwner() && usesCardView(items, spec);
                    Object record = hostPanel.createRecord(tile, card);
                    records.put(spec, record);
                }
            for (Map.Entry<String, ComponentSource> entry : sourceComponents.entrySet()) {
                if (!entry.getValue().moduleOwned
                        && (entry.getValue().owner == null || entry.getValue().item == null)) {
                    throw new IllegalStateException("Component source is incomplete "
                            + entry.getKey());
                }
            }
                item = Proxy.newProxyInstance(itemInterface.getClassLoader(),
                        new Class<?>[] {itemInterface}, this);
            } catch (Throwable error) {
                releaseRecords();
                if (error instanceof ReflectiveOperationException reflection) throw reflection;
                throw new IllegalStateException(error);
            }
        }

        boolean matches(ControlCenterConfig settings, boolean nextCompact, int nextColumns,
                List<ControlCenterLayoutPlan.Item> nextItems, Map<String, Object> nextTiles,
                Map<String, ComponentSource> nextComponents) {
            if (disposed || failed || compact != nextCompact || horizontal != isHorizontal(adapter.get())
                    || columns != nextColumns
                    || materialSettings.cardBlur != settings.cardBlur || materialSettings.tileBlur != settings.tileBlur
                    || materialSettings.surface != settings.surface
                    || materialSettings.borderEnabled != settings.borderEnabled
                    || materialSettings.borderWidthDp != settings.borderWidthDp
                    || materialSettings.tileScale != settings.tileScale
                    || spacingDp != settings.spacing || radiusDp != settings.cornerRadius
                    || !layoutPlan.equals(settings.layoutPlan)
                    || items.size() != nextItems.size()
                    || sourceTiles.size() != nextTiles.size()
                    || sourceComponents.size() != nextComponents.size()) return false;
            for (Map.Entry<String, Object> entry : sourceTiles.entrySet()) {
                if (nextTiles.get(entry.getKey()) != entry.getValue()) return false;
            }
            for (Map.Entry<String, ComponentSource> entry : sourceComponents.entrySet()) {
                ComponentSource next = nextComponents.get(entry.getKey());
                if (next == null || next.owner != entry.getValue().owner
                        || next.item != entry.getValue().item) return false;
            }
            for (int i = 0; i < items.size(); i++) {
                ControlCenterLayoutPlan.Item a = items.get(i);
                ControlCenterLayoutPlan.Item b = nextItems.get(i);
                if (!a.id.equals(b.id) || a.type != b.type
                        || !a.firstSpec.equals(b.firstSpec)
                        || !a.secondSpec.equals(b.secondSpec)
                        || a.x != b.x || a.y != b.y
                        || a.width != b.width || a.height != b.height
                        || a.direction != b.direction || a.shape != b.shape
                        || a.cornerRadius != b.cornerRadius || a.locked != b.locked
                        || a.hidden != b.hidden || a.zIndex != b.zIndex
                        || !java.util.Objects.equals(a.group, b.group)) return false;
            }
            return true;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];
            if ("toString".equals(name)) return "FusionControlGrid";
            if ("getType".equals(name)) return TILE_VIEW_TYPE;
            if ("getSpanSize".equals(name)) {
                Object ownerAdapter = adapter.get();
                Object span = readField(ownerAdapter, "span");
                return span instanceof Number ? Math.max(1, ((Number) span).intValue()) : columns;
            }
            if ("getHolder".equals(name)) return outerHolder;
            if ("setHolder".equals(name)) {
                outerHolder = args[0];
                trace(Log.INFO, "runtime grid proxy holder="
                        + (outerHolder == null ? "cleared" : "assigned"));
                // Some plugin revisions assign the holder before dispatching
                // MainPanelContent.onBindViewHolder. Mount here as a fallback
                // so the custom item cannot remain an unbound placeholder.
                if (outerHolder != null && grid == null && !disposed && !failed) {
                    bind();
                }
                return null;
            }
            if ("onBindViewHolder".equals(name)) {
                trace(Log.INFO, "runtime grid proxy onBind holder="
                        + (outerHolder == null ? "missing" : "ready"));
                bind();
            } else if ("onUnbindViewHolder".equals(name)) {
                unbind();
            } else if ("onViewAttachedToWindow".equals(name)) {
                attach();
            } else if ("onViewDetachedFromWindow".equals(name)) {
                detach();
            } else if ("updateMode".equals(name) && args != null
                    && !"NORMAL".equals(String.valueOf(args[0]))) {
                detach();
            }
            return null;
        }

        private void bind() {
            if (disposed || failed || outerHolder == null) return;
            ArrayList<TileBinding> nextBindings = new ArrayList<>();
            try {
                Object root = readField(outerHolder, "itemView");
                if (!(root instanceof ViewGroup view)) {
                    throw new IllegalStateException("Grid holder has no ViewGroup");
                }
                if (outerView == view && grid != null) return;
                trace(Log.INFO, "runtime grid proxy bind view="
                        + view.getClass().getName() + " columns=" + columns
                        + " rows=" + rowCount + " items=" + items.size());
                unbind();
                GridView nextGrid = new GridView(view.getContext(), columns, spacingDp,
                        radiusDp, firstRow, rowCount);
                if (horizontal) {
                    ControlCenterLayoutPlan saved = ControlCenterLayoutPlan.decode(layoutPlan);
                    if (saved != null) nextGrid.editorMode = saved.mode(compact);
                }
                for (ControlCenterLayoutPlan.Item placement : items) {
                    if (placement.type == ControlCenterLayoutPlan.Type.GROUP) {
                        addGroup(nextGrid, nextBindings, placement);
                    } else if (placement.type == ControlCenterLayoutPlan.Type.COMPONENT) {
                        addComponent(nextGrid, nextBindings, placement);
                    } else {
                        addTile(nextGrid, nextBindings, placement, placement.firstSpec, 0);
                    }
                    if (placement.type == ControlCenterLayoutPlan.Type.PAIR) {
                        addTile(nextGrid, nextBindings, placement, placement.secondSpec, 1);
                    }
                }
                nextGrid.applyMaterials(materialSettings);
                mount(view, nextGrid, nextBindings);
                trace(Log.INFO, "runtime grid proxy mounted parent="
                        + (nextGrid.getParent() == null ? "none"
                        : nextGrid.getParent().getClass().getName())
                        + " childCount=" + nextGrid.getChildCount()
                        + " carrierAnimation=native"
                        + " contentHeight=" + nextGrid.contentHeight());
                if (Boolean.TRUE.equals(call(outerHolder,
                        "getAttached$miui_controlcenter_release"))) attach();
                reportApplication(materialSettings);
            } catch (Throwable error) {
                releaseBindings(nextBindings);
                trace(Log.WARN, "Grid bind failed; restoring native panel", error);
                fail();
            }
        }

        private void addTile(GridView target, List<TileBinding> created,
                ControlCenterLayoutPlan.Item placement, String spec, int half)
                throws ReflectiveOperationException {
            View view = createTile(target, created, spec);
            target.addTile(view, placement, half);
        }

        private View createTile(ViewGroup target, List<TileBinding> created, String spec)
                throws ReflectiveOperationException {
            Object record = records.get(spec);
            if (record == null) throw new IllegalStateException("Missing record " + spec);
            ControlCenterHostAdapter.HolderBinding binding = hostPanel.bindTile(target, record,
                    hostPanel.hasCardsOwner() && usesCardView(items, spec));
            created.add(new TileBinding(binding.record, binding.holder));
            return binding.view;
        }

        private void addGroup(GridView target, List<TileBinding> created, ControlCenterLayoutPlan.Item placement) {
            ControlCenterGroupView group = new ControlCenterGroupView(target.getContext(), placement, radiusDp, member -> {
                try {
                    View tile = createTile(target, created, member.spec);
                    ControlCenterGroupEntry entry = new ControlCenterGroupEntry(target.getContext(), tile, member.showLabel, true, member.iconScale, () -> {
                        Object state = readField(tile, "state");
                        Object label = readField(state, "label"), subtitle = readField(state, "secondaryLabel");
                        return new String[] {label instanceof CharSequence ? label.toString() : member.spec,
                                subtitle instanceof CharSequence ? subtitle.toString() : ""};
                    });
                    created.get(created.size() - 1).groupEntry = entry;
                    return entry;
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Group tile bind failed", error); }
            });
            target.addTile(group, placement, -1);
            ControlCenterMaterials.registerRoot(group, materialSettings);
        }

        private void addComponent(GridView target, List<TileBinding> created,
                ControlCenterLayoutPlan.Item placement) throws ReflectiveOperationException {
            ComponentSource source = sourceComponents.get(placement.firstSpec);
            if (source == null) throw new IllegalStateException(
                    "Missing component " + placement.firstSpec);
            if (source.moduleOwned) {
                if (!ControlCenterComponentSpec.FIVE_G.equals(placement.firstSpec)) {
                    throw new IllegalStateException("Unknown module component " + placement.firstSpec);
                }
                ControlCenter5GTileView view = new ControlCenter5GTileView(target.getContext());
                view.setShape(placement.shape, placement.cornerRadius);
                created.add(TileBinding.moduleView(view));
                target.addTile(view, placement, -1);
                ControlCenterMaterials.registerRoot(view, materialSettings);
                return;
            }
            ControlCenterHostAdapter.HolderBinding nativeBinding =
                    hostPanel.bindComponent(target, source.owner, source.item);
            View view = nativeBinding.view;
            TileBinding binding = TileBinding.component(nativeBinding.owner,
                    nativeBinding.record, nativeBinding.holder);
            created.add(binding);
            binding.sliderLayout = ControlCenterSliderLayout.create(nativeBinding.holder, placement,
                    ControlCenterGridGeometry.radiusPx(target.getContext(), radiusDp),
                    materialSettings.enabled && materialSettings.borderEnabled
                            ? Math.max(1f, materialSettings.borderWidthDp
                                    * target.getResources().getDisplayMetrics().density) : 0f);
            target.addTile(view, placement, -1, binding.sliderLayout);
            ControlCenterMaterials.registerRoot(view, materialSettings);
            if (binding.sliderLayout != null) {
                trace(Log.INFO, "runtime slider spec=" + placement.firstSpec
                        + " axis=" + (placement.width > placement.height
                        ? "horizontal" : "vertical"));
            }
        }

        private void mount(ViewGroup view, GridView nextGrid,
                ArrayList<TileBinding> nextBindings) throws ReflectiveOperationException {
            // The RecyclerView-owned carrier participates in the native panel animation.
            // Only embedded holders need their independent transforms suppressed.
            outerView = view;
            grid = nextGrid;
            bindings = nextBindings;
            originalChildren = new ArrayList<>();
            oldBackground = view.getBackground();
            oldForeground = view.getForeground();
            oldClickable = view.isClickable();
            oldLongClickable = view.isLongClickable();
            oldAccessibility = view.getImportantForAccessibility();
            oldMinimumHeight = view.getMinimumHeight();
            ViewGroup.LayoutParams params = view.getLayoutParams();
            oldHeight = params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.height;
            while (view.getChildCount() > 0) {
                View child = view.getChildAt(0);
                originalChildren.add(new Child(child, child.getLayoutParams(),
                        child.getVisibility()));
                view.removeViewAt(0);
            }
            view.setBackground(null);
            view.setForeground(null);
            ControlCenterMaterials.setGridCarrier(view, true);
            view.setClickable(false);
            view.setLongClickable(false);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            gridViewport = new ControlCenterScaleViewport(view.getContext(), nextGrid,
                    nextGrid::scalePercent);
            view.addView(gridViewport, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, nextGrid.renderedHeight()));
            outerLayoutListener = (changedView, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                if (outerView == changedView && grid == nextGrid) {
                    guardPresentation(() -> enforceOuterHeight(view, nextGrid));
                }
            };
            view.addOnLayoutChangeListener(outerLayoutListener);
            outerHeightReconciler = new ControlCenterOuterHeightReconciler(view,
                    () -> outerView == view && grid == nextGrid && !disposed && !failed,
                    () -> guardPresentation(() -> pinOuterHeight(view, nextGrid)),
                    ControlCenterOuterHeightReconciler.VIEW_SCHEDULER);
            outerHeightReconciler.reconcile();
            scheduleMountTask(view, () -> {
                if (outerView == view && grid == nextGrid)
                    guardPresentation(() -> enforceOuterHeight(view, nextGrid));
            }, 0L);
            scheduleMountTask(view, () -> {
                if (outerView == view && grid == nextGrid)
                    guardPresentation(() -> enforceOuterHeight(view, nextGrid));
            }, 64L);
        }

        private void scheduleMountTask(View view, Runnable action, long delay) {
            Runnable task = new Runnable() {
                @Override public void run() {
                    mountTasks.remove(this);
                    if (outerView == view && !disposed && !failed) action.run();
                }
            };
            mountTasks.add(task);
            view.postDelayed(task, delay);
        }

        private void guardPresentation(Runnable change) {
            if (disposed || failed) return;
            try { change.run(); }
            catch (Throwable error) {
                trace(Log.WARN, "Grid presentation failed; restoring native panel", error);
                fail();
            }
        }

        private void enforceOuterHeight(ViewGroup view, GridView child) {
            if (outerHeightReconciler != null) {
                outerHeightReconciler.reconcile();
                return;
            }
            pinOuterHeight(view, child);
        }

        private void pinOuterHeight(ViewGroup view, GridView child) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            int target = child.renderedHeight();
            if (params != null && params.height != target) {
                params.height = target;
                view.setLayoutParams(params);
            }
            // QSTileItemView.changeExpand() can restore its native one-row
            // height after the proxy is mounted. The minimum keeps the
            // RecyclerView item large enough for every custom-grid row.
            if (view.getMinimumHeight() < target) view.setMinimumHeight(target);
            // Both setters above request layout when needed. An unconditional request
            // from the layout-change listener schedules redundant traversals.
        }

        private void normalizePresentation(String stage) {
            if (outerView == null || grid == null) return;
            int hidden = 0;
            int zeroAlpha = 0;
            int zeroSize = 0;
            for (int index = 0; index < grid.getChildCount(); index++) {
                View child = grid.getChildAt(index);
                if (child.getVisibility() != View.VISIBLE) hidden++;
                if (child.getAlpha() <= 0.01f) zeroAlpha++;
                if (child.getMeasuredWidth() <= 0 || child.getMeasuredHeight() <= 0) zeroSize++;
            }
            boolean changed = false;
            // Never reset the carrier's visibility/alpha/transform here. A delayed
            // "settled" callback can run while the native panel is already closing.
            if (grid.getVisibility() != View.VISIBLE) {
                grid.setVisibility(View.VISIBLE);
                changed = true;
            }
            for (int index = 0; index < grid.getChildCount(); index++) {
                View child = grid.getChildAt(index);
                if (child.getVisibility() != View.VISIBLE) {
                    child.setVisibility(View.VISIBLE);
                    changed = true;
                }
                if (Math.abs(child.getAlpha() - 1f) > 0.01f) {
                    child.setAlpha(1f);
                    changed = true;
                }
                if (Math.abs(child.getScaleX() - 1f) > 0.01f
                        || Math.abs(child.getScaleY() - 1f) > 0.01f
                        || Math.abs(child.getTranslationX()) > 0.5f
                        || Math.abs(child.getTranslationY()) > 0.5f) {
                    child.setScaleX(1f);
                    child.setScaleY(1f);
                    child.setTranslationX(0f);
                    child.setTranslationY(0f);
                    changed = true;
                }
            }
            if (!presentationLogged || changed || hidden > 0 || zeroAlpha > 0 || zeroSize > 0) {
                trace(Log.INFO, "runtime grid presentation stage=" + stage
                        + " carrierAnimation=native"
                        + " outerVisibility=" + outerView.getVisibility()
                        + " outerAlpha=" + outerView.getAlpha()
                        + " outerScale=" + outerView.getScaleX() + "x" + outerView.getScaleY()
                        + " outerTranslation=" + outerView.getTranslationX() + ","
                        + outerView.getTranslationY()
                        + " outerMeasured=" + outerView.getMeasuredWidth() + "x"
                        + outerView.getMeasuredHeight() + " childCount=" + grid.getChildCount()
                        + " hidden=" + hidden + " zeroAlpha=" + zeroAlpha
                        + " zeroSize=" + zeroSize + " changed=" + changed, null);
                presentationLogged = true;
            }
            if (changed) {
                enforceOuterHeight(outerView, grid);
                grid.requestLayout();
            }
        }

        private void attach() {
            if (attached || bindings == null || failed) return;
            Object ownerAdapter = adapter.get();
            if (ownerAdapter == null) return;
            try {
                Object frameCallback = readField(ownerAdapter, "frameCallback");
                for (TileBinding binding : bindings) {
                    if (binding.moduleOwned) continue;
                    if (binding.presentation == null) {
                        binding.presentation = new HolderPresentation(binding.holder);
                    } else {
                        binding.presentation.freeze();
                    }
                    call(binding.holder, "setAttached$miui_controlcenter_release", true);
                    call(binding.holder, "onViewAttachedToWindow", ownerAdapter, frameCallback);
                    if (binding.owner != null) {
                        callOptional(binding.record, "onViewAttachedToWindow");
                    } else {
                        call(binding.record, "onViewAttachedToWindow");
                    }
                    if (binding.groupEntry != null) binding.groupEntry.normalizeContent();
                }
                attached = true;
                trace(Log.INFO, "runtime grid proxy attached bindings=" + bindings.size());
                normalizePresentation("attached");
            } catch (Throwable error) {
                trace(Log.WARN, "Grid attach failed; restoring native panel", error);
                fail();
            }
        }

        private void detach() {
            if (bindings == null) return;
            for (TileBinding binding : bindings) {
                if (binding.moduleOwned) continue;
                RuntimeCleanup.run("tile detach callback", () -> {
                    if (binding.owner != null) {
                        if (ownsComponentHolder(binding)) callOptional(binding.record, "onViewDetachedFromWindow");
                    } else {
                        call(binding.record, "onViewDetachedFromWindow");
                    }
                });
                RuntimeCleanup.run("holder detach callback",
                        () -> call(binding.holder, "onViewDetachedFromWindow"));
                RuntimeCleanup.run("holder detached flag",
                        () -> call(binding.holder, "setAttached$miui_controlcenter_release", false));
            }
            attached = false;
        }

        private void unbind() {
            if (outerHeightReconciler != null) outerHeightReconciler.cancel();
            if (outerView != null) {
                for (Runnable task : new ArrayList<>(mountTasks))
                    RuntimeCleanup.run("cancel grid mount task", () -> outerView.removeCallbacks(task));
            }
            mountTasks.clear();
            RuntimeCleanup.run("detach bindings", this::detach);
            if (grid != null) RuntimeCleanup.run("release grid material ownership", grid::releaseMaterials);
            if (bindings != null) {
                releaseBindings(bindings);
            }
            if (outerView != null) {
                if (outerLayoutListener != null) {
                    RuntimeCleanup.run("remove grid layout listener",
                            () -> outerView.removeOnLayoutChangeListener(outerLayoutListener));
                }
                if (gridViewport != null && gridViewport.getParent() == outerView)
                    RuntimeCleanup.run("remove grid", () -> outerView.removeView(gridViewport));
                if (originalChildren != null) {
                    for (Child child : originalChildren) {
                        if (child.view.getParent() == null) {
                            RuntimeCleanup.run("restore native child",
                                    () -> outerView.addView(child.view, child.params));
                            RuntimeCleanup.run("restore child visibility",
                                    () -> child.view.setVisibility(child.visibility));
                        }
                    }
                }
                RuntimeCleanup.run("restore background", () -> outerView.setBackground(oldBackground));
                RuntimeCleanup.run("restore foreground", () -> outerView.setForeground(oldForeground));
                RuntimeCleanup.run("release carrier decoration ownership",
                        () -> ControlCenterMaterials.setGridCarrier(outerView, false));
                RuntimeCleanup.run("restore clickable", () -> outerView.setClickable(oldClickable));
                RuntimeCleanup.run("restore long clickable", () -> outerView.setLongClickable(oldLongClickable));
                RuntimeCleanup.run("restore accessibility",
                        () -> outerView.setImportantForAccessibility(oldAccessibility));
                RuntimeCleanup.run("restore minimum height", () -> outerView.setMinimumHeight(oldMinimumHeight));
                RuntimeCleanup.run("restore height", () -> {
                    ViewGroup.LayoutParams params = outerView.getLayoutParams();
                    if (params != null) {
                        params.height = oldHeight;
                        outerView.setLayoutParams(params);
                    }
                });
            }
            outerView = null;
            grid = null;
            gridViewport = null;
            outerHeightReconciler = null;
            outerLayoutListener = null;
            bindings = null;
            originalChildren = null;
        }

        private void releaseBindings(List<TileBinding> values) {
            for (TileBinding binding : values) {
                if (binding.moduleOwned) {
                    RuntimeCleanup.run("release module 5G tile", binding.moduleView::release);
                    continue;
                }
                if (binding.groupEntry != null)
                    RuntimeCleanup.run("restore group member surface", binding.groupEntry::release);
                if (binding.sliderLayout != null)
                    RuntimeCleanup.run("restore slider", binding.sliderLayout::restore);
                if (binding.owner == null) {
                    RuntimeCleanup.run("stop tile listening",
                            () -> call(binding.record, "setListening", false));
                }
                // A rotation can move a component to the other grid before this old state
                // is released. Its single controller holder already belongs to the new grid.
                RuntimeCleanup.run("unbind tile", () -> {
                    if (ownsComponentHolder(binding)) callOptional(binding.record, "onUnbindViewHolder");
                });
                RuntimeCleanup.run("clear tile holder", () -> {
                    if (ownsComponentHolder(binding)) call(binding.record, "setHolder", (Object) null);
                });
                RuntimeCleanup.run("clear holder item",
                        () -> call(binding.holder, "setItem$miui_controlcenter_release", (Object) null));
                RuntimeCleanup.run("clear holder owner", () -> call(binding.holder, "setOwner", (Object) null));
                if (binding.presentation != null)
                    RuntimeCleanup.run("restore holder presentation", binding.presentation::restore);
                RuntimeCleanup.run("recycle holder", () -> call(binding.holder, "recycle"));
            }
            values.clear();
        }

        private boolean ownsComponentHolder(TileBinding binding) throws ReflectiveOperationException {
            if (binding.moduleOwned) return false;
            return binding.owner == null || callOptional(binding.record, "getHolder") == binding.holder;
        }

        private void fail() {
            if (failed) return;
            failed = true;
            SystemUiHooks.reportControlCenterApplication(materialSettings, FusionActivationStatus.DEGRADED,
                    "control_center_mount_failed");
            new Handler(Looper.getMainLooper()).post(() -> {
                RuntimeCleanup.run("restore failed grid", () -> restoreNative(true));
                dispose();
            });
        }

        private void restoreSnapshot(LinkedHashMap<Object, List<Object>> snapshot, boolean notify) {
            if (hostPanel == null) return;
            ControlCenterHostAdapter.RestoreResult result = hostPanel.restoreSnapshot(snapshot, notify);
            if (result == ControlCenterHostAdapter.RestoreResult.RESTORED_WITHOUT_NOTIFY) {
                Log.w(TAG, "Current native panel restored without notify");
            }
        }

        private void restoreNative(boolean notify) {
            if (hostPanel == null) return;
            ControlCenterHostAdapter.RestoreResult result = hostPanel.restoreProxy(item, notify);
            if (result == ControlCenterHostAdapter.RestoreResult.SUPERSEDED) {
                // A later SystemUI distribution replaced the proxy; keep its native map.
                Log.w(TAG, "runtime grid proxy missing; keeping current native panel");
            } else if (result == ControlCenterHostAdapter.RestoreResult.RESTORED_WITHOUT_NOTIFY) {
                Log.w(TAG, "Native panel restored without notify");
            }
        }

        private void dispose() {
            if (disposed) return;
            disposed = true;
            RuntimeCleanup.run("unbind grid", this::unbind);
            RuntimeCleanup.run("release grid records", this::releaseRecords);
            RuntimeCleanup.run("release application tiles", () -> ControlCenterAppTileSource.release(appTiles));
        }

        private void releaseRecords() {
            for (Object record : records.values()) {
                RuntimeCleanup.run("stop record listening",
                        () -> {
                            if (hostPanel != null) hostPanel.setRecordListening(record, false);
                            else call(record, "setListening", false);
                        });
                RuntimeCleanup.run("remove record callback",
                        () -> {
                            if (hostPanel != null) hostPanel.removeRecordCallback(record);
                            else call(record, "removeCallback");
                        });
            }
            records.clear();
        }
    }

    private static final class TileBinding {
        final Object owner;
        final Object record;
        final Object holder;
        final boolean moduleOwned;
        final ControlCenter5GTileView moduleView;
        HolderPresentation presentation;
        ControlCenterSliderLayout sliderLayout;
        ControlCenterGroupEntry groupEntry;

        TileBinding(Object record, Object holder) {
            this(null, record, holder, false, null);
        }

        private TileBinding(Object owner, Object record, Object holder) {
            this(owner, record, holder, false, null);
        }

        private TileBinding(Object owner, Object record, Object holder, boolean moduleOwned,
                ControlCenter5GTileView moduleView) {
            this.owner = owner;
            this.record = record;
            this.holder = holder;
            this.moduleOwned = moduleOwned;
            this.moduleView = moduleView;
        }

        static TileBinding component(Object owner, Object item, Object holder) {
            return new TileBinding(owner, item, holder);
        }

        static TileBinding moduleView(ControlCenter5GTileView view) {
            return new TileBinding(null, null, null, true, view);
        }
    }

    /** Embedded holders stay local; the RecyclerView carrier owns the native panel animation. */
    static final class HolderPresentation {
        private final Object holder;
        private final boolean alpha;
        private final boolean scale;
        private final boolean translation;

        HolderPresentation(Object holder) throws ReflectiveOperationException {
            this.holder = holder;
            alpha = Boolean.TRUE.equals(callOptional(holder, "getIgnoreHolderAlpha"));
            scale = Boolean.TRUE.equals(callOptional(holder, "getIgnoreHolderScale"));
            translation = Boolean.TRUE.equals(callOptional(holder, "getIgnoreHolderTranslation"));
            freeze();
        }

        void freeze() throws ReflectiveOperationException {
            callOptional(holder, "setIgnoreHolderAlpha", true);
            callOptional(holder, "setIgnoreHolderScale", true);
            callOptional(holder, "setIgnoreHolderTranslation", true);
        }

        void restore() throws ReflectiveOperationException {
            RuntimeCleanup.run("restore holder alpha", () -> callOptional(holder, "setIgnoreHolderAlpha", alpha));
            RuntimeCleanup.run("restore holder scale", () -> callOptional(holder, "setIgnoreHolderScale", scale));
            RuntimeCleanup.run("restore holder translation",
                    () -> callOptional(holder, "setIgnoreHolderTranslation", translation));
        }
    }

    static boolean usesCardView(List<ControlCenterLayoutPlan.Item> items, String spec) {
        for (ControlCenterLayoutPlan.Item item : items) {
            if (item.firstSpec.equals(spec)) {
                return item.type == ControlCenterLayoutPlan.Type.TILE
                        && item.width > item.height
                        && item.shape != ControlCenterLayoutPlan.Shape.CIRCLE;
            }
        }
        return false;
    }

    private static final class ComponentSource {
        final Object owner;
        final Object item;
        final boolean moduleOwned;

        ComponentSource(Object owner, Object item) {
            this(owner, item, false);
        }

        ComponentSource(Object owner, Object item, boolean moduleOwned) {
            this.owner = owner;
            this.item = item;
            this.moduleOwned = moduleOwned;
        }

        static ComponentSource moduleOwned() {
            return new ComponentSource(MODULE_FIVE_G_COMPONENT, MODULE_FIVE_G_COMPONENT, true);
        }
    }

    private static final class Child {
        final View view;
        final ViewGroup.LayoutParams params;
        final int visibility;

        Child(View view, ViewGroup.LayoutParams params, int visibility) {
            this.view = view;
            this.params = params;
            this.visibility = visibility;
        }
    }

    // Created programmatically with the resolved layout geometry.
    @android.annotation.SuppressLint("ViewConstructor")
    static final class GridView extends ViewGroup {
        private final int columns;
        private final int gap;
        private final int radius;
        private final int firstRow;
        private final int rowCount;
        private final int rowHeight;
        private final ArrayList<Slot> slots = new ArrayList<>();
        private final Map<View, Slot> slotsByView = new IdentityHashMap<>();
        private final Map<View, Path> pairClips = new IdentityHashMap<>();
        private final Map<View, Path> slotClips = new IdentityHashMap<>();
        private final Map<String, Decoration> decorations = new LinkedHashMap<>();
        private final Map<View, ActiveTileBackground> activeTileBackgrounds = new IdentityHashMap<>();
        private final Matrix surfaceMatrix = new Matrix();
        private final RectF surfaceBounds = new RectF();
        private ControlCenterConfig materialSettings = ControlCenterConfig.defaults();
        private int lastMeasuredWidth = -1;
        private int lastMeasuredHeight = -1;
        private ControlCenterLayoutPlan.Mode editorMode;
        private boolean materialsReleased;
        private boolean syncingMaterials;
        private int materialProbeStage;
        private final Runnable materialProbe = new Runnable() {
            @Override public void run() {
                if (materialsReleased) return;
                markAllMaterialsDirty();
                invalidate();
                if (++materialProbeStage < 3) postDelayed(this, materialProbeStage == 1 ? 96L : 160L);
            }
        };

        GridView(android.content.Context context, int columns, int spacingDp,
                int radiusDp, int firstRow, int rowCount) {
            super(context);
            this.columns = columns;
            this.gap = ControlCenterGridGeometry.gapPx(context, spacingDp);
            this.radius = ControlCenterGridGeometry.radiusPx(context, radiusDp);
            this.firstRow = firstRow;
            this.rowCount = rowCount;
            this.rowHeight = ControlCenterGridGeometry.rowHeightPx(context);
            setClipChildren(false);
            setClipToPadding(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        // MIUI component holders update margins while they are created. Keep
        // their inflated child params compatible with that contract.
        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new MarginLayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
        }

        @Override
        public LayoutParams generateLayoutParams(AttributeSet attrs) {
            return new MarginLayoutParams(getContext(), attrs);
        }

        @Override
        protected LayoutParams generateLayoutParams(LayoutParams source) {
            return source instanceof MarginLayoutParams
                    ? new MarginLayoutParams((MarginLayoutParams) source)
                    : new MarginLayoutParams(source);
        }

        @Override
        protected boolean checkLayoutParams(LayoutParams params) {
            return params instanceof MarginLayoutParams;
        }

        int contentHeight() {
            return ControlCenterGridGeometry.contentHeightPx(rowCount, rowHeight, gap);
        }

        int scalePercent() { return materialSettings.enabled ? materialSettings.tileScale : 100; }

        int renderedHeight() { return Math.max(1, Math.round(contentHeight() * scalePercent() / 100f)); }

        ControlCenterLayoutPlan.Mode captureMode() {
            if (editorMode != null) return editorMode;
            LinkedHashMap<String, ControlCenterLayoutPlan.Item> items = new LinkedHashMap<>();
            for (Slot slot : slots) items.putIfAbsent(slot.item.id, slot.item);
            return new ControlCenterLayoutPlan.Mode(columns, new ArrayList<>(items.values()));
        }

        int captureSpacing() { return Math.round(gap / getResources().getDisplayMetrics().density); }

        void releaseMaterials() {
            materialsReleased = true;
            removeCallbacks(materialProbe);
            for (Slot slot : slots) {
                if (slot.view.getParent() == this)
                    RuntimeCleanup.run("release slot material ownership",
                            () -> ControlCenterMaterials.releaseGridSurface(slot.view, materialSettings));
                ActiveTileBackground background = activeTileBackgrounds.remove(slot.view);
                if (background != null && slot.view.getBackground() == background)
                    RuntimeCleanup.run("restore fallback tile background",
                            () -> slot.view.setBackground(background.nativeBackground));
            }
        }

        private void markSlotDirty(View source) {
            if (materialsReleased || syncingMaterials) return;
            if (source == this) markAllMaterialsDirty();
            else for (View current = source; current != null && current != this;
                    current = current.getParent() instanceof View parent ? parent : null) {
                Slot slot = slotsByView.get(current);
                if (slot != null) { slot.materialDirty = true; break; }
            }
            invalidate();
        }

        private void markAllMaterialsDirty() {
            for (Slot slot : slots) slot.materialDirty = true;
        }

        private void scheduleMaterialProbes() {
            removeCallbacks(materialProbe);
            materialProbeStage = 0;
            // OEM attachment/theme work can arrive after the first material pass.
            // Probe at 64, 160 and 320 ms, then stop until another lifecycle event.
            if (!materialsReleased) postDelayed(materialProbe, 64L);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (materialsReleased) return;
            markAllMaterialsDirty();
            scheduleMaterialProbes();
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(materialProbe);
            super.onDetachedFromWindow();
        }

        @Override public void invalidate() {
            super.invalidate();
        }

        @Override protected void onConfigurationChanged(Configuration configuration) {
            super.onConfigurationChanged(configuration);
            if (materialsReleased) return;
            updateDecorations(materialSettings);
            markAllMaterialsDirty();
            scheduleMaterialProbes();
            invalidate();
        }

        boolean managesDecoration(View view) {
            for (Slot slot : slots) {
                if (slot.item.type != ControlCenterLayoutPlan.Type.TILE
                        && slot.item.type != ControlCenterLayoutPlan.Type.PAIR) continue;
                for (View current = view; current != null && current != this;
                        current = current.getParent() instanceof View parent ? parent : null) {
                    if (current == slot.view) return true;
                }
            }
            return false;
        }

        void applyMaterials(ControlCenterConfig settings) {
            materialsReleased = false;
            materialSettings = settings;
            syncingMaterials = true;
            for (Slot slot : slots) {
                slot.materialDirty = false;
                ControlCenterMaterials.registerRoot(slot.view, settings);
                if (slot.item.type == ControlCenterLayoutPlan.Type.COMPONENT)
                    syncComponentSurface(slot);
                if (slot.item.type == ControlCenterLayoutPlan.Type.TILE
                        && (slot.item.shape == ControlCenterLayoutPlan.Shape.CIRCLE || nativeTile(slot.view))) {
                    ControlCenterMaterials.registerGridTileShape(slot.view, slot.item);
                    syncTileSurface(slot);
                }
                if (slot.half < 0 && slot.item.type != ControlCenterLayoutPlan.Type.COMPONENT) {
                    boolean card = slot.item.type != ControlCenterLayoutPlan.Type.TILE || slot.item.width > slot.item.height;
                    ControlCenterMaterials.register(slot.view, slot.view.getBackground(),
                            card ? ControlCenterMaterials.Layer.CARD : ControlCenterMaterials.Layer.TILE, settings);
                    if (slot.item.type != ControlCenterLayoutPlan.Type.TILE
                            && slot.item.type != ControlCenterLayoutPlan.Type.PAIR) {
                        ControlCenterMaterials.applySoftGlass(slot.view, settings,
                                card ? ControlCenterMaterials.Layer.CARD : ControlCenterMaterials.Layer.TILE);
                        ControlCenterMaterials.applyGlobalBorder(slot.view, settings);
                    }
                }
            }
            for (Slot slot : slots) {
                if (slot.item.type != ControlCenterLayoutPlan.Type.TILE
                        && slot.item.type != ControlCenterLayoutPlan.Type.PAIR) continue;
                decorations.computeIfAbsent(slot.item.id, ignored -> {
                    View overlay = new DecorationView(getContext());
                    overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    applyShape(overlay, slot.item);
                    GradientDrawable shape = new GradientDrawable();
                    if (slot.item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                        shape.setShape(GradientDrawable.OVAL);
                    } else {
                        shape.setShape(GradientDrawable.RECTANGLE);
                        shape.setCornerRadius(slot.item.cornerRadius > 0
                                ? slot.item.cornerRadius * getResources().getDisplayMetrics().density
                                : radius);
                    }
                    shape.setColor(android.graphics.Color.TRANSPARENT);
                    overlay.setBackground(shape);
                    overlay.setClipToOutline(false);
                    addView(overlay, new MarginLayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT));
                    return new Decoration(overlay, slot.item);
                });
            }
            updateDecorations(settings);
            syncingMaterials = false;
            scheduleMaterialProbes();
        }

        void updateDecorations(ControlCenterConfig settings) {
            boolean contentScaleChanged = materialSettings.tileScale != settings.tileScale
                    || materialSettings.enabled != settings.enabled;
            materialSettings = settings;
            markAllMaterialsDirty();
            for (Slot slot : slots) {
                if (slot.item.type != ControlCenterLayoutPlan.Type.TILE
                        && slot.item.type != ControlCenterLayoutPlan.Type.PAIR) continue;
                ControlCenterSoftGlass.apply(slot.view, false, 0);
                ControlCenterGlobalBorder.apply(slot.view, false, settings.borderWidthDp, false);
            }
            for (Decoration decoration : decorations.values()) {
                View overlay = decoration.view;
                boolean editing = ControlCenterNativeMode.isEditing(overlay);
                boolean card = decoration.item.type != ControlCenterLayoutPlan.Type.TILE
                        || decoration.item.width > decoration.item.height;
                int strength = ControlCenterMaterials.percent(settings,
                        card ? ControlCenterMaterials.Layer.CARD
                                : ControlCenterMaterials.Layer.TILE, editing);
                ControlCenterSoftGlass.apply(overlay, settings.surface, settings.enabled && !editing
                        && settings.surface.hasSoftGlassReflection(), strength);
                ControlCenterGlobalBorder.apply(overlay, settings.enabled && !editing
                        && settings.borderEnabled, settings.borderWidthDp,
                        settings.surface.resolve(overlay.getContext()).neutralForeground() == 0xff202124);
            }
            if (contentScaleChanged) requestLayout();
            // Some surfaces are owned by drawable wrappers and refresh only in dispatchDraw.
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (!materialsReleased) {
                syncingMaterials = true;
                syncNativeSurfaceDrawables();
                syncActiveTileBackgrounds();
                syncingMaterials = false;
            }
            for (Decoration decoration : decorations.values()) positionNativeDecoration(decoration);
            diagnoseDecorationSurfaces();
            super.dispatchDraw(canvas);
            // Draw rims after the content without creating elevated, shadow-casting planes.
            for (Decoration decoration : decorations.values())
                if (decoration.view.getVisibility() == View.VISIBLE)
                    super.drawChild(canvas, decoration.view, getDrawingTime());
        }

        private void diagnoseDecorationSurfaces() {
            for (Decoration decoration : decorations.values()) {
                View view = decoration.view;
                if (decoration.diagnosed || view.getWidth() <= 0 || view.getHeight() <= 0
                        || !diagnosticTile(decoration.item.firstSpec)) continue;
                decoration.diagnosed = true;
                try {
                    StringBuilder layers = new StringBuilder(ControlCenterMaterials.describeTileSurface(view));
                    for (Slot slot : slots) {
                        if (slot.half < 0 && slot.item.id.equals(decoration.item.id))
                            layers.append(" backing=").append(ControlCenterMaterials.describeTileSurface(slot.view));
                    }
                    trace(Log.INFO, "tile decoration layers spec=" + decoration.item.firstSpec + " " + layers);
                } catch (RuntimeException error) {
                    trace(Log.WARN, "tile decoration inspection unavailable", error);
                }
            }
        }

        private void syncNativeSurfaceDrawables() {
            for (Slot slot : slots) {
                // Components can install a fresh background when attached, after
                // the initial material pass completed during holder creation.
                if (slot.item.type == ControlCenterLayoutPlan.Type.COMPONENT) {
                    if (slot.materialRootBackground != slot.view.getBackground())
                        slot.materialDirty = true;
                    if (!slot.materialDirty) continue;
                    ControlCenterMaterials.registerRoot(slot.view, materialSettings);
                    syncComponentSurface(slot);
                    slot.materialRootBackground = slot.view.getBackground();
                    slot.materialDirty = false;
                    continue;
                }
                if (slot.item.type != ControlCenterLayoutPlan.Type.TILE
                        && slot.item.type != ControlCenterLayoutPlan.Type.PAIR) continue;
                View view = slot.view;
                if ((slot.item.type == ControlCenterLayoutPlan.Type.TILE
                        || slot.item.type == ControlCenterLayoutPlan.Type.PAIR)
                        && slot.item.shape != ControlCenterLayoutPlan.Shape.CIRCLE) {
                    boolean active = isActiveTile(slot.item);
                    if (!slot.materialActiveKnown || slot.materialActive != active) {
                        slot.materialActiveKnown = true;
                        slot.materialActive = active;
                        slot.materialDirty = true;
                    }
                }
                if (!slot.materialDirty) continue;
                if (nativeTile(view) && (slot.item.shape == ControlCenterLayoutPlan.Shape.CIRCLE
                        || materialSettings.enabled && slot.item.type == ControlCenterLayoutPlan.Type.TILE)) {
                    ControlCenterMaterials.registerRoot(view, materialSettings);
                    ControlCenterMaterials.registerGridTileShape(view, slot.item);
                    syncTileSurface(slot);
                } else if (view.getClass().getSimpleName().contains("QSCardItemView")) {
                    ControlCenterMaterials.registerRoot(view, materialSettings);
                    ControlCenterMaterials.syncCardView(view, slot.item.withShape(slot.item.shape,
                            slot.item.cornerRadius > 0 ? slot.item.cornerRadius : materialSettings.cornerRadius));
                }
                slot.materialDirty = false;
            }
        }

        private void syncComponentSurface(Slot slot) {
            ControlCenterLayoutPlan.Item shape = slot.item.withShape(slot.item.shape,
                    slot.item.cornerRadius > 0 ? slot.item.cornerRadius : materialSettings.cornerRadius);
            if (!ControlCenterMaterials.syncComponentView(slot.view, shape, materialSettings)) return;
            if (slot.materialDiagnosed || slot.view.getWidth() <= 0 || slot.view.getHeight() <= 0) return;
            slot.materialDiagnosed = true;
            try {
                trace(Log.INFO, "component surface layers spec=" + slot.item.firstSpec + " shape=" + shape.shape
                        + " radius=" + shape.cornerRadius + " " + ControlCenterMaterials.describeTileSurface(slot.view));
            } catch (RuntimeException error) {
                trace(Log.WARN, "component surface inspection unavailable", error);
            }
        }

        private void syncTileSurface(Slot slot) {
            boolean external = slot.item.type == ControlCenterLayoutPlan.Type.TILE && nativeTile(slot.view)
                    && slot.item.shape != ControlCenterLayoutPlan.Shape.CIRCLE;
            ControlCenterMaterials.syncTileView(slot.view, materialSettings, external);
            if (external) {
                for (Slot candidate : slots) {
                    if (candidate.half < 0 && candidate.item.id.equals(slot.item.id))
                        ControlCenterMaterials.applyTileBacking(candidate.view, slot.view, materialSettings);
                }
            }
            if (slot.materialDiagnosed || !diagnosticTile(slot.item.firstSpec)) return;
            View surface = ControlCenterMaterials.tileSurface(slot.view);
            if (surface instanceof android.widget.ImageView image && image.getDrawable() != null
                    && surface.getWidth() > 0 && surface.getHeight() > 0) {
                slot.materialDiagnosed = true;
                try {
                    trace(Log.INFO, "tile surface layers spec=" + slot.item.firstSpec + " shape=" + slot.item.shape
                            + " external=" + external + " " + ControlCenterMaterials.describeTileSurface(slot.view));
                } catch (RuntimeException error) {
                    trace(Log.WARN, "tile surface inspection unavailable", error);
                }
            }
        }

        private static boolean diagnosticTile(String spec) {
            return switch (spec) {
                case "custom_5g", "autobrightness", "papermode", "night" -> true;
                default -> false;
            };
        }

        private void syncActiveTileBackgrounds() {
            for (Decoration decoration : decorations.values()) {
                if (decoration.item.shape != ControlCenterLayoutPlan.Shape.CIRCLE) continue;
                View tile = tileSlot(decoration.item);
                if (tile == null) continue;
                Slot tileSlot = slotsByView.get(tile);
                boolean active = isActiveTile(decoration.item);
                ActiveTileBackground wrapper = activeTileBackgrounds.get(tile);
                boolean editing = ControlCenterNativeMode.isEditing(tile);
                if (!materialSettings.enabled || editing) {
                    // The deferred slot pass can already have cleared the dirty flag.
                    // Release our fallback before considering stable active-state shortcuts.
                    if (wrapper != null) {
                        activeTileBackgrounds.remove(tile);
                        if (tile.getBackground() == wrapper) tile.setBackground(wrapper.nativeBackground);
                    }
                    continue;
                }
                int strength = ControlCenterMaterials.percent(materialSettings,
                        ControlCenterMaterials.Layer.TILE, editing);
                ControlCenterSurface surface = ControlCenterMaterials.surface(materialSettings, editing)
                        .resolve(tile.getContext());
                int surfaceColor = ControlCenterMaterials.color(surface.stateColor(true), strength);
                // A settings refresh can change opacity without changing the active state.
                // Apply it before the stable-state shortcut, including after drawable registration.
                if (wrapper != null && tile.getBackground() == wrapper) wrapper.setSurfaceColor(surfaceColor);
                if (tileSlot != null) {
                    boolean stateChanged = !tileSlot.materialActiveKnown
                            || tileSlot.materialActive != active;
                    tileSlot.materialActiveKnown = true;
                    tileSlot.materialActive = active;
                    if (!tileSlot.materialDirty && !stateChanged && (active || wrapper == null)) continue;
                    tileSlot.materialDirty = true;
                }
                if (ControlCenterMaterials.syncTileView(tile, materialSettings)) {
                    // The OEM ImageView owns the visible background. Do not add
                    // a second surface to the padded QSTileItemView wrapper.
                    ActiveTileBackground stale = activeTileBackgrounds.remove(tile);
                    if (stale != null && tile.getBackground() == stale)
                        tile.setBackground(stale.nativeBackground);
                    continue;
                }
                if (active) {
                    if (tile.getBackground() instanceof ActiveTileBackground current) {
                        current.setSurfaceColor(surfaceColor);
                        if (tileSlot != null) tileSlot.materialDirty = false;
                        continue;
                    }
                    Drawable nativeBackground = tile.getBackground();
                    wrapper = new ActiveTileBackground(nativeBackground, surfaceColor);
                    activeTileBackgrounds.put(tile, wrapper);
                    tile.setBackground(wrapper);
                    if (tileSlot != null) tileSlot.materialDirty = false;
                } else if (wrapper != null && tile.getBackground() == wrapper) {
                    tile.setBackground(wrapper.nativeBackground);
                    activeTileBackgrounds.remove(tile);
                    if (tileSlot != null) tileSlot.materialDirty = false;
                }
            }
        }

        private View tileSlot(ControlCenterLayoutPlan.Item item) {
            for (Slot slot : slots) {
                if (slot.item.id.equals(item.id) && tileState(slot.view) != null)
                    return slot.view;
            }
            return null;
        }

        private Object tileState(View tile) {
            for (String field : new String[] {"state", "customizeState", "tileState"}) {
                Object value = readField(tile, field);
                if (value != null) return value;
            }
            return null;
        }

        private boolean isActiveTile(ControlCenterLayoutPlan.Item item) {
            View tile = tileSlot(item);
            if (tile == null) return false;
            Object state = tileState(tile);
            Object value = state instanceof Number ? state : readField(state, "state");
            return value instanceof Number number && number.intValue() == 2;
        }

        void addTile(View child, ControlCenterLayoutPlan.Item placement, int half) {
            addTile(child, placement, half, null);
        }

        void addTile(View child, ControlCenterLayoutPlan.Item placement, int half,
                ControlCenterSliderLayout sliderLayout) {
            // Native circles/cards/components already paint their stateful surface.
            // A full-slot backing plate behind a padded native circle creates a halo.
            if (half <= 0 && needsBackingSurface(child, placement)) {
                View background = new View(getContext());
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(cardColor());
                configureShape(shape, placement);
                background.setBackground(shape);
                background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                Slot backingSlot = new Slot(background, placement, -1);
                slots.add(backingSlot);
                slotsByView.put(background, backingSlot);
                slotClips.put(background, new Path());
                addView(background, new MarginLayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
            }
            Slot childSlot = new Slot(child, placement, half, sliderLayout);
            slots.add(childSlot);
            slotsByView.put(child, childSlot);
            // Ordinary singleton tiles arrive with half == 0 too; half is only
            // a split indicator when the placement itself is a pair.
            if (nativeCircle(child, placement) || moduleCircle(child, placement)) {
                // The OEM holder includes margins around a smaller, lower icon.
                // The inner surface owns clipping; a second slot circle cuts it.
                child.setClipToOutline(false);
            } else if (placement.type != ControlCenterLayoutPlan.Type.PAIR) {
                applyShape(child, placement);
                slotClips.put(child, new Path());
            } else {
                pairClips.put(child, new Path());
            }
            addView(child, new MarginLayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
        }

        private static boolean needsBackingSurface(View child, ControlCenterLayoutPlan.Item placement) {
            if (placement.type == ControlCenterLayoutPlan.Type.GROUP) return false;
            if (placement.type == ControlCenterLayoutPlan.Type.PAIR) return true;
            if (placement.type == ControlCenterLayoutPlan.Type.COMPONENT) return false;
            String name = child.getClass().getSimpleName();
            if (name.contains("QSCardItemView")) return false;
            if (nativeTile(child)) return placement.shape != ControlCenterLayoutPlan.Shape.CIRCLE;
            return child.getBackground() == null;
        }

        private static boolean nativeTile(View view) {
            return view.getClass().getSimpleName().contains("QSTileItemView");
        }

        private static boolean nativeCircle(View view, ControlCenterLayoutPlan.Item item) {
            return item.type == ControlCenterLayoutPlan.Type.TILE
                    && item.shape == ControlCenterLayoutPlan.Shape.CIRCLE
                    && nativeTile(view);
        }

        private static boolean moduleCircle(View view, ControlCenterLayoutPlan.Item item) {
            return view instanceof ControlCenter5GTileView
                    && item.shape == ControlCenterLayoutPlan.Shape.CIRCLE;
        }

        private void configureShape(GradientDrawable drawable,
                ControlCenterLayoutPlan.Item item) {
            if (item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                drawable.setShape(GradientDrawable.OVAL);
                return;
            }
            int corner = item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                    ? 0 : item.cornerRadius > 0
                    ? Math.round(item.cornerRadius * getResources().getDisplayMetrics().density)
                    : radius;
            drawable.setCornerRadius(corner);
        }

        private void applyShape(View view, ControlCenterLayoutPlan.Item item) {
            ControlCenterMaterials.applyShapeClip(view, item.shape, item.cornerRadius > 0 ? item.cornerRadius
                    : Math.round(radius / getResources().getDisplayMetrics().density));
        }

        private int cardColor() {
            TypedValue value = new TypedValue();
            if (getContext().getTheme().resolveAttribute(
                    android.R.attr.colorControlHighlight, value, true)) {
                try {
                    return value.resourceId == 0 ? value.data
                            : getContext().getColor(value.resourceId);
                } catch (android.content.res.Resources.NotFoundException ignored) {
                    // Use a mode-appropriate translucent surface below.
                }
            }
            boolean night = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return night ? 0x33ffffff : 0x26000000;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int unit = ControlCenterGridGeometry.columnUnitPx(width, columns, gap);
            for (Slot slot : slots) {
                Rect rect = bounds(slot, unit);
                if (slot.sliderLayout != null) slot.sliderLayout.measure(rect.width, rect.height);
                slot.view.measure(MeasureSpec.makeMeasureSpec(rect.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rect.height, MeasureSpec.EXACTLY));
            }
            for (Decoration decoration : decorations.values()) {
                Rect rect = fullBounds(decoration.item, unit);
                decoration.view.measure(MeasureSpec.makeMeasureSpec(rect.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rect.height, MeasureSpec.EXACTLY));
            }
            int height = contentHeight();
            setMeasuredDimension(width, height);
            if (width != lastMeasuredWidth || height != lastMeasuredHeight) {
                lastMeasuredWidth = width;
                lastMeasuredHeight = height;
                trace(Log.INFO, "runtime grid measured width=" + width
                        + " height=" + height + " mode=" + MeasureSpec.getMode(widthMeasureSpec)
                        + " childCount=" + slots.size());
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int unit = Math.max(1, (getWidth() - gap * (columns - 1)) / columns);
            for (Slot slot : slots) {
                Rect rect = bounds(slot, unit);
                slot.view.layout(rect.x, rect.y, rect.x + rect.width,
                        rect.y + rect.height);
                if (slot.item.type != ControlCenterLayoutPlan.Type.PAIR || slot.half < 0) {
                    Path clip = slotClips.get(slot.view);
                    if (clip != null) {
                        clip.reset();
                        addShapePath(clip, slot.item, rect);
                    }
                }
                if (slot.half < 0 && slot.item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                        && (slot.item.type == ControlCenterLayoutPlan.Type.TILE
                        || slot.item.type == ControlCenterLayoutPlan.Type.PAIR)
                        && slot.view.getBackground() instanceof GradientDrawable background) {
                    float corner = Math.min(rect.width, rect.height) / 2f;
                    if (background.getCornerRadius() != corner) {
                        background.setCornerRadius(corner);
                        slot.view.invalidateOutline();
                    }
                }
                if (slot.item.type == ControlCenterLayoutPlan.Type.PAIR && slot.half >= 0) {
                    Path clip = pairClips.get(slot.view);
                    if (clip != null) {
                        clip.reset();
                        // Both native holders are clipped in grid coordinates to the
                        // same whole-pair outline, rather than two independent halves.
                        Rect full = fullBounds(slot.item, unit);
                        float rightEdge = full.x + full.width;
                        float bottomEdge = full.y + full.height;
                        if (slot.item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                            clip.addOval(full.x, full.y, rightEdge, bottomEdge, Path.Direction.CW);
                        } else {
                            float corner = slot.item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                                    ? Math.min(full.width, full.height) / 2f
                                    : slot.item.cornerRadius > 0 ? slot.item.cornerRadius
                                    * getResources().getDisplayMetrics().density : radius;
                            clip.addRoundRect(full.x, full.y, rightEdge, bottomEdge,
                                    corner, corner, Path.Direction.CW);
                        }
                    }
                }
            }
            for (Decoration decoration : decorations.values()) {
                layoutDecoration(decoration, unit);
            }
            alignModuleCircles(unit);
        }

        private void alignModuleCircles(int unit) {
            Slot reference = null;
            View nativeSurface = null;
            for (Slot slot : slots) {
                if (!nativeCircle(slot.view, slot.item) || slot.item.width != 1
                        || slot.item.height != 1) continue;
                View surface = ControlCenterMaterials.tileSurface(slot.view);
                if (surface == null || surface.getWidth() <= 0 || surface.getHeight() <= 0) continue;
                reference = slot;
                nativeSurface = surface;
                break;
            }
            if (reference == null) return;
            Rect nativeBounds = fullBounds(reference.item, unit);
            surfaceBounds.set(0, 0, nativeSurface.getWidth(), nativeSurface.getHeight());
            surfaceMatrix.reset();
            nativeSurface.transformMatrixToGlobal(surfaceMatrix);
            transformMatrixToLocal(surfaceMatrix);
            surfaceMatrix.mapRect(surfaceBounds);
            float insetX = (surfaceBounds.left - nativeBounds.x) / nativeBounds.width;
            float insetY = (surfaceBounds.top - nativeBounds.y) / nativeBounds.height;
            float widthRatio = surfaceBounds.width() / nativeBounds.width;
            float heightRatio = surfaceBounds.height() / nativeBounds.height;
            for (Slot slot : slots) {
                if (!moduleCircle(slot.view, slot.item)) continue;
                Rect bounds = fullBounds(slot.item, unit);
                int width = Math.max(1, Math.round(bounds.width * widthRatio));
                int height = Math.max(1, Math.round(bounds.height * heightRatio));
                int left = bounds.x + Math.round(bounds.width * insetX);
                int top = bounds.y + Math.round(bounds.height * insetY);
                slot.view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                slot.view.layout(left, top, left + width, top + height);
            }
        }

        private void layoutDecoration(Decoration decoration, int unit) {
            if (positionNativeDecoration(decoration)) return;
            Rect rect = fullBounds(decoration.item, unit);
            positionDecoration(decoration.view, rect.x, rect.y, rect.width, rect.height, 1f, 1f);
        }

        private boolean positionNativeDecoration(Decoration decoration) {
            for (Slot slot : slots) {
                if (!slot.item.id.equals(decoration.item.id) || !nativeCircle(slot.view, slot.item)) continue;
                View surface = ControlCenterMaterials.tileSurface(slot.view);
                if (surface == null || surface.getWidth() <= 0 || surface.getHeight() <= 0) return false;
                slot.view.setClipToOutline(false);
                int size = Math.min(surface.getWidth(), surface.getHeight());
                float left = (surface.getWidth() - size) / 2f;
                float top = (surface.getHeight() - size) / 2f;
                surfaceBounds.set(left, top, left + size, top + size);
                surfaceMatrix.reset();
                surface.transformMatrixToGlobal(surfaceMatrix);
                transformMatrixToLocal(surfaceMatrix);
                surfaceMatrix.mapRect(surfaceBounds);
                positionDecoration(decoration.view, surfaceBounds.left, surfaceBounds.top,
                        size, size, surfaceBounds.width() / size, surfaceBounds.height() / size);
                return true;
            }
            return false;
        }

        private static void positionDecoration(View view, float x, float y, int width, int height,
                float scaleX, float scaleY) {
            if (view.getMeasuredWidth() != width || view.getMeasuredHeight() != height) {
                view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
            int left = (int) Math.floor(x), top = (int) Math.floor(y);
            view.layout(left, top, left + width, top + height);
            view.setPivotX(0f);
            view.setPivotY(0f);
            view.setTranslationX(x - left);
            view.setTranslationY(y - top);
            view.setScaleX(scaleX);
            view.setScaleY(scaleY);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof DecorationView) return false;
            Path clip = pairClips.get(child);
            if (clip == null) clip = slotClips.get(child);
            if (clip == null) return super.drawChild(canvas, child, drawingTime);
            int save = canvas.save();
            try {
                canvas.clipPath(clip);
                return super.drawChild(canvas, child, drawingTime);
            } finally {
                canvas.restoreToCount(save);
            }
        }

        private Rect fullBounds(ControlCenterLayoutPlan.Item item, int unit) {
            int x = item.x * (unit + gap);
            int y = (item.y - firstRow) * (rowHeight + gap);
            int width = ControlCenterGridGeometry.spanPx(unit, gap, item.width);
            int height = ControlCenterGridGeometry.spanPx(rowHeight, gap, item.height);
            if (item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                // A grid cell is not guaranteed to be square (the row height is
                // independent from the available column width). Keep circle
                // surfaces circular by centering the smaller dimension.
                int size = Math.min(width, height);
                x += (width - size) / 2;
                y += (height - size) / 2;
                width = height = size;
            }
            return new Rect(x, y, width, height);
        }

        private Rect bounds(Slot slot, int unit) {
            ControlCenterLayoutPlan.Item item = slot.item;
            Rect full = fullBounds(item, unit);
            int x = full.x;
            int y = full.y;
            int width = full.width;
            int height = full.height;
            if (item.type == ControlCenterLayoutPlan.Type.PAIR && slot.half >= 0) {
                if (item.direction == ControlCenterLayoutPlan.Direction.HORIZONTAL) {
                    int first = width / 2;
                    if (slot.half == 0) width = first;
                    else {
                        x += first;
                        width -= first;
                    }
                } else {
                    int first = height / 2;
                    if (slot.half == 0) height = first;
                    else {
                        y += first;
                        height -= first;
                    }
                }
            }
            return new Rect(x, y, Math.max(1, width), Math.max(1, height));
        }

        private void addShapePath(Path path, ControlCenterLayoutPlan.Item item, Rect rect) {
            if (item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                path.addOval(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                        Path.Direction.CW);
                return;
            }
            float density = getResources().getDisplayMetrics().density;
            float corner = item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                    ? Math.min(rect.width, rect.height) / 2f
                    : item.cornerRadius > 0 ? item.cornerRadius * density : radius;
            path.addRoundRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                    Math.max(0f, corner), Math.max(0f, corner), Path.Direction.CW);
        }
    }

    private static final class DecorationView extends View {
        DecorationView(android.content.Context context) { super(context); }
    }

    private static final class Slot {
        boolean materialDiagnosed;
        boolean materialDirty = true;
        boolean materialActive;
        boolean materialActiveKnown;
        Drawable materialRootBackground;
        final View view;
        final ControlCenterLayoutPlan.Item item;
        final int half;
        final ControlCenterSliderLayout sliderLayout;

        Slot(View view, ControlCenterLayoutPlan.Item item, int half) {
            this(view, item, half, null);
        }

        Slot(View view, ControlCenterLayoutPlan.Item item, int half,
                ControlCenterSliderLayout sliderLayout) {
            this.view = view;
            this.item = item;
            this.half = half;
            this.sliderLayout = sliderLayout;
        }
    }

    private static final class Decoration {
        boolean diagnosed;
        final View view;
        final ControlCenterLayoutPlan.Item item;

        Decoration(View view, ControlCenterLayoutPlan.Item item) {
            this.view = view;
            this.item = item;
        }
    }

    /** Draws the active circular surface above the OEM background and below tile children. */
    private static final class ActiveTileBackground extends Drawable {
        final Drawable nativeBackground;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        ActiveTileBackground(Drawable nativeBackground, int color) {
            this.nativeBackground = nativeBackground;
            fill.setColor(color);
        }

        void setSurfaceColor(int color) {
            if (fill.getColor() == color) return;
            fill.setColor(color);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            // Replace the active fill instead of compositing it over another material.
            // Keep the native background solely for restoration when the tile turns off.
            float size = Math.min(bounds.width(), bounds.height());
            float left = bounds.left + (bounds.width() - size) / 2f;
            float top = bounds.top + (bounds.height() - size) / 2f;
            canvas.drawOval(left, top, left + size, top + size, fill);
        }

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            if (nativeBackground != null) nativeBackground.setBounds(bounds);
        }

        @Override public void setAlpha(int alpha) {
            if (fill.getAlpha() == alpha) return;
            fill.setAlpha(alpha);
            invalidateSelf();
        }
        @Override public int getAlpha() { return fill.getAlpha(); }
        @Override public void setColorFilter(ColorFilter filter) {
            fill.setColorFilter(filter);
            if (nativeBackground != null) nativeBackground.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class Rect {
        final int x;
        final int y;
        final int width;
        final int height;

        Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    static Object readField(Object target, String name) {
        return ControlCenterHostAdapter.readField(target, name);
    }

    private static Method method(Class<?> type, String name, int count) {
        return ControlCenterHostAdapter.method(type, name, count);
    }

    static Object call(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        return ControlCenterHostAdapter.call(target, name, args);
    }

    static Object callOptional(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        return ControlCenterHostAdapter.callOptional(target, name, args);
    }

}
