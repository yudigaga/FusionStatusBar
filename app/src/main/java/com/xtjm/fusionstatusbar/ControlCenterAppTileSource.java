package com.xtjm.fusionstatusbar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** References only module-created tiles, never native host-owned instances. */
final class ControlCenterAppTileSource {
    static final class Lease {
        final Object tile;
        private final Runnable destroy;
        private int references = 1;
        Lease(Object tile, Runnable destroy) { this.tile = tile; this.destroy = destroy; }
        Lease retain() { if (references <= 0) throw new IllegalStateException("Released tile"); references++; return this; }
        void release() { if (references > 0 && --references == 0) destroy.run(); }
    }
    private ControlCenterAppTileSource() { }

    static void acquire(List<ControlCenterLayoutPlan.Item> items, Map<String, Object> available,
            Map<String, Lease> previous, Map<String, Lease> pending,
            Supplier<List<ControlCenterAppTiles.Entry>> catalog, Function<String, Lease> create) {
        acquire(items, available, previous, pending, spec -> true, catalog, create);
    }

    static void acquire(List<ControlCenterLayoutPlan.Item> items, Map<String, Object> available,
            Map<String, Lease> previous, Map<String, Lease> pending, java.util.function.Predicate<String> allowed,
            Supplier<List<ControlCenterAppTiles.Entry>> catalog, Function<String, Lease> create) {
        List<ControlCenterAppTiles.Entry> discovered = null;
        for (ControlCenterLayoutPlan.Item item : items) {
            if (item.hidden) continue;
            for (String spec : item.specs()) {
                if (available.containsKey(spec) || !allowed.test(spec)) continue;
                Lease lease = previous.get(spec);
                if (lease != null) lease = lease.retain();
                else {
                    String createSpec = spec;
                    if (spec.startsWith("custom(")) {
                        if (discovered == null) discovered = catalog.get();
                        ControlCenterAppTiles.Entry entry = ControlCenterAppTiles.find(discovered, spec);
                        if (entry != null) createSpec = entry.nativeSpec;
                    }
                    lease = create.apply(createSpec);
                }
                if (lease != null) {
                    pending.put(spec, lease);
                    available.put(spec, lease.tile);
                }
            }
        }
    }
    static void release(Map<String, Lease> leases) {
        for (Lease lease : leases.values()) RuntimeCleanup.run("release application tile", lease::release);
        leases.clear();
    }
}
