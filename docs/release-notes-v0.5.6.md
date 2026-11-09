# FusionStatusBar 0.5.6

Version `0.5.6`, `versionCode 220`. Local Release APK signed with the official certificate.

## Changes

- The fused icon in the regular status bar continues to use the existing status-bar icon-size setting. Both regular and fake Control Center status-bar copies use an independent fit scale and are measured within their own host bounds.
- Control Center page-switch alpha uses the notification shade's show/hide easing. The status-bar horizontal movement and gesture-driven progress remain OEM-owned. The custom-panel spring response now retains the OEM's `0.35s` response.

## Build Scope

- Built with `:app:assembleRelease`; unit tests and full Debug/Release Lint were not run for this build, as requested. Gradle's `lintVitalRelease` ran as part of Release assembly.
- Official v3 signing and 16 KiB ZIP alignment were verified. APK identity, SHA-256 and build scope are recorded in `../artifacts/releases/v0.5.6/build-info.json`.
- The APK was not installed on a device, and no LSPosed or target-ROM runtime validation was performed.
