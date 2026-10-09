# Runtime Independence v0.3.193

This local development build addresses two runtime issues reported against 0.3.192.

## Control Center edit row

The custom tile grid replaces a native quick-settings item and reserves the grid's rendered height in the parent RecyclerView. Xiaomi can restore that carrier to its native one-row height while the panel is animating. Height corrections were skipped during transforms, and the previous 0/64 ms checks did not guarantee a later correction. A cancellable, bounded frame check now waits for the carrier to settle before restoring the grid height.

## Disabled settings package

The settings package remains the configuration editor and preview Provider owner. Opening settings or saving committed configuration publishes complete snapshots to SystemUI and Launcher. Receiving host processes require the module's signature permission and persist the snapshot and action key in their own device-protected data. Normal runtime reads only the host snapshot, whether the settings package is enabled or disabled. Runtime-start requests target only the live settings receiver and cannot start the settings process.

Desktop actions use an explicit Launcher-to-SystemUI broadcast signed with a random key copied into both host data stores. SystemUI validates the command signature and, on API 34+, also verifies the shared sending UID. This removes the per-gesture Provider queue. Runtime application reports target a dynamic settings receiver rather than its Provider. The Launcher refreshes the shortcut catalog only when the settings UI requests it; control-center preview state queries are armed by an explicit preview request.

For the first install of this change, leave FusionStatusBar enabled in Package Manager and enabled in the Xposed manager. Restart SystemUI and `com.miui.home` to load the updated hooks, then open FusionStatusBar once so both host snapshots and the bridge key are published. After verifying the configured gestures, the settings package can be disabled with `pm disable`; the Xposed module itself must remain enabled. Re-enable the package to edit settings. If a host was not running when configuration was saved, opening settings again publishes the pending snapshot.

Automated regression tests cover the carrier-height race, cache fallback, Package Manager disable state, authenticated action dispatch, and API 34 sender identity sharing. This build has not been installed or exercised on the target HyperOS device. The on-demand shortcut catalog workflow still needs device validation.
